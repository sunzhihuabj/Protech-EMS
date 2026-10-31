import { Body, Controller, Get, Post, Req } from '@nestjs/common';
import type { Request } from 'express';
import { AuthService, SESSION_TTL_SEC, checkPasswordStrength } from '../../shared/auth.service';
import { PrismaService } from '../../shared/prisma.service';
import { PermissionCache, Public, CurrentUser, AuthUser } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';

/** FR-M00-004 登录与会话；FR-M00-002 me 返回权限与数据范围 */
@Controller('auth')
export class AuthController {
  constructor(
    private readonly auth: AuthService,
    private readonly prisma: PrismaService,
    private readonly permCache: PermissionCache,
    private readonly audit: AuditService,
  ) {}

  @Public()
  @Post('login')
  async login(
    @Body() body: { username?: string; password?: string },
    @Req() req: Request,
  ) {
    if (!body.username || !body.password) throw BizException.param('用户名与密码必填');
    const ip = (req.headers['x-forwarded-for'] as string)?.split(',')[0]?.trim() ?? req.ip;
    const result = await this.auth.login(body.username, body.password, ip, req.headers['user-agent']);
    await this.audit.log({
      action: 'login',
      objectType: 'SysUser',
      objectId: result.user.id,
      userId: BigInt(result.user.id),
      username: result.user.username,
      ip,
    });
    return result;
  }

  @Public()
  @Post('refresh')
  async refresh(@Body() body: { refreshToken?: string }, @Req() req: Request) {
    if (!body.refreshToken) throw BizException.param('refreshToken 必填');
    return this.auth.refresh(body.refreshToken, req.ip);
  }

  @Public()
  @Post('logout')
  async logout(@Body() body: { refreshToken?: string }) {
    if (body.refreshToken) await this.auth.logout(body.refreshToken);
    return null;
  }

  @Get('me')
  async me(@CurrentUser() user: AuthUser) {
    const u = await this.prisma.sysUser.findFirst({
      where: { id: user.id, deletedAt: null },
      select: { id: true, username: true, displayName: true, deptId: true, position: true },
    });
    if (!u) throw BizException.unauthorized('账号不可用');
    // 无库级外键（05 册规范）：角色/部门应用层装配
    const userRoleIds = (
      await this.prisma.sysUserRole.findMany({ where: { userId: user.id }, select: { roleId: true } })
    ).map((r) => r.roleId);
    const roleRows = userRoleIds.length
      ? await this.prisma.sysRole.findMany({
          where: { id: { in: userRoleIds }, deletedAt: null },
          select: { code: true, name: true, maxDataScope: true },
        })
      : [];
    const dept = u.deptId
      ? await this.prisma.sysDept.findFirst({
          where: { id: u.deptId, deletedAt: null },
          select: { id: true, name: true, code: true, chainCode: true },
        })
      : null;
    const perms = await this.permCache.codesOf(user.id);
    // R-PM 项目级授权（FR-M00-003）
    const grants = await this.prisma.projectPmGrant.findMany({
      where: { userId: user.id, revokedAt: null },
      select: { projectId: true, expiredAt: true },
    });
    return {
      user: {
        ...u,
        id: u.id.toString(),
        deptId: u.deptId ? u.deptId.toString() : null,
        dept: dept ? { ...dept, id: dept.id.toString() } : null,
      },
      roles: roleRows,
      permissions: Array.from(perms),
      pmGrants: grants.map((g) => ({
        projectId: g.projectId.toString(),
        expired: g.expiredAt ? g.expiredAt < new Date() : false,
      })),
      sessionTtlSec: SESSION_TTL_SEC,
    };
  }

  /** 会话完整性自检（前端启动时校验 token 与服务端一致） */
  @Get('whoami')
  async whoami(@CurrentUser() user: AuthUser) {
    return { id: user.id.toString(), username: user.username, displayName: user.displayName };
  }

  /** 修改本人密码（FR-M00-004 密码强度校验） */
  @Post('change-password')
  async changePassword(
    @CurrentUser() user: AuthUser,
    @Body() body: { oldPassword?: string; newPassword?: string },
  ) {
    if (!body.oldPassword || !body.newPassword) throw BizException.param('旧密码与新密码必填');
    const u = await this.prisma.sysUser.findFirst({ where: { id: user.id, deletedAt: null } });
    if (!u) throw BizException.unauthorized();
    if (!this.auth.verifyPassword(body.oldPassword, u.passwordHash)) {
      throw BizException.validation('旧密码错误');
    }
    checkPasswordStrength(body.newPassword);
    await this.prisma.sysUser.update({
      where: { id: user.id },
      data: { passwordHash: this.auth.hashPassword(body.newPassword) },
    });
    // 吊销全部会话强制重登
    await this.prisma.sysSession.updateMany({
      where: { userId: user.id, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    await this.audit.log({ action: 'change_password', objectType: 'SysUser', objectId: user.id });
    return null;
  }
}
