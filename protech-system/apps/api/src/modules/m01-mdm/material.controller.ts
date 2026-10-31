import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { MdmService } from './mdm.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId } from '../../shared/serialize';

/** FR-M01-005/006 物料主数据（一码一物；归一前置；DQ-01 查重拦截；旧码映射） */
@Controller('mdm/materials')
export class MaterialController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly mdm: MdmService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  @RequirePermission('mdm:material:list')
  async list(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? {
            OR: [
              { materialCode: { contains: q.keyword } },
              { name: { contains: q.keyword } },
              { spec: { contains: q.keyword } },
            ],
          }
        : {}),
      ...(q.category ? { category: q.category } : {}),
      ...(q.status ? { status: q.status } : {}),
      ...(q.abcClass ? { abcClass: q.abcClass } : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.material.findMany({
        where,
        orderBy: { materialCode: 'asc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.material.count({ where }),
    ]);
    return {
      list: list.map((m) => ({
        ...m,
        id: m.id.toString(),
        safetyStockMin: m.safetyStockMin ? Number(m.safetyStockMin) : null,
        safetyStockMax: m.safetyStockMax ? Number(m.safetyStockMax) : null,
      })),
      total,
      page,
      pageSize,
    };
  }

  /** 录入前置校验（FR-M01-006）：词典归一 + 查重（不落库） */
  @Post('check-dup')
  @RequirePermission('mdm:material:list')
  async checkDup(@Body() body: { name?: string; spec?: string }) {
    if (!body.name) throw BizException.param('name 必填');
    const dict = await this.prisma.normDict.findMany({ where: { deletedAt: null } });
    const norm = this.mdm.normalizeMaterialName(body.name, dict);
    const candidates = await this.mdm.checkMaterialDup(norm.name, body.spec);
    return { normalized: norm, candidates };
  }

  @Post()
  @RequirePermission('mdm:material:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const { material, normalized } = await this.mdm.createMaterial(body, me.id, {
      force: body.force === true,
    });
    await this.audit.log({
      action: 'create',
      objectType: 'Material',
      objectId: material.id,
      after: { materialCode: material.materialCode, name: material.name },
    });
    return { id: material.id.toString(), materialCode: material.materialCode, normalized };
  }

  /** 旧码解析（FR-M01-005）：4 套旧编码经 md_code_map 兼容查询主码 */
  @Get('resolve-old-code')
  @RequirePermission('mdm:material:list')
  async resolveOldCode(@Query('code') code?: string) {
    if (!code) throw BizException.param('code 必填');
    const hit = await this.mdm.resolveOldCode(code);
    return { resolved: hit !== null, ...(hit ?? {}) };
  }

  @Get(':id')
  @RequirePermission('mdm:material:list')
  async detail(@Param('id') id: string) {
    const m = await this.prisma.material.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!m) throw BizException.notFound('物料不存在');
    const oldCodes = await this.prisma.mdCodeMap.findMany({ where: { masterCode: m.materialCode } });
    return {
      ...m,
      id: m.id.toString(),
      safetyStockMin: m.safetyStockMin ? Number(m.safetyStockMin) : null,
      safetyStockMax: m.safetyStockMax ? Number(m.safetyStockMax) : null,
      oldCodes: oldCodes.map((c) => c.oldCode),
    };
  }

  @Put(':id')
  @RequirePermission('mdm:material:update')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const mid = parseId(id);
    const m = await this.prisma.material.findFirst({ where: { id: mid, deletedAt: null } });
    if (!m) throw BizException.notFound('物料不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    for (const k of ['name', 'spec', 'unit', 'category', 'legalEntityScope', 'abcClass', 'remark', 'status'] as const) {
      if (body[k] !== undefined) data[k] = body[k];
    }
    if (body.safetyStockMin !== undefined) data.safetyStockMin = body.safetyStockMin === null ? null : Number(body.safetyStockMin);
    if (body.safetyStockMax !== undefined) data.safetyStockMax = body.safetyStockMax === null ? null : Number(body.safetyStockMax);
    // 关键字段变更走主数据审批（FR-M01-012）
    const keyChanged = (data.name && data.name !== m.name) || (data.legalEntityScope && data.legalEntityScope !== m.legalEntityScope);
    if (keyChanged && body.force !== true) {
      throw BizException.validation('物料关键字段变更请走主数据变更审批（FR-M01-012）');
    }
    await this.prisma.material.update({ where: { id: mid }, data });
    await this.audit.log({ action: 'update', objectType: 'Material', objectId: mid, before: m, after: data });
    return null;
  }
}
