import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { SupplyService } from './supply.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M06 采购与库房接口（扫码过账/T+ 队列/齐套请购；BR-03/源头单据驱动） */
@Controller()
export class SupplyController {
  constructor(private readonly supply: SupplyService) {}

  // ---------------- 工单 ----------------

  @Post('work-orders')
  @RequirePermission('wo:create')
  async createWorkOrder(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const wo = await this.supply.createWorkOrder(body, me.id);
    return { id: wo.id.toString(), woCode: wo.woCode };
  }

  /** 齐套检查：pass / short（缺料→请购草稿+红灯） */
  @Post('work-orders/:id/kit-check')
  @RequirePermission('wo:kit-check')
  async kitCheck(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.supply.kitCheck(parseId(id), me.id);
  }

  @Post('work-orders/:id/transition')
  @RequirePermission('wo:transition')
  async woTransition(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.to) throw BizException.param('to 必填');
    return this.supply.workOrderTransition(parseId(id), String(body.to), me.id);
  }

  // ---------------- 请购 / 采购 ----------------

  @Post('purchase-reqs')
  @RequirePermission('pr:create')
  async createPr(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const req = await this.supply.createPurchaseReq(body, me.id);
    return { id: req.id.toString(), prCode: req.prCode };
  }

  @Post('purchase-reqs/:id/review')
  @RequirePermission('pr:review')
  async reviewPr(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.approved === undefined) throw BizException.param('approved 必填');
    return this.supply.reviewPurchaseReq(parseId(id), me.id, body.approved === true);
  }

  @Post('purchase-orders')
  @RequirePermission('po:create')
  async createPo(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const po = await this.supply.createPurchaseOrder(body, me.id);
    return { id: po.id.toString(), poCode: po.poCode };
  }

  // ---------------- 扫码过账（幂等） ----------------

  @Post('stock-moves')
  @RequirePermission('inv:scan')
  async stockMove(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.supply.stockMove(body, me.id);
  }

  // ---------------- T+ 推送队列 ----------------

  @Post('internal/tplus/push')
  async tplusPush(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.supply.pushQueueBatch();
  }

  @Get('integration/tplus/push-queue')
  @RequirePermission('tplus:queue')
  async pushQueue(@Query() q: Record<string, string>) {
    return this.supply.moveList({ ...q, tPlusStatus: q.tPlusStatus ?? 'pushed' });
  }

  /** T+ 可导入行集（财务下载） */
  @Get('integration/tplus/export')
  @RequirePermission('tplus:queue')
  async tplusExport() {
    return this.supply.exportPushQueue();
  }

  @Post('integration/tplus/confirm')
  @RequirePermission('tplus:confirm')
  async tplusConfirm(@Body() body: { ids?: string[] }, @CurrentUser() me: AuthUser) {
    if (!Array.isArray(body.ids) || !body.ids.length) throw BizException.param('ids 必填');
    return this.supply.confirmPushed(body.ids, me.id);
  }

  // ---------------- 库存 / 流水 / 快照 ----------------

  @Get('inventory')
  @RequirePermission('inv:view')
  async inventory(@Query() q: Record<string, string>) {
    return this.supply.inventoryList(q);
  }

  @Get('stock-moves')
  @RequirePermission('inv:view')
  async moves(@Query() q: Record<string, string>) {
    return this.supply.moveList(q);
  }

  @Post('internal/inventory/snapshot')
  async snapshot(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.supply.dailySnapshot();
  }
}
