import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { BomService } from './bom.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M05 BOM 接口（FR-M05-001~005/008~009/012 S3 集；BR-05 硬卡点） */
@Controller('boms')
export class BomController {
  constructor(
    private readonly boms: BomService,
    private readonly prisma: PrismaService,
  ) {}

  @Get()
  @RequirePermission('bom:view')
  async list(@Query() q: Record<string, string>) {
    const where = {
      deletedAt: null,
      ...(q.keyword ? { OR: [{ bomCode: { contains: q.keyword } }, { name: { contains: q.keyword } }] } : {}),
      ...(q.productLine ? { productLine: q.productLine } : {}),
      ...(q.status ? { status: q.status } : {}),
    };
    const list = await this.prisma.bom.findMany({ where, orderBy: { id: 'desc' }, take: 100 });
    return list.map((b) => ({ ...b, id: b.id.toString(), parentBomId: b.parentBomId?.toString() ?? null }));
  }

  @Get(':id')
  @RequirePermission('bom:view')
  async detail(@Param('id') id: string) {
    const bom = await this.prisma.bom.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!bom) throw BizException.notFound('BOM 不存在');
    const lines = await this.prisma.bomLine.findMany({ where: { bomId: bom.id, deletedAt: null }, orderBy: { sort: 'asc' } });
    const materialIds = [...new Set(lines.map((l) => l.materialId))];
    const materials = materialIds.length ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    return {
      ...bom,
      id: bom.id.toString(),
      parentBomId: bom.parentBomId?.toString() ?? null,
      lines: lines.map((l) => ({
        id: l.id.toString(),
        materialId: l.materialId.toString(),
        materialCode: mById.get(l.materialId)?.materialCode ?? '',
        materialName: mById.get(l.materialId)?.name ?? '',
        spec: mById.get(l.materialId)?.spec ?? null,
        unit: mById.get(l.materialId)?.unit ?? '',
        qty: Number(l.qty),
        positionCode: l.positionCode,
      })),
    };
  }

  @Post()
  @RequirePermission('bom:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const bom = await this.boms.create(body, me.id);
    return { id: bom.id.toString(), bomCode: bom.bomCode };
  }

  @Post(':id/lines')
  @RequirePermission('bom:create')
  async addLine(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const line = await this.boms.addLine(parseId(id), body, me.id);
    return { id: line.id.toString() };
  }

  @Post(':id/lint')
  @RequirePermission('bom:view')
  async lint(@Param('id') id: string) {
    return { findings: await this.boms.lint(parseId(id)) };
  }

  @Post(':id/publish')
  @RequirePermission('bom:release')
  async publish(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    const r = await this.boms.publish(parseId(id), me.id);
    return { status: r.bom.status, findings: r.findings.length };
  }

  @Post('ecn')
  @RequirePermission('bom:release')
  async createEcn(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const ecn = await this.boms.createEcn(body, me.id);
    return { id: ecn.id.toString(), ecnCode: ecn.ecnCode };
  }

  @Post('ecn/:id/review')
  @RequirePermission('bom:release')
  async reviewEcn(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.approved === undefined) throw BizException.param('approved 必填');
    return this.boms.approveEcn(parseId(id), me.id, body.approved === true);
  }
}
