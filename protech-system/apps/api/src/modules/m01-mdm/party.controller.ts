import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { MdmService } from './mdm.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** FR-M01-001/002/003 客户主体库（三级；类型多选；别名；三重查重） */
@Controller('mdm/parties')
export class PartyController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly mdm: MdmService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  @RequirePermission('mdm:party:list')
  async list(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    // 别名参与搜索（FR-M01-002/016）
    const aliasHits = q.keyword
      ? await this.prisma.alias.findMany({
          where: { ownerType: 'party', alias: { contains: q.keyword } },
          select: { ownerId: true },
          take: 100,
        })
      : [];
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? {
            OR: [
              { name: { contains: q.keyword } },
              { shortName: { contains: q.keyword } },
              { partyCode: { contains: q.keyword } },
              { id: { in: aliasHits.map((a) => a.ownerId) } },
            ],
          }
        : {}),
      ...(q.groupId ? { groupId: parseId(q.groupId) } : {}),
      ...(q.type ? { types: { contains: q.type } } : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.customerParty.findMany({
        where,
        orderBy: { partyCode: 'asc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.customerParty.count({ where }),
    ]);
    const ids = list.map((p) => p.id);
    const aliases = ids.length
      ? await this.prisma.alias.findMany({ where: { ownerType: 'party', ownerId: { in: ids } } })
      : [];
    return {
      list: list.map((p) => ({
        ...p,
        id: p.id.toString(),
        groupId: p.groupId?.toString() ?? null,
        types: JSON.parse(p.types || '[]'),
        aliases: aliases.filter((a) => a.ownerId === p.id).map((a) => a.alias),
      })),
      total,
      page,
      pageSize,
    };
  }

  /** 查重（新建前调用；FR-M01-003） */
  @Post('check-dup')
  @RequirePermission('mdm:party:list')
  async checkDup(@Body() body: { name?: string }) {
    if (!body.name) throw BizException.param('name 必填');
    return { candidates: await this.mdm.checkPartyDup(body.name) };
  }

  @Post()
  @RequirePermission('mdm:party:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const party = await this.mdm.createParty(body, me.id, { force: body.force === true });
    return { id: party.id.toString(), partyCode: party.partyCode };
  }

  @Get(':id')
  @RequirePermission('mdm:party:list')
  async detail(@Param('id') id: string) {
    const party = await this.prisma.customerParty.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!party) throw BizException.notFound('主体不存在');
    const aliases = await this.prisma.alias.findMany({ where: { ownerType: 'party', ownerId: party.id } });
    return {
      ...party,
      id: party.id.toString(),
      groupId: party.groupId?.toString() ?? null,
      types: JSON.parse(party.types || '[]'),
      aliases: aliases.map((a) => ({ id: a.id.toString(), alias: a.alias, source: a.source })),
    };
  }

  @Put(':id')
  @RequirePermission('mdm:party:update')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const pid = parseId(id);
    const party = await this.prisma.customerParty.findFirst({ where: { id: pid, deletedAt: null } });
    if (!party) throw BizException.notFound('主体不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    if (body.name !== undefined) data.name = String(body.name);
    if (body.shortName !== undefined) data.shortName = body.shortName;
    if (body.groupId !== undefined) data.groupId = parseIdOrNull(body.groupId);
    if (body.types !== undefined) data.types = JSON.stringify(body.types);
    if (body.remark !== undefined) data.remark = body.remark;
    if (body.status !== undefined) data.status = body.status;
    // 名称变更走主数据变更审批（FR-M01-012）——直接改名仅限纠错（force+audit）
    if (data.name && data.name !== party.name && body.force !== true) {
      throw BizException.validation('主体名称变更请走主数据变更审批（FR-M01-012）');
    }
    await this.prisma.customerParty.update({ where: { id: pid }, data });
    await this.audit.log({ action: 'update', objectType: 'CustomerParty', objectId: pid, before: party, after: data });
    return null;
  }

  @Post(':id/aliases')
  @RequirePermission('mdm:alias:add')
  async addAlias(@Param('id') id: string, @Body() body: { alias?: string }, @CurrentUser() me: AuthUser) {
    if (!body.alias) throw BizException.param('alias 必填');
    return this.mdm.addAlias('party', parseId(id), body.alias, me.id);
  }
}
