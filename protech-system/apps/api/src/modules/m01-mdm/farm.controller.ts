import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { MdmService } from './mdm.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId } from '../../shared/serialize';

/** FR-M01-004 风场库（一地一档永不变；同风场多期在项目档案区分） */
@Controller('mdm/farms')
export class FarmController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly mdm: MdmService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  @RequirePermission('mdm:farm:list')
  async list(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? {
            OR: [
              { fullName: { contains: q.keyword } },
              { farmCode: { contains: q.keyword } },
            ],
          }
        : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.windFarm.findMany({
        where,
        orderBy: { farmCode: 'asc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.windFarm.count({ where }),
    ]);
    return {
      list: list.map((f) => ({
        ...f,
        id: f.id.toString(),
        capacityMw: f.capacityMw ? Number(f.capacityMw) : null,
      })),
      total,
      page,
      pageSize,
    };
  }

  @Post()
  @RequirePermission('mdm:farm:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const farm = await this.mdm.createFarm(body, me.id, { force: body.force === true });
    await this.audit.log({ action: 'create', objectType: 'WindFarm', objectId: farm.id, after: { farmCode: farm.farmCode, fullName: farm.fullName } });
    return { id: farm.id.toString(), farmCode: farm.farmCode };
  }

  @Get(':id')
  @RequirePermission('mdm:farm:list')
  async detail(@Param('id') id: string) {
    const farm = await this.prisma.windFarm.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!farm) throw BizException.notFound('风场不存在');
    // 关联项目（一地多期）
    const projects = await this.prisma.project.findMany({
      where: { windFarmId: farm.id, deletedAt: null },
      select: { id: true, projectCode: true, standardName: true, status: true, phase: true },
      orderBy: { id: 'asc' },
    });
    return {
      ...farm,
      id: farm.id.toString(),
      capacityMw: farm.capacityMw ? Number(farm.capacityMw) : null,
      projects: projects.map((p) => ({ ...p, id: p.id.toString() })),
    };
  }

  @Put(':id')
  @RequirePermission('mdm:farm:update')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const fid = parseId(id);
    const farm = await this.prisma.windFarm.findFirst({ where: { id: fid, deletedAt: null } });
    if (!farm) throw BizException.notFound('风场不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    // fullName 为"业主提供全称去尾缀"登记项，变更走主数据审批（FR-M01-012）
    if (body.fullName !== undefined && body.fullName !== farm.fullName) {
      if (body.force !== true) {
        throw BizException.validation('风场全称变更请走主数据变更审批（FR-M01-012）');
      }
      data.fullName = String(body.fullName);
    }
    for (const k of ['province', 'city', 'county', 'remark', 'status'] as const) {
      if (body[k] !== undefined) data[k] = body[k];
    }
    if (body.capacityMw !== undefined) data.capacityMw = body.capacityMw === null ? null : Number(body.capacityMw);
    await this.prisma.windFarm.update({ where: { id: fid }, data });
    await this.audit.log({ action: 'update', objectType: 'WindFarm', objectId: fid, before: farm, after: data });
    return null;
  }
}
