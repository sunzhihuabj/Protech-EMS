import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { createHash, randomBytes } from 'node:crypto';
import * as bcrypt from 'bcryptjs';
import { PrismaService } from './prisma.service';
import { BizException } from './biz.exception';

export const PASSWORD_MIN = 8;
export const LOGIN_MAX_FAILS = 5;
export const LOGIN_LOCK_MINUTES = 15;
export const ACCESS_TOKEN_TTL_SEC = 30 * 60; // 短期 JWT
export const SESSION_TTL_SEC = 8 * 60 * 60; // 会话 8 小时（FR-M00-004）

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: { id: string; username: string; displayName: string; deptId: string | null };
}

/** FR-M00-004：密码强度校验（≥8 位且含字母与数字） */
export function checkPasswordStrength(pwd: string): void {
  if (typeof pwd !== 'string' || pwd.length < PASSWORD_MIN || !/[a-zA-Z]/.test(pwd) || !/\d/.test(pwd)) {
    throw BizException.param(`密码强度不足：至少 ${PASSWORD_MIN} 位且同时包含字母与数字`);
  }
}

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
  ) {}

  private sha256(s: string): string {
    return createHash('sha256').update(s).digest('hex');
  }

  hashPassword(plain: string): string {
    return bcrypt.hashSync(plain, 10);
  }

  verifyPassword(plain: string, hash: string): boolean {
    return bcrypt.compareSync(plain, hash);
  }

  async login(username: string, password: string, ip?: string, ua?: string): Promise<LoginResult> {
    const user = await this.prisma.sysUser.findFirst({
      where: { username, deletedAt: null },
    });
    if (!user) throw BizException.validation('用户名或密码错误');
    if (user.status !== 'active') throw BizException.forbidden('账号已停用，请联系管理员');

    const now = new Date();
    if (user.lockedUntil && user.lockedUntil > now) {
      const min = Math.ceil((user.lockedUntil.getTime() - now.getTime()) / 60000);
      throw BizException.validation(`登录失败次数过多，账号已锁定，请 ${min} 分钟后重试`);
    }

    const ok = this.verifyPassword(password, user.passwordHash);
    if (!ok) {
      const failCount = user.failCount + 1;
      const lockedUntil =
        failCount >= LOGIN_MAX_FAILS ? new Date(now.getTime() + LOGIN_LOCK_MINUTES * 60000) : null;
      await this.prisma.sysUser.update({
        where: { id: user.id },
        data: { failCount: lockedUntil ? 0 : failCount, lockedUntil },
      });
      throw BizException.validation(
        lockedUntil ? `连续 ${LOGIN_MAX_FAILS} 次登录失败，账号锁定 ${LOGIN_LOCK_MINUTES} 分钟` : '用户名或密码错误',
      );
    }

    await this.prisma.sysUser.update({
      where: { id: user.id },
      data: { failCount: 0, lockedUntil: null, lastLoginAt: now },
    });

    const accessToken = await this.jwt.signAsync(
      { sub: user.id.toString(), username: user.username },
      { expiresIn: ACCESS_TOKEN_TTL_SEC },
    );
    const refreshToken = randomBytes(48).toString('hex');
    await this.prisma.sysSession.create({
      data: {
        userId: user.id,
        refreshTokenHash: this.sha256(refreshToken),
        expiresAt: new Date(now.getTime() + SESSION_TTL_SEC * 1000),
        ip: ip ?? null,
        ua: ua ?? null,
      },
    });

    return {
      accessToken,
      refreshToken,
      expiresIn: ACCESS_TOKEN_TTL_SEC,
      user: {
        id: user.id.toString(),
        username: user.username,
        displayName: user.displayName,
        deptId: user.deptId ? user.deptId.toString() : null,
      },
    };
  }

  /** 刷新会话（轮换 refresh token；旧 token 立即吊销） */
  async refresh(refreshToken: string, ip?: string): Promise<{ accessToken: string; refreshToken: string; expiresIn: number }> {
    const session = await this.prisma.sysSession.findUnique({
      where: { refreshTokenHash: this.sha256(refreshToken) },
    });
    const now = new Date();
    if (!session || session.revokedAt || session.expiresAt < now) {
      throw BizException.unauthorized('会话已过期，请重新登录');
    }
    await this.prisma.sysSession.update({
      where: { id: session.id },
      data: { revokedAt: now },
    });
    const user = await this.prisma.sysUser.findFirst({ where: { id: session.userId, deletedAt: null } });
    if (!user || user.status !== 'active') throw BizException.unauthorized('会话已失效');

    const accessToken = await this.jwt.signAsync(
      { sub: user.id.toString(), username: user.username },
      { expiresIn: ACCESS_TOKEN_TTL_SEC },
    );
    const newRefresh = randomBytes(48).toString('hex');
    await this.prisma.sysSession.create({
      data: {
        userId: user.id,
        refreshTokenHash: this.sha256(newRefresh),
        expiresAt: new Date(now.getTime() + SESSION_TTL_SEC * 1000),
        ip: ip ?? null,
      },
    });
    return { accessToken, refreshToken: newRefresh, expiresIn: ACCESS_TOKEN_TTL_SEC };
  }

  async logout(refreshToken: string): Promise<void> {
    await this.prisma.sysSession.updateMany({
      where: { refreshTokenHash: this.sha256(refreshToken), revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }

  /** 按 JWT sub 解析当前用户（守卫调用） */
  async userFromSub(sub: string) {
    return this.prisma.sysUser.findFirst({
      where: { id: BigInt(sub), deletedAt: null, status: 'active' },
    });
  }
}
