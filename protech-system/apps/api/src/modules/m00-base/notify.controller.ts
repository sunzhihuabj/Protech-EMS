import { Controller, Get, Param, Post, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser } from '../../shared/guards';
import { parseId } from '../../shared/serialize';

/** FR-M00-007 消息通知中心（站内信+待办；已读未读） */
@Controller('sys/notifies')
export class NotifyController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  async my(@CurrentUser() me: AuthUser, @Query() q: Record<string, string>) {
    const unreadOnly = q.unread === '1';
    const list = await this.prisma.sysNotify.findMany({
      where: { userId: me.id, deletedAt: null, ...(unreadOnly ? { readAt: null } : {}) },
      orderBy: { id: 'desc' },
      take: 50,
    });
    const unread = await this.prisma.sysNotify.count({
      where: { userId: me.id, deletedAt: null, readAt: null },
    });
    return {
      unread,
      list: list.map((n) => ({ ...n, id: n.id.toString(), userId: n.userId.toString() })),
    };
  }

  @Post(':id/read')
  async read(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    const nid = parseId(id);
    await this.prisma.sysNotify.updateMany({
      where: { id: nid, userId: me.id, readAt: null },
      data: { readAt: new Date() },
    });
    return null;
  }

  @Post('read-all')
  async readAll(@CurrentUser() me: AuthUser) {
    await this.prisma.sysNotify.updateMany({
      where: { userId: me.id, readAt: null },
      data: { readAt: new Date() },
    });
    return null;
  }
}
