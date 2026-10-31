import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let ga: string;
let op: string;
let pm: string;

let deptIdGcjs: string;
let projectId: string;
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
  pm = await login('pm-demo');
  const depts = await get('/api/v1/sys/depts', admin);
  deptIdGcjs = depts.json().data.find((d: { code: string }) => d.code === 'GCJS').id;
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const prj = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-六期',
      shortName: '姚安六期',
      phase: '六期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift', 'fire'],
      unitCount: 10,
    },
    chainSw,
  );
  projectId = prj.json().data.id;
});

afterAll(async () => {
  await app?.close();
});

describe('BV-01 部门看板（FR-M10-025，08 册 §三六区）', () => {
  it('工程技术部六区：部门头/KPI/项目卡/红灯/单据动态/人员负荷', async () => {
    const v = await get(`/api/v1/kb/dept/${deptIdGcjs}`, chainSw);
    expect(v.statusCode).toBe(200);
    const d = v.json().data;
    expect(d.dept.name).toBe('工程技术部');
    expect(d.memberCount).toBeGreaterThanOrEqual(23);
    expect(d.kpis.length).toBeGreaterThan(0); // ⑤ 板指标引用
    expect(Array.isArray(d.projects)).toBe(true);
    expect(Array.isArray(d.load)).toBe(true);
    expect(d.load.every((l: { projectCount: number; overdueCards: number }) => typeof l.projectCount === 'number')).toBe(true);
  });

  it('非本部门金额与数据按权限（R-OP 可看视图结构）', async () => {
    const v = await get(`/api/v1/kb/dept/${deptIdGcjs}`, op);
    expect(v.statusCode).toBe(200);
  });
});

describe('BV-03 项目监管看板（FR-M10-027，08 册 §五五区）', () => {
  it('九阶段时间线 + 关键节点卡 + 团队 + 红灯 + 财务汇总', async () => {
    const v = await get(`/api/v1/kb/project/${projectId}`, chainSw);
    expect(v.statusCode).toBe(200);
    const d = v.json().data;
    expect(d.project.projectCode).toMatch(/^PJ\d{4}-\d{4}$/);
    expect(d.timeline.length).toBe(9);
    expect(d.timeline[0].state).toBe('done'); // 立项已完成（pending_exec）
    expect(d.keyNodes.contractAmountCents).toBeNull(); // 未挂合同
    expect(d.team.length).toBeGreaterThanOrEqual(0);
  });

  it('R-PM 授权后可见项目监管视图（FR-M00-003 联动）', async () => {
    const users = await get('/api/v1/sys/users?keyword=pm-demo', admin);
    const pmId = users.json().data.list[0].id;
    await post('/api/v1/pm-grants', { userId: pmId, projectId }, admin);
    const v = await get(`/api/v1/kb/project/${projectId}`, pm);
    expect(v.statusCode).toBe(200);
    expect(v.json().data.project.standardName).toContain('六期');
  });
});

describe('M13 数据治理（FR-M13-001~006/009）', () => {
  it('治理工单闭环：创建→处理→复核', async () => {
    const t = await post(
      '/api/v1/gov/tasks',
      { type: 'dup_code', payload: { codes: ['3080-FD-0001', 'M1-100'] }, sourceType: 'material_dup' },
      ga,
    );
    expect(t.statusCode).toBe(201);
    expect(t.json().data.taskCode).toMatch(/^GT\d{4}-\d{4}$/);

    const done = await post(`/api/v1/gov/tasks/${t.json().data.id}/process`, { result: { action: 'mapped' } }, ga);
    expect(done.json().data.status).toBe('done');
    const verified = await post(`/api/v1/gov/tasks/${t.json().data.id}/verify`, { result: { ok: true } }, admin);
    expect(verified.json().data.status).toBe('verified');
  });

  it('查重工作台：宽松口径候选组', async () => {
    const wb = await get('/api/v1/gov/dup-workbench', ga);
    expect(wb.statusCode).toBe(200);
    expect(Array.isArray(wb.json().data.groups)).toBe(true);
  });

  it('物料归并：md_code_map 只增 + 旧码 frozen（FR-M13-009 治理组权限）', async () => {
    // 新建两个相似物料
    await post('/api/v1/mdm/materials', { materialCode: '3080-FD-9001', name: '消防电池组件', spec: '12V 7Ah A', unit: '只', force: true }, admin);
    await post('/api/v1/mdm/materials', { materialCode: '3080-FD-9002', name: '消防电池组件', spec: '12V 7Ah B', unit: '只', force: true }, admin);
    const ms = await get('/api/v1/mdm/materials?keyword=900', admin);
    const list = ms.json().data.list.filter((m: { materialCode: string }) => m.materialCode.startsWith('3080-FD-90'));
    expect(list.length).toBe(2);
    const merge = await post(
      '/api/v1/gov/merge-material',
      { masterId: list[0].id, oldIds: [list[1].id] },
      ga,
    );
    expect(merge.json().data.merged).toBe(1);
    const oldMat = await get(`/api/v1/mdm/materials/${list[1].id}`, admin);
    expect(oldMat.json().data.status).toBe('frozen');
    const resolve = await get('/api/v1/mdm/materials/resolve-old-code?code=3080-FD-9002', op);
    expect(resolve.json().data.resolved).toBe(true);
    expect(resolve.json().data.masterCode).toBe('3080-FD-9001');
  });

  it('数据质量看板（⑨板指标源 FR-M13-006）', async () => {
    const qb = await get('/api/v1/gov/quality-board', ga);
    expect(qb.statusCode).toBe(200);
    expect(qb.json().data.materialTotal).toBeGreaterThan(0);
    expect(typeof qb.json().data.dupGroups).toBe('number');
  });

  it('归一词典新增（FR-M13-003）→ 录入端即时生效', async () => {
    await post('/api/v1/gov/norm-dict', { type: 'typo', raw: '按扭', normalized: '按钮' }, ga);
    const check = await post('/api/v1/mdm/materials/check-dup', { name: '按扭盒' }, op);
    expect(check.json().data.normalized.name).toContain('按钮');
  });
});

describe('M15 导入引擎（FR-M15-008）', () => {
  it('CSV 主体导入：校验/错误行回执/批次留痕', async () => {
    const csv = [
      '名称,简称,类型,别名',
      '中国华电集团有限公司,华电集团,业主,华电',
      ',缺名称行,业主,',
      '中广核新能源有限公司,中广核,非法类型,',
    ].join('\n');
    const res = await post('/api/v1/imports/parties', { csv }, ga);
    expect(res.statusCode).toBe(201);
    expect(res.json().data.totalRows).toBe(3);
    expect(res.json().data.okRows).toBe(1);
    expect(res.json().data.errorRows).toBe(2);
    const errs = res.json().data.errors;
    expect(errs.some((e: { message: string }) => e.message.includes('必填'))).toBe(true);
    expect(errs.some((e: { message: string }) => e.message.includes('类型'))).toBe(true);

    // 导入结果可查（别名搜索命中导入数据）
    const search = await get('/api/v1/mdm/search?q=华电', chainSw);
    expect(search.json().data.parties.length).toBeGreaterThan(0);
  });

  it('物料导入 + 未授权角色拒绝', async () => {
    const csv = ['物料编码,名称,单位', '3080-XC-7001,指示灯,个'].join('\n');
    const res = await post('/api/v1/imports/materials', { csv }, ga);
    expect(res.json().data.okRows).toBe(1);
    const denied = await post('/api/v1/imports/materials', { csv }, op);
    expect(denied.statusCode).toBe(403);
  });
});
