import { Body, Controller, Get, Param, Post, Put } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** FR-M00-001 部门树（14 部门种子；path 物化路径） */
@Controller('sys/depts')
export class DeptController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  async list() {
    const list = await this.prisma.sysDept.findMany({
      where: { deletedAt: null },
      orderBy: [{ sort: 'asc' }, { id: 'asc' }],
    });
    return list.map((d) => ({ ...d, id: d.id.toString(), parentId: d.parentId?.toString() ?? null }));
  }

  @Post()
  @RequirePermission('sys:dept:manage')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.code || !body.name) throw BizException.param('code/name 必填');
    const parentId = parseIdOrNull(body.parentId);
    let path = '/';
    if (parentId) {
      const parent = await this.prisma.sysDept.findFirst({ where: { id: parentId, deletedAt: null } });
      if (!parent) throw BizException.param('上级部门不存在');
      path = `${parent.path}${parent.id}/`;
    }
    const dept = await this.prisma.sysDept.create({
      data: {
        code: String(body.code),
        name: String(body.name),
        parentId,
        path,
        chainCode: (body.chainCode as string) ?? null,
        boardCode: (body.boardCode as string) ?? null,
        sort: Number(body.sort ?? 0),
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'SysDept', objectId: dept.id });
    return { id: dept.id.toString() };
  }

  @Put(':id')
  @RequirePermission('sys:dept:manage')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const deptId = parseId(id);
    const dept = await this.prisma.sysDept.findFirst({ where: { id: deptId, deletedAt: null } });
    if (!dept) throw BizException.notFound('部门不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    for (const k of ['name', 'chainCode', 'boardCode', 'status'] as const) {
      if (body[k] !== undefined) data[k] = body[k];
    }
    if (body.sort !== undefined) data.sort = Number(body.sort);
    await this.prisma.sysDept.update({ where: { id: deptId }, data });
    await this.audit.log({ action: 'update', objectType: 'SysDept', objectId: deptId, before: dept, after: data });
    return null;
  }
}
