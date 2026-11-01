import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { EventBusService, DingtalkService, PrintService, ReconciliationService, PRINT_TEMPLATES, PrintTemplateKey } from './integration-extra.service';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M15 系统集成（FR-M15-005/009/010/011/012 + 集成监控台） */
@Controller('integration')
export class IntegrationExtraController {
  constructor(
    private readonly bus: EventBusService,
    private readonly dingtalk: DingtalkService,
    private readonly print: PrintService,
    private readonly reconcile: ReconciliationService,
    private readonly prisma: PrismaService,
  ) {}

  /** FR-M15-011 集成监控台：T+ 队列/事件流水/钉钉通道/导入批次 */
  @Get('monitor')
  @RequirePermission('tplus:queue')
  async monitor() {
    const [queueCounts, importBatches] = await Promise.all([
      this.prisma.stockMove.groupBy({ by: ['tPlusStatus'], _count: { _all: true } }),
      this.prisma.importBatch.findMany({ orderBy: { id: 'desc' }, take: 10 }),
    ]);
    return {
      tPlusQueue: queueCounts.map((q) => ({ status: q.tPlusStatus, count: q._count._all })),
      eventJournal: this.bus.journal.slice(0, 20),
      dingtalk: this.dingtalk.lastResults.slice(0, 10),
      importBatches: importBatches.map((b) => ({
        id: b.id.toString(),
        templateKey: b.templateKey,
        totalRows: b.totalRows,
        okRows: b.okRows,
        errorRows: b.errorRows,
        status: b.status,
      })),
    };
  }

  /** FR-M15-005 钉钉手动测试推送（R-SYS） */
  @Post('dingtalk/test')
  @RequirePermission('sys:monitor:view')
  async dingtalkTest(@Body() body: { title?: string; text?: string }) {
    return this.dingtalk.sendMarkdown(body.title ?? 'Protech 测试推送', body.text ?? '钉钉 Webhook 连通性测试（FR-M15-005）');
  }

  /** FR-M15-009 打印渲染（送货单/验收单/派工单，HTML A4；dispatch 支持按单据取数） */
  @Post('print/:key')
  async renderPrint(@Param('key') key: string, @Body() body: Record<string, unknown>) {
    if (!PRINT_TEMPLATES.includes(key as PrintTemplateKey)) throw BizException.param(`打印模板不存在：${key}`);
    if (key === 'dispatch' && body.dispatchId) {
      const d = await this.prisma.dispatch.findFirst({ where: { id: parseId(body.dispatchId as string) } });
      if (d) {
        const [project, farm] = await Promise.all([
          this.prisma.project.findFirst({ where: { id: d.projectId }, select: { projectCode: true } }),
          this.prisma.windFarm.findFirst({ where: { id: d.windFarmId }, select: { fullName: true } }),
        ]);
        body = {
          pgCode: d.pgCode,
          projectCode: project?.projectCode ?? '',
          farmName: farm?.fullName ?? '',
          taskDesc: d.taskDesc ?? '',
          planDays: d.planDays ?? '—',
          date: new Date().toLocaleDateString('zh-CN'),
          ...body,
        };
      }
    }
    return this.print.render(key as PrintTemplateKey, body as Record<string, string | number>);
  }

  @Post('print/:key/template')
  @RequirePermission('sys:config:manage')
  async setPrintTemplate(@Param('key') key: string, @Body() body: { html?: string }) {
    if (!body.html) throw BizException.param('html 必填');
    await this.print.setTemplate(key as PrintTemplateKey, body.html);
    return null;
  }

  /** FR-M15-012 月度对账报告（中台 vs T+ 代理口径） */
  @Get('reconciliation')
  @RequirePermission('tplus:queue')
  async reconciliation(@Query('period') period?: string) {
    return this.reconcile.monthlyReport(period);
  }
}
