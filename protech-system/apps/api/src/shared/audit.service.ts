import { Injectable } from '@nestjs/common';
import { PrismaService } from './prisma.service';
import { currentContext } from './request-context';
import { serialize } from './serialize';

export interface AuditEntry {
  action: string; // login / grant / revoke / export / config / code_gen / escalate / soft_delete / ...
  objectType: string;
  objectId?: string | number | bigint | null;
  before?: unknown;
  after?: unknown;
  userId?: bigint;
  username?: string;
  ip?: string;
}

/** 审计服务（FR-M00-006：登录/权限变更/导出/配置修改/红灯处置/编码生成全部落审计；≥3 年保留） */
@Injectable()
export class AuditService {
  constructor(private readonly prisma: PrismaService) {}

  async log(entry: AuditEntry): Promise<void> {
    const ctx = currentContext();
    try {
      await this.prisma.sysAuditLog.create({
        data: {
          userId: entry.userId ?? ctx.userId ?? null,
          username: entry.username ?? ctx.username ?? null,
          action: entry.action,
          objectType: entry.objectType,
          objectId: entry.objectId === undefined || entry.objectId === null ? null : String(entry.objectId),
          before: entry.before === undefined ? null : JSON.stringify(serialize(entry.before)),
          after: entry.after === undefined ? null : JSON.stringify(serialize(entry.after)),
          ip: entry.ip ?? ctx.ip ?? null,
        },
      });
    } catch (e) {
      // 审计失败不阻断业务，但必须留下痕迹
      console.error('[audit] 写入失败', entry.objectType, (e as Error).message);
    }
  }
}
