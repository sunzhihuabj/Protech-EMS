import { Injectable, OnModuleInit } from '@nestjs/common';
import { EventBusService } from './integration-extra.service';
import { DingtalkService } from './integration-extra.service';
import { PrismaService } from '../../shared/prisma.service';
import { NotifyService } from '../../shared/notify.service';

/**
 * 事件订阅装配（FR-M15-010）：业务事件 → 通知/钉钉。
 * 订阅方懒加载 Prisma/Notify（避免与业务模块循环依赖）。
 */
@Injectable()
export class BusSubscriptions implements OnModuleInit {
  constructor(
    private readonly bus: EventBusService,
    private readonly dingtalk: DingtalkService,
    private readonly prisma: PrismaService,
    private readonly notify: NotifyService,
  ) {}

  onModuleInit() {
    // 红灯升级 → 钉钉白名单事件（FR-M00-008：红灯升级/48h 未响应）
    this.bus.on(['card.escalated'], async (e) => {
      const title = String(e.payload.title ?? '红灯卡升级');
      await this.dingtalk.sendMarkdown(`【红灯升级】${title}`, `**${title}**\n\n> 卡点：${String(e.payload.blockedReason ?? '—')}\n> 来源：看板红灯引擎（BR-07）`);
    });

    // 齐套缺料 → 通知项目 PM
    this.bus.on(['kit.short'], async (e) => {
      const pmUserId = e.payload.pmUserId ? BigInt(String(e.payload.pmUserId)) : null;
      if (pmUserId) {
        await this.notify.send({
          userId: pmUserId,
          type: 'remind',
          title: `工单 ${String(e.payload.woCode)} 齐套缺料，已生成请购 ${String(e.payload.prCode)}`,
          bizType: 'work_order',
          bizId: String(e.payload.workOrderId ?? ''),
        });
      }
    });

    // 发货 → 通知 PM（派工草稿已由 M06→M07 服务级联动生成）
    this.bus.on(['stock_move.ship'], async (e) => {
      const pmUserId = e.payload.pmUserId ? BigInt(String(e.payload.pmUserId)) : null;
      if (pmUserId) {
        await this.notify.send({
          userId: pmUserId,
          type: 'todo',
          title: `项目 ${String(e.payload.projectCode)} 已发货，派工草稿已生成待确认`,
          bizType: 'project',
          bizId: String(e.payload.projectId ?? ''),
        });
      }
    });
  }
}
