import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AppConfigService } from '../../shared/app-config.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';

/**
 * 红灯升级引擎（11 册 §5.3，FR-M10-014~016，BR-07）。
 * worker 每 30 分钟调用 /internal/scans/redlight 与 /internal/scans/no-response；
 * api 层无"取消升级/删除红灯"接口，仅 resolve。
 */
@Injectable()
export class ScanService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: AppConfigService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  /** 红灯 3 天自动升级总板（BR-07；阈值可配 kb_redlight_days） */
  async redlightScan(): Promise<{ escalated: number }> {
    const days = await this.config.getNumber('kb_redlight_days', 3);
    const deadline = new Date(Date.now() - days * 86400000);
    const cards = await this.prisma.kbCard.findMany({
      where: { deletedAt: null, status: 'blocked', blockedAt: { lt: deadline }, escalatedAt: null },
      take: 100,
    });
    for (const card of cards) {
      await this.prisma.kbCard.update({
        where: { id: card.id },
        data: { escalatedAt: new Date() },
      });
      await this.prisma.kbEscalation.create({
        data: { cardId: card.id, level: 'executive', reason: `blocked_${days}d` },
      });
      await this.audit.log({ action: 'escalate', objectType: 'KbCard', objectId: card.id, after: { level: 'executive' } });
      // FR-M10-016 升级通知：链主/被挂方负责人/分管副总/总板主持（站内+钉钉经 BusSubscriptions card.escalated）
      // 升级推送：链主/被挂方负责人/分管副总/总板主持（站内；钉钉走 M15 Webhook）
      await this.notify.sendMany(
        await this.boardStakeholders(card.boardId),
        {
          type: 'escalate',
          title: `【红灯升级总板】${card.title} 已受阻超 ${days} 天（BR-07 链主无权压卡）`,
          content: card.blockedReason ?? undefined,
          bizType: 'kb_card',
          bizId: card.id.toString(),
        },
      );
    }
    return { escalated: cards.length };
  }

  /** 48h 未响应升级公司周会（FR-M10-015 第二级） */
  async noResponseScan(): Promise<{ escalated: number }> {
    const hours = await this.config.getNumber('respond_hours_second', 48);
    const deadline = new Date(Date.now() - hours * 3600000);
    // 被挂卡（crossFlag=in 体现为 crossBoardId 非空且非本人板）48h 无响应
    const cards = await this.prisma.kbCard.findMany({
      where: {
        deletedAt: null,
        status: 'blocked',
        crossBoardId: { not: null },
        respondedAt: null,
        createdAt: { lt: deadline },
      },
      take: 100,
    });
    let count = 0;
    for (const card of cards) {
      const already = await this.prisma.kbEscalation.findFirst({
        where: { cardId: card.id, level: 'meeting', resolvedAt: null },
      });
      if (already) continue;
      await this.prisma.kbEscalation.create({
        data: { cardId: card.id, level: 'meeting', reason: 'no_response_48h' },
      });
      await this.audit.log({ action: 'escalate', objectType: 'KbCard', objectId: card.id, after: { level: 'meeting' } });
      count++;
    }
    return { escalated: count };
  }

  /** 未开会提醒（FR-M10-022）：日板连续 N 工作日未开会议模式 → 提醒链主 */
  async meetingRemindScan(): Promise<{ reminded: number }> {
    const days = await this.config.getNumber('meeting_remind_days', 2);
    const deadline = new Date(Date.now() - days * 86400000);
    const dailyBoards = await this.prisma.board.findMany({ where: { type: 'daily', deletedAt: null } });
    let reminded = 0;
    for (const b of dailyBoards) {
      const last = await this.prisma.kbMeeting.findFirst({
        where: { boardId: b.id },
        orderBy: { meetingDate: 'desc' },
      });
      if ((!last || last.meetingDate < deadline) && b.ownerDept) {
        const head = await this.prisma.sysUser.findFirst({
          where: { deptId: { in: await this.deptIdsByCode(b.ownerDept) }, deletedAt: null, position: { contains: '负责人' } },
        });
        if (head) {
          await this.notify.send({
            userId: head.id,
            type: 'remind',
            title: `【${b.name}】已连续 ${days} 个工作日未开早会（FR-M10-022，连续 5 日将通报）`,
            bizType: 'board',
            bizId: b.id.toString(),
          });
          reminded++;
        }
      }
    }
    return { reminded };
  }

  private async deptIdsByCode(code: string): Promise<bigint[]> {
    const depts = await this.prisma.sysDept.findMany({ where: { code, deletedAt: null }, select: { id: true } });
    return depts.map((d) => d.id);
  }

  private async boardStakeholders(boardId: bigint): Promise<bigint[]> {
    const board = await this.prisma.board.findFirst({ where: { id: boardId } });
    const ids = new Set<bigint>();
    if (board?.ownerDept) {
      const heads = await this.prisma.sysUser.findMany({
        where: { deptId: { in: await this.deptIdsByCode(board.ownerDept) }, deletedAt: null },
        take: 3,
      });
      for (const h of heads) ids.add(h.id);
    }
    // R-EXEC/R-CHAIN 通知：按角色查
    const roles = await this.prisma.sysRole.findMany({
      where: { code: { in: ['R-EXEC', 'R-CHAIN'] }, deletedAt: null },
      select: { id: true },
    });
    const urs = await this.prisma.sysUserRole.findMany({
      where: { roleId: { in: roles.map((r) => r.id) } },
      select: { userId: true },
    });
    for (const u of urs) ids.add(u.userId);
    return Array.from(ids);
  }
}
