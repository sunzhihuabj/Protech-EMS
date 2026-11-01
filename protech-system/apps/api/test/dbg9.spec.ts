import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
let op: string;
beforeAll(async () => { await getApp(); op = await login('op-demo'); });
afterAll(async () => { (await getApp()).close(); });
describe('dbg perm', () => {
  it('me permissions', async () => {
    const me = await req('GET', '/api/v1/auth/me', undefined, op);
    const perms = me.json().data.permissions;
    console.log('OP PERMS sample:', JSON.stringify(perms.filter((p: string) => p.includes('integration') || p.includes('tplus'))));
    const mon = await req('GET', '/api/v1/integration/monitor', undefined, op);
    console.log('MONITOR:', mon.statusCode, JSON.stringify(mon.body).slice(0, 150));
    expect(true).toBe(true);
  });
});
