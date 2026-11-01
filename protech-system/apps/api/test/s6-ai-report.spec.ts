import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let ga: string;
let op: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

let contractId: string;

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  ga = await login('ga-demo');
  op = await login('op-demo');
  const entities = await get('/api/v1/mdm/entities', chainSw);
  const entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const contract = await post(
    '/api/v1/contracts',
    {
      contractName: 'AI 提取演示合同',
      nature: 'XS',
      bizType: 'SB',
      ourEntityId: entityId,
      counterpartyId: ownerId,
      amountCents: '50000000',
    },
    chainSw,
  );
  contractId = contract.json().data.id;
});

afterAll(async () => {
  await app?.close();
});

describe('M16 AI 能力点（FR-M16-001/002/003/014）', () => {
  it('能力点白名单：R1 三点开放，其余拒绝', async () => {
    const caps = await get('/api/v1/ai/capabilities', op);
    const enabled = caps.json().data.filter((c: { enabled: boolean }) => c.enabled);
    expect(enabled.map((c: { code: string }) => c.code).sort()).toEqual(['AI-01', 'AI-02', 'AI-03', 'AI-04', 'AI-05', 'AI-06', 'AI-07', 'AI-08', 'AI-10', 'AI-11']);

    const blocked = await post('/api/v1/ai/AI-09/run', { question: '回款率' }, op);
    expect(blocked.statusCode).toBe(403);
  });

  it('AI-01 合同要素提取：Ask-first 草稿 + 确认入库 + 调用审计', async () => {
    const run = await post('/api/v1/ai/AI-01/run', { contractId }, op);
    expect(run.statusCode).toBe(201);
    const d = run.json().data;
    expect(d.askFirst).toBe(true);
    expect(d.draft.contractName).toBeTruthy();
    expect(d.draft.amountCents.confidence).toBeGreaterThan(0.9);

    // 确认采纳（找最新 callId）
    const calls = await get('/api/v1/ai/calls', op);
    const callId = calls.json().data[0].id;
    const confirm = await post(
      '/api/v1/ai/calls/' + callId + '/confirm',
      { accepted: true, aiFields: { paymentTerms: 'AI 提取条款：预付 30%', contact: 'AI 联系人' } },
      op,
    );
    expect(confirm.json().data.recorded).toBe(true);

    // 审计：ai_call 落库
    expect(calls.json().data.some((c: { capability: string }) => c.capability === 'AI-01')).toBe(true);
  });

  it('AI-02 查重辅助：候选聚类 + 归并依据', async () => {
    const run = await post('/api/v1/ai/AI-02/run', { name: '三峡新能源集团', scope: 'party' }, chainSw);
    expect(run.statusCode).toBe(201);
    const clusters = run.json().data.clusters;
    expect(clusters.length).toBeGreaterThan(0);
    expect(clusters[0].basis).toContain('人工判读');
  });

  it('AI-03 名称归一：候选 + 依据（地名一致/业主待补）', async () => {
    // 准备项目档案（候选源）
    const entities = await get('/api/v1/mdm/entities', chainSw);
    const entityId2 = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
    const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
    const farmId2 = farms.json().data.list[0].id;
    const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
    const ownerId2 = parties.json().data.list[0].id;
    await post(
      '/api/v1/mdm/projects',
      {
        standardName: '三峡新能源-姚安大龙口-七期',
        shortName: '姚安七期',
        phase: '七期',
        windFarmId: farmId2,
        ownerPartyId: ownerId2,
        legalEntityId: entityId2,
        orderType: 'owner_retrofit',
        productLines: ['lift'],
        unitCount: 3,
      },
      chainSw,
    );
    const run = await post('/api/v1/ai/AI-03/run', { rawName: '三峡新能源姚安大龙口二期技改', scope: 'project' }, chainSw);
    expect(run.statusCode).toBe(201);
    expect(run.json().data.suggestions.length).toBeGreaterThan(0);
    expect(run.json().data.suggestions[0].basis).toBeTruthy();
  });
});

describe('M06 补货分级（FR-M06-013/014）', () => {
  let matId: string;
  beforeAll(async () => {
    const fd = await get('/api/v1/mdm/materials?keyword=消防电池', chainSw);
    matId = fd.json().data.list[0].id;
  });

  it('分级路由：380 元标准件 → tier1；6000 元 → tier3', async () => {
    const t1 = await post('/api/v1/reissues', { materialId: matId, amountCents: '38000', reason: 'wear', qty: 2 }, op);
    expect(t1.json().data.tier).toBe('tier1');
    const t3 = await post('/api/v1/reissues', { materialId: matId, amountCents: '600000', reason: 'design_add', qty: 1 }, op);
    expect(t3.json().data.tier).toBe('tier3');
    const bad = await post('/api/v1/reissues', { materialId: matId, amountCents: '100', reason: 'unknown' }, op);
    expect(bad.statusCode).toBe(422);
  });

  it('安全件先发后签（4h 出库 24h 补签）', async () => {
    const s = await post('/api/v1/reissues', { materialId: matId, amountCents: '20000', reason: 'qty_short', isSafetyPart: true }, op);
    const id = s.json().data.id;
    const ship = await post(`/api/v1/reissues/${id}/ship-now`, {}, chainSw);
    expect(ship.json().data.status).toBe('shipped');
    expect(new Date(ship.json().data.signedBackDeadline).getTime()).toBeGreaterThan(Date.now() + 23 * 3600000);
    const back = await post(`/api/v1/reissues/${id}/sign-back`, {}, chainSw);
    expect(back.json().data.signedBack).toBe(1);
  });

  it('原因结构分析（FR-M06-014）', async () => {
    const stats = await get('/api/v1/reissues/stats', chainSw);
    expect(stats.json().data.reasonDist.length).toBeGreaterThan(0);
    expect(stats.json().data.topMaterials.length).toBeGreaterThan(0);
  });
});

describe('M14 预置报表（FR-M14-001/002/006）', () => {
  it('四张预置报表 + 导出水印', async () => {
    for (const key of ['contracts', 'projects', 'stock-moves', 'receipts']) {
      const r = await get(`/api/v1/reports/${key}`, chainSw);
      expect(r.statusCode).toBe(200);
      if (r.json().data.count > 0) {
        expect(r.json().data.rows[0]._水印).toContain('负责人');
        break;
      }
    }
    const csv = await get('/api/v1/reports/contracts/csv', admin);
    expect(csv.json().data.content.split('\n')[0]).toContain('_水印');
  });

  it('未知报表拒绝', async () => {
    const bad = await get('/api/v1/reports/unknown-key', chainSw);
    expect(bad.statusCode).toBe(422);
  });
});
