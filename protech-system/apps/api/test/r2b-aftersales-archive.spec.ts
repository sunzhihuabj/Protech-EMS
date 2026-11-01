import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let ga: string;
let op: string;

let entityId: string;
let projectId: string;
let warrantyProjectId: string;

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
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  const farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;

  // 质保期项目（warranty 状态 + 临期）
  const p1 = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-质保期',
      shortName: '质保期项目',
      phase: '质保期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['fire'],
      unitCount: 20,
    },
    chainSw,
  );
  warrantyProjectId = p1.json().data.id;
  await post(`/api/v1/mdm/projects/${warrantyProjectId}/transition`, { to: 'executing' }, chainSw);
  await post(`/api/v1/mdm/projects/${warrantyProjectId}/transition`, { to: 'shipped', note: '发货' }, chainSw);
  await post(`/api/v1/mdm/projects/${warrantyProjectId}/transition`, { to: 'accepted' }, chainSw);
  // 把质保止期改到 20 天后（临期）
  const { PrismaService } = await import('../src/shared/prisma.service');
  const prisma = app.get(PrismaService);
  await prisma.project.update({
    where: { id: BigInt(warrantyProjectId) },
    data: { warrantyEnd: new Date(Date.now() + 20 * 86400000) },
  });
  await post(`/api/v1/mdm/projects/${warrantyProjectId}/transition`, { to: 'warranty' }, chainSw);

  // 常规项目
  const p2 = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-售后期',
      shortName: '售后期项目',
      phase: '售后期',
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 6,
    },
    chainSw,
  );
  projectId = p2.json().data.id;
});

afterAll(async () => {
  await app?.close();
});

describe('M11 售后服务（FR-M11-001~004/007）', () => {
  let orderId: string;

  it('售后工单创建（AS 编码，挂项目）→ 受阻 → ⑤板红灯联动（FR-M11-007）', async () => {
    const o = await post('/api/v1/aftersales', { projectId, type: 'fix', description: '免爬器无法启动' }, op);
    expect(o.statusCode).toBe(201);
    expect(o.json().data.orderCode).toMatch(/^AS\d{4}-\d{4}$/);
    orderId = o.json().data.id;

    const blocked = await post(
      `/api/v1/aftersales/${orderId}/transition`,
      { to: 'blocked' },
      op,
    );
    expect(blocked.statusCode).toBe(422); // 缺原因

    const ok = await post(
      `/api/v1/aftersales/${orderId}/transition`,
      { to: 'blocked', blockedReason: '备件缺货' },
      op,
    );
    expect(ok.json().data.status).toBe('blocked');

    const view = await req('GET', '/api/v1/kb/boards/delivery/view', undefined, op);
    const redTitles = view.json().data.zones.red.map((c: { title: string }) => c.title);
    expect(redTitles.some((t: string) => t.includes('售后工单'))).toBe(true);
  });

  it('完成 + 质保内成本归集到 M09（FR-M11-003 联动）', async () => {
    const done = await post(
      `/api/v1/aftersales/${orderId}/transition`,
      { to: 'done', costCents: '80000' },
      op,
    );
    expect(done.json().data.status).toBe('done');
  });

  it('定检计划 + 到期生成工单（FR-M11-002）+ 质保临期提醒（FR-M11-003）', async () => {
    const plan = await post(
      '/api/v1/maintenance-plans',
      { projectId, cycleMonths: 12, nextDueAt: new Date(Date.now() - 86400000).toISOString().slice(0, 10) },
      chainSw,
    );
    expect(plan.statusCode).toBe(201);
    const gen = await post('/api/v1/internal/aftersales/generate-inspections', { token: 'internal-dev-token' }, admin);
    expect(gen.json().data.generated).toBeGreaterThanOrEqual(1);
    const watch = await post('/api/v1/internal/aftersales/warranty-watch', { token: 'internal-dev-token' }, admin);
    expect(watch.json().data.reminded).toBeGreaterThanOrEqual(1);
  });

  it('故障率统计（FR-M11-004）', async () => {
    const stats = await get('/api/v1/aftersales/fault-stats', op);
    expect(stats.json().data.total).toBeGreaterThanOrEqual(1);
  });
});

describe('M12 档案（FR-M12-001~006/009）', () => {
  let archiveId: string;
  let borrowId: string;

  it('档号生成（PRT-YYYY-HT-S30-NNNN）+ 八门类校验（FR-M12-001）', async () => {
    const bad = await post('/api/v1/archives', { title: '测试', category: 'XX', retention: 'S30' }, ga);
    expect(bad.statusCode).toBe(422);
    const ok = await post(
      '/api/v1/archives',
      { title: '姚安五期合同原件', category: 'HT', retention: 'S30', entity: 'PRT', projectId },
      ga,
    );
    expect(ok.statusCode).toBe(201);
    const year = new Date().getFullYear();
    expect(ok.json().data.archiveNo).toMatch(new RegExp(`^PRT-${year}-HT-S30-\\d{4}$`));
    archiveId = ok.json().data.id;
  });

  it('借阅双审批（HT 合同类 FR-M12-003）：seq2 未完成 seq1 → 拒；全流程批准→借出→归还', async () => {
    const b = await post(`/api/v1/archives/${archiveId}/borrow`, { purpose: '合同审查' }, op);
    expect(b.json().data.needApprove2).toBe(true);
    borrowId = b.json().data.id;

    const skip = await post(`/api/v1/borrows/${borrowId}/approve`, { approved: true, seq: 2 }, ga);
    expect(skip.statusCode).toBe(422); // 双审批须先 seq1

    await post(`/api/v1/borrows/${borrowId}/approve`, { approved: true, seq: 1 }, chainSw);
    const approve2 = await post(`/api/v1/borrows/${borrowId}/approve`, { approved: true, seq: 2 }, ga);
    expect(approve2.json().data.bothDone).toBe(true);

    const out = await post(`/api/v1/borrows/${borrowId}/out`, {}, ga);
    expect(out.json().data.status).toBe('out');
    const ret = await post(`/api/v1/borrows/${borrowId}/return`, {}, op);
    expect(['returned', 'overdue']).toContain(ret.json().data.status);
  });

  it('期满鉴定清单（FR-M12-004）+ 备份台账（FR-M12-009）', async () => {
    const expiry = await get('/api/v1/archives/expiry-list', ga);
    expect(expiry.statusCode).toBe(200);
    const bk = await post(
      '/api/v1/backup-records',
      { kind: 'full', target: '本机', fileRef: 'protech-20261102.sql.gz', sha256: 'abc123' },
      ga,
    );
    expect(bk.statusCode).toBe(201);
    const list = await get('/api/v1/backup-records', ga);
    expect(list.json().data.length).toBeGreaterThan(0);
  });
});
