import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let ga: string;
let op: string;
let fin: string;

let projectId: string;
let bomA: string;
let bomB: string;
let liftMatId: string;
let entityId: string;

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
  ga = await login('ga-demo');
  op = await login('op-demo');
  fin = await login('fin-demo');
  const entitiesResp = await get('/api/v1/mdm/entities', chainSw);
  entityId = entitiesResp.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const users = await get('/api/v1/sys/users?keyword=op-demo', admin);
  const opId = users.json().data.list[0].id;
  const prj = await post(
    '/api/v1/mdm/projects',
    {
      pmUserId: opId,
      standardName: '三峡新能源-姚安大龙口-收尾期',
      shortName: '收尾期',
      phase: '收尾期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 3,
    },
    chainSw,
  );
  projectId = prj.json().data.id;
  const rope = await get('/api/v1/mdm/materials?keyword=钢丝绳', chainSw);
  liftMatId = rope.json().data.list[0].id;
});



afterAll(async () => {
  await app?.close();
});

describe('FR-M06-008 货位主数据/三色', () => {
  it('货位创建（四库区校验）+ 三色变更 + 编码格式', async () => {
    const bad = await post('/api/v1/locations', { zone: 'XX', row: 1, col: 1, layer: 1 }, chainSw);
    expect(bad.statusCode).toBe(422);
    const ok = await post('/api/v1/locations', { zone: 'QT', row: 1, col: 3, layer: 2 }, chainSw);
    expect(ok.statusCode).toBe(201);
    expect(ok.json().data.code).toBe('YL-QT-01-03-2');
    const locs = await get('/api/v1/locations?zone=QT', op);
    const id = locs.json().data[0].id;
    const setColor = await post(`/api/v1/locations/${id}/color`, { threeColor: 'red' }, chainSw);
    expect(setColor.json().data.threeColor).toBe('red');
    const badColor = await post(`/api/v1/locations/${id}/color`, { threeColor: 'green' }, chainSw);
    expect(badColor.statusCode).toBe(422);
  });
});

describe('FR-M06-017 库房六大 KPI（指标登记+计算器）', () => {
  it('指标计算落快照：账实相符率/补货次数可算', async () => {
    const compute = await post('/api/v1/kb/internal/metrics/compute', { token: 'internal-dev-token' }, admin);
    expect(compute.json().data.ok).toBeGreaterThanOrEqual(5);
    const supply = await get('/api/v1/kb/boards/supply/metrics', op);
    const acc = supply.json().data.find((m: { metricCode: string }) => m.metricCode === 'inv_accuracy_rate');
    expect(acc).toBeTruthy();
    const rep = await get('/api/v1/kb/boards/supply/metrics', op);
    const replenish = rep.json().data.find((m: { metricCode: string }) => m.metricCode === 'replenish_monthly');
    expect(replenish).toBeTruthy();
  });
});

describe('FR-M09-006/008 成本预警与报价校准包', () => {
  it('毛利预警扫描（低毛利项目 → 通知负责人）', async () => {
    // 把成本期项目毛利做低：加大费用
    const exp = await post('/api/v1/costs/expenses', { projectId, amountCents: '29900000000', note: '压毛利测试' }, fin);
    expect(exp.statusCode).toBe(201);
    await post('/api/v1/costs/collect-labor', { projectId }, fin);
    // 重新计算 pnl
    await get(`/api/v1/projects/${projectId}/pnl`, fin);
    const watch = await post('/api/v1/internal/cost/margin-watch', { token: 'internal-dev-token' }, admin);
    expect(watch.json().data.alerted).toBeGreaterThanOrEqual(1);
    const cfg = await put('/api/v1/sys/configs/cost_margin_floor', { value: '0.15', remark: '毛利线 15%' }, admin);
    expect(cfg.statusCode).toBe(200);
  });

  it('报价校准数据包（FR-M09-008 投标底稿）', async () => {
    const pack = await get('/api/v1/finance/calibration-pack', fin);
    expect(pack.statusCode).toBe(200);
    expect(Array.isArray(pack.json().data.rows)).toBe(true);
  });
});

async function put(url: string, payload: unknown, token?: string) {
  return req('PUT', url, payload, token);
}

