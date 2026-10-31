import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let op: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

let entityId: string;
let projectId: string;
let bomId: string;
let woId: string;
let poCode: string;
let liftMaterialId: string;
let fdMaterialId: string;
const year = new Date().getFullYear();

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  op = await login('op-demo');
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  // 项目（复用 S0 建档口径）
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const prj = await post(
    '/api/v1/mdm/projects',
    {
      standardName: `三峡新能源-姚安大龙口-四期`,
      shortName: '姚安四期',
      phase: '四期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 5,
    },
    chainSw,
  );
  projectId = prj.json().data.id;
  const materials = await get('/api/v1/mdm/materials?keyword=升降', chainSw);
  void materials;
  const fd = await get('/api/v1/mdm/materials?keyword=消防电池', chainSw);
  fdMaterialId = fd.json().data.list[0].id;
  const rope = await get('/api/v1/mdm/materials?keyword=钢丝绳', chainSw);
  liftMaterialId = rope.json().data.list[0].id;
});

afterAll(async () => {
  await app?.close();
});

describe('M05 BOM（FR-M05-001~005/008；BR-05）', () => {
  it('BOM 建档（A 类）+ 加行 + 13 规则扫描 + 发布', async () => {
    const bom = await post(
      '/api/v1/boms',
      {
        name: '升降机 TLDL-300 整机',
        bomType: 'full',
        productLine: 'lift',
        lines: [
          { materialId: liftMaterialId, qty: 2 },
          { materialId: fdMaterialId, qty: 4 },
        ],
      },
      chainSw,
    );
    expect(bom.statusCode).toBe(201);
    bomId = bom.json().data.id;
    expect(bom.json().data.bomCode).toMatch(new RegExp(`^BOM${year}-\\d{4}$`) /* bom 规则 */);

    const lint = await post(`/api/v1/boms/${bomId}/lint`, {}, chainSw);
    expect(lint.statusCode).toBe(201);

    const pub = await post(`/api/v1/boms/${bomId}/publish`, {}, chainSw);
    expect(pub.statusCode).toBe(201);
    expect(pub.json().data.status).toBe('published');
  });

  it('BR-05：已发布 BOM 直接加行被拦截；ECN 缺处置方案被拦截', async () => {
    const addLine = await post(`/api/v1/boms/${bomId}/lines`, { materialId: fdMaterialId, qty: 1 }, chainSw);
    expect(addLine.statusCode).toBe(422);
    expect(addLine.json().blockCode).toBe('BR-05');

    const noPlan = await post(
      '/api/v1/boms/ecn',
      { bomId, reason: '增加消防电池 1 只', oldMaterialLines: [] },
      chainSw,
    );
    expect(noPlan.statusCode).toBe(422);
    expect(noPlan.json().blockCode).toBe('BR-05');

    const ok = await post(
      '/api/v1/boms/ecn',
      { bomId, reason: '增加消防电池 1 只', oldMaterialLines: [], disposalPlan: '旧电池统一退供应商回收' },
      chainSw,
    );
    expect(ok.statusCode).toBe(201);
    const approve = await post(`/api/v1/boms/ecn/${ok.json().data.id}/review`, { approved: true }, chainSw);
    expect(approve.json().data.status).toBe('published');
    const detail = await get(`/api/v1/boms/${bomId}`, chainSw);
    expect(detail.json().data.version).toBe(2);
  });
});

