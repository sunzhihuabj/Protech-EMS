import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import { assertCents, PRODUCT_LINE, isValidCode } from '@protech/shared-types';

/** FR-M02-004 报价状态机 */
const QUOTE_TRANSITIONS: Record<string, string[]> = {
  draft: ['sent', 'expired'],
  sent: ['won', 'lost', 'expired'],
  won: [],
  lost: [],
  expired: [],
};

/**
 * M02 报价管理（FR-M02-001~008）：报价登记/多头提示/历史成交价/状态机/中标转合同/转化统计。
 */
@Injectable()
export class QuotationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
  ) {}

  /** FR-M02-002 多头报价提示：同客户+同产品线未失效报价清单 */
  async multiQuoteHint(partyId: bigint, productLines: string[]): Promise<unknown[]> {
    const rows = await this.prisma.quotation.findMany({
      where: { partyId, status: { in: ['draft', 'sent'] }, deletedAt: null },
      orderBy: { id: 'desc' },
      take: 10,
    });
    return rows
      .filter((q) => (JSON.parse(q.productLines || '[]') as string[]).some((p) => productLines.includes(p)))
      .map((q) => ({
        id: q.id.toString(),
        quotationCode: q.quotationCode,
        totalCents: q.totalCents.toString(),
        status: q.status,
        createdAt: q.createdAt,
      }));
  }

  /** FR-M02-003 历史成交价参考（P25/P50/P90+最近 5 笔）；FR-M02-007 报价附件挂 fileId 扩展位 */
  async priceHistory(partyId: bigint, productLine: string) {
    const rows = await this.prisma.quotation.findMany({
      where: { partyId, status: 'won', deletedAt: null, productLines: { contains: productLine } },
      orderBy: { id: 'desc' },
      take: 100,
    });
    // 单价口径（分/台）参与分位
    const unitPrices = rows
      .map((q) => (q.qty > 0 ? Number(q.unitPriceCents) : 0))
      .filter((v) => v > 0)
      .sort((a, b) => a - b);
    const pct = (p: number) =>
      unitPrices.length ? String(Math.round(unitPrices[Math.min(unitPrices.length - 1, Math.floor((p / 100) * unitPrices.length))])) : null;
    return {
      count: unitPrices.length,
      p25: pct(25),
      p50: pct(50),
      p90: pct(90),
      recent: rows.slice(0, 5).map((q) => ({
        quotationCode: q.quotationCode,
        unitPriceCents: q.unitPriceCents.toString(),
        totalCents: q.totalCents.toString(),
        qty: q.qty,
        createdAt: q.createdAt,
      })),
    };
  }

  async create(body: Record<string, unknown>, userId: bigint) {
    const partyId = parseIdOrNull(body.partyId);
    if (!partyId) throw BizException.param('partyId 必填（BR-08 下拉）');
    const party = await this.prisma.customerParty.findFirst({ where: { id: partyId, deletedAt: null } });
    if (!party) throw BizException.notFound('客户主体不存在');

    const productLines = Array.isArray(body.productLines) ? (body.productLines as string[]) : [];
    if (!productLines.length || productLines.some((p) => !isValidCode(PRODUCT_LINE, p))) {
      throw BizException.param('productLines 非法（E-03）');
    }
    const qty = Number(body.qty ?? 0);
    if (!Number.isInteger(qty) || qty <= 0) throw BizException.param('qty 必须为正整数');
    let unitPriceCents: bigint;
    let totalCents: bigint;
    try {
      unitPriceCents = assertCents(body.unitPriceCents, 'unitPriceCents');
      totalCents =
        body.totalCents !== undefined && body.totalCents !== null && body.totalCents !== ''
          ? assertCents(body.totalCents, 'totalCents')
          : unitPriceCents * BigInt(qty);
    } catch (e) {
      throw BizException.validation((e as Error).message, 'BR-10');
    }

    const quotationCode = await this.codes.next('quotation');
    const q = await this.prisma.quotation.create({
      data: {
        quotationCode,
        partyId,
        groupId: party.groupId,
        projectId: parseIdOrNull(body.projectId),
        productLines: JSON.stringify(productLines),
        qty,
        unitPriceCents,
        totalCents,
        validUntil: body.validUntil ? new Date(String(body.validUntil)) : null,
        ownerId: userId,
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'Quotation', objectId: q.id, after: { quotationCode } });
    return q;
  }

  async transition(id: bigint, to: string, userId: bigint, body: Record<string, unknown>) {
    const q = await this.prisma.quotation.findFirst({ where: { id, deletedAt: null } });
    if (!q) throw BizException.notFound('报价单不存在');
    const allowed = QUOTE_TRANSITIONS[q.status] ?? [];
    if (!allowed.includes(to)) throw BizException.validation(`状态不允许：${q.status} → ${to}`);
    // FR-M02-004：中标必填预计签约时间
    let expectedSignAt = q.expectedSignAt;
    if (to === 'won') {
      if (!body.expectedSignAt) throw BizException.param('中标必须填写预计签约时间（FR-M02-004）');
      expectedSignAt = new Date(String(body.expectedSignAt));
    }
    const updated = await this.prisma.quotation.update({
      where: { id },
      data: {
        status: to,
        expectedSignAt,
        lostReason: to === 'lost' ? ((body.lostReason as string) ?? null) : q.lostReason,
        updatedBy: userId,
      },
    });
    await this.audit.log({
      action: 'quote_status',
      objectType: 'Quotation',
      objectId: id,
      before: { status: q.status },
      after: { status: to },
    });
    return updated;
  }

  /** FR-M02-005 中标转合同：一键生成合同草稿（带出客户/价格），合同评审走 M03 */
  async convertToContract(id: bigint, userId: bigint, body: Record<string, unknown>) {
    const q = await this.prisma.quotation.findFirst({ where: { id, deletedAt: null } });
    if (!q) throw BizException.notFound('报价单不存在');
    if (q.status !== 'won') throw BizException.validation('仅中标报价可转合同（FR-M02-005）');
    if (q.convertedContractId) throw BizException.validation('该报价已转化，禁止重复转化');

    const ourEntityId = parseIdOrNull(body.ourEntityId);
    if (!ourEntityId) throw BizException.param('ourEntityId 必填（我方签约主体）');
    const nature = String(body.nature ?? 'XS');
    if (!['XS', 'CG', 'KJ'].includes(nature)) throw BizException.param('nature 非法（E-06）');
    const bizType = String(body.bizType ?? 'SB');

    const contractCode = await this.codes.next('contract', {
      entity: (await this.prisma.legalEntity.findFirst({ where: { id: ourEntityId } }))?.contractPrefix ?? 'PRT',
      nature,
      biz: bizType,
    });
    const contract = await this.prisma.contract.create({
      data: {
        contractCode,
        contractName: String(body.contractName ?? `${q.quotationCode} 销售合同`),
        nature,
        bizType,
        ourEntityId,
        counterpartyId: q.partyId,
        amountCents: q.totalCents,
        quotationId: q.id,
        status: 'drafting',
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.prisma.quotation.update({
      where: { id },
      data: { convertedContractId: contract.id, updatedBy: userId },
    });
    await this.audit.log({
      action: 'convert',
      objectType: 'Contract',
      objectId: contract.id,
      after: { from: q.quotationCode, contractCode },
    });
    return contract;
  }

  /** FR-M02-006 转化统计：报价数/中标率/转化周期 */
  async stats() {
    const [total, won, converted] = await Promise.all([
      this.prisma.quotation.count({ where: { deletedAt: null } }),
      this.prisma.quotation.count({ where: { deletedAt: null, status: 'won' } }),
      this.prisma.quotation.count({ where: { deletedAt: null, convertedContractId: { not: null } } }),
    ]);
    const wonRows = await this.prisma.quotation.findMany({
      where: { deletedAt: null, status: 'won', convertedContractId: { not: null } },
      select: { createdAt: true, updatedAt: true },
      take: 200,
    });
    const cycleDays = wonRows.map((r) => (r.updatedAt.getTime() - r.createdAt.getTime()) / 86400000);
    const avgCycle = cycleDays.length ? Number((cycleDays.reduce((a, b) => a + b, 0) / cycleDays.length).toFixed(1)) : null;
    return {
      total,
      won,
      winRate: total ? Number((won / total).toFixed(4)) : 0,
      converted,
      avgWinCycleDays: avgCycle,
    };
  }
}
