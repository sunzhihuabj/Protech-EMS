import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req, TestResponse } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let op: string;
let chainSw: string;
let pm: string;

const year = new Date().getFullYear();

function get(url: string, token?: string): Promise<TestResponse> {
  return req('GET', url, undefined, token);
}
function post(url: string, payload: unknown, token?: string): Promise<TestResponse> {
  return req('POST', url, payload, token);
}
function put(url: string, payload: unknown, token?: string): Promise<TestResponse> {
  return req('PUT', url, payload, token);
}

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  op = await login('op-demo');
  chainSw = await login('chain-sw');
  pm = await login('pm-demo');
});
afterAll(async () => {
  await app?.close();
});

describe('M00 认证与会话（FR-M00-004）', () => {
  it('登录成功返回 token/refresh/expires', async () => {
    const res = await post('/api/v1/auth/login', { username: 'admin', password: 'ProjLife@2026' });
    expect(res.statusCode).toBe(201);
    const body = res.json();
    expect(body.code).toBe(0);
    expect(body.data.accessToken).toBeTruthy();
    expect(body.data.expiresIn).toBe(1800);
  });

  it('未带 token 访问业务接口 → 401 E-AUTH', async () => {
    const res = await get('/api/v1/mdm/projects');
    expect(res.statusCode).toBe(401);
    expect(res.json().blockCode).toBe('E-AUTH');
  });

  it('失败 5 次锁定 15 分钟（FR-M00-004）', async () => {
    for (let i = 0; i < 5; i++) {
      const res = await post('/api/v1/auth/login', { username: 'SWB02', password: 'wrong-password' });
      expect(res.statusCode).toBe(422);
    }
    const res = await post('/api/v1/auth/login', { username: 'SWB02', password: 'ProjLife@2026' });
    expect(res.statusCode).toBe(422);
    expect(res.json().msg).toMatch(/锁定/);
    const res2 = await post('/api/v1/auth/login', { username: 'SWB02', password: 'wrong-password' });
    expect(res2.json().msg).toMatch(/锁定/);
  });

  it('me 返回角色/权限/数据范围（FR-M00-002）', async () => {
    const res = await get('/api/v1/auth/me', admin);
    const body = res.json().data;
    expect(body.user.username).toBe('admin');
    expect(body.roles.some((r: { code: string }) => r.code === 'R-SYS')).toBe(true);
    expect(body.permissions).toContain('*');
  });

  it('refresh 轮换会话', async () => {
    const l = await post('/api/v1/auth/login', { username: 'qa-demo', password: 'ProjLife@2026' });
    const refreshToken = l.json().data.refreshToken;
    const r1 = await post('/api/v1/auth/refresh', { refreshToken });
    expect(r1.statusCode).toBe(201);
    const r2 = await post('/api/v1/auth/refresh', { refreshToken });
    expect(r2.statusCode).toBe(401); // 旧 refresh 已吊销
  });

  it('密码强度校验', async () => {
    const me = await get('/api/v1/auth/me', op);
    void me;
    const res = await post('/api/v1/auth/change-password', { oldPassword: 'ProjLife@2026', newPassword: 'short1' }, op);
    expect(res.statusCode).toBe(422);
    expect(res.json().msg).toMatch(/密码强度/);
  });
});

describe('M00 RBAC（FR-M00-002 三级授权 + 默认闭口）', () => {
  it('无权限角色访问用户列表 → 403', async () => {
    const res = await get('/api/v1/sys/users', op);
    expect(res.statusCode).toBe(403);
    expect(res.json().code).toBe(2002);
  });

  it('R-SYS 全量访问', async () => {
    const res = await get('/api/v1/sys/users', admin);
    expect(res.statusCode).toBe(200);
    expect(res.json().data.total).toBeGreaterThan(120);
  });

  it('R-CHAIN 可查项目但不可管用户', async () => {
    expect((await get('/api/v1/mdm/projects', chainSw)).statusCode).toBe(200);
    expect((await get('/api/v1/sys/users', chainSw)).statusCode).toBe(403);
  });

  it('121 人组织：部门树 16 行（14 部门 + 技术中心两组）', async () => {
    const res = await get('/api/v1/sys/depts', admin);
    expect(res.json().data.length).toBe(16);
    const names = res.json().data.map((d: { name: string }) => d.name);
    expect(names).toContain('工程技术部');
    expect(names).toContain('技术中心·系统集成组');
  });
});

