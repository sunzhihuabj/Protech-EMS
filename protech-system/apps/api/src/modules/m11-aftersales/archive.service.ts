import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AppConfigService } from '../../shared/app-config.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** E-13 门类 → 是否双审批（FR-M12-003：合同/财务/人事双审批；证照外带分管副总） */
const DOUBLE_APPROVE_CATEGORIES = ['HT', 'CW', 'RS'];

/**
 * M11 售后（FR-M11-001~003/007）+ M12 档案（FR-M12-001~006/009/010）。
 * 档号规则复用编码引擎 archive 模板（{entity}-{YYYY}-{cat}-{ret}-{NNNN}）。
 */
@Injectable()
export class AfterArchiveService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly config: AppConfigService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 售后工单（FR-M11-001） ----------------

  async createAfterSalesOrder(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseId(body.projectId);
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const type = String(body.type ?? 'fix');
    if (!['fix', 'inspect', 'retrofit'].includes(type)) throw BizException.param('type 须为 fix/inspect/retrofit');
    const orderCode = await this.codes.next('aftersales');
    const order = await this.prisma.afterSalesOrder.create({
      data: {
        orderCode,
        projectId,
        type,
        description: String(body.description ?? ''),
        ownerId: body.ownerId ? parseId(body.ownerId) : userId,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'AfterSalesOrder', objectId: order.id, after: { orderCode } });
    return order;
  }

  /** 状态推进：受阻 → 自动挂⑤板红灯（FR-M11-007）；完成 → 质保内成本归集（FR-M11-003） */
  async transitionOrder(orderId: bigint, to: string, userId: bigint, blockedReason?: string, costCents?: bigint) {
    const order = await this.prisma.afterSalesOrder.findFirst({ where: { id: orderId, deletedAt: null } });
    if (!order) throw BizException.notFound('工单不存在');
    const allowed: Record<string, string[]> = {
      open: ['in_progress', 'blocked'],
      in_progress: ['blocked', 'done'],
      blocked: ['in_progress', 'done'],
      done: [],
    };
    if (!(allowed[order.status] ?? []).includes(to)) throw BizException.validation(`状态不允许：${order.status} → ${to}`);
    if (to === 'blocked' && !blockedReason) throw BizException.validation('受阻必须填写原因（联动红灯）');

    const data: Record<string, unknown> = { status: to, updatedBy: userId };
    if (to === 'in_progress' && !order.startedAt) data.startedAt = new Date();
    if (to === 'blocked') data.blockedReason = blockedReason;
    if (to === 'done') data.doneAt = new Date();
    if (costCents !== undefined && costCents !== null) data.costCents = costCents;
    await this.prisma.afterSalesOrder.update({ where: { id: orderId }, data });

    if (to === 'blocked') {
      // FR-M11-007：工单受阻自动生成红灯卡到⑤板
      const deliveryBoard = await this.prisma.board.findFirst({ where: { boardCode: 'delivery' } });
      if (deliveryBoard) {
        await this.prisma.kbCard.create({
          data: {
            boardId: deliveryBoard.id,
            title: `售后工单 ${order.orderCode} 受阻（${order.type}）`,
            ownerId: order.ownerId,
            dueDate: new Date(Date.now() + 3 * 86400000),
            status: 'blocked',
            blockedReason,
            nextAction: '售后责任人限期消缺',
            sourceType: 'aftersales',
            sourceId: order.id.toString(),
            createdBy: userId,
            updatedBy: userId,
          },
        });
      }
    }
    if (to === 'done' && costCents && costCents > 0n) {
      // FR-M11-003 质保内成本归集到 M09
      await this.prisma.costEntry.create({
        data: {
          projectId: order.projectId,
          costType: 'expense',
          sourceType: 'aftersales',
          sourceId: order.id.toString(),
          amountCents: costCents,
          period: new Date().toISOString().slice(0, 7),
          note: `售后工单 ${order.orderCode} 消缺成本`,
          createdBy: userId,
        },
      });
    }
    await this.audit.log({ action: 'as_order_status', objectType: 'AfterSalesOrder', objectId: orderId, before: { status: order.status }, after: { status: to } });
    return { status: to };
  }

  /** FR-M11-002 定检排程：到期生成工单（worker 调用） */
  async generateDueInspections(userId: bigint): Promise<{ generated: number }> {
    const due = await this.prisma.maintenancePlan.findMany({
      where: { status: 'active', nextDueAt: { lte: new Date() } },
      take: 100,
    });
    let generated = 0;
    for (const plan of due) {
      const exists = await this.prisma.afterSalesOrder.findFirst({
        where: { projectId: plan.projectId, type: 'inspect', createdAt: { gte: new Date(Date.now() - 30 * 86400000) } },
      });
      if (exists) continue;
      await this.createAfterSalesOrder(
        { projectId: plan.projectId, type: 'inspect', description: `周期定检（计划 #${plan.id} 到期自动生成）`, ownerId: userId },
        userId,
      );
      await this.prisma.maintenancePlan.update({
        where: { id: plan.id },
        data: { lastDoneAt: plan.nextDueAt, nextDueAt: new Date(plan.nextDueAt.getTime() + plan.cycleMonths * 30 * 86400000) },
      });
      generated++;
    }
    return { generated };
  }

  /** FR-M11-003 质保期跟踪：临期 30 天提醒 */
  async warrantyWatch(): Promise<{ reminded: number }> {
    const deadline = new Date(Date.now() + 30 * 86400000);
    const projects = await this.prisma.project.findMany({
      where: { deletedAt: null, warrantyEnd: { lte: deadline, gte: new Date() }, status: { in: ['accepted', 'warranty'] } },
    });
    for (const p of projects) {
      if (p.pmUserId) {
        await this.notify.send({
          userId: p.pmUserId,
          type: 'remind',
          title: `项目 ${p.projectCode} 质保期将于 ${p.warrantyEnd?.toLocaleDateString('zh-CN')} 到期（FR-M11-003 临期提醒）`,
          bizType: 'project',
          bizId: p.id.toString(),
        });
      }
    }
    return { reminded: projects.length };
  }

  /** FR-M11-004 故障率统计（按产品线） */
  async faultStats() {
    const orders = await this.prisma.afterSalesOrder.findMany({ where: { type: 'fix', deletedAt: null } });
    const projectIds = [...new Set(orders.map((o) => o.projectId))];
    const projects = projectIds.length
      ? await this.prisma.project.findMany({ where: { id: { in: projectIds } }, select: { id: true, productLines: true } })
      : [];
    const pById = new Map(projects.map((p) => [p.id, p]));
    const byLine: Record<string, number> = {};
    for (const o of orders) {
      const lines = JSON.parse(pById.get(o.projectId)?.productLines ?? '[]') as string[];
      for (const line of lines) byLine[line] = (byLine[line] ?? 0) + 1;
    }
    return { byProductLine: byLine, total: orders.length };
  }

  // ---------------- 档案（FR-M12-001~006） ----------------

  async createArchive(body: Record<string, unknown>, userId: bigint) {
    const category = String(body.category ?? '');
    const retention = String(body.retention ?? 'S30');
    const entity = String(body.entity ?? 'PRT');
    if (!body.title || !['XZ', 'ZZ', 'HT', 'CW', 'RS', 'GC', 'JY', 'SX'].includes(category)) {
      throw BizException.param('title 必填；category 须为八门类（E-13）');
    }
    if (!['Y', 'S30', 'S10'].includes(retention)) throw BizException.param('retention 须为 Y/S30/S10（E-14）');
    const archiveNo = await this.codes.next('archive', { entity, cat: category, ret: retention });
    // 期满鉴定基准：保管期限起算 + 期限（Y=9999/S30=30 年/S10=10 年）
    const years = retention === 'Y' ? 9999 : retention === 'S30' ? 30 : 10;
    const dueAt = new Date();
    dueAt.setFullYear(dueAt.getFullYear() + years);
    const archive = await this.prisma.archive.create({
      data: {
        archiveNo,
        category,
        retention,
        title: String(body.title),
        physicalLoc: (body.physicalLoc as string) ?? null,
        fileId: body.fileId ? parseId(body.fileId) : null,
        contractId: body.contractId ? parseId(body.contractId) : null,
        projectId: body.projectId ? parseId(body.projectId) : null,
        dueAt,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'Archive', objectId: archive.id, after: { archiveNo } });
    return archive;
  }

  /** FR-M12-003 借阅审批：门类判定单/双审批；批准后 ≤5 工作日 */
  async requestBorrow(body: Record<string, unknown>, userId: bigint) {
    const archiveId = parseId(body.archiveId);
    const archive = await this.prisma.archive.findFirst({ where: { id: archiveId, deletedAt: null } });
    if (!archive) throw BizException.notFound('档案不存在');
    if (archive.status === 'borrowed') throw BizException.validation('档案已借出');
    const needApprove2 = DOUBLE_APPROVE_CATEGORIES.includes(archive.category);
    const borrow = await this.prisma.archiveBorrow.create({
      data: {
        archiveId,
        borrowerId: userId,
        purpose: String(body.purpose ?? ''),
        needApprove2,
        createdBy: userId,
      },
    });
    await this.audit.log({ action: 'borrow_request', objectType: 'ArchiveBorrow', objectId: borrow.id, after: { archiveNo: archive.archiveNo } });
    return { id: borrow.id.toString(), needApprove2 };
  }

  async approveBorrow(borrowId: bigint, approverSeq: 1 | 2, userId: bigint, approved: boolean) {
    const borrow = await this.prisma.archiveBorrow.findFirst({ where: { id: borrowId } });
    if (!borrow) throw BizException.notFound('借阅单不存在');
    if (borrow.status !== 'pending') throw BizException.validation('借阅单已处理');
    if (borrow.needApprove2 && approverSeq === 2 && !borrow.approver1Id) {
      throw BizException.validation('双审批档案须先完成第一审批');
    }
    if (!approved) {
      await this.prisma.archiveBorrow.update({ where: { id: borrowId }, data: { status: 'rejected' } });
      return { status: 'rejected' };
    }
    const data: Record<string, unknown> = approverSeq === 1 ? { approver1Id: userId } : { approver2Id: userId };
    const bothDone = borrow.needApprove2 ? approverSeq === 2 || borrow.approver1Id !== null : true;
    if (bothDone && (borrow.needApprove2 ? approverSeq === 2 && borrow.approver1Id !== null : true)) {
      const holidays = await this.config.getHolidays();
      const { addBusinessDays } = await import('@protech/shared-types');
      const dueAt = addBusinessDays(new Date(), 5, holidays);
      Object.assign(data, { status: 'approved', approvedAt: new Date(), dueAt });
    }
    await this.prisma.archiveBorrow.update({ where: { id: borrowId }, data });
    return { status: 'approved', bothDone };
  }

  /** 借出登记：档案状态 → 借出 */
  async markOut(borrowId: bigint, userId: bigint) {
    const borrow = await this.prisma.archiveBorrow.findFirst({ where: { id: borrowId } });
    if (!borrow || borrow.status !== 'approved') throw BizException.validation('借阅单未批准');
    await this.prisma.$transaction([
      this.prisma.archiveBorrow.update({ where: { id: borrowId }, data: { status: 'out', outAt: new Date() } }),
      this.prisma.archive.update({ where: { id: borrow.archiveId }, data: { status: 'borrowed' } }),
    ]);
    await this.audit.log({ action: 'borrow_out', objectType: 'ArchiveBorrow', objectId: borrowId });
    return { status: 'out' };
  }

  /** 归还：档案回在库；逾期标记 */
  async markReturned(borrowId: bigint, userId: bigint) {
    const borrow = await this.prisma.archiveBorrow.findFirst({ where: { id: borrowId } });
    if (!borrow || borrow.status !== 'out') throw BizException.validation('借阅单未在借出状态');
    const overdue = borrow.dueAt ? borrow.dueAt < new Date() : false;
    await this.prisma.$transaction([
      this.prisma.archiveBorrow.update({
        where: { id: borrowId },
        data: { status: overdue ? 'overdue' : 'returned', returnedAt: new Date() },
      }),
      this.prisma.archive.update({ where: { id: borrow.archiveId }, data: { status: 'zai_ku' } }),
    ]);
    await this.audit.log({ action: 'borrow_return', objectType: 'ArchiveBorrow', objectId: borrowId, after: { overdue } });
    return { status: overdue ? 'overdue' : 'returned' };
  }

  /** FR-M12-004 期满鉴定清单（每年 12 月 worker 生成） */
  async expiryList() {
    const due = await this.prisma.archive.findMany({
      where: { deletedAt: null, dueAt: { lte: new Date() }, status: { not: 'destroyed' } },
      take: 200,
    });
    return due.map((a) => ({ ...a, id: a.id.toString(), fileId: a.fileId?.toString() ?? null, contractId: a.contractId?.toString() ?? null, projectId: a.projectId?.toString() ?? null }));
  }

  /** FR-M12-006 项目归档提醒（验收后 30 日）+ FR-M12-002 合同 30 日移交（worker） */
  async archiveRemindScan(): Promise<{ reminded: number }> {
    const deadline = new Date(Date.now() - 30 * 86400000);
    const projects = await this.prisma.project.findMany({
      where: { status: 'accepted', deletedAt: null, updatedAt: { lte: deadline } },
      take: 100,
    });
    let reminded = 0;
    for (const p of projects) {
      const archived = await this.prisma.archive.findFirst({ where: { projectId: p.id, deletedAt: null } });
      if (!archived && p.pmUserId) {
        await this.notify.send({
          userId: p.pmUserId,
          type: 'remind',
          title: `项目 ${p.projectCode} 验收已超 30 日，请完成资料归档（FR-M12-006）`,
          bizType: 'project',
          bizId: p.id.toString(),
        });
        reminded++;
      }
    }
    return { reminded };
  }
}
