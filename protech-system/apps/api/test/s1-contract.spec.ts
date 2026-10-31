import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { getApp, login, req } from './app.factory';
import type { INestApplication } from '@nestjs/common';

let app: INestApplication;
let admin: string;
let chainSw: string;

async function get(url: string, token?: string) {
  return req('GET', url, undefined, token);
}
async function post(url: string, payload: unknown, token?: string) {
  return req('POST', url, payload, token);
}

let farmId: string;
let ownerId: string;
let entityId: string;
let projectId: string;

beforeAll(async () => {
  app = await getApp();
  admin = await login('admin');
  chainSw = await login('chain-sw');
  const farms = await get('/api/v1/mdm/farms?keyword=姚安', chainSw);
  farmId = farms.json().data.list[0].id;
  const parties = await get('/api/v1/mdm/parties?keyword=三峡', chainSw);
  ownerId = parties.json().data.list[0].id;
  const entities = await get('/api/v1/mdm/entities', chainSw);
  entityId = entities.json().data.find((e: { entityCode: string }) => e.entityCode === 'PRT').id;
});

afterAll(async () => {
  await app?.close();
});

async function createProject(shortName: string) {
  const res = await post(
    '/api/v1/mdm/projects',
    {
      standardName: `三峡新能源-姚安大龙口-${shortName}`,
      shortName,
      phase: shortName,
      windFarmId: farmId,
      ownerPartyId: ownerId,
      legalEntityId: entityId,
      orderType: 'owner_retrofit',
      productLines: ['lift'],
      unitCount: 2,
      contractAmountCents: '100000000',
    },
    chainSw,
  );
  expect(res.statusCode).toBe(201);
  return res.json().data.id as string;
}

describe('M02 报价（FR-M02-001~006）', () => {
  let quoteId: string;
  const year = new Date().getFullYear();

  it('报价登记 → QT 编码；BR-10 浮点拒绝', async () => {
    const bad = await post(
      '/api/v1/quotations',
      { partyId: ownerId, productLines: ['lift'], qty: 10, unitPriceCents: 1.5 },
      chainSw,
    );
    expect(bad.statusCode).toBe(422);
    expect(bad.json().blockCode).toBe('BR-10');

    const res = await post(
      '/api/v1/quotations',
      { partyId: ownerId, productLines: ['lift'], qty: 10, unitPriceCents: '50000000', remark: '姚安升降机 10 台' },
      chainSw,
    );
    expect(res.statusCode).toBe(201);
    expect(res.json().data.quotationCode).toMatch(new RegExp(`^QT${year}-\\d{4}$`));
    quoteId = res.json().data.id;
  });

  it('多头报价提示（FR-M02-002）+ 中标必填预计签约时间（FR-M02-004）', async () => {
    const hints = await post('/api/v1/quotations/check-multi', { partyId: ownerId, productLines: ['lift'] }, chainSw);
    expect(hints.json().data.hints.length).toBeGreaterThan(0);

    const noSign = await post(`/api/v1/quotations/${quoteId}/transition`, { to: 'sent' }, chainSw);
    expect(noSign.json().data.status).toBe('sent');
    const bad = await post(`/api/v1/quotations/${quoteId}/transition`, { to: 'won' }, chainSw);
    expect(bad.statusCode).toBe(422);
    const ok = await post(
      `/api/v1/quotations/${quoteId}/transition`,
      { to: 'won', expectedSignAt: '2026-12-01' },
      chainSw,
    );
    expect(ok.json().data.status).toBe('won');
  });

  it('中标转合同（FR-M02-005）：带出客户与金额', async () => {
    const res = await post(
      `/api/v1/quotations/${quoteId}/convert`,
      { ourEntityId: entityId, nature: 'XS', bizType: 'SB', contractName: '姚安二期升降机采购合同' },
      chainSw,
    );
    expect(res.statusCode).toBe(201);
    expect(res.json().data.contractCode).toMatch(new RegExp(`^PRT-${year}-XS-SB-\\d{4}$`));
    const again = await post(
      `/api/v1/quotations/${quoteId}/convert`,
      { ourEntityId: entityId },
      chainSw,
    );
    expect(again.statusCode).toBe(422);
  });
});