describe('M00 编码引擎（FR-M00-005 + BR-09）', () => {
  it('预览不占号：连续两次预览一致', async () => {
    const r1 = await post('/api/v1/sys/code-rules/project/preview', {}, admin);
    const r2 = await post('/api/v1/sys/code-rules/project/preview', {}, admin);
    expect(r1.json().data.code).toBe(r2.json().data.code);
    expect(r1.json().data.code).toMatch(new RegExp(`^PJ${year}-\\d{4}$`));
  });

  it('合同编号模板变量渲染', async () => {
    const res = await post(
      '/api/v1/sys/code-rules/contract/preview',
      { entity: 'PRT', nature: 'XS', biz: 'SB' },
      admin,
    );
    expect(res.json().data.code).toMatch(new RegExp(`^PRT-${year}-XS-SB-\\d{4}$`));
  });
});

describe('M01 客户主体（FR-M01-001~003 别名/查重）', () => {
  it('查重命中别名与相似主体（≥0.85）', async () => {
    const res = await post('/api/v1/mdm/parties/check-dup', { name: '远景能源集团有限公司' }, chainSw);
    const candidates = res.json().data.candidates;
    expect(candidates.length).toBeGreaterThan(0);
    expect(candidates[0].name).toContain('远景');
    expect(['exact', 'alias', 'similar']).toContain(candidates[0].matchedBy);
  });

  it('新建命中不 force → 2001 + 候选列表；force → 成功', async () => {
    const dup = await post(
      '/api/v1/mdm/parties',
      { name: '三峡新能源集团（新录）有限公司', types: ['owner'] },
      chainSw,
    );
    expect(dup.statusCode).toBe(422);
    expect(dup.json().msg).toMatch(/疑似已存在/);
    const ok = await post(
      '/api/v1/mdm/parties',
      { name: '华电新能源发展有限公司', types: ['owner'], alias: '华电新能源' },
      chainSw,
    );
    expect(ok.statusCode).toBe(201);
    expect(ok.json().data.partyCode).toMatch(/^C\d{4}$/);
  });

  it('别名参与全局搜索（FR-M01-016）', async () => {
    const res = await get('/api/v1/mdm/search?q=金风', admin);
    expect(res.json().data.parties.length).toBeGreaterThan(0);
  });

  it('BR-08/DQ-04：引用字段传自由文本 → 403', async () => {
    const res = await post(
      '/api/v1/mdm/parties',
      { name: '某项目公司', groupId: '远景能源集团', types: ['owner'] },
      chainSw,
    );
    expect(res.statusCode).toBe(403);
    expect(res.json().blockCode).toBe('BR-08');
  });
});

describe('M01 风场库（FR-M01-004）', () => {
  it('新建风场编码 WF{YYYY}-{NNN}；同全称拒绝', async () => {
    const ok = await post(
      '/api/v1/mdm/farms',
      { fullName: '华能大安风力发电场', province: '吉林省', city: '白城市', county: '大安市', capacityMw: 100 },
      chainSw,
    );
    expect(ok.statusCode).toBe(201);
    expect(ok.json().data.farmCode).toMatch(new RegExp(`^WF${year}-\\d{3}$`));
    const dup = await post('/api/v1/mdm/farms', { fullName: '华能大安风力发电场' }, chainSw);
    expect(dup.statusCode).toBe(422);
  });
});

describe('M01 物料（FR-M01-005/006 归一+DQ-01 拦截+旧码映射）', () => {
  it('录入前置归一：台套→台、符号替换', async () => {
    const res = await post('/api/v1/mdm/materials/check-dup', { name: '小车按钮（急停）台套' }, op);
    expect(res.json().data.normalized.applied.length).toBeGreaterThan(0);
  });

  it('一物多码拦截（DQ-01）并返回候选', async () => {
    const res = await post(
      '/api/v1/mdm/materials',
      { materialCode: '3080-FD-9999', name: '消防电池', spec: '12V 7Ah', unit: '只' },
      admin,
    );
    expect(res.statusCode).toBe(422);
    expect(res.json().msg).toMatch(/一物多码/);
    expect(res.json().data.candidates.length).toBeGreaterThan(0);
    // force 建档（治理确认场景）
    const forced = await post(
      '/api/v1/mdm/materials',
      { materialCode: '3080-FD-9999', name: '消防电池', spec: '12V 7Ah', unit: '只', force: true },
      admin,
    );
    expect(forced.statusCode).toBe(201);
  });

  it('旧码映射解析（FR-M01-005）', async () => {
    const app = await getApp();
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    await prisma.mdCodeMap.create({ data: { oldCode: 'M1-123', masterCode: '3080-FD-0001', source: 'bom_audit' } });
    const res = await get('/api/v1/mdm/materials/resolve-old-code?code=M1-123', op);
    expect(res.json().data.resolved).toBe(true);
    expect(res.json().data.masterCode).toBe('3080-FD-0001');
  });
});

