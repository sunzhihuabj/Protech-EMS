import {
  CanActivate,
  ExecutionContext,
  Injectable,
  SetMetadata,
  createParamDecorator,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';
import { PrismaService } from './prisma.service';
import { BizException } from './biz.exception';
import { AuthService } from './auth.service';
import { runWithContext } from './request-context';

export interface AuthUser {
  id: bigint;
  username: string;
  displayName: string;
  deptId: bigint | null;
}

export const IS_PUBLIC = 'isPublic';
export const Public = () => SetMetadata(IS_PUBLIC, true);

export const PERM_KEY = 'requirePermission';
/** 按钮级/菜单级权限码（FR-M00-002）；未标注的端点默认拒绝（默认闭口） */
export const RequirePermission = (code: string) => SetMetadata(PERM_KEY, code);

export const CurrentUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): AuthUser => ctx.switchToHttp().getRequest().user,
);

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly jwt: JwtService,
    private readonly prisma: PrismaService,
    private readonly reflector: Reflector,
    private readonly authService: AuthService,
  ) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, [
      ctx.getHandler(),
      ctx.getClass(),
    ]);
    const req = ctx.switchToHttp().getRequest<Request>();
    if (isPublic) return true;

    const header = req.headers.authorization ?? '';
    if (!header.startsWith('Bearer ')) throw BizException.unauthorized();
    const token = header.slice(7);
    let payload: { sub?: string; username?: string };
    try {
      payload = await this.jwt.verifyAsync(token);
    } catch {
      throw BizException.unauthorized();
    }
    if (!payload.sub) throw BizException.unauthorized();

    const user = await this.authService.userFromSub(payload.sub);
    if (!user) throw BizException.unauthorized('账号不可用');

    (req as Request & { user: AuthUser }).user = {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      deptId: user.deptId,
    };

    // 审计上下文（createdBy/updatedBy/IP）
    const ip =
      (req.headers['x-forwarded-for'] as string)?.split(',')[0]?.trim() ?? req.ip ?? undefined;
    return runWithContext({ userId: user.id, username: user.username, ip }, () => true);
  }
}

/** 角色权限解析缓存（60s；授权变更主动失效） */
@Injectable()
export class PermissionCache {
  private cache = new Map<string, { codes: Set<string>; expiresAt: number }>();
  private static TTL = 60_000;

  constructor(private readonly prisma: PrismaService) {}

  invalidate(userId?: bigint) {
    if (userId === undefined) this.cache.clear();
    else this.cache.delete(userId.toString());
  }

  async codesOf(userId: bigint): Promise<Set<string>> {
    const key = userId.toString();
    const hit = this.cache.get(key);
    if (hit && hit.expiresAt > Date.now()) return hit.codes;
    const userRoles = await this.prisma.sysUserRole.findMany({
      where: { userId },
      select: { roleId: true },
    });
    const roleRows = userRoles.length
      ? await this.prisma.sysRole.findMany({
          where: { id: { in: userRoles.map((r) => r.roleId) }, deletedAt: null },
          select: { id: true, code: true },
        })
      : [];
    const rpIds = roleRows.length
      ? (
          await this.prisma.sysRolePermission.findMany({
            where: { roleId: { in: roleRows.map((r) => r.id) } },
            select: { permissionId: true },
          })
        ).map((x) => x.permissionId)
      : [];
    const permRows = rpIds.length
      ? await this.prisma.sysPermission.findMany({
          where: { id: { in: rpIds }, deletedAt: null },
          select: { code: true },
        })
      : [];
    const codes = new Set<string>();
    if (roleRows.some((r) => r.code === 'R-SYS')) codes.add('*');
    for (const p of permRows) codes.add(p.code);
    this.cache.set(key, { codes, expiresAt: Date.now() + PermissionCache.TTL });
    return codes;
  }
}

@Injectable()
export class PermissionGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly cache: PermissionCache,
  ) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const perm = this.reflector.getAllAndOverride<string>(PERM_KEY, [
      ctx.getHandler(),
      ctx.getClass(),
    ]);
    if (!perm) return true; // 未标注端点（公开或内部），JwtAuthGuard 仍兜底认证
    const req = ctx.switchToHttp().getRequest<Request & { user?: AuthUser }>();
    if (!req.user) throw BizException.unauthorized();
    const codes = await this.cache.codesOf(req.user.id);
    if (codes.has('*') || codes.has(perm)) return true;
    throw BizException.forbidden(`缺少权限：${perm}`);
  }
}
