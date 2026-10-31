import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AuthService } from '../../shared/auth.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { CurrentUser, AuthUser, PermissionCache, RequirePermission } from '../../shared/guards';
import { parseId, parseIdOrNull, parseIds } from '../../shared/serialize';
import { normalizePage } from '@protech/shared-types';

/** FR-M00-001 组织与用户管理（14 部门 121 人种子；兼任多角色；离职停用） */
@Controller('sys/users')
export class UserController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auth: AuthService,
    private readonly audit: AuditService,
    private readonly permCache: PermissionCache,
  ) {}

  @Get()
  @RequirePermission('sys:user:list')
  async page(@Query() q: Record<string, string>) {
    const { page, pageSize, skip } = normalizePage({
      page: Number(q.page) || 1,
      pageSize: Number(q.pageSize) || 20,
    });
    const take = pageSize;
    void page;
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? {
            OR: [
              { username: { contains: q.keyword } },
              { displayName: { contains: q.keyword } },
            ],
          }
        : {}),
      ...(q.deptId ? { deptId: parseId(q.deptId) } : {}),
      ...(q.status ? { status: q.status } : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.sysUser.findMany({
        where,
        skip,
        take,
        orderBy: { id: 'asc' },
        select: {
          id: true,
          username: true,
          displayName: true,
          deptId: true,
          position: true,
          email: true,
          phone: true,
          status: true,
          lastLoginAt: true,
        },
      }),
      this.prisma.sysUser.count({ where }),
    ]);
    // 无库级外键（05 册规范），角色/部门名称应用层装配
    const ids = list.map((u) => u.id);
    const [userRoles, depts] = await Promise.all([
      ids.length
        ? this.prisma.sysUserRole.findMany({
            where: { userId: { in: ids } },
            select: { userId: true, roleId: true },
          })
        : Promise.resolve([]),
      ids.length
        ? this.prisma.sysDept.findMany({
            where: { id: { in: list.map((u) => u.deptId).filter((d): d is bigint => d !== null) } },
            select: { id: true, name: true },
          })
        : Promise.resolve([]),
    ]);
    const deptName = new Map(depts.map((d) => [d.id.toString(), d.name]));
    const roleIds = [...new Set(userRoles.map((r) => r.roleId))];
    const roles = roleIds.length
      ? await this.prisma.sysRole.findMany({
          where: { id: { in: roleIds }, deletedAt: null },
          select: { id: true, code: true, name: true },
        })
      : [];
    const roleById = new Map(roles.map((r) => [r.id, r]));
    return {
      list: list.map((u) => ({
        ...u,
        id: u.id.toString(),
        deptId: u.deptId ? u.deptId.toString() : null,
        deptName: u.deptId ? deptName.get(u.deptId.toString()) ?? null : null,
        roles: userRoles
          .filter((r) => r.userId === u.id)
          .map((r) => roleById.get(r.roleId))
          .filter((r): r is { id: bigint; code: string; name: string } => !!r)
          .map((r) => ({ code: r.code, name: r.name })),
      })),
      total,
    };
  }

  @Post()
  @RequirePermission('sys:user:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.username || !body.displayName || !body.password) {
      throw BizException.param('username/displayName/password 必填');
    }
    const exist = await this.prisma.sysUser.findFirst({ where: { username: String(body.username), deletedAt: null } });
    if (exist) throw BizException.validation('用户名已存在');
    const user = await this.prisma.sysUser.create({
      data: {
        username: String(body.username),
        displayName: String(body.displayName),
        passwordHash: this.auth.hashPassword(String(body.password)),
        deptId: parseIdOrNull(body.deptId),
        position: (body.position as string) ?? null,
        email: (body.email as string) ?? null,
        phone: (body.phone as string) ?? null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    return { id: user.id.toString() };
  }

  @Put(':id')
  @RequirePermission('sys:user:update')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const uid = parseId(id);
    const user = await this.prisma.sysUser.findFirst({ where: { id: uid, deletedAt: null } });
    if (!user) throw BizException.notFound('用户不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    if (body.displayName !== undefined) data.displayName = String(body.displayName);
    if (body.deptId !== undefined) data.deptId = parseIdOrNull(body.deptId);
    if (body.position !== undefined) data.position = body.position;
    if (body.email !== undefined) data.email = body.email;
    if (body.phone !== undefined) data.phone = body.phone;
    if (body.status !== undefined) {
      if (!['active', 'disabled'].includes(String(body.status))) {
        throw BizException.param('status 仅支持 active/disabled');
      }
      data.status = body.status;
      // 停用即吊销会话（离职停用后单据按规则转交由各模块处理）
      if (body.status === 'disabled') {
        await this.prisma.sysSession.updateMany({
          where: { userId: uid, revokedAt: null },
          data: { revokedAt: new Date() },
        });
      }
    }
    await this.prisma.sysUser.update({ where: { id: uid }, data });
    await this.audit.log({ action: 'update', objectType: 'SysUser', objectId: uid, before: { status: user.status, displayName: user.displayName }, after: data });
    this.permCache.invalidate(uid);
    return null;
  }

  @Post(':id/roles')
  @RequirePermission('sys:user:assign-role')
  async assignRoles(@Param('id') id: string, @Body() body: { roleIds?: unknown }, @CurrentUser() me: AuthUser) {
    const uid = parseId(id);
    const user = await this.prisma.sysUser.findFirst({ where: { id: uid, deletedAt: null } });
    if (!user) throw BizException.notFound('用户不存在');
    const roleIds = parseIds(body.roleIds);
    const roles = await this.prisma.sysRole.findMany({ where: { id: { in: roleIds }, deletedAt: null } });
    if (roles.length !== roleIds.length) throw BizException.param('存在无效角色');
    await this.prisma.$transaction([
      this.prisma.sysUserRole.deleteMany({ where: { userId: uid } }),
      ...roleIds.map((roleId) => this.prisma.sysUserRole.create({ data: { userId: uid, roleId, grantedBy: me.id } })),
    ]);
    await this.audit.log({
      action: 'grant',
      objectType: 'SysUserRole',
      objectId: uid,
      after: { roleIds: roleIds.map(String) },
    });
    this.permCache.invalidate(uid);
    return null;
  }

  /** 重置密码（R-SYS；FR-M00-004） */
  @Post(':id/reset-password')
  @RequirePermission('sys:user:update')
  async resetPassword(@Param('id') id: string, @Body() body: { password?: string }, @CurrentUser() me: AuthUser) {
    if (!body.password) throw BizException.param('password 必填');
    this.assertStrength(body.password);
    const uid = parseId(id);
    await this.prisma.sysUser.update({
      where: { id: uid },
      data: { passwordHash: this.auth.hashPassword(body.password), failCount: 0, lockedUntil: null },
    });
    await this.prisma.sysSession.updateMany({
      where: { userId: uid, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    await this.audit.log({ action: 'reset_password', objectType: 'SysUser', objectId: uid });
    return null;
  }

  private assertStrength(pwd: string) {
    if (typeof pwd !== 'string' || pwd.length < 8 || !/[a-zA-Z]/.test(pwd) || !/\d/.test(pwd)) {
      throw BizException.param('密码强度不足：至少 8 位且同时包含字母与数字');
    }
  }
}
