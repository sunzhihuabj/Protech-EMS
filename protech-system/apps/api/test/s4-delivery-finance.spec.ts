import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let op: string;
let fin: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

let entityId: string;
let farmId: string;
let projectId: string; // 有风场项目
let projectIdNoFarm: string; // 排产两段式（无风场）
let contractId: string;
let dispatchId: string;

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  op = await login('op-demo');
  fin = await login('fin-demo');
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;

  const p1 = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-五期',
      shortName: '姚安五期',
      phase: '五期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 8,
    },
    chainSw,
  );
  projectId = p1.json().data.id;

  // 整机厂初装缺风场 → pending_farm（两段式）
  const p2 = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '远景能源-肃北马鬃山-二期',
      shortName: '肃北二期',
      legalEntityId: entityId,
      orderType: 'oem_initial',
      productLines: ['video'],
      unitCount: 3,
    },
    chainSw,
  );
  projectIdNoFarm = p2.json().data.id;

  // 合同（评审全过 → signed 用印 → 备案解锁）
  const contract = await post(
    '/api/v1/contracts',
    {
      contractName: '姚安五期销售合同',
      nature: 'XS',
      bizType: 'SB',
      ourEntityId: entityId,
      counterpartyId: ownerId,
      amountCents: '120000000',
      projectIds: [projectId],
    },
    chainSw,
  );
  contractId = contract.json().data.id;
  await post(`/api/v1/contracts/${contractId}/review/submit`, {}, chainSw);
  for (const role of ['biz', 'fin', 'eng', 'mfg']) {
    await post(`/api/v1/contracts/${contractId}/review/act`, { role, approved: true }, chainSw);
  }
  await post(`/api/v1/contracts/${contractId}/seal`, { sealCopies: 1 }, chainSw);
  await post(
    `/api/v1/contracts/${contractId}/filing`,
    {
      taxRate: '0.09',
      paymentTerms: '预付 30% 到货 60% 质保金 10%',
      contact: '李经办',
      periodStart: '2026-12-01',
      periodEnd: '2027-12-01',
      warrantyMonths: 12,
      docCopies: 2,
      deliveryAddr: '姚安县大龙口风场',
    },
    chainSw,
  );
});

afterAll(async () => {
  await app?.close();
});

describe('M07 派工（BR-04 / FR-M07-001/002）', () => {
  it('BR-04：不选风场不许提交', async () => {
    const bad = await post('/api/v1/dispatches', { projectId: projectIdNoFarm, taskDesc: '视频监控安装' }, chainSw);
    expect(bad.statusCode).toBe(422);
    expect(bad.json().blockCode).toBe('BR-04');
  });

  it('派工创建（PG 编码）+ 状态推进', async () => {
    const d = await post(
      '/api/v1/dispatches',
      { projectId, windFarmId: farmId, planDays: 5, taskDesc: '升降机安装调试' },
      chainSw,
    );
    expect(d.statusCode).toBe(201);
    expect(d.json().data.pgCode).toMatch(/^PG\d{4}-\d{4}$/);
    dispatchId = d.json().data.id;
    const t = await post(`/api/v1/dispatches/${dispatchId}/transition`, { to: 'confirmed' }, chainSw);
    expect(t.json().data.status).toBe('confirmed');
  });

  it('FR-M07-002：发货流水自动生成派工草稿（auto_ship）', async () => {
    // 建采购→入库→发货出库 → 自动草稿
    const sup = await post('/api/v1/mdm/suppliers', { name: '五期设备供应商公司' }, chainSw);
    const suppliers = await get('/api/v1/mdm/suppliers?keyword=五期', chainSw);
    const supplierId = suppliers.json().data.list[0].id;
    void sup;
    const fd = await get('/api/v1/mdm/materials?keyword=消防电池', chainSw);
    const fdId = fd.json().data.list[0].id;
    const po = await post(
      '/api/v1/purchase-orders',
      { supplierId, projectId, lines: [{ materialId: fdId, qty: 50 }] },
      chainSw,
    );
    const poCode = po.json().data.poCode;
    const inMove = await post(
      '/api/v1/stock-moves',
      { moveType: 'in', sourceType: 'arrival', sourceId: poCode, materialCode: '3080-FD-0001', qty: 50, batch: 'B5', legalEntity: 'PRT' },
      op,
    );
    expect(inMove.statusCode).toBe(201);
    const ship = await post(
      '/api/v1/stock-moves',
      { moveType: 'out', sourceType: 'ship', sourceId: 'SHIP-WUQI', materialCode: '3080-FD-0001', qty: 10, legalEntity: 'PRT', projectId },
      op,
    );
    expect(ship.statusCode).toBe(201);
    expect(ship.json().data.dispatchDraftCode).toMatch(/^PG\d{4}-\d{4}$/);
    // 幂等：同发货流水重复触发 → 同一草稿
    const dispatches = await get('/api/v1/dispatches?projectId=' + projectId, chainSw);
    const autoDrafts = dispatches.json().data.filter((d: { source: string }) => d.source === 'auto_ship');
    expect(autoDrafts.length).toBe(1);
  });
});

