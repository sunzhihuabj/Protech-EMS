import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';

/** FR-M01-001 客户集团库（142→14 归并顶层；种子导入） */
@Controller('mdm/groups')
export class GroupController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly codes: CodeEngineService,
  ) {}

  @Get()
  @RequirePermission('mdm:group:list')
  async list(@Query() q: Record<string, string>) {
    const groups = await this.prisma.customerGroup.findMany({
      where: { deletedAt: null, ...(q.keyword ? { name: { contains: q.keyword } } : {}) },
      orderBy: { id: 'asc' },
    });
    const counts = await this.prisma.customerParty.groupBy({
      by: ['groupId'],
      where: { deletedAt: null, groupId: { not: null } },
      _count: { _all: true },
    });
    const countMap = new Map(counts.map((c) => [c.groupId?.toString(), c._count._all]));
    return groups.map((g) => ({
      ...g,
      id: g.id.toString(),
      partyCount: countMap.get(g.id.toString()) ?? 0,
    }));
  }

  @Post()
  @RequirePermission('mdm:group:manage')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.name) throw BizException.param('name 必填');
    const groupCode = String(body.groupCode ?? '').trim() || (await this.codes.next('party')).replace('C', 'G');
    const group = await this.prisma.customerGroup.create({
      data: {
        groupCode,
        name: String(body.name),
        region: (body.region as string) ?? null,
        remark: (body.remark as string) ?? null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'CustomerGroup', objectId: group.id, after: body });
    return { id: group.id.toString(), groupCode };
  }
}
