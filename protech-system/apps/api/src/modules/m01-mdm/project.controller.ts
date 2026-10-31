import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { ProjectService } from './project.service';
import { ScopeService } from '../../shared/scope.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId, parseIdOrNull, parseIds } from '../../shared/serialize';
import { isValidCode } from '@protech/shared-types';
import { PROJECT_STATUS } from '@protech/shared-types';

/** FR-M04-001~010 / FR-M01-008~011：项目档案（全系统锚点）列表/详情/建档/状态/子项/待回填 */
@Controller('mdm/projects')
export class ProjectController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly projects: ProjectService,
    private readonly scope: ScopeService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  @RequirePermission('mdm:project:list')
  async list(@Query() q: Record<string, string>, @CurrentUser() me: AuthUser) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const scopeWhere = await this.scope.projectWhere(me);
    const where = {
      AND: [
        { deletedAt: null },
        scopeWhere,
        ...(q.keyword
          ? [
              {
                OR: [
                  { projectCode: { contains: q.keyword } },
                  { standardName: { contains: q.keyword } },
                  { shortName: { contains: q.keyword } },
                ],
              },
            ]
          : []),
        ...(q.status ? [{ status: q.status }] : []),
        ...(q.windFarmId ? [{ windFarmId: parseId(q.windFarmId) }] : []),
        ...(q.ownerPartyId ? [{ ownerPartyId: parseId(q.ownerPartyId) }] : []),
        ...(q.orderType ? [{ orderType: q.orderType }] : []),
      ],
    };
    const [list, total] = await Promise.all([
      this.prisma.project.findMany({
        where,
        orderBy: q.sort === '-created_at' ? { createdAt: 'desc' } : { id: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.project.count({ where }),
    ]);
    return {
      list: list.map((p) => this.present(p)),
      total,
      page,
      pageSize,
    };
  }

  /** FR-M04-010 未关联风场清单（排产订单月度清零跟踪） */
  @Get('pending-tasks')
  @RequirePermission('mdm:project:list')
  async pendingTasks() {
    return this.projects.pendingTasks();
  }

  @Get(':id')
  @RequirePermission('mdm:project:list')
  async detail(@Param('id') id: string) {
    const p = await this.prisma.project.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!p) throw BizException.notFound('项目不存在');
    const [farm, owner, items, aliases] = await Promise.all([
      p.windFarmId
        ? this.prisma.windFarm.findFirst({ where: { id: p.windFarmId }, select: { id: true, farmCode: true, fullName: true } })
        : Promise.resolve(null),
      p.ownerPartyId
        ? this.prisma.customerParty.findFirst({ where: { id: p.ownerPartyId }, select: { id: true, partyCode: true, name: true } })
        : Promise.resolve(null),
      this.prisma.projectProductItem.findMany({ where: { projectId: p.id, deletedAt: null }, orderBy: { id: 'asc' } }),
      this.prisma.alias.findMany({ where: { ownerType: 'project', ownerId: p.id } }),
    ]);
    const completeness = await this.projects.completeness(p);
    return {
      ...this.present(p),
      farm: farm ? { ...farm, id: farm.id.toString() } : null,
      owner: owner ? { ...owner, id: owner.id.toString() } : null,
      productItems: items.map((i) => ({ ...i, id: i.id.toString(), projectId: i.projectId.toString(), contractId: i.contractId?.toString() ?? null })),
      aliases: aliases.map((a) => ({ id: a.id.toString(), alias: a.alias })),
      completeness,
    };
  }

  /** FR-M01-009 名称生成器预览（不落库） */
  @Post('name-preview')
  @RequirePermission('mdm:project:list')
  async namePreview(@Body() body: Record<string, unknown>) {
    return this.projects.namePreview(body as Parameters<ProjectService['namePreview']>[0]);
  }

  /** FR-M01-010 三重查重 */
  @Post('check-dup')
  @RequirePermission('mdm:project:list')
  async checkDup(@Body() body: Record<string, unknown>) {
    return this.projects.checkDup(body as Parameters<ProjectService['checkDup']>[0]);
  }

  @Post()
  @RequirePermission('mdm:project:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const p = await this.projects.create(body, me.id, { force: body.force === true });
    return { id: p.id.toString(), projectCode: p.projectCode, status: p.status };
  }

  /** 编辑（运营字段直改；风场/业主/期次三类结构变更走 FR-M01-011 变更单） */
  @Put(':id')
  @RequirePermission('mdm:project:update')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const pid = parseId(id);
    const p = await this.prisma.project.findFirst({ where: { id: pid, deletedAt: null } });
    if (!p) throw BizException.notFound('项目不存在');
    const STRUCTURAL = ['windFarmId', 'ownerPartyId', 'phase'] as const;
    const touchedStructural = STRUCTURAL.filter((k) => body[k] !== undefined);
    if (touchedStructural.length && body.force !== true) {
      throw BizException.validation(
        `字段 ${touchedStructural.join('/')} 属三类结构变更（风场补全/主体变更/期次拆分），请走主数据变更审批（FR-M01-011/012）`,
      );
    }
    const data: Record<string, unknown> = { updatedBy: me.id };
    for (const k of ['standardName', 'shortName', 'remark', 'status'] as const) {
      if (body[k] !== undefined) data[k] = body[k];
    }
    if (body.productLines !== undefined) data.productLines = JSON.stringify(body.productLines);
    if (body.oemPartyIds !== undefined) data.oemPartyIds = JSON.stringify(body.oemPartyIds);
    if (body.unitCount !== undefined) {
      const n = Number(body.unitCount);
      if (!Number.isInteger(n) || n < 0) throw BizException.param('unitCount 必须为非负整数');
      data.unitCount = n;
    }
    if (body.contractAmountCents !== undefined) {
      const v = body.contractAmountCents;
      if (typeof v === 'number' && !Number.isInteger(v)) {
        throw BizException.validation('contractAmountCents 必须为整数分（BR-10）', 'BR-10');
      }
      data.contractAmountCents = v === null ? null : BigInt(String(v).trim());
    }
    for (const k of ['warrantyStart', 'warrantyEnd'] as const) {
      if (body[k] !== undefined) data[k] = body[k] ? new Date(String(body[k])) : null;
    }
    for (const k of ['pmUserId', 'engOwnerId', 'aftersalesOwnerId', 'salesOwnerId', 'collectorId'] as const) {
      if (body[k] !== undefined) data[k] = parseIdOrNull(body[k]);
    }
    if (body.pendingFlags !== undefined) data.pendingFlags = JSON.stringify(body.pendingFlags);
    await this.prisma.project.update({ where: { id: pid }, data });
    await this.audit.log({ action: 'update', objectType: 'Project', objectId: pid, before: p, after: data });
    return null;
  }

  /** 状态迁移（E-04 状态机；closed 触发 R-PM 7 天回收 + 归档提醒） */
  @Post(':id/transition')
  @RequirePermission('mdm:project:update')
  async transition(
    @Param('id') id: string,
    @Body() body: { to?: string; note?: string },
    @CurrentUser() me: AuthUser,
  ) {
    if (!body.to) throw BizException.param('to 必填');
    const p = await this.projects.transition(parseId(id), body.to, me.id, body.note);
    return { id: p.id.toString(), status: p.status };
  }

  /** FR-M04-009 产品子项（一风场多产品不拆项目） */
  @Post(':id/product-items')
  @RequirePermission('mdm:project:item')
  async addProductItem(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const pid = parseId(id);
    const p = await this.prisma.project.findFirst({ where: { id: pid, deletedAt: null } });
    if (!p) throw BizException.notFound('项目不存在');
    if (!body.productLine || !isValidCode({ climber: '', lift: '', ladder: '', rail: '', video: '', fire: '' }, String(body.productLine))) {
      throw BizException.param('productLine 非法（E-03）');
    }
    const unitCount = Number(body.unitCount ?? 0);
    if (!Number.isInteger(unitCount) || unitCount <= 0) throw BizException.param('unitCount 必须为正整数');
    const item = await this.prisma.projectProductItem.create({
      data: {
        projectId: pid,
        productLine: String(body.productLine),
        contractId: parseIdOrNull(body.contractId),
        contractCode: (body.contractCode as string) ?? null,
        unitCount,
        warrantyStart: body.warrantyStart ? new Date(String(body.warrantyStart)) : null,
        warrantyEnd: body.warrantyEnd ? new Date(String(body.warrantyEnd)) : null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'ProjectProductItem', objectId: item.id, after: body });
    return { id: item.id.toString() };
  }

  @Post(':id/aliases')
  @RequirePermission('mdm:project:update')
  async addAlias(@Param('id') id: string, @Body() body: { alias?: string }, @CurrentUser() me: AuthUser) {
    if (!body.alias) throw BizException.param('alias 必填');
    const row = await this.prisma.alias.create({
      data: { ownerType: 'project', ownerId: parseId(id), alias: body.alias.trim(), source: 'manual', createdBy: me.id },
    });
    return { id: row.id.toString() };
  }

  private present(p: {
    id: bigint;
    projectCode: string;
    standardName: string;
    shortName: string;
    phase: string | null;
    windFarmId: bigint | null;
    ownerPartyId: bigint | null;
    oemPartyIds: string | null;
    legalEntityId: bigint;
    orderType: string;
    productLines: string | null;
    unitCount: number;
    contractAmountCents: bigint | null;
    warrantyStart: Date | null;
    warrantyEnd: Date | null;
    pmUserId: bigint | null;
    engOwnerId: bigint | null;
    aftersalesOwnerId: bigint | null;
    salesOwnerId: bigint | null;
    collectorId: bigint | null;
    status: string;
    pendingFlags: string | null;
    remark: string | null;
    createdAt: Date;
    updatedAt: Date;
  }) {
    return {
      ...p,
      id: p.id.toString(),
      windFarmId: p.windFarmId?.toString() ?? null,
      ownerPartyId: p.ownerPartyId?.toString() ?? null,
      oemPartyIds: JSON.parse(p.oemPartyIds ?? '[]'),
      legalEntityId: p.legalEntityId.toString(),
      productLines: JSON.parse(p.productLines ?? '[]'),
      contractAmountCents: p.contractAmountCents?.toString() ?? null,
      pmUserId: p.pmUserId?.toString() ?? null,
      engOwnerId: p.engOwnerId?.toString() ?? null,
      aftersalesOwnerId: p.aftersalesOwnerId?.toString() ?? null,
      salesOwnerId: p.salesOwnerId?.toString() ?? null,
      collectorId: p.collectorId?.toString() ?? null,
      pendingFlags: JSON.parse(p.pendingFlags ?? '[]'),
    };
  }
}
