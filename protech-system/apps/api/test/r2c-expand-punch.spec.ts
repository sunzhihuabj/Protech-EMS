import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let op: string;

let entityId: string;
let projectId: string;
let dispatchId: string;
let sectionBomId: string;
let engBomId: string;
let liftMatId: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  op = await login('op-demo');
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const prj = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-参数化期',
      shortName: '参数化期',
      phase: '参数化期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['ladder'],
      unitCount: 2,
    },
    chainSw,
  );
  projectId = prj.json().data.id;
  const rope = await get('/api/v1/mdm/materials?keyword=钢丝绳', chainSw);
  liftMatId = rope.json().data.list[0].id;
  const fd = await get('/api/v1/mdm/materials?keyword=消防电池', chainSw);
  const fdId = fd.json().data.list[0].id;

  // B 类标准节 BOM
  const section = await post(
    '/api/v1/boms',
    { name: '爬梯标准节', bomType: 'full', productLine: 'ladder', lines: [{ materialId: liftMatId, qty: 4 }] },
    chainSw,
  );
  sectionBomId = section.json().data.id;
  void fdId;
});

afterAll(async () => {
  await app?.close();
});

describe('M05 B 类参数化展开（FR-M05-006）', () => {
  it('参数模板 + 展开：塔高 30m → ceil(30/3)=10 节 + 附件包', async () => {
    // 附件包 BOM
    const acc = await post(
      '/api/v1/boms',
      { name: '爬梯附件包', bomType: 'full', productLine: 'ladder', lines: [{ materialId: liftMatId, qty: 2 }] },
      chainSw,
    );
    const tpl = await post(
      '/api/v1/param-templates',
      {
        templateCode: 'LADDER-STD',
        productLine: 'ladder',
        standardSectionBomId: sectionBomId,
        accessoryPackBomId: acc.json().data.id,
        params: [
          { key: 'height', label: '塔高(m)' },
          { key: 'sectionHeight', label: '每节高度(m)', perSection: true },
        ],
      },
      chainSw,
    );
    expect(tpl.statusCode).toBe(201);

    const expand = await post('/api/v1/boms/expand', { templateCode: 'LADDER-STD', params: { height: 30, sectionHeight: 3 } }, chainSw);
    expect(expand.statusCode).toBe(201);
    expect(expand.json().data.sectionCount).toBe(10);

    // 展开 BOM 行数=标准节行×1+附件包行×1，数量放大 10 倍
    const detail = await get(`/api/v1/boms/${expand.json().data.id}`, chainSw);
    expect(detail.json().data.bomType).toBe('param');
    expect(Number(detail.json().data.lines[0].qty)).toBe(40); // 4×10
  });
});

describe('M05 C 类工程 BOM 与套件包（FR-M05-007）', () => {
  it('套件包（机舱消防套件）→ 工程 BOM 点位展开', async () => {
    const kit = await post(
      '/api/v1/kit-packs',
      { kitCode: 'KIT-FIRE-01', name: '机舱消防套件', lines: [{ materialId: liftMatId, qty: 1 }] },
      chainSw,
    );
    expect(kit.statusCode).toBe(201);

    const eng = await post(
      '/api/v1/boms/eng',
      {
        name: '智慧机舱消防工程 BOM',
        projectId,
        productLine: 'fire',
        positions: [
          { kitCode: 'KIT-FIRE-01', positionCode: '机舱' },
          { materialId: liftMatId, qty: 2, positionCode: '塔底' },
        ],
      },
      chainSw,
    );
    expect(eng.statusCode).toBe(201);
    engBomId = eng.json().data.id;

    const detail = await get(`/api/v1/boms/${engBomId}`, chainSw);
    expect(detail.json().data.bomType).toBe('eng');
    expect(detail.json().data.lines.length).toBe(2); // 套件 1 行 + 塔底 1 行
    expect(detail.json().data.lines[0].positionCode).toBe('机舱');
  });
});

describe('M07 点位销项（FR-M07-003）', () => {
  let punchItemId: string;

  it('派工单（BR-04 补风场）→ 生成点位清单 → 逐项销项', async () => {
    const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
    const farmId = farms.json().data.list[0].id;
    const d = await post('/api/v1/dispatches', { projectId, windFarmId: farmId, taskDesc: '消防安装' }, chainSw);
    dispatchId = d.json().data.id;

    const gen = await post(`/api/v1/dispatches/${dispatchId}/punch-list`, { bomId: engBomId }, chainSw);
    expect(gen.json().data.generated).toBe(2);

    const dup = await post(`/api/v1/dispatches/${dispatchId}/punch-list`, { bomId: engBomId }, chainSw);
    expect(dup.statusCode).toBe(422); // 防重复生成

    const list = await get(`/api/v1/dispatches/${dispatchId}/punch-list`, op);
    expect(list.json().data.items.length).toBe(2);
    punchItemId = list.json().data.items[0].id;

    const p1 = await post(`/api/v1/punch-items/${punchItemId}/punch`, { doneQty: 1 }, op);
    // 总点位 = 机舱套件 1 + 塔底 2 = 3
    expect(p1.json().data.progress).toBeCloseTo(1 / 3, 4);
  });

  it('缺件标记 → 红灯联动（⑤板）', async () => {
    const punch = await post(`/api/v1/punch-items/${punchItemId}/punch`, { doneQty: 0, shortage: '钢丝绳缺 2 根' }, op);
    expect(punch.json().data.progress).toBe(0);
    const view = await req('GET', '/api/v1/kb/boards/delivery/view', undefined, op);
    const redTitles = view.json().data.zones.red.map((c: { title: string }) => c.title);
    expect(redTitles.some((t: string) => t.includes('销项缺件'))).toBe(true);
  });
});
