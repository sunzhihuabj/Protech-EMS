import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { DeliveryService } from './delivery.service';
import { FinanceService } from '../m08-finance/finance.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M07 派工/交底/日志/问题 + M04 里程碑接口（BR-04 硬卡点） */
@Controller()
export class DeliveryController {
  constructor(
    private readonly delivery: DeliveryService,
    private readonly prisma: PrismaService,
  ) {}

  @Post('dispatches')
  @RequirePermission('dispatch:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const d = await this.delivery.createDispatch(body, me.id);
    return { id: d.id.toString(), pgCode: d.pgCode, source: d.source };
  }

  @Post('dispatches/:id/transition')
  @RequirePermission('dispatch:transition')
  async transition(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.to) throw BizException.param('to 必填');
    return this.delivery.dispatchTransition(parseId(id), String(body.to), me.id);
  }

  @Get('dispatches')
  @RequirePermission('dispatch:view')
  async list(@Query() q: Record<string, string>) {
    const where = {
      deletedAt: null,
      ...(q.projectId ? { projectId: parseId(q.projectId) } : {}),
      ...(q.status ? { status: q.status } : {}),
    };
    const list = await this.prisma.dispatch.findMany({ where, orderBy: { id: 'desc' }, take: 100 });
    return list.map((d) => ({ ...d, id: d.id.toString(), projectId: d.projectId.toString(), windFarmId: d.windFarmId.toString(), shipMoveId: d.shipMoveId?.toString() ?? null }));
  }

  @Post('milestones/init')
  @RequirePermission('milestone:manage')
  async initMilestones(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.projectId) throw BizException.param('projectId 必填');
    return this.delivery.initMilestones(parseId(body.projectId), me.id, body.planDates as Record<string, string>);
  }

  @Get('projects/:id/milestones')
  @RequirePermission('milestone:view')
  async milestones(@Param('id') id: string) {
    return this.delivery.milestonesOf(parseId(id));
  }

  @Post('milestones/:id/complete')
  @RequirePermission('milestone:manage')
  async completeMilestone(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.delivery.completeMilestone(parseId(id), me.id);
  }

  @Post('disclosures')
  @RequirePermission('disclosure:create')
  async createDisclosure(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const d = await this.delivery.createDisclosure(body, me.id);
    return { id: d.id.toString() };
  }

  @Post('site-logs')
  @RequirePermission('sitelog:create')
  async createSiteLog(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const row = await this.delivery.createSiteLog(body, me.id);
    return { id: row.id.toString() };
  }

  @Post('site-issues')
  @RequirePermission('issue:create')
  async createSiteIssue(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const row = await this.delivery.createSiteIssue(body, me.id);
    return { id: row.id.toString() };
  }

  @Post('site-issues/:id/close')
  @RequirePermission('issue:create')
  async closeIssue(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.delivery.closeSiteIssue(parseId(id), me.id);
  }
}

/** M08 开票/回款/账龄/验收/催收接口（BR-02 冻结校验） */
@Controller()
export class FinanceController {
  constructor(
    private readonly finance: FinanceService,
    private readonly prisma: PrismaService,
  ) {}

  @Post('invoices')
  @RequirePermission('invoice:apply')
  async applyInvoice(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const inv = await this.finance.applyInvoice(body, me.id);
    return { id: inv.id.toString(), status: inv.status };
  }

  @Post('invoices/:id/issue')
  @RequirePermission('invoice:issue')
  async issueInvoice(@Param('id') id: string, @Body() body: { invoiceNo?: string }, @CurrentUser() me: AuthUser) {
    if (!body.invoiceNo) throw BizException.param('invoiceNo 必填');
    return this.finance.issueInvoice(parseId(id), me.id, body.invoiceNo);
  }

  @Post('payment-plans/generate')
  @RequirePermission('receipt:manage')
  async generatePlan(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.contractId) throw BizException.param('contractId 必填');
    const rows = await this.finance.generatePaymentPlan(
      parseId(body.contractId),
      me.id,
      body.plan as Array<{ node: string; percent: number; dueAt?: string }>,
    );
    return rows.map((r) => ({ ...r, id: r.id.toString(), contractId: r.contractId.toString(), amountCents: r.amountCents.toString() }));
  }

  @Post('receipts')
  @RequirePermission('receipt:manage')
  async registerReceipt(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const r = await this.finance.registerReceipt(body, me.id);
    return { id: r.id.toString() };
  }

  @Get('finance/aging')
  @RequirePermission('finance:view')
  async aging() {
    return this.finance.aging();
  }

  @Get('finance/receipt-rate')
  @RequirePermission('finance:view')
  async receiptRate() {
    return this.finance.receiptRate();
  }

  @Post('acceptances')
  @RequirePermission('acceptance:create')
  async accept(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const a = await this.finance.registerAcceptance(body, me.id);
    return { id: a.id.toString() };
  }

  @Post('collections')
  @RequirePermission('collection:add')
  async addCollection(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const row = await this.finance.addCollection(body, me.id);
    return { id: row.id.toString() };
  }

  @Post('internal/finance/remind-due')
  async remindDue(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.finance.remindDuePlans();
  }
}