describe('M01 项目档案（FR-M01-008~011 全系统锚点）', () => {
  let farmId: string;
  let ownerId: string;
  let entityId: string;
  let pmUserId: string;
  let projectCode: string;
  let projectId: string;

  it('name-preview：业主+风场+期次 → 标准名（FR-M01-009/S-02）', async () => {
    const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
    farmId = farms.json().data.list[0].id;
    const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
    ownerId = parties.json().data.list[0].id;
    const entities = await get('/api/v1/mdm/entities', chainSw);
    entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
    const users = await get('/api/v1/sys/users?keyword=pm-demo', admin);
    pmUserId = users.json().data.list[0].id;

    const res = await post(
      '/api/v1/mdm/projects/name-preview',
      { ownerPartyId: ownerId, windFarmId: farmId, phase: '二期', orderType: 'owner_retrofit' },
      chainSw,
    );
    void res;
    // 生成器支持 ownerName 或 ownerPartyId 查询（此处用名称路径）
    const res2 = await post(
      '/api/v1/mdm/projects/name-preview',
      { ownerName: '三峡新能源', windFarmId: farmId, phase: '二期', orderType: 'owner_retrofit' },
      chainSw,
    );
    expect(res2.json().data.standardName).toBe('三峡新能源-姚安大龙口-二期');
    expect(res2.json().data.codeSuggestion).toMatch(new RegExp(`^PJ${year}-\\d{4}$`));
  });

  it('建档：三重查重通过 → PJ 编码 + pending_exec + 完整度', async () => {
    const res = await post(
      '/api/v1/mdm/projects',
      {
        standardName: '三峡新能源-姚安大龙口-二期',
        shortName: '姚安二期',
        phase: '二期',
        windFarmId: farmId,
        ownerPartyId: ownerId,
        legalEntityId: entityId,
        orderType: 'owner_retrofit',
        productLines: ['lift', 'climber'],
        unitCount: 12,
        contractAmountCents: '634100000',
        pmUserId,
        engOwnerId: pmUserId,
        salesOwnerId: pmUserId,
        collectorId: pmUserId,
        alias: '姚安大龙口二个项目',
      },
      chainSw,
    );
    expect(res.statusCode).toBe(201);
    projectCode = res.json().data.projectCode;
    projectId = res.json().data.id;
    expect(projectCode).toMatch(new RegExp(`^PJ${year}-\\d{4}$`));
    const detail = await get(`/api/v1/mdm/projects/${projectId}`, chainSw);
    expect(detail.json().data.completeness.percent).toBe(100);
  });

  it('BR-10：金额浮点直接拒绝', async () => {
    const res = await post(
      '/api/v1/mdm/projects',
      {
        standardName: '测试浮点金额风场-一期',
        shortName: '浮点测试',
        windFarmId: farmId,
        legalEntityId: entityId,
        orderType: 'owner_retrofit',
        productLines: ['lift'],
        unitCount: 1,
        contractAmountCents: 634.1,
      },
      chainSw,
    );
    expect(res.statusCode).toBe(422);
    expect(res.json().blockCode).toBe('BR-10');
  });

  it('整机厂初装缺风场 → pending_farm（FR-M03-015 两段式对齐）', async () => {
    const res = await post(
      '/api/v1/mdm/projects',
      {
        standardName: '远景能源-肃北马鬃山-一期',
        shortName: '肃北一期',
        legalEntityId: entityId,
        orderType: 'oem_initial',
        productLines: ['video'],
        unitCount: 5,
      },
      chainSw,
    );
    expect(res.statusCode).toBe(201);
    expect(res.json().data.status).toBe('pending_farm');
    const tasks = await get('/api/v1/mdm/projects/pending-tasks', chainSw);
    expect(tasks.json().data.some((t: { projectCode: string }) => t.projectCode === res.json().data.projectCode)).toBe(true);
  });

  it('重复标准名建档 → 三重查重命中（FR-M01-010）', async () => {
    const res = await post(
      '/api/v1/mdm/projects',
      {
        standardName: '三峡新能源-姚安大龙口-二期',
        shortName: '重复测试',
        windFarmId: farmId,
        legalEntityId: entityId,
        orderType: 'owner_retrofit',
        productLines: ['lift'],
        unitCount: 1,
      },
      chainSw,
    );
    expect(res.statusCode).toBe(422);
    expect(res.json().msg).toMatch(/三重查重/);
  });

  it('结构变更字段直改 → 拒绝并引导变更单（FR-M01-011）', async () => {
    const res = await put(`/api/v1/mdm/projects/${projectId}`, { phase: '三期' }, chainSw);
    expect(res.statusCode).toBe(422);
    expect(res.json().msg).toMatch(/变更审批/);
  });

  it('状态机迁移与守卫（E-04）', async () => {
    const bad = await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'accepted' }, chainSw);
    expect(bad.statusCode).toBe(422);
    const ok = await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'executing' }, chainSw);
    expect(ok.json().data.status).toBe('executing');
  });

  it('BR-09：业务编码不可修改（Prisma 中间件硬闸门）', async () => {
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    await expect(
      prisma.project.update({ where: { id: BigInt(projectId) }, data: { projectCode: 'PJ2099-9999' } }),
    ).rejects.toMatchObject({ blockCode: 'BR-09' });
  });

  it('BR-11：物理删除被中间件拦截', async () => {
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    await expect(prisma.project.delete({ where: { id: BigInt(projectId) } })).rejects.toMatchObject({
      blockCode: 'BR-11',
    });
    await expect(prisma.customerParty.deleteMany({ where: {} })).rejects.toMatchObject({
      blockCode: 'BR-11',
    });
  });

  it('R-PM 授权 → me 返回授权 + 可见项目 + 关闭后 7 天回收（FR-M00-003）', async () => {
    const grant = await post('/api/v1/pm-grants', { userId: pmUserId, projectId }, admin);
    expect(grant.statusCode).toBe(201);
    const me = await get('/api/v1/auth/me', pm);
    expect(me.json().data.pmGrants.length).toBeGreaterThan(0);
    const list = await get('/api/v1/mdm/projects', pm);
    expect(list.json().data.list.some((p: { id: string }) => p.id === projectId)).toBe(true);

    // 关闭项目 → expiredAt=+7 天（FR-M04-013）
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'shipped', note: '测试发货' }, chainSw);
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'accepted', note: '测试验收' }, chainSw);
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'warranty' }, chainSw);
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'closed' }, chainSw);
    const grants = await get(`/api/v1/pm-grants?userId=${pmUserId}`, admin);
    const g = grants.json().data.find((x: { projectId: string }) => x.projectId === projectId);
    expect(g).toBeTruthy();
    expect(new Date(g.expiredAt).getTime()).toBeGreaterThan(Date.now() + 6 * 24 * 3600 * 1000);
  });
});

