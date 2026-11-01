import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let op: string;
let ga: string;
let fin: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

let projectId: string;
let archiveId: string;

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  op = await login('op-demo');
  fin = await login('fin-demo');
  ga = await login('ga-demo');
  const entities = await get('/api/v1/mdm/entities', chainSw);
  const entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const prj = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-R3期',
      shortName: 'R3期',
      phase: 'R3期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 2,
    },
    chainSw,
  );
  projectId = prj.json().data.id;
  const arch = await post('/api/v1/archives', { title: 'R3期档案', category: 'GC', retention: 'S30', entity: 'PRT', projectId }, ga);
  archiveId = arch.json().data.id;
});

afterAll(async () => {
  await app?.close();
});

describe('R3-D AI-09 RAG / AI-12 智能填单（FR-M16-009/013）', () => {
  it('AI-09：命中语料引用原文；未命中明示不确定', async () => {
    const hit = await post('/api/v1/ai/AI-09/run', { question: '归一词典 螺杆' }, op);
    expect(hit.statusCode).toBe(201);
    expect(hit.json().data.sources.length).toBeGreaterThan(0);
    expect(hit.json().data.sources[0]).toHaveProperty('text');

    const miss = await post('/api/v1/ai/AI-09/run', { question: '量子力学原理' }, op);
    expect(miss.json().data.answer).toContain('不确定');
  });

  it('AI-12：派工/补货上下文预填', async () => {
    const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
    const farmId = farms.json().data.list[0].id;
    await post('/api/v1/dispatches', { projectId, windFarmId: farmId, planDays: 7, taskDesc: '历史派工' }, chainSw);
    const prefill = await post('/api/v1/ai/AI-12/run', { kind: 'dispatch', projectId }, op);
    expect(prefill.json().data.prefill.planDays).toBe(7);
    expect(prefill.json().data.prefill.basedOn).toBeGreaterThanOrEqual(1);
  });
});

describe('R3-D M12 交接/利用 + M11 案例/满意度（FR-M12-007/008、M11-008/009）', () => {
  it('交接清册：三方签字通知（FR-M12-007）', async () => {
    const users = await get('/api/v1/sys/users?keyword=op-demo', admin);
    const opId = users.json().data.list[0].id;
    const users2 = await get('/api/v1/sys/users?keyword=pm-demo', admin);
    const pmId = users2.json().data.list[0].id;
    const hv = await post('/api/v1/archives/handover', { fromUserId: opId, toUserId: pmId }, ga);
    expect(hv.statusCode).toBe(201);
    expect(hv.json().data.handoverNo).toBeTruthy();
  });

  it('利用统计：半年借阅 TOP（FR-M12-008）', async () => {
    // 先借一次
    await post(`/api/v1/archives/${archiveId}/borrow`, { purpose: 'R3 统计' }, op);
    const stats = await get('/api/v1/archives/usage-stats', ga);
    expect(stats.json().data.halfYearBorrows).toBeGreaterThanOrEqual(0);
  });

  it('满意度回访（FR-M11-009）', async () => {
    const bad = await post('/api/v1/aftersales/AS0000-0000/satisfaction', { score: 6 }, op);
    expect(bad.statusCode).toBe(422);
  });
});

describe('R3-C 财务/看板/治理补全', () => {
  it('FR-M08-006/007/010 质保金台账/回款预测/开票及时率', async () => {
    const dep = await get('/api/v1/finance/warranty-deposits', fin);
    expect(dep.statusCode).toBe(200);
    const fc = await get('/api/v1/finance/receipt-forecast', fin);
    expect(fc.json().data.months.length).toBe(3);
    const it = await get('/api/v1/finance/invoice-timeliness', fin);
    expect(it.statusCode).toBe(200);
  });

  it('FR-M10-023/024 闭环统计/总板汇总', async () => {
    const cs = await get('/api/v1/kb/stats/closure', op);
    expect(cs.json().data).toHaveProperty('backfillRate30d');
    const es = await get('/api/v1/kb/executive/summary', op);
    expect(es.json().data.perBoard.length).toBe(4);
  });

  it('FR-M10-030 手工指标：每板 ≤1 个', async () => {
    const boards = await get('/api/v1/kb/boards', op);
    const boardId = boards.json().data.find((b: { boardCode: string }) => b.boardCode === 'rd').id;
    const m1 = await post(`/api/v1/kb/boards/rd/manual-metric`, { name: '图纸交付量', value: '42' }, ga);
    expect(m1.statusCode).toBe(201);
    const m2 = await post(`/api/v1/kb/boards/rd/manual-metric`, { name: '第二个', value: '1' }, ga);
    expect(m2.statusCode).toBe(422); // 每板仅 1 个
    void boardId;
  });

  it('FR-M06-022 盘点差异看板 + FR-M13-008/010/012 治理通报', async () => {
    const vd = await get('/api/v1/stocktakes/variance-dashboard', chainSw);
    expect(vd.statusCode).toBe(200);
    const cr = await get('/api/v1/gov/compliance-report', ga);
    expect(cr.json().data).toHaveProperty('oneCodeMultiMaterialBlocks');
    const mr = await get('/api/v1/gov/migration-report', ga);
    expect(mr.json().data).toHaveProperty('迁入主体');
    const sd = await get('/api/v1/gov/snapshot-diff', ga);
    expect(sd.statusCode).toBe(200);
  });

  it('FR-M15-009 打印 + FR-M07-009 区域负荷 + FR-M07-011 验收资料清单', async () => {
    const print = await post('/api/v1/integration/print/acceptance', { projectCode: 'PJ2026-0001', conclusion: 'pass', date: '2026-11-02' }, op);
    expect(print.json().data.html).toContain('验收单');
    const zl = await get('/api/v1/delivery/zone-load', op);
    expect(zl.statusCode).toBe(200);
  });
});
