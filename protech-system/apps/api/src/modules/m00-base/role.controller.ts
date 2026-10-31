import { Body, Controller, Get, Param, Post, Put } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { PermissionCache, CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId, parseIds } from '../../shared/serialize';

/** FR-M00-002 角色与权限（菜单/按钮/数据三级；数据范围 E-16 六档） */
@Controller('sys/roles')
export class RoleController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly permCache: PermissionCache,
  ) {}

  @Get()
  async list() {
    const roles = await this.prisma.sysRole.findMany({ where: { deletedAt: null }, orderBy: { id: 'asc' } });
    const rps = await this.prisma.sysRolePermission.findMany({
      select: { roleId: true, permissionId: true },
    });
    return roles.map((r) => ({
      ...r,
      id: r.id.toString(),
      permissionIds: rps.filter((x) => x.roleId === r.id).map((x) => x.permissionId.toString()),
    }));
  }

  @Get('permissions')
  async permissions() {
    return (await this.prisma.sysPermission.findMany({ where: { deletedAt: null }, orderBy: [{ module: 'asc' }, { code: 'asc' }]}))
      .map((p) => ({ ...p, id: p.id.toString(), parentId: p.parentId?.toString() ?? null }));
  }

  @Post()
  @RequirePermission('sys:role:manage')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.code || !body.name) throw BizException.param('code/name 必填');
    const role = await this.prisma.sysRole.create({
      data: {
        code: String(body.code),
        name: String(body.name),
        maxDataScope: String(body.maxDataScope ?? 'self'),
        remark: (body.remark as string) ?? null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'SysRole', objectId: role.id, after: body });
    return { id: role.id.toString() };
  }

  @Put(':id')
  @RequirePermission('sys:role:manage')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const rid = parseId(id);
    const role = await this.prisma.sysRole.findFirst({ where: { id: rid, deletedAt: null } });
    if (!role) throw BizException.notFound('角色不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    if (body.name !== undefined) data.name = body.name;
    if (body.maxDataScope !== undefined) data.maxDataScope = body.maxDataScope;
    if (body.remark !== undefined) data.remark = body.remark;
    await this.prisma.sysRole.update({ where: { id: rid }, data });
    await this.audit.log({ action: 'update', objectType: 'SysRole', objectId: rid, before: role, after: data });
    return null;
  }

  /** 授权（全量替换；审计 FR-M00-006 权限变更） */
  @Post(':id/permissions')
  @RequirePermission('sys:role:manage')
  async assignPermissions(@Param('id') id: string, @Body() body: { permissionIds?: unknown }, @CurrentUser() me: AuthUser) {
    const rid = parseId(id);
    const role = await this.prisma.sysRole.findFirst({ where: { id: rid, deletedAt: null } });
    if (!role) throw BizException.notFound('角色不存在');
    const pids = parseIds(body.permissionIds);
    const perms = await this.prisma.sysPermission.findMany({ where: { id: { in: pids }, deletedAt: null } });
    if (perms.length !== pids.length) throw BizException.param('存在无效权限');
    await this.prisma.$transaction([
      this.prisma.sysRolePermission.deleteMany({ where: { roleId: rid } }),
      ...pids.map((permissionId) =>
        this.prisma.sysRolePermission.create({ data: { roleId: rid, permissionId, grantedBy: me.id } }),
      ),
    ]);
    await this.audit.log({
      action: 'grant',
      objectType: 'SysRolePermission',
      objectId: rid,
      after: { permissionIds: pids.map(String) },
    });
    this.permCache.invalidate(); // 全员失效（角色授权影响所有持有者）
    return null;
  }
}
