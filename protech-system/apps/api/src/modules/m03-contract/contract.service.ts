import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { AppConfigService } from '../../shared/app-config.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import { assertCents, ratioToString, archiveDeadline, bcCode, CONTRACT_NATURE, isValidCode } from '@protech/shared-types';

/** E-05 合同状态机（05 册 §6.2） */
const CONTRACT_TRANSITIONS: Record<string, string[]> = {
  drafting: ['reviewed'],
  reviewed: ['signed'],
  signed: ['performing', 'partial_invoiced', 'accepted', 'warranty'],
  performing: ['changed', 'partial_invoiced', 'accepted', 'warranty'],
  changed: ['performing'],
  partial_invoiced: ['accepted', 'warranty'],
  accepted: ['warranty', 'closed'],
  warranty: ['closed'],
  closed: [],
};

const REVIEW_ROLES = ['biz', 'fin', 'eng', 'mfg'] as const;

/**
 * M03 合同管理（FR-M03-001~015）。
 * 两卡点：BR-01 评审提交必须带项目编码；BR-02 用印后 3 工作日未建档 → 冻结开票。
 * 评审通过自动立项（FR-M03-011）：project.status → pending_exec。
 */
@Injectable()
export class ContractService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
    private readonly config: AppConfigService,
  ) {}

  // ---------------- 建档 ----------------

  async create(body: Record<string, unknown>, userId: bigint) {
    const counterpartyId = parseIdOrNull(body.counterpartyId);
    const ourEntityId = parseIdOrNull(body.ourEntityId);
    if (!counterpartyId || !ourEntityId) throw BizException.param('counterpartyId/ourEntityId 必填（BR-08 下拉）');
    const nature = String(body.nature ?? '');
    if (!isValidCode(CONTRACT_NATURE, nature)) throw BizException.param('nature 必须为 XS/CG/KJ（E-06）');
    const bizType = String(body.bizType ?? '');
    if (!bizType) throw BizException.param('bizType 必填（业务码）');

    let amountCents: bigint;
    try {
      amountCents = assertCents(body.amountCents, 'amountCents');
    } catch (e) {
      throw BizException.validation((e as Error).message, 'BR-10');
    }
    const taxRate = body.taxRate !== undefined && body.taxRate !== null ? ratioToString(body.taxRate as string, 4) : null;

    const entity = await this.prisma.legalEntity.findFirst({ where: { id: ourEntityId, deletedAt: null } });
    if (!entity) throw BizException.notFound('我方法人主体不存在');
    const counterparty = await this.prisma.customerParty.findFirst({
      where: { id: counterpartyId, deletedAt: null },
    });
    if (!counterparty) throw BizException.notFound('对方主体不存在');

    const contractCode = await this.codes.next('contract', {
      entity: entity.contractPrefix,
      nature,
      biz: bizType,
    });

    const contract = await this.prisma.contract.create({
      data: {
        contractCode,
        contractName: String(body.contractName ?? `${counterparty.name}-${nature}`),
        nature,
        bizType,
        ourEntityId,
        counterpartyId,
        amountCents,
        taxRate,
        currency: String(body.currency ?? 'CNY'),
        paymentTerms: (body.paymentTerms as string) ?? null,
        settlement: (body.settlement as string) ?? null,
        invoiceReq: (body.invoiceReq as string) ?? null,
        contact: (body.contact as string) ?? null,
        phone: (body.phone as string) ?? null,
        deliveryAddr: (body.deliveryAddr as string) ?? null,
        signedAt: body.signedAt ? new Date(String(body.signedAt)) : null,
        periodStart: body.periodStart ? new Date(String(body.periodStart)) : null,
        periodEnd: body.periodEnd ? new Date(String(body.periodEnd)) : null,
        warrantyMonths: body.warrantyMonths != null ? Number(body.warrantyMonths) : null,
        quotationId: parseIdOrNull(body.quotationId),
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });

    // 合同-项目关联（≥1 为 BR-01 前提；创建时可暂挂，评审提交时强校验）
    const projectIds = Array.isArray(body.projectIds) ? body.projectIds : [];
    for (const pid of projectIds) {
      await this.attachProject(contract.id, parseId(pid), userId);
    }
    await this.audit.log({ action: 'create', objectType: 'Contract', objectId: contract.id, after: { contractCode } });
    return contract;
  }

  async attachProject(contractId: bigint, projectId: bigint, userId: bigint, orderLine?: string) {
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound(`项目不存在：${projectId}`);
    await this.prisma.contractProject.upsert({
      where: { contractId_projectId: { contractId, projectId } },
      create: { contractId, projectId, orderLine: orderLine ?? null, createdBy: userId },
      update: { orderLine: orderLine ?? null },
    });
  }

  // ---------------- 评审（BR-01 + 会签 + 自动立项） ----------------

  /** FR-M03-004 提交评审：BR-01 校验（至少挂 1 个项目）→ 生成四角色会签任务 */
  async submitReview(contractId: bigint, userId: bigint) {
    const contract = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!contract) throw BizException.notFound('合同不存在');
    if (contract.status !== 'drafting') throw BizException.validation(`仅起草中合同可提交评审（当前 ${contract.status}）`);

    const links = await this.prisma.contractProject.count({ where: { contractId } });
    if (links === 0) {
      // BR-01 服务端硬卡点
      throw BizException.br(
        'BR-01',
        '评审未关联项目编码，请先建档取码或选择已有项目',
      );
    }

    const round = (await this.maxRound(contractId)) + 1;
    await this.prisma.contractReview.createMany({
      data: REVIEW_ROLES.map((role) => ({ contractId, round, role })),
    });
    const updated = await this.prisma.contract.update({
      where: { id: contractId },
      data: { reviewStatus: 'reviewing', updatedBy: userId },
    });
    await this.audit.log({ action: 'review_submit', objectType: 'Contract', objectId: contractId, after: { round } });
    void updated;
    return { round, roles: REVIEW_ROLES };
  }

  /** 会签动作；四角色全部通过 → reviewed + 自动立项（FR-M03-011） */
  async reviewAct(contractId: bigint, role: string, userId: bigint, approved: boolean, opinion?: string) {
    if (!REVIEW_ROLES.includes(role as (typeof REVIEW_ROLES)[number])) {
      throw BizException.param(`role 必须为 ${REVIEW_ROLES.join('/')}`);
    }
    const contract = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!contract) throw BizException.notFound('合同不存在');
    if (contract.reviewStatus !== 'reviewing') throw BizException.validation('合同不在评审中');
    const round = await this.maxRound(contractId);
    const task = await this.prisma.contractReview.findFirst({
      where: { contractId, round, role },
      orderBy: { id: 'desc' },
    });
    if (!task) throw BizException.notFound('评审任务不存在');
    if (task.result) throw BizException.validation('该角色已会签');

    await this.prisma.contractReview.update({
      where: { id: task.id },
      data: { reviewerId: userId, result: approved ? 'approved' : 'rejected', opinion: opinion ?? null, reviewedAt: new Date() },
    });
    await this.audit.log({
      action: approved ? 'review_approve' : 'review_reject',
      objectType: 'ContractReview',
      objectId: task.id,
      after: { role, opinion: opinion ?? null },
    });

    if (!approved) {
      await this.prisma.contract.update({
        where: { id: contractId },
        data: { reviewStatus: 'rejected', updatedBy: userId },
      });
      return { reviewStatus: 'rejected' };
    }

    const pending = await this.prisma.contractReview.findFirst({ where: { contractId, round, result: null } });
    if (pending) return { reviewStatus: 'reviewing', pendingRole: pending.role };

    // 全部通过 → 事务：reviewed + 自动立项 + 回写 + 事件（11 册 §5.1）
    const links = await this.prisma.contractProject.findMany({ where: { contractId } });
    await this.prisma.$transaction(async (tx) => {
      await tx.contract.update({
        where: { id: contractId },
        data: { status: 'reviewed', reviewStatus: 'approved', updatedBy: userId },
      });
      for (const link of links) {
        await tx.project.updateMany({
          where: { id: link.projectId, status: { in: ['pending_farm'] } },
          data: { status: 'pending_exec', updatedBy: userId },
        });
      }
    });
    for (const link of links) {
      const project = await this.prisma.project.findFirst({ where: { id: link.projectId } });
      if (project?.pmUserId) {
        await this.notify.send({
          userId: project.pmUserId,
          type: 'todo',
          title: `合同 ${contract.contractCode} 评审通过，项目 ${project.projectCode} 已立项（签约待执行）`,
          bizType: 'project',
          bizId: project.id.toString(),
        });
      }
    }
    return { reviewStatus: 'approved', status: 'reviewed', autoProjects: links.length };
  }

  private async maxRound(contractId: bigint): Promise<number> {
    const last = await this.prisma.contractReview.findFirst({
      where: { contractId },
      orderBy: { round: 'desc' },
    });
    return last?.round ?? 0;
  }

  // ---------------- 用印 / 建档（BR-02） ----------------

  /** 用印登记（FR-M03-002 用印节点）：status → signed；archive_deadline = 用印+3 工作日 */
  async seal(contractId: bigint, userId: bigint, body: Record<string, unknown>) {
    const contract = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!contract) throw BizException.notFound('合同不存在');
    if (contract.status !== 'reviewed') throw BizException.validation(`仅已评审合同可用印（当前 ${contract.status}）`);
    const holidays = await this.config.getHolidays();
    const sealAt = body.sealAt ? new Date(String(body.sealAt)) : new Date();
    const deadline = archiveDeadline(sealAt, holidays);
    const updated = await this.prisma.contract.update({
      where: { id: contractId },
      data: {
        status: 'signed',
        sealAt,
        sealCopies: body.sealCopies != null ? Number(body.sealCopies) : null,
        signedAt: contract.signedAt ?? sealAt,
        archiveDeadlineAt: deadline,
        invoicingLocked: false,
        updatedBy: userId,
      },
    });
    await this.audit.log({
      action: 'seal',
      objectType: 'Contract',
      objectId: contractId,
      after: { sealAt, archiveDeadlineAt: deadline },
    });
    return updated;
  }

  /** BR-02 判定源（11 册 §4.2：GET /contracts/:id/archive-status） */
  async archiveStatus(contractId: bigint) {
    const c = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!c) throw BizException.notFound('合同不存在');
    const now = new Date();
    const overdue = !!(c.sealAt && !c.filedAt && c.archiveDeadlineAt && c.archiveDeadlineAt < now);
    if (overdue && !c.invoicingLocked) {
      await this.prisma.contract.update({ where: { id: c.id }, data: { invoicingLocked: true } });
      await this.audit.log({ action: 'br02_freeze', objectType: 'Contract', objectId: c.id });
      c.invoicingLocked = true;
    }
    const elements = await this.filingElements(c);
    return {
      sealedAt: c.sealAt,
      archiveDeadlineAt: c.archiveDeadlineAt,
      filedAt: c.filedAt,
      overdue,
      invoicingLocked: c.invoicingLocked,
      elementsFilled: elements.filled,
      elementsTotal: elements.total,
    };
  }

  /** 25 要素分组校验（FR-M03-001：A 基本/B 商务/C 履约/D 管理） */
  private async filingElements(c: {
    contractName: string;
    nature: string;
    bizType: string;
    ourEntityId: bigint;
    counterpartyId: bigint;
    amountCents: bigint;
    taxRate: string | null;
    paymentTerms: string | null;
    settlement: string | null;
    invoiceReq: string | null;
    signedAt: Date | null;
    effectiveAt: Date | null;
    periodStart: Date | null;
    periodEnd: Date | null;
    warrantyMonths: number | null;
    warrantyDepositRate: string | null;
    docCopies: number | null;
    contact: string | null;
    phone: string | null;
    deliveryAddr: string | null;
    filePath: string | null;
    sealAt: Date | null;
    sealCopies: number | null;
  }) {
    const required = [
      'contractName',
      'nature',
      'bizType',
      'ourEntityId',
      'counterpartyId',
      'amountCents',
      'taxRate',
      'paymentTerms',
      'contact',
      'signedAt',
      'periodStart',
      'periodEnd',
      'warrantyMonths',
      'docCopies',
      'deliveryAddr',
    ] as const;
    const missing: string[] = [];
    for (const k of required) {
      const v = (c as Record<string, unknown>)[k];
      if (v === null || v === undefined || v === '') missing.push(k);
    }
    return { filled: required.length - missing.length, total: required.length, missing };
  }

  /** 建档备案（FR-M03-005/卡点二）：25 要素补全 → performing + 解锁开票 */
  async filing(contractId: bigint, userId: bigint, body: Record<string, unknown>) {
    const c = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!c) throw BizException.notFound('合同不存在');
    if (!c.sealAt) throw BizException.validation('合同尚未用印，无需备案');
    const data: Record<string, unknown> = { updatedBy: userId };
    for (const k of [
      'paymentTerms',
      'settlement',
      'invoiceReq',
      'contact',
      'phone',
      'deliveryAddr',
      'filePath',
    ] as const) {
      if (body[k] !== undefined) data[k] = body[k];
    }
    for (const k of ['effectiveAt', 'periodStart', 'periodEnd'] as const) {
      if (body[k] !== undefined) data[k] = body[k] ? new Date(String(body[k])) : null;
    }
    if (body.warrantyMonths !== undefined) data.warrantyMonths = Number(body.warrantyMonths);
    if (body.warrantyDepositRate !== undefined) data.warrantyDepositRate = body.warrantyDepositRate;
    if (body.docCopies !== undefined) data.docCopies = Number(body.docCopies);
    if (body.taxRate !== undefined) data.taxRate = ratioToString(body.taxRate as string, 4);

    const merged = { ...c, ...data } as Parameters<ContractService['filingElements']>[0];
    const check = await this.filingElements(merged);
    if (check.missing.length) {
      throw Object.assign(BizException.validation(`25 要素未录全，缺失：${check.missing.join('、')}`), {
        data: check,
      });
    }
    const updated = await this.prisma.contract.update({
      where: { id: contractId },
      data: { ...data, status: 'performing', filedAt: new Date(), filedBy: userId, invoicingLocked: false },
    });
    await this.audit.log({ action: 'filing', objectType: 'Contract', objectId: contractId, after: data });
    return updated;
  }

  // ---------------- 变更（-BCn） ----------------

  /** FR-M03-007 补充协议：提交变更单 → 审批通过生成 -BC{n} 合同 */
  async createChange(contractId: bigint, userId: bigint, body: Record<string, unknown>) {
    const c = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!c) throw BizException.notFound('合同不存在');
    if (!['signed', 'performing', 'partial_invoiced', 'changed'].includes(c.status)) {
      throw BizException.validation('当前状态不允许变更');
    }
    const n = (await this.prisma.contractChange.count({ where: { contractId, deletedAt: null } })) + 1;
    const change = await this.prisma.contractChange.create({
      data: {
        contractId,
        changeCode: bcCode(c.contractCode, n),
        content: JSON.stringify(Array.isArray(body.content) ? body.content : []),
        reason: String(body.reason ?? ''),
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'ContractChange', objectId: change.id, after: { changeCode: change.changeCode } });
    return change;
  }

  async approveChange(changeId: bigint, userId: bigint, approved: boolean, note?: string) {
    const change = await this.prisma.contractChange.findFirst({ where: { id: changeId, deletedAt: null } });
    if (!change) throw BizException.notFound('变更单不存在');
    if (change.status !== 'pending') throw BizException.validation('变更单已处理');
    if (!approved) {
      const updated = await this.prisma.contractChange.update({
        where: { id: changeId },
        data: { status: 'rejected', reviewedBy: userId, reviewedAt: new Date() },
      });
      void updated;
      return { status: 'rejected' };
    }
    const master = await this.prisma.contract.findFirstOrThrow({ where: { id: change.contractId } });
    const bc = await this.prisma.contract.create({
      data: {
        contractCode: change.changeCode,
        contractName: `${master.contractName}-补充协议`,
        nature: master.nature,
        bizType: master.bizType,
        ourEntityId: master.ourEntityId,
        counterpartyId: master.counterpartyId,
        amountCents: master.amountCents,
        taxRate: master.taxRate,
        status: 'signed',
        parentContractId: master.id,
        bcSeq: Number(change.changeCode.split('-BC')[1] ?? 1),
        sealAt: new Date(),
        updatedBy: userId,
        createdBy: userId,
      },
    });
    await this.prisma.contractChange.update({
      where: { id: changeId },
      data: { status: 'approved', newContractId: bc.id, reviewedBy: userId, reviewedAt: new Date() },
    });
    await this.prisma.contract.update({
      where: { id: master.id },
      data: { status: 'changed', updatedBy: userId },
    });
    await this.audit.log({ action: 'approve', objectType: 'ContractChange', objectId: changeId, after: { bcCode: bc.contractCode } });
    return { status: 'approved', bcContractCode: bc.contractCode };
  }

  // ---------------- 查询 ----------------

  async detail(contractId: bigint) {
    const c = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!c) throw BizException.notFound('合同不存在');
    const [links, reviews, changes] = await Promise.all([
      this.prisma.contractProject.findMany({ where: { contractId } }),
      this.prisma.contractReview.findMany({ where: { contractId }, orderBy: [{ round: 'asc' }, { id: 'asc' }] }),
      this.prisma.contractChange.findMany({ where: { contractId, deletedAt: null }, orderBy: { id: 'asc' } }),
    ]);
    const projectIds = links.map((l) => l.projectId);
    const projects = projectIds.length
      ? await this.prisma.project.findMany({
          where: { id: { in: projectIds } },
          select: { id: true, projectCode: true, standardName: true, status: true },
        })
      : [];
    const status = await this.archiveStatus(contractId);
    return {
      ...this.present(c),
      projects: projects.map((p) => ({ ...p, id: p.id.toString() })),
      reviews: reviews.map((r) => ({
        ...r,
        id: r.id.toString(),
        contractId: r.contractId.toString(),
        reviewerId: r.reviewerId?.toString() ?? null,
      })),
      changes: changes.map((x) => ({ ...x, id: x.id.toString(), contractId: x.contractId.toString(), newContractId: x.newContractId?.toString() ?? null })),
      archive: status,
    };
  }

  present(c: {
    id: bigint;
    contractCode: string;
    contractName: string;
    nature: string;
    bizType: string;
    ourEntityId: bigint;
    counterpartyId: bigint;
    amountCents: bigint;
    taxRate: string | null;
    currency: string;
    status: string;
    reviewStatus: string;
    sealAt: Date | null;
    filedAt: Date | null;
    archiveDeadlineAt: Date | null;
    invoicingLocked: boolean;
    quotationId: bigint | null;
    createdAt: Date;
  }) {
    return {
      ...c,
      id: c.id.toString(),
      ourEntityId: c.ourEntityId.toString(),
      counterpartyId: c.counterpartyId.toString(),
      amountCents: c.amountCents.toString(),
      quotationId: c.quotationId?.toString() ?? null,
    };
  }
}
