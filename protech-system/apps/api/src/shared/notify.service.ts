import { Injectable } from '@nestjs/common';
import { PrismaService } from './prisma.service';

/**
 * 消息通知中心（FR-M00-007）：站内信+待办；类型 todo/escalate/remind/mention。
 * 钉钉 Webhook 通道（FR-M00-008）为 M15 集成订阅方，此处只发站内 + 事件。
 */
@Injectable()
export class NotifyService {
  constructor(private readonly prisma: PrismaService) {}

  async send(params: {
    userId: bigint;
    type: 'todo' | 'escalate' | 'remind' | 'mention';
    title: string;
    content?: string;
    bizType?: string;
    bizId?: string;
  }): Promise<void> {
    await this.prisma.sysNotify.create({
      data: {
        userId: params.userId,
        type: params.type,
        title: params.title,
        content: params.content ?? null,
        bizType: params.bizType ?? null,
        bizId: params.bizId ?? null,
      },
    });
  }

  async sendMany(userIds: bigint[], params: Omit<Parameters<NotifyService['send']>[0], 'userId'>) {
    for (const userId of userIds) {
      await this.send({ ...params, userId });
    }
  }
}
