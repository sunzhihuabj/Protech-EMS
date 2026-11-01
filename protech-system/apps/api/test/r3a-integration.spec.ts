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

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  op = await login('op-demo');
  // 种子库独立运行时无项目 → 本规格自建
  const entities = await get('/api/v1/mdm/entities', chainSw);
  const entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-集成期',
      shortName: '集成期',
      phase: '集成期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 1,
    },
    chainSw,
  );
});

afterAll(async () => {
  await app?.close();
});

describe('M15 集成补全（FR-M15-005/009/010/011/012）', () => {
  it('FR-M15-005 钉钉 Webhook：未配置时降级站内（R-EXEC/R-SYS 可见）', async () => {
    const res = await post('/api/v1/integration/dingtalk/test', { title: '连通测试', text: 'hello' }, admin);
    expect(res.statusCode).toBe(201);
    expect(res.json().data.channel).toBe('degraded'); // dev 无 webhook 配置
    const notifies = await get('/api/v1/sys/notifies', admin);
    expect(notifies.json().data.list.some((n: { title: string }) => n.title.includes('钉钉降级'))).toBe(true);
  });

  it('FR-M15-009 打印模板：派工单按单据取数渲染 + 模板可配', async () => {
    // 找一张已有派工单（r2c spec 可能建过——本库独立种子，若无则先建）
    const farms = await get('/api/v1/mdm/farms?keyword=姚安', admin);
    const farmId = farms.json().data.list[0].id;
    const projects = await get('/api/v1/mdm/projects?keyword=集成期', admin);
    const pid = projects.json().data.list[0].id;
    const d = await post('/api/v1/dispatches', { projectId: pid, windFarmId: farmId, taskDesc: '打印演示派工' }, admin);
    const pgCode = d.json().data.pgCode;
    const print = await post('/api/v1/integration/print/dispatch', { dispatchId: d.json().data.id }, op);
    expect(print.statusCode).toBe(201);
    expect(print.json().data.html).toContain(pgCode);
    expect(print.json().data.html).toContain('派工单');

    // 模板可配（Q-07）
    const setTpl = await post(
      '/api/v1/integration/print/dispatch/template',
      { html: '<h1>{{pgCode}} 自定义模板</h1>' },
      admin,
    );
    expect(setTpl.statusCode).toBe(201);
    const reprint = await post('/api/v1/integration/print/dispatch', { dispatchId: d.json().data.id }, op);
    expect(reprint.json().data.html).toContain('自定义模板');
    // 恢复默认
    await post(
      '/api/v1/integration/print/dispatch/template',
      { html: '<h2>派工单</h2><p>编号：{{pgCode}}</p><p>项目：{{projectCode}}｜风场：{{farmName}}</p><p>任务：{{taskDesc}}</p><p>工期：{{planDays}} 天</p><hr/><p>派工人：____ 班组长：____</p>' },
      admin,
    );
  });

  it('FR-M15-012 月度对账报告（差异清单+匹配率）', async () => {
    const rep = await get('/api/v1/integration/reconciliation', op);
    expect(rep.statusCode).toBe(200);
    expect(rep.json().data).toHaveProperty('matchRate');
    expect(rep.json().data).toHaveProperty('diffLines');
  });

  it('FR-M15-011 集成监控台：T+ 队列/事件流水/导入批次', async () => {
    const m = await get('/api/v1/integration/monitor', op);
    expect(m.statusCode).toBe(200);
    expect(m.json().data).toHaveProperty('tPlusQueue');
    expect(m.json().data).toHaveProperty('eventJournal');
    expect(m.json().data).toHaveProperty('importBatches');
  });
});
