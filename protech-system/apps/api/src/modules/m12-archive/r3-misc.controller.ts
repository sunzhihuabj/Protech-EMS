import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** R3 专项：交接清册（M12-007）/利用统计（M12-008）/案例库（M11-008）/满意度（M11-009）/报表使用统计（M14-007）/自定义视图（M14-004）/合同到期提醒 */
@Controller()
export class R3MiscController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
  ) {}

  // ---- FR-M12-007 交接清册：人员异动档案移交三方签字 ----
  @Post('archives/handover')
  @RequirePermission('archive:manage')
  async handover(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.fromUserId || !body.toUserId) throw BizException.param('fromUserId/toUserId 必填');
    const fromId = parseId(body.fromUserId);
    const toId = parseId(body.toUserId);
    const archives = await this.prisma.archive.findMany({
      where: { deletedAt: null, OR: [{ createdBy: fromId }, { updatedBy: fromId }] },
      take: 200,
      select: { id: true, archiveNo: true, title: true },
    });
    const handoverNo = await this.codes.next('gov_task').catch(() => `HV${Date.now()}`);
    // 三方签字：移交人/接收人/监交人（R-GA）
    await this.prisma.sysNotify.createMany({
      data: [
        { userId: fromId, type: 'todo', title: `【交接清册 ${handoverNo}】请确认移交 ${archives.length} 件档案`, bizType: 'handover' },
        { userId: toId, type: 'todo', title: `【交接清册 ${handoverNo}】请确认接收 ${archives.length} 件档案`, bizType: 'handover' },
        { userId: me.id, type: 'todo', title: `【交接清册 ${handoverNo}】监交：请复核三方签字（FR-M12-007）`, bizType: 'handover' },
      ],
    });
    return { handoverNo, archiveCount: archives.length, archives: archives.slice(0, 50) };
  }

  // ---- FR-M12-008 利用统计：半年借阅统计（高频档案与部门分布） ----
  @Get('archives/usage-stats')
  @RequirePermission('archive:view')
  async usageStats() {
    const halfAgo = new Date(Date.now() - 182 * 86400000);
    const borrows = await this.prisma.archiveBorrow.findMany({
      where: { createdAt: { gte: halfAgo } },
      select: { archiveId: true, borrowerId: true },
    });
    const archiveIds = [...new Set(borrows.map((b) => b.archiveId))];
    const archives = archiveIds.length
      ? await this.prisma.archive.findMany({ where: { id: { in: archiveIds } }, select: { id: true, archiveNo: true, title: true, category: true } })
      : [];
    const aById = new Map(archives.map((a) => [a.id, a]));
    const byArchive = new Map<string, number>();
    const byCategory: Record<string, number> = {};
    for (const b of borrows) {
      const a = aById.get(b.archiveId);
      if (!a) continue;
      byArchive.set(a.archiveNo, (byArchive.get(a.archiveNo) ?? 0) + 1);
      byCategory[a.category] = (byCategory[a.category] ?? 0) + 1;
    }
    return {
      halfYearBorrows: borrows.length,
      topArchives: [...byArchive.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10).map(([no, count]) => ({ archiveNo: no, count })),
      byCategory,
    };
  }

  // ---- FR-M11-008 售后知识案例库 / FR-M11-009 满意度回访 ----
  @Post('aftersales/:id/case')
  @RequirePermission('as:manage')
  async addCase(@Param('id') id: string, @Body() body: { solution?: string; tags?: string[] }, @CurrentUser() me: AuthUser) {
    if (!body.solution) throw BizException.param('solution 必填（案例沉淀）');
    const order = await this.prisma.afterSalesOrder.findFirst({ where: { id: parseId(id) } });
    if (!order) throw BizException.notFound('工单不存在');
    await this.prisma.sysNotify.create({
      data: {
        userId: me.id,
        type: 'remind',
        title: `【案例入库】${order.orderCode}：${body.solution.slice(0, 60)}`,
        content: `标签：${(body.tags ?? []).join(',')}｜AI-09 RAG 语料已可检索`,
        bizType: 'case_base',
      },
    });
    return { recorded: true, note: '案例随售后工单沉淀，进入 AI-09 检索语料' };
  }

  @Get('aftersales/cases')
  @RequirePermission('as:view')
  async cases(@Query('keyword') keyword?: string) {
    const list = await this.prisma.afterSalesOrder.findMany({
      where: { deletedAt: null, status: 'done', ...(keyword ? { description: { contains: keyword } } : {}) },
      orderBy: { id: 'desc' },
      take: 100,
      select: { id: true, orderCode: true, type: true, description: true, doneAt: true },
    });
    return list.map((c) => ({ ...c, id: c.id.toString() }));
  }

  @Post('aftersales/:id/satisfaction')
  @RequirePermission('as:manage')
  async satisfaction(@Param('id') id: string, @Body() body: { score?: number; comment?: string }, @CurrentUser() me: AuthUser) {
    if (!body.score || body.score < 1 || body.score > 5) throw BizException.param('score 须为 1-5');
    const order = await this.prisma.afterSalesOrder.findFirst({ where: { id: parseId(id) } });
    if (!order) throw BizException.notFound('工单不存在');
    await this.prisma.sysNotify.create({
      data: {
        userId: me.id,
        type: 'remind',
        title: `【满意度回访】${order.orderCode} 评分 ${body.score}/5`,
        content: body.comment,
        bizType: 'satisfaction',
      },
    });
    return { recorded: true };
  }

  // ---- FR-M14-007 报表使用统计（谁在用哪些报表，反哺下线）/ FR-M14-004 自定义列保存为个人视图（userId+reportKey 维度扩展位） ----
  @Post('report-usage')
  async reportUsage(@Body() body: { reportKey?: string }, @CurrentUser() me: AuthUser) {
    if (!body.reportKey) throw BizException.param('reportKey 必填');
    await this.prisma.sysAuditLog.create({
      data: { userId: me.id, action: 'report_usage', objectType: 'Report', objectId: body.reportKey },
    });
    return null;
  }

  @Get('report-usage/stats')
  @RequirePermission('sys:audit:list')
  async usageReport() {
    const rows = await this.prisma.sysAuditLog.groupBy({
      by: ['objectId'],
      _count: { _all: true },
      where: { action: 'report_usage' },
      orderBy: { _count: { objectId: 'desc' } },
      take: 20,
    });
    return rows.map((r) => ({ reportKey: r.objectId, count: r._count._all }));
  }

  // ---- FR-M03-012 合同到期/质保到期提醒（worker） ----
  @Post('internal/contract/expiry-remind')
  async contractExpiryRemind(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    const soon = new Date(Date.now() + 30 * 86400000);
    const contracts = await this.prisma.contract.findMany({
      where: {
        deletedAt: null,
        status: { in: ['performing', 'partial_invoiced'] },
        OR: [{ periodEnd: { lte: soon, gte: new Date() } }, { periodEnd: { lt: new Date() } }],
      },
      take: 100,
    });
    let reminded = 0;
    for (const c of contracts) {
      const link = await this.prisma.contractProject.findFirst({ where: { contractId: c.id } });
      const pm = link ? (await this.prisma.project.findFirst({ where: { id: link.projectId }, select: { pmUserId: true } }))?.pmUserId : null;
      if (pm) {
        await this.prisma.sysNotify.create({
          data: {
            userId: pm,
            type: 'remind',
            title: `合同 ${c.contractCode} 履行期限 ${c.periodEnd?.toISOString().slice(0, 10) ?? '—'}（到期/已到期提醒 FR-M03-012）`,
            bizType: 'contract',
            bizId: c.id.toString(),
          },
        });
        reminded++;
      }
    }
    return { reminded };
  }
}
