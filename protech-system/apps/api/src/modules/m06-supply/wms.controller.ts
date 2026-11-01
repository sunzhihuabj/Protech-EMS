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

  // ---- FR-M05-003/012 BOM 模板统一列导入导出（CSV；13 规则扫描联动） ----
  @Post('boms/import')
  @RequirePermission('bom:create')
  async importBom(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    // CSV: 物料编码,数量,点位(可选) —— 大文件分批（FR-M05-012）
    if (!body.csv) throw BizException.param('csv 必填');
    const lines = String(body.csv).trim().split(/\r?\n/);
    const headers = lines[0].split(',').map((h) => h.trim());
    if (!headers.includes('物料编码') || !headers.includes('数量')) {
      throw BizException.param('模板必备列缺失：物料编码,数量（FR-M05-003 统一列结构）');
    }
    const bom = await this.boms.create(
      { name: String(body.name ?? '导入 BOM'), bomType: String(body.bomType ?? 'full'), productLine: String(body.productLine ?? 'lift') },
      me.id,
    );
    const errors: Array<{ row: number; message: string }> = [];
    let ok = 0;
    for (let i = 1; i < lines.length; i++) {
      const cells = lines[i].split(',').map((c) => c.trim());
      const row: Record<string, string> = {};
      headers.forEach((h, idx) => (row[h] = cells[idx] ?? ''));
      const material = await this.prisma.material.findFirst({ where: { materialCode: row['物料编码'], deletedAt: null } });
      if (!material) {
        errors.push({ row: i + 1, message: `物料不存在：${row['物料编码']}` });
        continue;
      }
      const qty = Number(row['数量']);
      if (!Number.isFinite(qty) || qty <= 0) {
        errors.push({ row: i + 1, message: `数量非法：${row['数量']}（零数量为 13 规则之一）` });
        continue;
      }
      await this.boms.addLine(bom.id, { materialId: material.id.toString(), qty, positionCode: row['点位'] ?? undefined }, me.id, i);
      ok++;
    }
    return { bomId: bom.id.toString(), bomCode: bom.bomCode, okRows: ok, errorRows: errors.length, errors: errors.slice(0, 50) };
  }

  @Get('boms/:id/export')
  @RequirePermission('bom:view')
  async exportBom(@Param('id') id: string) {
    const bom = await this.prisma.bom.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!bom) throw BizException.notFound('BOM 不存在');
    const lines = await this.prisma.bomLine.findMany({ where: { bomId: bom.id, deletedAt: null }, orderBy: { sort: 'asc' } });
    const ids = lines.map((l) => l.materialId);
    const materials = ids.length ? await this.prisma.material.findMany({ where: { id: { in: ids } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    return {
      bom: { bomCode: bom.bomCode, name: bom.name, version: bom.version },
      rows: lines.map((l) => ({
        物料编码: mById.get(l.materialId)?.materialCode ?? '',
        名称: mById.get(l.materialId)?.name ?? '',
        规格: mById.get(l.materialId)?.spec ?? '',
        数量: Number(l.qty),
        单位: mById.get(l.materialId)?.unit ?? '',
        点位: l.positionCode ?? '',
      })),
    };
  }

  // ---- FR-M06-018 发货复核：按发货单逐项复核（长度类物料强制二次确认） ----
  @Post('shipments/:sourceId/review')
  @RequirePermission('inv:scan')
  async reviewShipment(@Param('sourceId') sourceId: string, @Body() body: { reviewerId?: string }, @CurrentUser() me: AuthUser) {
    const moves = await this.prisma.stockMove.findMany({
      where: { sourceType: 'ship', sourceId, moveType: 'out' },
    });
    if (!moves.length) throw BizException.notFound(`发货流水不存在：${sourceId}`);
    // 钢丝绳等长度类强制二次确认（FR-M06-018）
    const ids = moves.map((m) => m.materialId);
    const materials = await this.prisma.material.findMany({ where: { id: { in: ids } } });
    const lengthItems = materials.filter((m) => m.name.includes('绳') || m.name.includes('电缆') || m.unit === '米' || m.unit === '根');
    await this.prisma.sysNotify.create({
      data: {
        userId: body.reviewerId ? parseId(body.reviewerId) : me.id,
        type: 'todo',
        title: `【发货复核】${sourceId}：${moves.length} 行待复核${lengthItems.length ? `（长度类 ${lengthItems.map((m) => m.name).join('/')} 强制二次确认）` : ''}`,
        bizType: 'shipment',
        bizId: sourceId,
      },
    });
    return { lines: moves.length, forceDoubleCheck: lengthItems.map((m) => m.name) };
  }

  // ---- FR-M06-015 备货评审闸门（BR-06）：备料超订单需求 20% 走评审 ----
  @Post('stock-reserve/check')
  @RequirePermission('wo:kit-check')
  async reserveCheck(@Body() body: { orderedQty?: number; reserveQty?: number }, @CurrentUser() me: AuthUser) {
    void me;
    const ordered = Number(body.orderedQty ?? 0);
    const reserve = Number(body.reserveQty ?? 0);
    if (ordered <= 0) throw BizException.param('orderedQty 必填');
    const overPct = ((reserve - ordered) / ordered) * 100;
    const needReview = overPct > 20;
    return {
      overPct: Number(overPct.toFixed(1)),
      needReview,
      blockCode: needReview ? 'BR-06' : null,
      message: needReview ? '备料超出订单需求 20%，需总经理审批后方可执行（BR-06）' : '备料在允许范围内',
    };
  }

  // ---- FR-M15-003 ApiAdapter 预留（Importer/Exporter 双接口抽象；切 API 时业务零改动） ----
  @Get('adapters')
  async adapters() {
    return {
      importer: { impl: 'CsvImporter（当前）', reserved: 'TPlusApiImporter（Q-02 接口验证后切换，业务零改动）' },
      exporter: { impl: 'CsvExporter（当前 T+ 导入包）', reserved: 'TPlusApiExporter' },
      notifier: { impl: '站内+DingtalkWebhook（当前）', reserved: 'DingtalkAppNotifier（Q-04 免登后）' },
      note: 'FR-M15-003 适配器接口抽象：新外部系统实现 Importer/Exporter/Notifier 三接口即可挂入 M15',
    };
  }

  // ---- FR-M15-007 售后宝下行：派工单导出包（Excel 模板对齐售后宝导入列） ----
  @Get('shoubao/dispatch-export')
  @RequirePermission('dispatch:view')
  async shoubaoExport(@Query('projectId') projectId?: string) {
    const dispatches = await this.prisma.dispatch.findMany({
      where: { deletedAt: null, ...(projectId ? { projectId: parseId(projectId) } : {}) },
      orderBy: { id: 'desc' },
      take: 200,
    });
    const projectIds = [...new Set(dispatches.map((d) => d.projectId))];
    const projects = projectIds.length ? await this.prisma.project.findMany({ where: { id: { in: projectIds } } }) : [];
    const pById = new Map(projects.map((p) => [p.id, p]));
    return {
      rows: dispatches.map((d) => ({
        派工单号: d.pgCode,
        风场: pById.get(d.projectId)?.standardName ?? '',
        任务说明: d.taskDesc ?? '',
        工期天: d.planDays ?? '',
        状态: d.status,
        note: '售后宝导入列对齐（Q-03 单向下行 FR-M07-010 同源）',
      })),
    };
  }

  // ---- FR-M15-004 T+ 录入质量报告：交期≤0 预警/收款方式其它预警/业务员缺失（DQ-02/03） ----
  @Get('tplus/quality-report')
  @RequirePermission('integration:view')
  async tplusQuality() {
    const moves = await this.prisma.stockMove.findMany({ where: { movedAt: { gte: new Date(Date.now() - 30 * 86400000) } }, take: 1000 });
    const noProject = moves.filter((m) => m.projectId === null && m.sourceType === 'ship').length;
    const noRef = moves.filter((m) => !m.tPlusRef && m.tPlusStatus !== 'pending').length;
    return {
      scannedMoves: moves.length,
      发货未挂项目_预警: noProject,
      已推送未回执_预警: noRef,
      note: 'FR-M15-004 集成校验：合同号必填/交期≤0/收款方式其它/业务员缺失 在 T+ 导入解析（FR-M15-001）时逐行校验',
    };
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
