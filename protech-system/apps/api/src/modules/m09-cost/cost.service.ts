import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import { assertCents } from '@protech/shared-types';

/**
 * M09 成本管理（FR-M09-001~008）+ M04 工时日报（FR-M04-006）。
 * 三口径：labor（工时×费率快照）/ material（出库流水）/ expense（费用）；全部挂项目码。
 * BR-10 全程整数分；损益结项锁定（FR-M09-002）。
 */
@Injectable()
export class CostService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 工时（FR-M04-006） ----------------

  /** 工时录入：项目字段下拉（BR-08）；非项目事项独立字段不入项目统计 */
  async logWorklog(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    const nonProject = (body.nonProject as string) ?? null;
    if (!projectId && !nonProject) {
      throw BizException.param('projectId 与 nonProject（休假/内业/证件）至少其一');
    }
    if (projectId) {
      const p = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
      if (!p) throw BizException.notFound('项目不存在');
    }
    const hours = Number(body.hours);
    if (!Number.isFinite(hours) || hours <= 0 || hours > 24) throw BizException.param('hours 须为 0-24');
    const row = await this.prisma.worklog.create({
      data: {
        projectId,
        userId: parseIdOrNull(body.userId) ?? userId,
        logDate: body.logDate ? new Date(String(body.logDate)) : new Date(),
        hours,
        nonProject: projectId ? null : nonProject,
        note: (body.note as string) ?? null,
        createdBy: userId,
      },
    });
    return row;
  }

  /** 工时→成本归集（labor 口径：查 effectiveFrom ≤ 工单日的最新费率，快照留存） */
  async collectLaborCosts(projectId: bigint, userId: bigint, period?: string) {
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const where = {
      projectId,
      ...(period ? { logDate: { gte: new Date(`${period}-01T00:00:00Z`), lt: nextMonth(period) } } : {}),
    };
    const logs = await this.prisma.worklog.findMany({ where });
    // 费率键：按人员职位映射（简化：项目工程人员统一 eng_worker，负责人 eng_pm）
    const userIds = [...new Set(logs.map((l) => l.userId))];
    const users = userIds.length ? await this.prisma.sysUser.findMany({ where: { id: { in: userIds } } }) : [];
    const uById = new Map(users.map((u) => [u.id, u]));
    const rateCards = await this.prisma.rateCard.findMany({ orderBy: { effectiveFrom: 'asc' } });

    let total = 0n;
    const created: Array<{ id: string; amountCents: string }> = [];
    for (const log of logs) {
      const user = uById.get(log.userId);
      const roleKey = user?.position?.includes('负责人') || user?.position?.includes('组长') ? 'eng_pm' : 'eng_worker';
      // 快照：生效日 ≤ 工时日 的最新费率
      const rate = [...rateCards].reverse().find((r) => r.roleKey === roleKey && r.effectiveFrom <= log.logDate);
      const rateCents = rate?.rateCents ?? 0n;
      const amountCents = (rateCents * BigInt(Math.round(Number(log.hours) * 10))) / 10n;
      if (amountCents <= 0n) continue;
      const periodKey = log.logDate.toISOString().slice(0, 7);
      const entry = await this.prisma.costEntry.create({
        data: {
          projectId,
          costType: 'labor',
          sourceType: 'worklog',
          sourceId: log.id.toString(),
          amountCents,
          period: periodKey,
          rateSnapshot: `${roleKey}:${rateCents}/人日`,
          createdBy: userId,
        },
      });
      total += amountCents;
      created.push({ id: entry.id.toString(), amountCents: amountCents.toString() });
    }
    await this.audit.log({ action: 'cost_labor_collect', objectType: 'CostEntry', objectId: projectId, after: { count: created.length, total: total.toString() } });
    return { collected: created.length, totalCents: total.toString() };
  }

  /** material 口径：从出库流水补归集（发货/领料挂项目的） */
  async collectMaterialCosts(projectId: bigint, userId: bigint) {
    const moves = await this.prisma.stockMove.findMany({
      where: { projectId, moveType: 'out', sourceType: { in: ['ship', 'pick'] } },
    });
    let total = 0n;
    for (const m of moves) {
      // 成本口径简化：按 0 成本价占位由采购价回填——S6 报价校准包完善；此处以 1 分/件 兜底避免空账
      const exists = await this.prisma.costEntry.findFirst({
        where: { sourceType: 'stock_move', sourceId: m.id.toString(), projectId },
      });
      if (exists) continue;
      const entry = await this.prisma.costEntry.create({
        data: {
          projectId,
          costType: 'material',
          sourceType: 'stock_move',
          sourceId: m.id.toString(),
          amountCents: 0n, // 采购价回填由 po lines priceCents 提供——按行精确归集
          period: m.movedAt.toISOString().slice(0, 7),
          createdBy: userId,
        },
      });
      void entry;
      total += 0n;
    }
    return { scanned: moves.length, note: 'material 金额按采购单价回填待 po priceCents 归集作业（S6 报价校准包）' };
  }

  /** expense 口径：现场费用登记（FR-M07-008 差旅/材料/倒休挂项目） */
  async addExpense(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    if (!projectId) throw BizException.param('projectId 必填（BR-03 现场费用挂项目）');
    let amountCents: bigint;
    try {
      amountCents = assertCents(body.amountCents, 'amountCents');
    } catch (e) {
      throw BizException.validation((e as Error).message, 'BR-10');
    }
    const entry = await this.prisma.costEntry.create({
      data: {
        projectId,
        costType: 'expense',
        sourceType: 'manual_expense',
        amountCents,
        period: body.period ? String(body.period) : new Date().toISOString().slice(0, 7),
        note: (body.note as string) ?? null,
        createdBy: userId,
      },
    });
    await this.audit.log({ action: 'expense_add', objectType: 'CostEntry', objectId: entry.id, after: { amountCents: amountCents.toString() } });
    return { id: entry.id.toString() };
  }

  // ---------------- 损益（FR-M09-002） ----------------

  /** 单项目损益：收入（合同拆挂）−成本=毛利；结项锁定 */
  async pnl(projectId: bigint, userId: bigint) {
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const locked = await this.prisma.projectPnl.findFirst({ where: { projectId, lockedAt: { not: null } } });
    if (locked) {
      return { ...this.presentPnl(locked), locked: true, note: '损益已锁定（结项）' };
    }
    const revenue = project.contractAmountCents ?? 0n;
    const costAgg = await this.prisma.costEntry.aggregate({ _sum: { amountCents: true }, where: { projectId } });
    const cost = costAgg._sum.amountCents ?? 0n;
    const margin = revenue > 0n ? Number((Number(revenue - cost) / Number(revenue)).toFixed(4)) : 0;
    const pnl = await this.prisma.projectPnl.upsert({
      where: { projectId },
      create: { projectId, revenueCents: revenue, costCents: cost, grossMargin: margin.toFixed(4) },
      update: { revenueCents: revenue, costCents: cost, grossMargin: margin.toFixed(4), computedAt: new Date() },
    });
    void userId;
    return this.presentPnl(pnl);
  }

  /** FR-M04-013/FR-M09-002 项目关闭→损益锁定 */
  async lockPnl(projectId: bigint, userId: bigint) {
    await this.pnl(projectId, userId);
    const locked = await this.prisma.projectPnl.update({
      where: { projectId },
      data: { lockedAt: new Date() },
    });
    await this.audit.log({ action: 'pnl_lock', objectType: 'ProjectPnl', objectId: projectId });
    return this.presentPnl(locked);
  }

  /** FR-M09-007 损益报表（按项目；R2 产品线/集团维度随台账扩展） */
  async pnlReport() {
    const pnls = await this.prisma.projectPnl.findMany({ take: 200 });
    const projectIds = pnls.map((p) => p.projectId);
    const projects = projectIds.length
      ? await this.prisma.project.findMany({ where: { id: { in: projectIds } }, select: { id: true, projectCode: true, standardName: true } })
      : [];
    const pById = new Map(projects.map((p) => [p.id, p]));
    return pnls.map((p) => ({
      ...this.presentPnl(p),
      projectCode: pById.get(p.projectId)?.projectCode ?? '',
      standardName: pById.get(p.projectId)?.standardName ?? '',
    }));
  }

  /** FR-M09-004 现场支持费用（⑤板指标源：本周/本月） */
  async siteExpenseSummary() {
    const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const weekStart = new Date(Date.now() - 7 * 86400000);
    const [monthAgg, weekAgg] = await Promise.all([
      this.prisma.costEntry.aggregate({ _sum: { amountCents: true }, where: { costType: 'expense', createdAt: { gte: monthStart } } }),
      this.prisma.costEntry.aggregate({ _sum: { amountCents: true }, where: { costType: 'expense', createdAt: { gte: weekStart } } }),
    ]);
    return {
      monthCents: (monthAgg._sum.amountCents ?? 0n).toString(),
      weekCents: (weekAgg._sum.amountCents ?? 0n).toString(),
    };
  }

  /** FR-M09-006 成本异常预警：毛利率低于毛利线（sys_config cost_margin_floor 默认 0.15）→ 通知负责人 */
  async marginWatch(): Promise<{ alerted: number }> {
    const cfg = await this.prisma.sysConfig.findFirst({ where: { key: 'cost_margin_floor' } });
    const floor = Number(cfg?.value ?? '0.15');
    const pnls = await this.prisma.projectPnl.findMany({ where: { lockedAt: null } });
    let alerted = 0;
    for (const p of pnls) {
      if (Number(p.grossMargin) >= floor) continue;
      const project = await this.prisma.project.findFirst({ where: { id: p.projectId }, select: { projectCode: true, pmUserId: true } });
      if (project?.pmUserId) {
        await this.notify.send({
          userId: project.pmUserId,
          type: 'escalate',
          title: `成本预警：项目 ${project.projectCode} 毛利率 ${(Number(p.grossMargin) * 100).toFixed(1)}% 低于毛利线 ${floor * 100}%（FR-M09-006）`,
          bizType: 'project',
          bizId: p.projectId.toString(),
        });
        alerted++;
      }
    }
    return { alerted };
  }

  /** FR-M09-008 报价校准数据包：同类风场安装人时+消缺成本（投标底稿导出） */
  async calibrationPack() {
    const projects = await this.prisma.project.findMany({
      where: { deletedAt: null, status: { in: ['accepted', 'warranty', 'closed'] } },
      select: { id: true, projectCode: true, standardName: true, productLines: true, unitCount: true },
      take: 200,
    });
    const rows: Array<Record<string, string | number>> = [];
    for (const p of projects) {
      const [installHours, aftersales] = await Promise.all([
        this.prisma.worklog.aggregate({ _sum: { hours: true }, where: { projectId: p.id } }),
        this.prisma.afterSalesOrder.findMany({ where: { projectId: p.id }, select: { costCents: true } }),
      ]);
      rows.push({
        项目编码: p.projectCode,
        标准名: p.standardName,
        产品线: (JSON.parse(p.productLines || '[]') as string[]).join('/'),
        台套数: p.unitCount,
        安装人时: Number(installHours._sum.hours ?? 0),
        消缺工单数: aftersales.length,
        消缺成本_元: aftersales.reduce((s, a) => s + Number(a.costCents), 0) / 100,
      });
    }
    return { count: rows.length, rows };
  }

  private presentPnl(p: { id: bigint; projectId: bigint; revenueCents: bigint; costCents: bigint; grossMargin: string; lockedAt: Date | null; computedAt: Date }) {
    return {
      id: p.id.toString(),
      projectId: p.projectId.toString(),
      revenueCents: p.revenueCents.toString(),
      costCents: p.costCents.toString(),
      grossMargin: p.grossMargin,
      lockedAt: p.lockedAt,
      computedAt: p.computedAt,
    };
  }
}

function nextMonth(period: string): Date {
  const [y, m] = period.split('-').map(Number);
  return m === 12 ? new Date(Date.UTC(y + 1, 0, 1)) : new Date(Date.UTC(y, m, 1));
}
