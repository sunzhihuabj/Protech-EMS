import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { CostService } from './cost.service';
import { StocktakeService } from './stocktake.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M09 成本 + M04 工时 + M06 盘点/呆滞（R2-1 接口） */
@Controller()
export class CostController {
  constructor(
    private readonly cost: CostService,
    private readonly stocktake: StocktakeService,
    private readonly prisma: PrismaService,
  ) {}

  // ---- 工时（FR-M04-006） ----

  @Post('worklogs')
  @RequirePermission('worklog:create')
  async logWorklog(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const row = await this.cost.logWorklog(body, me.id);
    return { id: row.id.toString() };
  }

  @Get('worklogs')
  @RequirePermission('worklog:create')
  async worklogs(@Query() q: Record<string, string>) {
    const list = await this.prisma.worklog.findMany({
      where: {
        ...(q.projectId ? { projectId: parseId(q.projectId) } : {}),
        ...(q.userId ? { userId: parseId(q.userId) } : {}),
      },
      orderBy: { id: 'desc' },
      take: 100,
    });
    return list.map((w) => ({
      ...w,
      id: w.id.toString(),
      projectId: w.projectId?.toString() ?? null,
      userId: w.userId.toString(),
      hours: Number(w.hours),
    }));
  }

  // ---- 成本归集 / 损益 ----

  @Post('costs/collect-labor')
  @RequirePermission('cost:manage')
  async collectLabor(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.projectId) throw BizException.param('projectId 必填');
    return this.cost.collectLaborCosts(parseId(body.projectId), me.id, body.period as string);
  }

  @Post('costs/collect-material')
  @RequirePermission('cost:manage')
  async collectMaterial(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.projectId) throw BizException.param('projectId 必填');
    return this.cost.collectMaterialCosts(parseId(body.projectId), me.id);
  }

  @Post('costs/expenses')
  @RequirePermission('cost:manage')
  async addExpense(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.cost.addExpense(body, me.id);
  }

  @Post('rate-cards')
  @RequirePermission('cost:manage')
  async addRateCard(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.roleKey || body.rateCents === undefined) throw BizException.param('roleKey/rateCents 必填');
    const row = await this.prisma.rateCard.create({
      data: {
        roleKey: String(body.roleKey),
        rateCents: BigInt(String(body.rateCents).trim()),
        effectiveFrom: body.effectiveFrom ? new Date(String(body.effectiveFrom)) : new Date(),
        remark: (body.remark as string) ?? null,
        createdBy: me.id,
      },
    });
    return { id: row.id.toString() };
  }

  @Get('projects/:id/pnl')
  @RequirePermission('cost:view')
  async pnl(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.cost.pnl(parseId(id), me.id);
  }

  @Post('projects/:id/pnl/lock')
  @RequirePermission('cost:manage')
  async lockPnl(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.cost.lockPnl(parseId(id), me.id);
  }

  @Get('finance/pnl-report')
  @RequirePermission('cost:view')
  async pnlReport() {
    return this.cost.pnlReport();
  }

  @Get('finance/site-expense')
  @RequirePermission('cost:view')
  async siteExpense() {
    return this.cost.siteExpenseSummary();
  }

  // ---- 盘点（FR-M06-009/010） ----

  @Post('stocktakes')
  @RequirePermission('stocktake:manage')
  async createTask(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.stocktake.createTask(body, me.id);
  }

  @Post('stocktakes/:taskId/lines/:lineId/count')
  @RequirePermission('stocktake:count')
  async count(
    @Param('taskId') taskId: string,
    @Param('lineId') lineId: string,
    @Body() body: { countedQty?: number },
    @CurrentUser() me: AuthUser,
  ) {
    if (body.countedQty === undefined) throw BizException.param('countedQty 必填');
    return this.stocktake.count(parseId(taskId), parseId(lineId), Number(body.countedQty), me.id);
  }

  /** FR-M06-022 盘点差异看板：当月差异笔数/金额/闭环率/归因分布（③板指标源） */
  @Get('stocktakes/variance-dashboard')
  @RequirePermission('stocktake:view')
  async varianceDashboard() {
    const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const vs = await this.prisma.stockVariance.findMany({ where: { createdAt: { gte: monthStart } }, take: 500 });
    const closed = vs.filter((v) => v.step === 'approved' || v.step === 'rectified').length;
    const byCause: Record<string, number> = {};
    for (const v of vs) {
      const k = v.cause ?? '未归因';
      byCause[k] = (byCause[k] ?? 0) + 1;
    }
    return {
      monthVarianceCount: vs.length,
      varianceAmountCents: vs.reduce((s, v) => s + Number(v.varianceCents), 0).toString(),
      closeRate: vs.length ? Number((closed / vs.length).toFixed(4)) : null,
      byCause,
    };
  }

  @Get('stocktakes/:taskId')
  @RequirePermission('stocktake:manage')
  async taskDetail(@Param('taskId') taskId: string) {
    const task = await this.prisma.stocktakeTask.findFirst({ where: { id: parseId(taskId) } });
    if (!task) throw BizException.notFound('盘点任务不存在');
    const lines = await this.prisma.stocktakeLine.findMany({ where: { taskId: task.id } });
    const variances = await this.prisma.stockVariance.findMany({ where: { taskId: task.id } });
    return {
      task: { ...task, id: task.id.toString() },
      lines: lines.map((l) => ({
        ...l,
        id: l.id.toString(),
        taskId: l.taskId.toString(),
        materialId: l.materialId.toString(),
        bookQty: Number(l.bookQty),
        countedQty: l.countedQty === null ? null : Number(l.countedQty),
        varianceQty: l.varianceQty === null ? null : Number(l.varianceQty),
      })),
      variances: variances.map((v) => ({
        ...v,
        id: v.id.toString(),
        materialId: v.materialId.toString(),
        varianceQty: Number(v.varianceQty),
      })),
    };
  }

  @Post('variances/:id/attribute')
  @RequirePermission('stocktake:manage')
  async attribute(@Param('id') id: string, @Body() body: { cause?: string }, @CurrentUser() me: AuthUser) {
    if (!body.cause) throw BizException.param('cause 必填（E-09）');
    return this.stocktake.attributeVariance(parseId(id), body.cause, me.id);
  }

  @Post('variances/:id/approve')
  @RequirePermission('stocktake:approve')
  async approve(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.adjust === undefined) throw BizException.param('adjust 必填');
    return this.stocktake.approveVariance(parseId(id), me.id, body.adjust === true);
  }

  // ---- 呆滞（FR-M06-011/012） ----

  @Post('internal/slowmoving/scan')
  async scanSlow(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.stocktake.scanSlowMoving();
  }

  @Get('slowmoving')
  @RequirePermission('slowmoving:view')
  async listSlow(@Query() q: Record<string, string>) {
    return this.stocktake.listSlowMoving(q);
  }

  @Post('slowmoving/:id/dispose')
  @RequirePermission('slowmoving:manage')
  async dispose(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.stocktake.proposeDisposal(parseId(id), body, me.id);
  }
}
