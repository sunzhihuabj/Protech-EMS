import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { MetricService } from './meeting-metric.service';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/**
 * 三级角色视图（08 册）：BV-01 部门看板（FR-M10-025）与 BV-03 项目监管看板（FR-M10-027）。
 * 复用 metric_def/卡片引擎，零数据重复。
 */
@Injectable()
export class DeptProjectViewService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly metrics: MetricService,
  ) {}

  // ---------------- BV-01 部门看板（六区） ----------------

  async deptView(deptId: bigint) {
    const dept = await this.prisma.sysDept.findFirst({ where: { id: deptId, deletedAt: null } });
    if (!dept) throw BizException.notFound('部门不存在');

    // 部门成员（含子部门）
    const subtree = await this.prisma.sysDept.findMany({
      where: { deletedAt: null, OR: [{ path: { startsWith: dept.path + dept.id + '/' } }, { id: dept.id }] },
      select: { id: true },
    });
    const deptIds = subtree.map((d) => d.id);
    const members = await this.prisma.sysUser.findMany({
      where: { deptId: { in: deptIds }, deletedAt: null, status: 'active' },
      select: { id: true, displayName: true, position: true, deptId: true },
    });
    const memberIds = members.map((m) => m.id);

    // 项目卡片区：部门成员担任责任人/负责人/经手的项目
    const projects = memberIds.length
      ? await this.prisma.project.findMany({
          where: {
            deletedAt: null,
            OR: [
              { pmUserId: { in: memberIds } },
              { engOwnerId: { in: memberIds } },
              { salesOwnerId: { in: memberIds } },
              { collectorId: { in: memberIds } },
              { aftersalesOwnerId: { in: memberIds } },
            ],
          },
          select: { id: true, projectCode: true, standardName: true, status: true, pmUserId: true },
          take: 50,
          orderBy: { updatedAt: 'desc' },
        })
      : [];

    // 红灯区：成员名下红灯卡
    const redCards = memberIds.length
      ? await this.prisma.kbCard.findMany({
          where: { ownerId: { in: memberIds }, status: 'blocked', deletedAt: null },
          take: 50,
          orderBy: { blockedAt: 'asc' },
        })
      : [];

    // 单据动态区：成员经手的最近出入库流水
    const recentMoves = memberIds.length
      ? await this.prisma.stockMove.findMany({
          where: { operatorId: { in: memberIds } },
          orderBy: { id: 'desc' },
          take: 10,
        })
      : [];

    // 人员负荷区：在办项目数 / 逾期卡数
    const load = await Promise.all(
      members.slice(0, 30).map(async (m) => {
        const [projCount, overdueCards, redCount] = await Promise.all([
          this.prisma.project.count({
            where: {
              deletedAt: null,
              OR: [{ pmUserId: m.id }, { engOwnerId: m.id }, { salesOwnerId: m.id }],
            },
          }),
          this.prisma.kbCard.count({
            where: { ownerId: m.id, deletedAt: null, status: { in: ['todo', 'doing', 'blocked'] }, dueDate: { lt: new Date() } },
          }),
          this.prisma.kbCard.count({ where: { ownerId: m.id, status: 'blocked', deletedAt: null } }),
        ]);
        return {
          userId: m.id.toString(),
          displayName: m.displayName,
          position: m.position,
          projectCount: projCount,
          overdueCards,
          redCards: redCount,
        };
      }),
    );

    // KPI 区：本部门承担的九板指标（board 主责部门映射）
    const boardCodeByDept: Record<string, string> = {
      SWB: 'order', JHB: 'supply', CGB: 'supply', CCB: 'supply', SCB: 'mfg', GYB: 'mfg',
      ZLAQ: 'mfg', GCJS: 'delivery', JCXZ: 'delivery', YFSJ: 'rd', XSTD: 'market',
      SCB_SH: 'market', CWB: 'fin', QGB: 'ga', ZJCB: 'exec',
    };
    const kpiBoard = boardCodeByDept[dept.code];
    const kpis = kpiBoard ? await this.metrics.boardMetrics(kpiBoard) : [];

    return {
      dept: { ...dept, id: dept.id.toString(), parentId: dept.parentId?.toString() ?? null },
      memberCount: members.length,
      kpis,
      projects: projects.map((p) => ({ ...p, id: p.id.toString(), pmUserId: p.pmUserId?.toString() ?? null })),
      redCards: redCards.map((c) => ({ ...c, id: c.id.toString(), boardId: c.boardId.toString(), ownerId: c.ownerId.toString() })),
      recentMoves: recentMoves.map((m) => ({ ...m, id: m.id.toString(), materialId: m.materialId.toString(), operatorId: m.operatorId.toString(), projectId: m.projectId?.toString() ?? null })),
      load,
    };
  }

  // ---------------- BV-03 项目监管看板（五区，08 册 §5.3） ----------------

  /** 九阶段全流程时间线 */
  private timelineOf(project: { status: string; createdAt: Date; warrantyStart: Date | null; warrantyEnd: Date | null }) {
    const stages = ['立项', '方案交底', '生产齐套', '发货', '进场安装', '调试验收', '结算', '回款', '质保'];
    const statusMap: Record<string, string> = {
      pending_farm: '立项',
      pending_exec: '方案交底', // 评审通过即立项完成（FR-M03-011），当前阶段=方案交底
      executing: '生产齐套',
      shipped: '发货',
      accepted: '调试验收',
      warranty: '质保',
      closed: '质保',
    };
    const currentIdx = stages.indexOf(statusMap[project.status] ?? '立项');
    return stages.map((name, i) => ({
      stage: name,
      state: i < currentIdx ? 'done' : i === currentIdx ? 'current' : 'todo',
    }));
  }

  async projectView(projectId: bigint) {
    const p = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!p) throw BizException.notFound('项目不存在');

    const workOrders = await this.prisma.workOrder.findMany({
      where: { projectId, deletedAt: null },
      select: { id: true, woCode: true, status: true, kitStatus: true },
    });
    const woIds = workOrders.map((w) => w.id.toString());

    const [contracts, dispatches, milestones, redCards, siteLogs, siteIssues, receipts, invoices] = await Promise.all([
      this.prisma.contractProject.findMany({ where: { projectId } }),
      this.prisma.dispatch.findMany({ where: { projectId, deletedAt: null }, orderBy: { id: 'desc' }, take: 10 }),
      this.prisma.milestone.findMany({ where: { projectId, deletedAt: null }, orderBy: { sort: 'asc' } }),
      this.prisma.kbCard.findMany({
        where: {
          status: 'blocked',
          deletedAt: null,
          OR: [
            { sourceType: 'project', sourceId: projectId.toString() },
            ...(woIds.length ? [{ sourceType: 'work_order', sourceId: { in: woIds } }] : []),
          ],
        },
        take: 20,
      }),
      this.prisma.siteLog.findMany({ where: { projectId }, orderBy: { id: 'desc' }, take: 5 }),
      this.prisma.siteIssue.findMany({ where: { projectId }, orderBy: { id: 'desc' }, take: 10 }),
      this.prisma.receipt.findMany({ where: { projectId }, orderBy: { id: 'desc' }, take: 10 }),
      this.prisma.invoice.findMany({ where: { projectId }, orderBy: { id: 'desc' }, take: 10 }),
    ]);

    // 关键节点卡
    const contractIds = contracts.map((c) => c.contractId);
    const contractAgg = contractIds.length
      ? await this.prisma.contract.aggregate({ _sum: { amountCents: true }, where: { id: { in: contractIds } } })
      : { _sum: { amountCents: null } };
    const receiptAgg = await this.prisma.receipt.aggregate({ _sum: { amountCents: true }, where: { projectId } });

    // 团队
    const teamUserIds = [p.pmUserId, p.engOwnerId, p.aftersalesOwnerId, p.salesOwnerId, p.collectorId].filter(
      (x): x is bigint => x !== null,
    );
    const team = teamUserIds.length
      ? await this.prisma.sysUser.findMany({
          where: { id: { in: teamUserIds } },
          select: { id: true, displayName: true, position: true },
        })
      : [];

    return {
      project: {
        id: p.id.toString(),
        projectCode: p.projectCode,
        standardName: p.standardName,
        status: p.status,
        unitCount: p.unitCount,
        contractAmountCents: p.contractAmountCents?.toString() ?? null,
        warrantyStart: p.warrantyStart,
        warrantyEnd: p.warrantyEnd,
        pendingFlags: JSON.parse(p.pendingFlags ?? '[]'),
      },
      timeline: this.timelineOf(p),
      keyNodes: {
        contractAmountCents: contractAgg._sum.amountCents?.toString() ?? p.contractAmountCents?.toString() ?? null,
        receiptCents: receiptAgg._sum.amountCents?.toString() ?? '0',
        kitShort: workOrders.filter((w) => w.kitStatus === 'short').length,
        milestoneOverdue: milestones.filter((m) => m.status === 'pending' && m.planDate && m.planDate < new Date()).length,
      },
      milestones: milestones.map((m) => ({ ...m, id: m.id.toString(), projectId: m.projectId.toString() })),
      workOrders: workOrders.map((w) => ({ ...w, id: w.id.toString() })),
      dispatches: dispatches.map((d) => ({ ...d, id: d.id.toString(), projectId: d.projectId.toString(), windFarmId: d.windFarmId.toString(), shipMoveId: d.shipMoveId?.toString() ?? null })),
      redCards: redCards.map((c) => ({ ...c, id: c.id.toString(), boardId: c.boardId.toString(), ownerId: c.ownerId.toString() })),
      recentLogs: siteLogs.map((l) => ({ ...l, id: l.id.toString(), projectId: l.projectId.toString(), authorId: l.authorId.toString() })),
      issues: siteIssues.map((i) => ({ ...i, id: i.id.toString(), projectId: i.projectId.toString() })),
      finance: {
        invoices: invoices.map((i) => ({ ...i, id: i.id.toString(), contractId: i.contractId.toString(), projectId: i.projectId?.toString() ?? null, amountCents: i.amountCents.toString() })),
        receipts: receipts.map((r) => ({ ...r, id: r.id.toString(), contractId: r.contractId.toString(), projectId: r.projectId?.toString() ?? null, amountCents: r.amountCents.toString() })),
      },
      team: team.map((t) => ({ ...t, id: t.id.toString() })),
    };
  }
}