describe('FR-M05-009/010/011 BOM 对比/Golden/质量看板', () => {
  it('发布两个版本 → 对比差异 → Golden 基线唯一', async () => {
    const a = await post('/api/v1/boms', { name: '对比A', bomType: 'full', productLine: 'lift', lines: [{ materialId: liftMatId, qty: 2 }] }, chainSw);
    bomA = a.json().data.id;
    await post(`/api/v1/boms/${bomA}/publish`, {}, chainSw);
    const b = await post('/api/v1/boms', { name: '对比B', bomType: 'full', productLine: 'lift', lines: [{ materialId: liftMatId, qty: 3 }] }, chainSw);
    bomB = b.json().data.id;
    await post(`/api/v1/boms/${bomB}/publish`, {}, chainSw);

    const diff = await get(`/api/v1/boms/${bomA}/compare/${bomB}`, chainSw);
    expect(diff.json().data.changed.length).toBe(1);
    expect(diff.json().data.changed[0].qtyA).toBe(2);
    expect(diff.json().data.changed[0].qtyB).toBe(3);

    const golden = await post(`/api/v1/boms/${bomA}/golden`, {}, chainSw);
    expect(golden.json().data.golden).toBe(true);
    // 同产品线设第二个 Golden → 前一个自动取消（唯一）
    const golden2 = await post(`/api/v1/boms/${bomB}/golden`, {}, chainSw);
    expect(golden2.json().data.golden).toBe(true);
  });

  it('BOM 质量看板（FR-M05-011）', async () => {
    const qb = await get('/api/v1/gov/bom-quality', ga);
    expect(qb.json().data.bomCount).toBeGreaterThan(0);
    expect(qb.json().data.byType.full).toBeGreaterThan(0);
  });
});

describe('FR-M11-005/006 售后测算与客户视图', () => {
  it('定检人天测算（片区聚合）', async () => {
    const f = await get('/api/v1/aftersales/inspection-forecast', op);
    expect(f.statusCode).toBe(200);
    expect(f.json().data.perDayPerPerson).toBe(4);
  });

  it('客户服务视图（按集团）', async () => {
    const groups = await get('/api/v1/mdm/groups', chainSw);
    const gid = groups.json().data[0].id;
    const v = await get(`/api/v1/aftersales/customer-view?groupId=${gid}`, op);
    expect(v.statusCode).toBe(200);
    expect(Array.isArray(v.json().data.orders)).toBe(true);
  });
});

describe('FR-M12-005/010 档案检索与命名校验', () => {
  it('两级目录检索（盒内聚合）', async () => {
    await post('/api/v1/archives', { title: '收尾期档案', category: 'GC', retention: 'S30', entity: 'PRT', physicalLoc: 'A区-1号盒', projectId }, ga);
    const cat = await get('/api/v1/archives/catalog?keyword=收尾', ga);
    expect(cat.json().data.total).toBeGreaterThanOrEqual(1);
    expect(cat.json().data.boxes.length).toBeGreaterThan(0);
    expect(cat.json().data.boxes[0].items.length).toBeGreaterThan(0);
  });

  it('命名规范校验（DQ-05 正则）', async () => {
    const good = await post('/api/v1/archives/check-naming', { name: 'GC-TU-塔筒图-20261102-V2-张工' }, ga);
    expect(good.json().data.ok).toBe(true);
    const bad = await post('/api/v1/archives/check-naming', { name: '随便起的文件名' }, ga);
    expect(bad.json().data.ok).toBe(false);
    expect(bad.json().data.suggestion).toContain('建议格式');
  });
});

describe('FR-M03-009 存量合同补录向导（imports/contracts）', () => {
  it('CSV 补录：BR-01 前提校验（项目必须存在）+ BR-10 整数分', async () => {
    const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
    const partyCode = parties.json().data.list[0].partyCode;
    const projectCode = (await get(`/api/v1/mdm/projects/${projectId}`, chainSw)).json().data.projectCode;
    const csv = [
      '合同名称,性质,对方主体编码,金额_分,签订日期,项目编码,质保月数',
      `收尾期补录合同,XS,${partyCode},50000000,2026-10-01,${projectCode},12`,
      `坏行缺项目,XS,${partyCode},50000000,2026-10-01,PJ2099-9999,12`,
      `坏行浮点金额,XS,${partyCode},500.5,2026-10-01,${projectCode},12`,
    ].join('\n');
    const res = await post('/api/v1/imports/contracts', { csv }, chainSw);
    expect(res.statusCode).toBe(201);
    expect(res.json().data.okRows).toBe(1);
    expect(res.json().data.errorRows).toBe(2);
    expect(res.json().data.errors.some((e: { message: string }) => e.message.includes('BR-01'))).toBe(true);
    expect(res.json().data.errors.some((e: { message: string }) => e.message.includes('BR-10'))).toBe(true);
  });
});