describe('M00 配置/审计/通知（FR-M00-006/007/009）', () => {
  it('配置修改留痕 + 审计可查', async () => {
    const res = await put('/api/v1/sys/configs/kb_redlight_days', { value: '3', remark: '制度值确认' }, admin);
    expect(res.statusCode).toBe(200);
    const logs = await get('/api/v1/sys/audit-logs?objectType=SysConfig', admin);
    expect(logs.json().data.list.length).toBeGreaterThan(0);
    expect(logs.json().data.list[0].action).toBe('config');
  });

  it('编码生成落审计（FR-M00-006）', async () => {
    const logs = await get('/api/v1/sys/audit-logs?action=code_gen', admin);
    expect(logs.json().data.total).toBeGreaterThan(0);
  });

  it('通知中心：授权发送站内信（FR-M00-007）', async () => {
    const res = await get('/api/v1/sys/notifies', pm);
    expect(res.statusCode).toBe(200);
    expect(res.json().data.list.some((n: { title: string }) => n.title.includes('您已被授权查看项目'))).toBe(true);
  });

  it('系统监控健康检查（FR-M00-013）', async () => {
    const res = await get('/api/v1/monitor/health', op);
    expect(res.json().data.db).toBe(true);
    expect(res.json().data.status).toBe('up');
  });
});