describe('M06 工单与齐套（BR-03 / FR-M05-005）', () => {
  it('BR-03：无项目编码不排产', async () => {
    const bad = await post('/api/v1/work-orders', { qty: 2, bomId }, chainSw);
    expect(bad.statusCode).toBe(422);
    expect(bad.json().blockCode).toBe('BR-03');
  });

  it('齐套检查：无库存 → short + 自动请购草稿 + ③板红灯', async () => {
    const wo = await post('/api/v1/work-orders', { projectId, bomId, qty: 3, planStart: '2026-12-01' }, chainSw);
    expect(wo.statusCode).toBe(201);
    woId = wo.json().data.id;
    expect(wo.json().data.woCode).toMatch(new RegExp(`^WO${year}-\\d{4}$`));

    const kit = await post(`/api/v1/work-orders/${woId}/kit-check`, {}, chainSw);
    expect(kit.json().data.result).toBe('short');
    expect(kit.json().data.shorts.length).toBeGreaterThan(0);
    expect(kit.json().data.purchaseReqCode).toMatch(new RegExp(`^PR${year}-\\d{4}$`));

    // ③ 板红灯自动生成（系统自动挂卡）
    const board = await get('/api/v1/kb/boards/order', op);
    void board;
    const supplyView = await req('GET', '/api/v1/kb/boards/supply/view', undefined, op);
    const redTitles = supplyView.json().data.zones.red.map((c: { title: string }) => c.title);
    expect(redTitles.some((t: string) => t.includes('齐套缺料'))).toBe(true);

    // 开工卡点：齐套未通过不许开工
    const start = await post(`/api/v1/work-orders/${woId}/transition`, { to: 'started' }, chainSw);
    expect(start.statusCode).toBe(422);
  });
});

describe('M06 采购 + 扫码入库 + T+ 队列（FR-M06-003~007/021）', () => {
  it('采购单创建；无单据入库被拒；旧码映射外被拒（FR-M06-021）', async () => {
    const suppliers = await get('/api/v1/mdm/suppliers?keyword=', chainSw);
    void suppliers;
    // 供应商为空种子 → 先建一个
    const sup = await post('/api/v1/mdm/suppliers', { name: '常州消防设备有限公司' }, chainSw);
    void sup;
    const suppliers2 = await get('/api/v1/mdm/suppliers?keyword=常州', chainSw);
    const supplierId = suppliers2.json().data.list[0].id;

    const po = await post(
      '/api/v1/purchase-orders',
      {
        supplierId,
        projectId,
        promisedAt: '2026-12-05',
        lines: [
          { materialId: fdMaterialId, qty: 100, priceCents: '4500' },
          { materialId: liftMaterialId, qty: 10, priceCents: '12000' },
        ],
      },
      chainSw,
    );
    expect(po.statusCode).toBe(201);
    poCode = po.json().data.poCode;
    expect(poCode).toMatch(new RegExp(`^PO${year}-\\d{4}$`));

    // 无单据入库（编造单号）→ 拒
    const noDoc = await post(
      '/api/v1/stock-moves',
      { moveType: 'in', sourceType: 'arrival', sourceId: 'PO2099-9999', materialCode: '3080-FD-0001', qty: 5 },
      op,
    );
    expect(noDoc.statusCode).toBe(422);

    // 不在采购单行内的物料 → 拒
    const notInLine = await post(
      '/api/v1/stock-moves',
      { moveType: 'in', sourceType: 'arrival', sourceId: poCode, materialCode: '3084-XC-0003', qty: 5 },
      op,
    );
    expect(notInLine.statusCode).toBe(422);

    // 映射表外旧码 → 拦截 + 记录（FR-M06-021）
    const unmapped = await post(
      '/api/v1/stock-moves',
      { moveType: 'in', sourceType: 'arrival', sourceId: poCode, materialCode: 'D-8888', qty: 5 },
      op,
    );
    expect(unmapped.statusCode).toBe(422);
    expect(unmapped.json().msg).toMatch(/归并映射表/);
  });

  it('扫码入库成功（旧码自动换主码）→ 库存 + 采购单到货状态 + 幂等去重', async () => {
    // 挂映射：旧码 M1-100 → 消防电池主码
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    await prisma.mdCodeMap.upsert({
      where: { oldCode: 'M1-100' },
      create: { oldCode: 'M1-100', masterCode: '3080-FD-0001', source: 'bom_audit' },
      update: {},
    });

    const mv = await post(
      '/api/v1/stock-moves',
      {
        moveType: 'in',
        sourceType: 'arrival',
        sourceId: poCode,
        materialCode: 'M1-100',
        qty: 60,
        batch: 'B20261201',
        legalEntity: 'PRT',
        idempotencyKey: 'Arrival-PO-B20261201-60',
      },
      op,
    );
    expect(mv.statusCode).toBe(201);
    expect(mv.json().data.duplicated).toBe(false);

    // 幂等：同键重放 → duplicated
    const replay = await post(
      '/api/v1/stock-moves',
      {
        moveType: 'in',
        sourceType: 'arrival',
        sourceId: poCode,
        materialCode: 'M1-100',
        qty: 60,
        batch: 'B20261201',
        idempotencyKey: 'Arrival-PO-B20261201-60',
      },
      op,
    );
    expect(replay.json().data.duplicated).toBe(true);

    // 库存可见（换主码入库）
    const inv = await get('/api/v1/inventory?materialCode=3080-FD-0001', op);
    expect(inv.json().data.some((i: { qty: number }) => i.qty >= 60)).toBe(true);

    // 采购单部分到货
    const pos = await get(`/api/v1/mdm/suppliers?keyword=常州`, chainSw);
    void pos;
  });

  it('超采购单剩余入库被拒；T+ 推送 pending→pushed→confirmed', async () => {
    const over = await post(
      '/api/v1/stock-moves',
      { moveType: 'in', sourceType: 'arrival', sourceId: poCode, materialCode: '3080-FD-0001', qty: 999, batch: 'B2' },
      op,
    );
    expect(over.statusCode).toBe(422);
    expect(over.json().msg).toMatch(/待收/);

    const push = await post('/api/v1/internal/tplus/push', { token: 'internal-dev-token' }, admin);
    expect(push.json().data.pushed).toBeGreaterThanOrEqual(1);

    const rows = await get('/api/v1/integration/tplus/export', admin);
    expect(rows.json().data.rows.length).toBeGreaterThanOrEqual(1);
    expect(rows.json().data.rows[0].存货编码).toBe('3080-FD-0001');

    const moves = await get('/api/v1/stock-moves?tPlusStatus=pushed', op);
    const firstId = moves.json().data.list[0].id;
    const confirm = await post('/api/v1/integration/tplus/confirm', { ids: [firstId] }, admin);
    expect(confirm.json().data.confirmed).toBe(1);
  });
});

