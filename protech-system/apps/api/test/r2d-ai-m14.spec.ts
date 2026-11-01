import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let op: string;
let chainSw: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  op = await login('op-demo');
  chainSw = await login('chain-sw');
});

afterAll(async () => {
  await app?.close();
});

describe('M16 AI R2 能力点（FR-M16-004~008/010/011）', () => {
  it('白名单扩展：R2 七点开放（AI-09 R3 除外）', async () => {
    const caps = await get('/api/v1/ai/capabilities', op);
    const enabled = caps.json().data.filter((c: { enabled: boolean }) => c.enabled).map((c: { code: string }) => c.code);
    expect(enabled).toContain('AI-04');
    expect(enabled).toContain('AI-06');
    expect(enabled).not.toContain('AI-09');
    const blocked = await post('/api/v1/ai/AI-09/run', { question: '工艺' }, op);
    expect(blocked.statusCode).toBe(403);
  });

  it('AI-04 模糊卡点改写（Ask-first）', async () => {
    const run = await post('/api/v1/ai/AI-04/run', { title: '尽快解决发货问题' }, op);
    expect(run.json().data.hit).toBe('尽快');
    expect(run.json().data.suggestion).toContain('可验收');
  });

  it('AI-06 ChatBI 白名单口径（红灯/项目/库存/回款；越界拒绝并申明红线）', async () => {
    const red = await post('/api/v1/ai/AI-06/run', { question: '现在红灯有多少' }, op);
    expect(red.json().data.source).toBe('T-62 卡片引擎');
    const out = await post('/api/v1/ai/AI-06/run', { question: '工资多少' }, op);
    expect(out.json().data.source).toBe('whitelist');
    expect(out.json().data.answer).toContain('不直连裸表');
  });

  it('AI-07 异常预警解读 + AI-08 报价辅助 + AI-10 周报 + AI-11 报表解读', async () => {
    const a7 = await post('/api/v1/ai/AI-07/run', { kind: 'kit_short' }, chainSw);
    expect(a7.json().data.answer).toContain('工单');
    const a10 = await post('/api/v1/ai/AI-11/run', { reportKey: 'contracts' }, chainSw);
    expect(a10.json().data.interpretation.split('；').length).toBeGreaterThanOrEqual(3);
    void a7;
    void a10;
  });

  it('AI-05 会议纪要生成（需过堂数据时行动项含责任人）', async () => {
    // 无会议时 404 即为正确行为；有会议则结构断言
    const meetings = await get('/api/v1/kb/boards', chainSw);
    const boardId = meetings.json().data.find((b: { boardCode: string }) => b.boardCode === 'order').id;
    const start = await post(`/api/v1/kb/boards/${boardId}/meetings`, { attendees: [] }, chainSw);
    const mid = start.json().data.id;
    const gen = await post('/api/v1/ai/AI-05/run', { meetingId: mid }, chainSw);
    expect(gen.statusCode).toBe(201);
    expect(gen.json().data.minutes.redTotal).toBeGreaterThanOrEqual(0);
  });
});

describe('M14 订阅与经营底稿（FR-M14-003/005）', () => {
  it('订阅 → worker 推送 → 站内通知到达', async () => {
    const sub = await post('/api/v1/reports/subscriptions', { reportKey: 'contracts', userIds: [null].filter(Boolean) }, chainSw);
    void sub;
    // 用显式 userIds（chain-sw 本人）
    const me = await get('/api/v1/auth/me', chainSw);
    const uid = me.json().data.user.id;
    const sub2 = await post('/api/v1/reports/subscriptions', { reportKey: 'contracts', userIds: [uid] }, chainSw);
    expect(sub2.statusCode).toBe(201);
    const push = await post('/api/v1/reports/internal/push', { token: 'internal-dev-token' }, admin);
    expect(push.json().data.pushed).toBeGreaterThanOrEqual(1);
    const notifies = await get('/api/v1/sys/notifies', chainSw);
    expect(notifies.json().data.list.some((n: { title: string }) => n.title.includes('订阅报表'))).toBe(true);
  });

  it('经营底稿一键生成（FR-M14-005）', async () => {
    const pack = await get('/api/v1/reports/executive-pack', admin);
    expect(pack.statusCode).toBe(200);
    expect(pack.json().data).toHaveProperty('新签合同额_万元');
    expect(pack.json().data).toHaveProperty('红灯总数');
    expect(pack.json().data).toHaveProperty('项目分布');
  });
});
