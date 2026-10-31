import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import { assertCents } from '@protech/shared-types';

/** 账龄分桶（FR-M08-003：30/60/90/180/360+） */
export const AGING_BUCKETS = ['0-30', '31-60', '61-90', '91-180', '181-360', '360+'] as const;

/**
 * M08 验收回款（FR-M08-001~005/008/009 S4 集）。
 * BR-02：开票申请受合同 invoicing_locked 冻结校验；验收触发质保起算。
 */
@Injectable()
export class FinanceService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 开票（BR-02） ----------------

  /** FR-M08-001 开票申请：BR-02 冻结校验（用印超 3 工作日未备案 → 拒绝） */
  async applyInvoice(body: Record<string, unknown>, userId: bigint) {
    const contractId = parseIdOrNull(body.contractId);
    if (!contractId) throw BizException.param('contractId 必填');
    const contract = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!contract) throw BizException.notFound('合同不存在');

    // BR-02 动态判定：signed 且未备案且超期 → 置锁并拒绝
    if (contract.sealAt && !contract.filedAt && contract.archiveDeadlineAt && contract.archiveDeadlineAt < new Date()) {
      if (!contract.invoicingLocked) {
        await this.prisma.contract.update({ where: { id: contractId }, data: { invoicingLocked: true } });
        await this.audit.log({ action: 'br02_freeze', objectType: 'Contract', objectId: contractId });
      }
      throw BizException.br('BR-02', '该合同已用印超过 3 个工作日未建档，开票申请已被冻结；请先完成建档');
    }
    if (contract.invoicingLocked) {
      throw BizException.br('BR-02', '该合同开票已被冻结（BR-02），请先完成建档备案');
    }

    let amountCents: bigint;
    try {
      amountCents = assertCents(body.amountCents, 'amountCents');
    } catch (e) {
      throw BizException.validation((e as Error).message, 'BR-10');
    }
    const invoice = await this.prisma.invoice.create({
      data: {
        contractId,
        projectId: parseIdOrNull(body.projectId),
        amountCents,
        invoiceNo: (body.invoiceNo as string) ?? null,
        status: 'applied',
        applicantId: userId,
        createdBy: userId,
      },
    });
    // 部分开票状态推进
    if (['signed', 'performing'].includes(contract.status)) {
      await this.prisma.contract.update({
        where: { id: contractId },
        data: { status: contract.status === 'signed' ? 'performing' : 'partial_invoiced' },
      });
    }
    await this.audit.log({ action: 'invoice_apply', objectType: 'Invoice', objectId: invoice.id, after: { amountCents: amountCents.toString() } });
    return invoice;
  }

  async issueInvoice(invoiceId: bigint, userId: bigint, invoiceNo: string) {
    const inv = await this.prisma.invoice.findFirst({ where: { id: invoiceId } });
    if (!inv) throw BizException.notFound('开票单不存在');
    if (inv.status !== 'applied') throw BizException.validation('开票单已处理');
    await this.prisma.invoice.update({
      where: { id: invoiceId },
      data: { status: 'issued', invoiceNo, invoicedAt: new Date() },
    });
    await this.audit.log({ action: 'invoice_issue', objectType: 'Invoice', objectId: invoiceId, after: { invoiceNo } });
    return { status: 'issued' };
  }

  // ---------------- 回款计划 / 登记（FR-M08-002） ----------------

  /** 按合同金额生成三段计划（预付/到货/验收/质保金，比例可传入） */
  async generatePaymentPlan(contractId: bigint, userId: bigint, plan?: Array<{ node: string; percent: number; dueAt?: string }>) {
    const contract = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!contract) throw BizException.notFound('合同不存在');
    const existing = await this.prisma.paymentPlan.count({ where: { contractId } });
    if (existing > 0) throw BizException.validation('回款计划已生成');
    const rows = (plan ?? [
      { node: 'delivery', percent: 0.6 },
      { node: 'acceptance', percent: 0.3 },
      { node: 'warranty', percent: 0.1 },
    ]).map((p) => {
      const amountCents = (contract.amountCents * BigInt(Math.round(p.percent * 10000))) / 10000n;
      return {
        contractId,
        node: p.node,
        dueAt: p.dueAt ? new Date(p.dueAt) : null,
        amountCents,
        createdBy: userId,
      };
    });
    await this.prisma.paymentPlan.createMany({ data: rows });
    await this.audit.log({ action: 'plan_generate', objectType: 'PaymentPlan', objectId: contractId, after: { count: rows.length } });
    return this.prisma.paymentPlan.findMany({ where: { contractId } });
  }

  async registerReceipt(body: Record<string, unknown>, userId: bigint) {
    const contractId = parseIdOrNull(body.contractId);
    if (!contractId || body.amountCents === undefined) throw BizException.param('contractId/amountCents 必填');
    let amountCents: bigint;
    try {
      amountCents = assertCents(body.amountCents, 'amountCents');
    } catch (e) {
      throw BizException.validation((e as Error).message, 'BR-10');
    }
    const receipt = await this.prisma.receipt.create({
      data: {
        contractId,
        projectId: parseIdOrNull(body.projectId),
        planId: parseIdOrNull(body.planId),
        amountCents,
        receivedAt: body.receivedAt ? new Date(String(body.receivedAt)) : new Date(),
        method: (body.method as string) ?? null,
        collectorId: parseIdOrNull(body.collectorId),
        remark: (body.remark as string) ?? null,
        createdBy: userId,
      },
    });
    if (body.planId) {
      await this.prisma.paymentPlan.updateMany({
        where: { id: parseId(body.planId), status: 'pending' },
        data: { status: 'received' },
      });
    }
    await this.audit.log({ action: 'receipt_register', objectType: 'Receipt', objectId: receipt.id, after: { amountCents: amountCents.toString() } });
    return receipt;
  }

  // ---------------- 账龄与回款率（FR-M08-003/009） ----------------

  /** 应收账龄（合同维度：累计开票 - 累计回款，按最早未清计划到期日起算） */
  async aging() {
    const contracts = await this.prisma.contract.findMany({
      where: { deletedAt: null, status: { in: ['signed', 'performing', 'partial_invoiced', 'accepted', 'warranty'] } },
    });
    const result = [];
    for (const c of contracts) {
      const invoices = await this.prisma.invoice.aggregate({
        _sum: { amountCents: true },
        where: { contractId: c.id, status: { not: 'void' } },
      });
      const receipts = await this.prisma.receipt.aggregate({
        _sum: { amountCents: true },
        where: { contractId: c.id },
      });
      const invoiced = invoices._sum.amountCents ?? 0n;
      const received = receipts._sum.amountCents ?? 0n;
      const outstanding = invoiced - received;
      if (outstanding <= 0n) {
        result.push({ contractId: c.id.toString(), contractCode: c.contractCode, outstandingCents: '0', bucket: null, overdueDays: 0 });
        continue;
      }
      // 最早未清计划到期日 → 逾期天数 → 分桶
      const pendingPlans = await this.prisma.paymentPlan.findFirst({
        where: { contractId: c.id, status: 'pending', dueAt: { not: null } },
        orderBy: { dueAt: 'asc' },
      });
      const dueAt = pendingPlans?.dueAt ?? null;
      const overdueDays = dueAt ? Math.max(0, Math.floor((Date.now() - dueAt.getTime()) / 86400000)) : 0;
      const bucket =
        overdueDays <= 30 ? '0-30' : overdueDays <= 60 ? '31-60' : overdueDays <= 90 ? '61-90' : overdueDays <= 180 ? '91-180' : overdueDays <= 360 ? '181-360' : '360+';
      result.push({
        contractId: c.id.toString(),
        contractCode: c.contractCode,
        outstandingCents: outstanding.toString(),
        dueAt,
        overdueDays,
        bucket,
        overdue: overdueDays > 0,
      });
    }
    return { buckets: AGING_BUCKETS, list: result };
  }

  /** 回款率 = 当期实收 / 当期应开票应收（12 册口径；按月） */
  async receiptRate(): Promise<{ rate: number | null; receivedCents: string; invoicedCents: string }> {
    const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const invoiced = await this.prisma.invoice.aggregate({
      _sum: { amountCents: true },
      where: { status: { not: 'void' }, createdAt: { gte: monthStart } },
    });
    const received = await this.prisma.receipt.aggregate({
      _sum: { amountCents: true },
      where: { receivedAt: { gte: monthStart } },
    });
    const inv = invoiced._sum.amountCents ?? 0n;
    const rec = received._sum.amountCents ?? 0n;
    return {
      rate: inv > 0n ? Number((Number(rec) / Number(inv)).toFixed(4)) : null,
      receivedCents: rec.toString(),
      invoicedCents: inv.toString(),
    };
  }

  // ---------------- 验收（FR-M08-005：质保起算） ----------------

  async registerAcceptance(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    if (!projectId || !body.conclusion) throw BizException.param('projectId/conclusion 必填');
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const acceptedAt = body.acceptedAt ? new Date(String(body.acceptedAt)) : new Date();
    const acceptance = await this.prisma.acceptance.create({
      data: {
        projectId,
        contractId: parseIdOrNull(body.contractId),
        acceptedAt,
        conclusion: String(body.conclusion),
        documents: JSON.stringify(Array.isArray(body.documents) ? body.documents : []),
        createdBy: userId,
      },
    });
    // 验收触发：项目状态 → accepted；质保期起算（warrantyStart=验收日，end=+warrantyMonths）
    if (project.status === 'shipped' || project.status === 'executing') {
      const warrantyStart = acceptedAt;
      const warrantyEnd = new Date(acceptedAt);
      warrantyEnd.setMonth(warrantyEnd.getMonth() + (project.warrantyStart && project.warrantyEnd ? 0 : 12));
      await this.prisma.project.update({
        where: { id: projectId },
        data: { status: 'accepted', warrantyStart, warrantyEnd, updatedBy: userId },
      });
    }
    // 合同状态联动 accepted
    if (body.contractId) {
      await this.prisma.contract.updateMany({
        where: { id: parseId(body.contractId), status: { in: ['signed', 'performing', 'partial_invoiced'] } },
        data: { status: 'accepted' },
      });
    }
    await this.audit.log({ action: 'acceptance', objectType: 'Acceptance', objectId: acceptance.id, after: { projectId: projectId.toString() } });
    return acceptance;
  }

  // ---------------- 催收记录（FR-M08-008） ----------------

  async addCollection(body: Record<string, unknown>, userId: bigint) {
    const contractId = parseIdOrNull(body.contractId);
    if (!contractId) throw BizException.param('contractId 必填');
    const row = await this.prisma.collection.create({
      data: {
        contractId,
        method: String(body.method ?? 'call'),
        content: (body.content as string) ?? null,
        createdBy: userId,
      },
    });
    return row;
  }

  /** FR-M08-004 worker：回款计划到期前 7 天提醒催款责任人 */
  async remindDuePlans(): Promise<{ reminded: number }> {
    const deadline = new Date(Date.now() + 7 * 86400000);
    const plans = await this.prisma.paymentPlan.findMany({
      where: { status: 'pending', dueAt: { lte: deadline, gte: new Date() } },
      take: 100,
    });
    for (const plan of plans) {
      const contract = await this.prisma.contract.findFirst({ where: { id: plan.contractId } });
      const project = contract ? await this.prisma.project.findFirst({ where: { id: { not: undefined } }, take: 0 }) : null;
      void project;
      // 催款责任人：合同关联项目的 collectorId（简化：取首关联项目）
      const link = contract ? await this.prisma.contractProject.findFirst({ where: { contractId: contract.id } }) : null;
      const collector = link ? (await this.prisma.project.findFirst({ where: { id: link.projectId } }))?.collectorId : null;
      if (collector) {
        await this.notify.send({
          userId: collector,
          type: 'remind',
          title: `回款提醒：${contract?.contractCode ?? ''} ${plan.node} 计划 ${Number(plan.amountCents) / 1_000_000} 万元将于 ${plan.dueAt?.toLocaleDateString('zh-CN')} 到期（FR-M08-004 双责任人）`,
          bizType: 'contract',
          bizId: plan.contractId.toString(),
        });
      }
    }
    return { reminded: plans.length };
  }
}
