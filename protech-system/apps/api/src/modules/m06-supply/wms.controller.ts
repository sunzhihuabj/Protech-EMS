import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { CostService } from '../m09-cost/cost.service';
import { StocktakeService } from '../m09-cost/stocktake.service';
import { BomService } from '../m05-bom/bom.service';
import { AfterArchiveService } from '../m11-aftersales/archive.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** R2-F/G/H 收尾端点：货位/KPI/成本预警/校准包/BOM 对比/Golden/质量看板/售后测算/档案检索/命名校验 */
@Controller()
export class R2FinishController {
  constructor(
    private readonly cost: CostService,
    private readonly stocktake: StocktakeService,
    private readonly boms: BomService,
    private readonly after: AfterArchiveService,
    private readonly prisma: PrismaService,
  ) {}

  // ---- FR-M06-008 货位主数据/三色 ----

  @Post('locations')
  @RequirePermission('slowmoving:manage')
  async createLocation(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const zone = String(body.zone ?? '');
    if (!['QT', 'TY', 'TJ', 'BJ'].includes(zone)) throw BizException.param('zone 须为 QT/TY/TJ/BJ（四库区）');
    const row = Number(body.row ?? 1);
    const col = Number(body.col ?? 1);
    const layer = Number(body.layer ?? 1);
    if (![row, col, layer].every((n) => Number.isInteger(n) && n > 0)) throw BizException.param('row/col/layer 须为正整数');
    const code = `${body.warehouse ?? 'YL'}-${zone}-${String(row).padStart(2, '0')}-${String(col).padStart(2, '0')}-${layer}`;
    const loc = await this.prisma.wmsLocation.upsert({
      where: { code },
      create: { code, warehouse: String(body.warehouse ?? 'YL'), zone, row, col, layer, threeColor: String(body.threeColor ?? 'blue'), remark: (body.remark as string) ?? null, createdBy: me.id, updatedBy: me.id },
      update: { threeColor: String(body.threeColor ?? 'blue'), status: String(body.status ?? 'active'), updatedBy: me.id },
    });
    return { id: loc.id.toString(), code: loc.code };
  }

  @Get('locations')
  @RequirePermission('inv:view')
  async locations(@Query() q: Record<string, string>) {
    const list = await this.prisma.wmsLocation.findMany({
      where: { deletedAt: null, ...(q.zone ? { zone: q.zone } : {}), ...(q.threeColor ? { threeColor: q.threeColor } : {}) },
      orderBy: { code: 'asc' },
      take: 300,
    });
    return list.map((l) => ({ ...l, id: l.id.toString() }));
  }

  @Post('locations/:id/color')
  @RequirePermission('slowmoving:manage')
  async setColor(@Param('id') id: string, @Body() body: { threeColor?: string }, @CurrentUser() me: AuthUser) {
    if (!body.threeColor || !['blue', 'yellow', 'red'].includes(body.threeColor)) {
      throw BizException.param('threeColor 须为 blue/yellow/red（E-20）');
    }
    await this.prisma.wmsLocation.update({
      where: { id: parseId(id) },
      data: { threeColor: body.threeColor, updatedBy: me.id },
    });
    return { threeColor: body.threeColor };
  }

  // ---- FR-M09-006/008 成本预警与校准包 ----

  @Post('internal/cost/margin-watch')
  async marginWatch(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.cost.marginWatch();
  }

  @Get('finance/calibration-pack')
  @RequirePermission('cost:view')
  async calibrationPack() {
    return this.cost.calibrationPack();
  }

  // ---- FR-M05-009/010/011 BOM 深化 ----

  @Get('boms/:idA/compare/:idB')
  @RequirePermission('bom:view')
  async compare(@Param('idA') idA: string, @Param('idB') idB: string) {
    return this.boms.compare(parseId(idA), parseId(idB));
  }

  @Post('boms/:id/golden')
  @RequirePermission('bom:release')
  async setGolden(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.boms.setGolden(parseId(id), me.id);
  }

  @Get('gov/bom-quality')
  @RequirePermission('gov:view')
  async bomQuality() {
    return this.boms.qualityBoard();
  }

  // ---- FR-M11-005/006 售后测算/客户视图 ----

  @Get('aftersales/inspection-forecast')
  @RequirePermission('as:view')
  async inspectionForecast() {
    return this.after.inspectionForecast();
  }

  @Get('aftersales/customer-view')
  @RequirePermission('as:view')
  async customerView(@Query('groupId') groupId?: string) {
    if (!groupId) throw BizException.param('groupId 必填');
    return this.after.customerServiceView(parseId(groupId));
  }

  // ---- FR-M12-005/010 档案检索/命名校验 ----

  @Get('archives/catalog')
  @RequirePermission('archive:view')
  async catalog(@Query('keyword') keyword?: string) {
    return this.after.catalog(keyword);
  }

  @Post('archives/check-naming')
  @RequirePermission('archive:view')
  async checkNaming(@Body() body: { name?: string }) {
    if (!body.name) throw BizException.param('name 必填');
    return this.after.checkNaming(body.name);
  }
}