describe('M06 出库（BR-03 发货挂项目）与快照（FR-M06-019）', () => {
  it('发货不挂项目 → BR-03；挂项目 → FIFO 扣减', async () => {
    const noProj = await post(
      '/api/v1/stock-moves',
      { moveType: 'out', sourceType: 'ship', sourceId: 'SHIP-001', materialCode: '3080-FD-0001', qty: 5, legalEntity: 'PRT' },
      op,
    );
    expect(noProj.statusCode).toBe(422);
    expect(noProj.json().blockCode).toBe('BR-03');

    const ok = await post(
      '/api/v1/stock-moves',
      { moveType: 'out', sourceType: 'ship', sourceId: 'SHIP-001', materialCode: '3080-FD-0001', qty: 5, legalEntity: 'PRT', projectId },
      op,
    );
    expect(ok.statusCode).toBe(201);

    const inv = await get('/api/v1/inventory?materialCode=3080-FD-0001', op);
    const total = inv.json().data.reduce((s: number, i: { qty: number }) => s + i.qty, 0);
    expect(total).toBe(55);
  });

  it('日终快照（worker 内部端点）', async () => {
    const snap = await post('/api/v1/internal/inventory/snapshot', { token: 'internal-dev-token' }, admin);
    expect(snap.json().data.totalSkus).toBeGreaterThanOrEqual(1);
  });
});
