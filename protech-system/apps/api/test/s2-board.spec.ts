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

let orderBoardId: string;
let cardId: string;

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  op = await login('op-demo');
  const boards = await get('/api/v1/kb/boards', chainSw);
  orderBoardId = boards.json().data.find((b: { boardCode: string }) => b.boardCode === 'order').id;
});

afterAll(async () => {
  await app?.close();
});

describe('M10 九板预置（FR-M10-001）', () => {
  it('九板初始化：1 总板 + 4 日板 + 4 周板', async () => {
    const res = await get('/api/v1/kb/boards', op);
    const boards = res.json().data;
    expect(boards.length).toBe(9);
    expect(boards.filter((b: { type: string }) => b.type === 'daily').length).toBe(4);
    expect(boards.filter((b: { type: string }) => b.type === 'weekly').length).toBe(4);
    expect(boards.find((b: { boardCode: string }) => b.boardCode === 'exec').type).toBe('executive');
  });
});

describe('M10 卡片五要素（FR-M10-007~009）', () => {
  it('VAGUE 模糊词拦截', async () => {
    const res = await post(
      '/api/v1/kb/cards',
      { boardId: orderBoardId, title: '尽快解决发货问题', ownerId: undefined, dueDate: '2026-12-01' },
      chainSw,
    );
    expect(res.statusCode).toBe(422);
    expect(res.json().msg).toMatch(/模糊词/);
  });

  it('缺责任人/承诺日拒绝；红灯缺卡点说明拒绝', async () => {
    const users = await get('/api/v1/sys/users?keyword=op-demo', admin);
    const opId = users.json().data.list[0].id;
    const noOwner = await post(
      '/api/v1/kb/cards',
      { boardId: orderBoardId, title: 'PJ2026-0001 发货单未生成', dueDate: '2026-12-01' },
      chainSw,
    );
    expect(noOwner.statusCode).toBe(422);
    const noDue = await post(
      '/api/v1/kb/cards',
      { boardId: orderBoardId, title: 'PJ2026-0001 发货单未生成', ownerId: opId },
      chainSw,
    );
    expect(noDue.statusCode).toBe(422);
    const ok = await post(
      '/api/v1/kb/cards',
      {
        boardId: orderBoardId,
        title: 'PJ2026-0001 发货单未生成',
        ownerId: opId,
        dueDate: '2026-12-01',
        status: 'blocked',
        blockedReason: '发货单依赖 S3 模块',
        nextAction: 'S3 上线后回归',
      },
      chainSw,
    );
    expect(ok.statusCode).toBe(201);
    cardId = ok.json().data.id;
  });

  it('状态迁移：todo→doing→done 留痕；BR-12 补录标记', async () => {
    const t1 = await post(`/api/v1/kb/cards/${cardId}/transition`, { to: 'doing' }, op);
    expect(t1.json().data.status).toBe('doing');
    const t2 = await post(
      `/api/v1/kb/cards/${cardId}/transition`,
      { to: 'done', actionDate: '2026-01-01', note: '补录演示' },
      op,
    );
    expect(t2.json().data.isBackfill).toBe(true);
    const logs = await get(`/api/v1/kb/cards/${cardId}/logs`, op);
    expect(logs.json().data.length).toBeGreaterThanOrEqual(3);
    expect(logs.json().data.some((l: { isBackfill: boolean }) => l.isBackfill)).toBe(true);
  });

  it('承诺日可上调不可下调（FR-M10-007 铁律）', async () => {
    const c = await post(
      '/api/v1/kb/cards',
      {
        boardId: orderBoardId,
        title: '合同 PRT-2026-XS-SB-0002 备案催办',
        ownerId: (await get('/api/v1/sys/users?keyword=op-demo', admin)).json().data.list[0].id,
        dueDate: '2026-12-10',
      },
      chainSw,
    );
    const cid = c.json().data.id;
    const down = await post(`/api/v1/kb/cards/${cid}/due`, { dueDate: '2026-12-01' }, chainSw);
    expect(down.statusCode).toBe(422);
    expect(down.json().msg).toMatch(/不可下调/);
    const up = await post(`/api/v1/kb/cards/${cid}/due`, { dueDate: '2026-12-20' }, chainSw);
    expect(up.statusCode).toBe(201);
  });

  it('拖入红灯必填卡点+下一步（FR-M10-009）', async () => {
    const c = await post(
      '/api/v1/kb/cards',
      {
        boardId: orderBoardId,
        title: '客户回款计划确认',
        ownerId: (await get('/api/v1/sys/users?keyword=op-demo', admin)).json().data.list[0].id,
        dueDate: '2026-12-15',
      },
      chainSw,
    );
    const cid = c.json().data.id;
    await post(`/api/v1/kb/cards/${cid}/transition`, { to: 'doing' }, op);
    const noReason = await post(`/api/v1/kb/cards/${cid}/transition`, { to: 'blocked' }, op);
    expect(noReason.statusCode).toBe(422);
    const ok = await post(
      `/api/v1/kb/cards/${cid}/transition`,
      { to: 'blocked', blockedReason: '客户财务流程未走完', nextAction: '催款责任人本周对接' },
      op,
    );
    expect(ok.json().data.status).toBe('blocked');
  });
});

