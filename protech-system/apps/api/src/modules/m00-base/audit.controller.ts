import { Controller, Get, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';
import { parseIdOrNull } from '../../shared/serialize';

/** FR-M00-006 审计日志查询（按对象/人/时间；保留 ≥3 年） */
@Controller('sys/audit-logs')
export class AuditController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  @RequirePermission('sys:audit:list')
  async page(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      ...(q.objectType ? { objectType: q.objectType } : {}),
      ...(q.objectId ? { objectId: q.objectId } : {}),
      ...(q.action ? { action: q.action } : {}),
      ...(q.userId ? { userId: parseIdOrNull(q.userId) } : {}),
      ...(q.from || q.to
        ? {
            createdAt: {
              ...(q.from ? { gte: new Date(q.from) } : {}),
              ...(q.to ? { lte: new Date(q.to) } : {}),
            },
          }
        : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.sysAuditLog.findMany({
        where,
        orderBy: { id: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.sysAuditLog.count({ where }),
    ]);
    return {
      list: list.map((l) => ({ ...l, id: l.id.toString(), userId: l.userId?.toString() ?? null })),
      total,
      page,
      pageSize,
    };
  }
}
