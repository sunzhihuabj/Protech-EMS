import { Controller, Get } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';

/** FR-M00-013 系统监控（服务健康/任务队列/集成推送队列状态页 R-SYS） */
@Controller('monitor')
export class MonitorController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('health')
  async health() {
    let dbOk = false;
    try {
      await this.prisma.$queryRaw`SELECT 1`;
      dbOk = true;
    } catch {
      dbOk = false;
    }
    return {
      status: dbOk ? 'up' : 'degraded',
      db: dbOk,
      time: new Date().toISOString(),
      env: process.env.NODE_ENV ?? 'dev',
    };
  }

  @Get('stats')
  @RequirePermission('sys:monitor:view')
  async stats() {
    const [users, sessions, auditToday, sessionsActive] = await Promise.all([
      this.prisma.sysUser.count({ where: { deletedAt: null } }),
      this.prisma.sysSession.count({ where: { revokedAt: null, expiresAt: { gt: new Date() } } }),
      this.prisma.sysAuditLog.count({ where: { createdAt: { gte: new Date(new Date().setHours(0, 0, 0, 0)) } } }),
      this.prisma.sysSession.count({ where: { revokedAt: null } }),
    ]);
    return { users, sessions, sessionsActive, auditToday };
  }
}