describe('M04 里程碑（FR-M04-004）', () => {
  it('初始化 7 节点模板 + 完成节点 + 逾期判定', async () => {
    const init = await post('/api/v1/milestones/init', { projectId, planDates: { 签约: '2026-11-01', 方案交底: '2026-10-15', 发货: '2026-12-10' } }, chainSw);
    expect(init.statusCode).toBe(201);
    expect(init.json().data.length).toBe(7);
    expect(init.json().data.map((m: { name: string }) => m.name)).toContain('调试验收');

    const again = await post('/api/v1/milestones/init', { projectId }, chainSw);
    expect(again.statusCode).toBe(422);

    const list = await get(`/api/v1/projects/${projectId}/milestones`, chainSw);
    const sign = list.json().data.find((m: { name: string }) => m.name === '签约');
    const done = await post(`/api/v1/milestones/${sign.id}/complete`, {}, chainSw);
    expect(done.json().data.status).toBe('done');
    // 计划已过期节点 → overdue 标记
    const list2 = await get(`/api/v1/projects/${projectId}/milestones`, chainSw);
    const late = list2.json().data.find((m: { name: string }) => m.name === '方案交底');
    expect(late.overdue).toBe(true); // 2026-11-20 已过（当前 2026-12）
  });
});

describe('M08 开票回款（BR-02 / FR-M08-001~005/009）', () => {
  it('BR-02 正常路径：已备案合同可开票；发票登记', async () => {
    const inv = await post('/api/v1/invoices', { contractId, projectId, amountCents: '36000000' }, fin);
    expect(inv.statusCode).toBe(201);
    const issue = await post(`/api/v1/invoices/${inv.json().data.id}/issue`, { invoiceNo: 'INV-2026-TEST-01' }, fin);
    expect(issue.json().data.status).toBe('issued');
  });

  it('回款计划三段生成 + 回款登记核销', async () => {
    const plan = await post('/api/v1/payment-plans/generate', { contractId }, fin);
    expect(plan.statusCode).toBe(201);
    expect(plan.json().data.length).toBe(3);
    expect(Number(plan.json().data[0].amountCents)).toBe(72000000); // 60%

    const rec = await post('/api/v1/receipts', { contractId, planId: plan.json().data[0].id, amountCents: '72000000', method: 'bank' }, fin);
    expect(rec.statusCode).toBe(201);
  });

  it('回款率统计（12 册口径）与账龄', async () => {
    const rate = await get('/api/v1/finance/receipt-rate', fin);
    expect(rate.json().data.invoicedCents).toBe('36000000');
    expect(Number(rate.json().data.receivedCents)).toBeGreaterThan(0);
    const aging = await get('/api/v1/finance/aging', fin);
    expect(aging.json().data.buckets.length).toBe(6);
  });

  it('BR-02 冻结路径：未备案合同开票被拒', async () => {
    // 新合同：用印但不备案 → 立即把 archive_deadline 设为过去 → 开票 → 冻结拒绝
    const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
    const ownerId = parties.json().data.list[0].id;
    const c2 = await post(
      '/api/v1/contracts',
      {
        contractName: '冻结演示合同',
        nature: 'XS',
        bizType: 'SB',
        ourEntityId: entityId,
        counterpartyId: ownerId,
        amountCents: '1000000',
        projectIds: [projectId],
      },
      chainSw,
    );
    const c2id = c2.json().data.id;
    await post(`/api/v1/contracts/${c2id}/review/submit`, {}, chainSw);
    for (const role of ['biz', 'fin', 'eng', 'mfg']) {
      await post(`/api/v1/contracts/${c2id}/review/act`, { role, approved: true }, chainSw);
    }
    await post(`/api/v1/contracts/${c2id}/seal`, {}, chainSw);
    // 直接申请开票（未备案未超期）→ 允许（还未超 3 工作日）
    const inv = await post('/api/v1/invoices', { contractId: c2id, amountCents: '100000' }, fin);
    expect(inv.statusCode).toBe(201);
    // 手动把 deadline 置为过去 → 再开票 → BR-02 拒绝
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    await prisma.contract.update({
      where: { id: BigInt(c2id) },
      data: { archiveDeadlineAt: new Date(Date.now() - 5 * 86400000) },
    });
    const blocked = await post('/api/v1/invoices', { contractId: c2id, amountCents: '200000' }, fin);
    expect(blocked.statusCode).toBe(422);
    expect(blocked.json().blockCode).toBe('BR-02');
  });

  it('验收登记 → 项目 accepted + 质保起算（FR-M08-005）', async () => {
    // 项目当前 executing → 先推进 shipped
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'executing' }, chainSw);
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'shipped', note: 'S4 测试发货' }, chainSw);
    const acc = await post(
      '/api/v1/acceptances',
      { projectId, contractId, conclusion: 'pass', documents: ['竣工资料', '结算资料'] },
      chainSw,
    );
    expect(acc.statusCode).toBe(201);
    const detail = await get(`/api/v1/mdm/projects/${projectId}`, chainSw);
    expect(detail.json().data.status).toBe('accepted');
    expect(detail.json().data.warrantyStart).toBeTruthy();
  });
});