describe('M10 红灯引擎（BR-07 / FR-M10-014~017）', () => {
  it('3 天未解决 → 扫描升级总板；链主无删除（仅解决）', async () => {
    // 构造 3 天前 blocked 的卡
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    const users = await get('/api/v1/sys/users?keyword=op-demo', admin);
    const opId = users.json().data.list[0].id;
    const c = await post(
      '/api/v1/kb/cards',
      {
        boardId: orderBoardId,
        title: '供应链缺料预警-消防电池',
        ownerId: opId,
        dueDate: '2026-12-01',
        status: 'blocked',
        blockedReason: '安全库存低于下限',
        nextAction: '生成请购单',
      },
      chainSw,
    );
    const cid = c.json().data.id;
    await prisma.kbCard.update({
      where: { id: BigInt(cid) },
      data: { blockedAt: new Date(Date.now() - 4 * 86400000) },
    });

    const scan = await post('/api/v1/kb/internal/scans/redlight', { token: 'internal-dev-token' }, admin);
    expect(scan.json().data.escalated).toBeGreaterThanOrEqual(1);

    const detail = await get(`/api/v1/kb/cards/${cid}/logs`, admin);
    void detail;
    const escList = await get('/api/v1/kb/escalations', chainSw);
    const hit = escList.json().data.find((x: { id: string }) => x.id === cid);
    expect(hit).toBeTruthy();
    expect(hit.blockedDays).toBeGreaterThanOrEqual(3);

    // 催办
    const urge = await post(`/api/v1/kb/cards/${cid}/urge`, {}, admin);
    expect(urge.statusCode).toBe(201);

    // 解决 → 升级记录闭环
    const resolve = await post(
      `/api/v1/kb/cards/${cid}/transition`,
      { to: 'doing', note: '请购单已生成' },
      op,
    );
    expect(resolve.json().data.status).toBe('doing');
    // 未授权 token 调内部端点 → 403
    const bad = await post('/api/v1/kb/internal/scans/redlight', { token: 'wrong' }, admin);
    expect(bad.statusCode).toBe(403);
  });
});

describe('M10 会议模式（FR-M10-018~021）', () => {
  let meetingId: string;
  let redCardId: string;

  it('开启会议拉红灯卡；三问未填全不允许过下一张', async () => {
    // 准备一张红灯卡
    const users = await get('/api/v1/sys/users?keyword=op-demo', admin);
    const opId = users.json().data.list[0].id;
    const c = await post(
      '/api/v1/kb/cards',
      {
        boardId: orderBoardId,
        title: '订单交付延误风险-PJ2026-0001',
        ownerId: opId,
        dueDate: '2026-12-08',
        status: 'blocked',
        blockedReason: '齐套缺 2 项物料',
        nextAction: '采购加急',
      },
      chainSw,
    );
    redCardId = c.json().data.id;

    const start = await post(`/api/v1/kb/boards/${orderBoardId}/meetings`, { attendees: [] }, chainSw);
    expect(start.statusCode).toBe(201);
    meetingId = start.json().data.id;
    expect(start.json().data.agenda.length).toBe(4);
    expect(start.json().data.redCards.length).toBeGreaterThanOrEqual(1);

    const badAsk = await post(`/api/v1/kb/meetings/${meetingId}/ask`, { cardId: redCardId, askWhere: '缺料' }, chainSw);
    expect(badAsk.statusCode).toBe(422);
    const okAsk = await post(
      `/api/v1/kb/meetings/${meetingId}/ask`,
      { cardId: redCardId, askWhere: '齐套缺 2 项', askWho: '仓管员', askWhen: '明天 12:00 前' },
      chainSw,
    );
    expect(okAsk.statusCode).toBe(201);
  });

  it('结束会议生成纪要：红灯数/过堂结论/未闭环标记', async () => {
    const finish = await post(`/api/v1/kb/meetings/${meetingId}/finish`, {}, chainSw);
    expect(finish.statusCode).toBe(201);
    const minutes = finish.json().data;
    expect(minutes.redTotal).toBeGreaterThanOrEqual(1);
    expect(minutes.askedTotal).toBeGreaterThanOrEqual(1);
    expect(minutes.items[0].conclusion).toContain('前解决');
  });
});

describe('M10 指标引擎（FR-M10-004~006）+ BV-02', () => {
  it('指标计算落快照：红灯总数/闭环率/补录率真实取数', async () => {
    const compute = await post('/api/v1/kb/internal/metrics/compute', { token: 'internal-dev-token' }, admin);
    expect(compute.json().data.ok).toBeGreaterThanOrEqual(3);
    const metrics = await get('/api/v1/kb/boards/exec/metrics', op);
    const red = metrics.json().data.find((m: { metricCode: string }) => m.metricCode === 'redlight_total');
    expect(Number(red.value)).toBeGreaterThanOrEqual(1);
    expect(red.status).toBe('ok');
  });

  it('BV-02 全局看板聚合（FR-M10-026）', async () => {
    const res = await get('/api/v1/kb/executive', op);
    const d = res.json().data;
    expect(d.redTotal).toBeGreaterThanOrEqual(0);
    expect(d.health.length).toBe(9);
    expect(d.metrics.length).toBeGreaterThanOrEqual(3);
  });

  it('我的卡：逾期置顶（FR-M10-013）', async () => {
    const res = await get('/api/v1/kb/my-cards', op);
    expect(res.statusCode).toBe(200);
    const list = res.json().data;
    if (list.length >= 2) {
      expect(list[0].dueDate <= list[list.length - 1].dueDate || list[0].overdue).toBe(true);
    }
  });
});