describe('M03 合同（BR-01 评审带码 → 自动立项；BR-02 建档冻结）', () => {
  let contractId: string;
  let projectId2: string;
  const year = new Date().getFullYear();

  it('合同建档：无项目可建（BR-01 在评审提交时拦截）', async () => {
    const res = await post(
      '/api/v1/contracts',
      {
        contractName: '姚安三期销售合同',
        nature: 'XS',
        bizType: 'SB',
        ourEntityId: entityId,
        counterpartyId: ownerId,
        amountCents: '80000000',
        taxRate: '0.09',
      },
      chainSw,
    );
    expect(res.statusCode).toBe(201);
    contractId = res.json().data.id;
    expect(res.json().data.contractCode).toMatch(new RegExp(`^PRT-${year}-XS-SB-\\d{4}$`));
  });

  it('BR-01：未挂项目提交评审 → 422 blockCode=BR-01', async () => {
    const res = await post(`/api/v1/contracts/${contractId}/review/submit`, {}, chainSw);
    expect(res.statusCode).toBe(422);
    expect(res.json().blockCode).toBe('BR-01');
    expect(res.json().msg).toMatch(/评审未关联项目编码/);
  });

  it('挂项目 → 评审四角色会签 → 全过自动立项（FR-M03-011）', async () => {
    projectId2 = await createProject('三期');
    const attach = await post(`/api/v1/contracts/${contractId}/projects`, { projectId: projectId2 }, chainSw);
    expect(attach.statusCode).toBe(201);

    const before = await get(`/api/v1/mdm/projects/${projectId2}`, chainSw);
    expect(before.json().data.status).toBe('pending_exec'); // 建档即 pending_exec（有风场）

    const submit = await post(`/api/v1/contracts/${contractId}/review/submit`, {}, chainSw);
    expect(submit.statusCode).toBe(201);
    expect(submit.json().data.roles).toEqual(['biz', 'fin', 'eng', 'mfg']);

    // biz 通过 → 仍 reviewing
    const r1 = await post(
      `/api/v1/contracts/${contractId}/review/act`,
      { role: 'biz', approved: true, opinion: '商务条款 OK' },
      chainSw,
    );
    expect(r1.json().data.reviewStatus).toBe('reviewing');
    // fin 驳回 → rejected
    const r2 = await post(
      `/api/v1/contracts/${contractId}/review/act`,
      { role: 'fin', approved: false, opinion: '金额与报价不一致' },
      chainSw,
    );
    expect(r2.json().data.reviewStatus).toBe('rejected');
    // 重新提交（第二轮）
    await post(`/api/v1/contracts/${contractId}/review/submit`, {}, chainSw);
    for (const role of ['biz', 'fin', 'eng']) {
      await post(`/api/v1/contracts/${contractId}/review/act`, { role, approved: true }, chainSw);
    }
    const final = await post(
      `/api/v1/contracts/${contractId}/review/act`,
      { role: 'mfg', approved: true },
      chainSw,
    );
    expect(final.json().data.status).toBe('reviewed');
    expect(final.json().data.autoProjects).toBeGreaterThan(0);
  });

  it('用印 → archive_deadline=+3 工作日（BR-02 起算）', async () => {
    const res = await post(`/api/v1/contracts/${contractId}/seal`, { sealCopies: 2 }, chainSw);
    expect(res.statusCode).toBe(201);
    expect(res.json().data.status).toBe('signed');
    const status = await get(`/api/v1/contracts/${contractId}/archive-status`, chainSw);
    expect(status.json().data.overdue).toBe(false);
    expect(status.json().data.invoicingLocked).toBe(false);
    expect(new Date(status.json().data.archiveDeadlineAt).getTime()).toBeGreaterThan(
      new Date(res.json().data.archiveDeadlineAt ?? 0).getTime() - 86400000,
    );
  });

  it('25 要素未录全 → 备案拒绝并列缺失；补全 → performing + 解锁', async () => {
    const bad = await post(`/api/v1/contracts/${contractId}/filing`, { contact: '张经办' }, chainSw);
    expect(bad.statusCode).toBe(422);
    expect(bad.json().msg).toMatch(/未录全/);
    const ok = await post(
      `/api/v1/contracts/${contractId}/filing`,
      {
        taxRate: '0.09',
        paymentTerms: '预付 30%，到货 60%，质保金 10%',
        contact: '张经办',
        phone: '13800000000',
        deliveryAddr: '云南省楚雄州姚安县大龙口风场',
        periodStart: '2026-12-01',
        periodEnd: '2027-12-01',
        warrantyMonths: 12,
        docCopies: 2,
      },
      chainSw,
    );
    expect(ok.statusCode).toBe(201);
    expect(ok.json().data.status).toBe('performing');
    expect(ok.json().data.invoicingLocked).toBe(false);
  });

  it('补充协议 -BC1（FR-M03-007）：审批通过生成 BCn 编号', async () => {
    const ch = await post(
      `/api/v1/contracts/${contractId}/changes`,
      { content: [{ field: 'amountCents', after: '90000000' }], reason: '增加 3 台升降机' },
      chainSw,
    );
    expect(ch.statusCode).toBe(201);
    expect(ch.json().data.changeCode).toMatch(/-BC1$/);
    const approve = await post(
      `/api/v1/contracts/changes/${ch.json().data.id}/review`,
      { approved: true },
      chainSw,
    );
    expect(approve.statusCode).toBe(201);
    expect(approve.json().data.bcContractCode).toMatch(/-BC1$/);
  });

  it('BR-09：合同编码不可改（中间件）', async () => {
    const { PrismaService } = await import('../src/shared/prisma.service');
    const prisma = app.get(PrismaService);
    await expect(
      prisma.contract.update({ where: { id: BigInt(contractId) }, data: { contractCode: 'PRT-2099-XS-HK-0001' } }),
    ).rejects.toMatchObject({ blockCode: 'BR-09' });
  });
});
