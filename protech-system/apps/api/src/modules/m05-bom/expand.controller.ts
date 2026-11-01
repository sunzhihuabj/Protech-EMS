import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ExpandService } from './expand.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M05 B/C 类 + M07 点位销项接口（R2-C） */
@Controller()
export class ExpandController {
  constructor(
    private readonly expand: ExpandService,
    private readonly prisma: PrismaService,
  ) {}

  @Post('param-templates')
  @RequirePermission('bom:create')
  async createTemplate(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const tpl = await this.expand.createParamTemplate(body, me.id);
    return { id: tpl.id.toString(), templateCode: tpl.templateCode };
  }

  @Get('param-templates')
  @RequirePermission('bom:view')
  async templates() {
    const list = await this.prisma.paramTemplate.findMany({ orderBy: { id: 'desc' }, take: 50 });
    return list.map((t) => ({
      ...t,
      id: t.id.toString(),
      standardSectionBomId: t.standardSectionBomId.toString(),
      accessoryPackBomId: t.accessoryPackBomId?.toString() ?? null,
    }));
  }

  /** B 类参数展开（FR-M05-006）：塔高 → 标准节×N + 附件包 */
  @Post('boms/expand')
  @RequirePermission('bom:create')
  async expandBom(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.expand.expand(body, me.id);
  }

  /** C 类套件包（FR-M05-007） */
  @Post('kit-packs')
  @RequirePermission('bom:create')
  async createKit(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const kit = await this.expand.createKitPack(body, me.id);
    return { id: kit.id.toString(), kitCode: kit.kitCode };
  }

  @Post('boms/eng')
  @RequirePermission('bom:create')
  async createEngBom(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.expand.createEngBom(body, me.id);
  }

  /** 点位清单生成（FR-M07-003） */
  @Post('dispatches/:id/punch-list')
  @RequirePermission('issue:create')
  async generatePunchList(@Param('id') id: string, @Body() body: { bomId?: string }, @CurrentUser() me: AuthUser) {
    if (!body.bomId) throw BizException.param('bomId 必填');
    return this.expand.generatePunchList(parseId(id), parseId(body.bomId), me.id);
  }

  @Get('dispatches/:id/punch-list')
  @RequirePermission('dispatch:view')
  async punchList(@Param('id') id: string) {
    const list = await this.prisma.punchItem.findMany({ where: { dispatchId: parseId(id) }, orderBy: { id: 'asc' } });
    const materialIds = [...new Set(list.map((i) => i.materialId))];
    const materials = materialIds.length ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    const total = list.reduce((s, i) => s + i.requiredQty, 0);
    const done = list.reduce((s, i) => s + i.doneQty, 0);
    return {
      progress: total ? Number((done / total).toFixed(4)) : 0,
      items: list.map((i) => ({
        ...i,
        id: i.id.toString(),
        dispatchId: i.dispatchId.toString(),
        bomLineId: i.bomLineId.toString(),
        materialId: i.materialId.toString(),
        materialName: mById.get(i.materialId)?.name ?? '',
        doneAt: i.doneAt,
      })),
    };
  }

  @Post('punch-items/:id/punch')
  @RequirePermission('issue:create')
  async punch(@Param('id') id: string, @Body() body: { doneQty?: number; shortage?: string }, @CurrentUser() me: AuthUser) {
    if (body.doneQty === undefined) throw BizException.param('doneQty 必填');
    return this.expand.punch(parseId(id), Number(body.doneQty), me.id, body.shortage);
  }
}
