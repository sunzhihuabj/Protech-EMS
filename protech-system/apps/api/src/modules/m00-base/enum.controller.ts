import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId } from '../../shared/serialize';

/** FR-M00-011 数据字典管理（枚举统一管理；新增走变更流程；停用不删） */
@Controller('sys/enums')
export class EnumController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  async list(@Query('type') type?: string) {
    const items = await this.prisma.sysEnumItem.findMany({
      where: { deletedAt: null, ...(type ? { enumType: type } : {}) },
      orderBy: [{ enumType: 'asc' }, { sort: 'asc' }],
    });
    return items.map((i) => ({ ...i, id: i.id.toString() }));
  }

  @Post()
  @RequirePermission('sys:enum:manage')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.enumType || !body.code || !body.label) throw BizException.param('enumType/code/label 必填');
    const dup = await this.prisma.sysEnumItem.findFirst({
      where: { enumType: String(body.enumType), code: String(body.code), deletedAt: null },
    });
    if (dup) throw BizException.validation('枚举项已存在');
    const item = await this.prisma.sysEnumItem.create({
      data: {
        enumType: String(body.enumType),
        code: String(body.code),
        label: String(body.label),
        sort: Number(body.sort ?? 0),
        remark: (body.remark as string) ?? null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'SysEnumItem', objectId: item.id, after: body });
    return { id: item.id.toString() };
  }

  @Put(':id')
  @RequirePermission('sys:enum:manage')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const eid = parseId(id);
    const item = await this.prisma.sysEnumItem.findFirst({ where: { id: eid, deletedAt: null } });
    if (!item) throw BizException.notFound('枚举项不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    // code 不可改（枚举存 code 的引用完整性）
    if (body.label !== undefined) data.label = body.label;
    if (body.sort !== undefined) data.sort = Number(body.sort);
    if (body.remark !== undefined) data.remark = body.remark;
    if (body.status !== undefined) {
      if (!['active', 'inactive'].includes(String(body.status))) throw BizException.param('status 非法');
      data.status = body.status; // 停用不删
    }
    await this.prisma.sysEnumItem.update({ where: { id: eid }, data });
    await this.audit.log({ action: 'update', objectType: 'SysEnumItem', objectId: eid, before: item, after: data });
    return null;
  }
}
