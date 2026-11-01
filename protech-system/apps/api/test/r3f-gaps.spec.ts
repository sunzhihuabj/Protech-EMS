import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;
let op: string;
let fin: string;

let projectId: string;
let farmId: string;
let matId: string;
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
  op = await login('op-demo');
  fin = await login('fin-demo');
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  const ownerId = parties.json().data.list[0].id;
  const prj = await post(
    '/api/v1/mdm/projects',
    {
      standardName: '三峡新能源-姚安大龙口-R3F',
      shortName: 'R3F',
      phase: 'R3F',
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
  const rope = await get('/api/v1/mdm/materials?keyword=钢丝绳', chainSw);
  matId = rope.json().data.list[0].id;
});

afterAll(async () => {
  await app?.close();
});

describe('R3-F 真缺口收尾', () => {
  it('FR-M05-003/012 BOM CSV 导入（模板校验+错误行）+ 导出', async () => {
    const csv = ['物料编码,数量,点位', `${'3080-TS-0002'},4,塔底`, `BAD-CODE,2,`, `${'3080-TS-0002'},0,`].join('\n');
    const imp = await post('/api/v1/boms/import', { csv, name: '导入 BOM R3F', productLine: 'lift' }, chainSw);
    expect(imp.statusCode).toBe(201);
    expect(imp.json().data.okRows).toBe(1);
    expect(imp.json().data.errorRows).toBe(2);
    const exp = await get(`/api/v1/boms/${imp.json().data.bomId}/export`, chainSw);
    expect(exp.json().data.rows.length).toBe(1);
    expect(exp.json().data.rows[0]['物料编码']).toBe('3080-TS-0002');
  });

  it('FR-M06-018 发货复核：长度类强制二次确认', async () => {
    // 先入库再发货
    const sup = await post('/api/v1/mdm/suppliers', { name: 'R3F 供应商公司' }, chainSw);
    const suppliers = await get('/api/v1/mdm/suppliers?keyword=R3F', chainSw);
    const supplierId = suppliers.json().data.list[0].id;
    void sup;
    const po = await post('/api/v1/purchase-orders', { supplierId, lines: [{ materialId: matId, qty: 10 }] }, chainSw);
    await post(
      '/api/v1/stock-moves',
      { moveType: 'in', sourceType: 'arrival', sourceId: po.json().data.poCode, materialCode: '3080-TS-0002', qty: 10, batch: 'B9' },
      op,
    );
    const ship = await post(
      '/api/v1/stock-moves',
      { moveType: 'out', sourceType: 'ship', sourceId: 'SHIP-R3F', materialCode: '3080-TS-0002', qty: 4, legalEntity: 'PRT', projectId },
      op,
    );
    expect(ship.statusCode).toBe(201);
    const review = await post('/api/v1/shipments/SHIP-R3F/review', {}, op);
    expect(review.json().data.lines).toBe(1);
    expect(review.json().data.forceDoubleCheck).toContain('钢丝绳');
  });

  it('FR-M06-015 备货评审闸门（BR-06：超 20%）', async () => {
    const ok = await post('/api/v1/stock-reserve/check', { orderedQty: 100, reserveQty: 110 }, chainSw);
    expect(ok.json().data.needReview).toBe(false);
    const over = await post('/api/v1/stock-reserve/check', { orderedQty: 100, reserveQty: 130 }, chainSw);
    expect(over.json().data.needReview).toBe(true);
    expect(over.json().data.blockCode).toBe('BR-06');
  });

  it('FR-M07-004 交底及时率 + FR-M07-009 区域负荷', async () => {
    // 进场=shipped 口径：先推进项目
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'executing' }, chainSw);
    await post(`/api/v1/mdm/projects/${projectId}/transition`, { to: 'shipped', note: 'R3F 发货' }, chainSw);
    await post('/api/v1/disclosures', { projectId, type: 'plan' }, op);
    const rate = await get('/api/v1/delivery/disclosure-rate', op);
    expect(rate.json().data.进场项目数).toBeGreaterThan(0);
    const zl = await get('/api/v1/delivery/zone-load', op);
    expect(zl.statusCode).toBe(200);
  });

  it('FR-M04-012 设备档案沉淀（工单 BOM→设备档案）', async () => {
    const bom = await post('/api/v1/boms', { name: 'R3F 设备 BOM', bomType: 'full', productLine: 'lift', lines: [{ materialId: matId, qty: 1 }] }, chainSw);
    await post('/api/v1/work-orders', { projectId, bomId: bom.json().data.id, qty: 1 }, chainSw);
    const profile = await get(`/api/v1/mdm/projects/${projectId}/equipment-profile`, chainSw);
    expect(profile.json().data.profile.length).toBeGreaterThan(0);
    expect(profile.json().data.profile[0].lines.length).toBeGreaterThan(0);
  });

  it('FR-M02-008 报价打印（quote 模板）', async () => {
    const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
    const q = await post(
      '/api/v1/quotations',
      { partyId: parties.json().data.list[0].id, productLines: ['lift'], qty: 5, unitPriceCents: '80000000' },
      chainSw,
    );
    const print = await post(`/api/v1/quotations/${q.json().data.id}/print`, {}, op);
    expect(print.json().data.html).toContain('报价单');
    expect(print.json().data.html).toContain(q.json().data.quotationCode);
  });

  it('FR-M15-003 适配器预留 / FR-M15-004 T+ 质量报告 / FR-M15-007 售后宝导出', async () => {
    const ad = await get('/api/v1/adapters', op);
    expect(ad.json().data.importer.reserved).toContain('TPlusApiImporter');
    const qr = await get('/api/v1/tplus/quality-report', op);
    expect(qr.statusCode).toBe(200);
    const sb = await get('/api/v1/shoubao/dispatch-export', op);
    expect(sb.statusCode).toBe(200);
  });
});
