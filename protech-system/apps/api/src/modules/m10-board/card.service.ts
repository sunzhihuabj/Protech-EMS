import { Injectable, OnModuleInit } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AppConfigService } from '../../shared/app-config.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** 九板预置（08 册 §二：1 总板 + 4 日板 + 4 周板；可改名不可删） */
export const BOARDS = [
  { boardCode: 'exec', name: '① 公司经营总看板', type: 'executive', ownerDept: 'ZJCB', rhythm: '周一 30 分钟' },
  { boardCode: 'order', name: '② 订单交付看板', type: 'daily', ownerDept: 'SWB', rhythm: '每日 15—30 分钟' },
  { boardCode: 'supply', name: '③ 供应链看板', type: 'daily', ownerDept: 'JHB', rhythm: '每日' },
  { boardCode: 'mfg', name: '④ 生产制造看板', type: 'daily', ownerDept: 'SCB', rhythm: '每日' },
  { boardCode: 'delivery', name: '⑤ 工程交付看板', type: 'daily', ownerDept: 'GCJS', rhythm: '每日' },
  { boardCode: 'rd', name: '⑥ 研发看板', type: 'weekly', ownerDept: 'YFSJ', rhythm: '每周' },
  { boardCode: 'market', name: '⑦ 市场销售看板', type: 'weekly', ownerDept: 'XSTD', rhythm: '每周' },
  { boardCode: 'fin', name: '⑧ 财务看板', type: 'weekly', ownerDept: 'CWB', rhythm: '每周' },
  { boardCode: 'ga', name: '⑨ 企管看板', type: 'weekly', ownerDept: 'QGB', rhythm: '每周' },
] as const;

/** E-07 卡片状态机（05 册 §6.3） */
const CARD_TRANSITIONS: Record<string, string[]> = {
  todo: ['doing', 'blocked', 'done'],
  doing: ['blocked', 'done', 'todo'],
  blocked: ['doing', 'done'], // BR-07：解决才摘，无删除
  done: [],
};

/** FR-M10-007 模糊词库（可配置；默认制度词表） */
const DEFAULT_VAGUE = ['尽快', '尽快解决', '跟进', '持续跟进', '跟进中', '协调', '处理一下', '抓紧', '看看', '研究', '推进', '加强'];

/** 卡片引擎（FR-M10-007~013）：五要素校验/状态机/留痕/跨部门挂卡/升级 */
@Injectable()
export class CardService implements OnModuleInit {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: AppConfigService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  /** 九板 + 指标定义幂等预置（FR-M10-001） */
  async onModuleInit() {
    for (const b of BOARDS) {
      await this.prisma.board.upsert({
        where: { boardCode: b.boardCode },
        create: { ...b },
        update: {},
      });
    }
    const vague = await this.prisma.sysConfig.findFirst({ where: { key: 'kb_vague_words' } });
    if (!vague) {
      await this.prisma.sysConfig.create({
        data: { key: 'kb_vague_words', value: JSON.stringify(DEFAULT_VAGUE), valueType: 'json', remark: '模糊卡点词库（FR-M10-007 VAGUE 拦截）' },
      });
    }
  }

  private async vagueWords(): Promise<string[]> {
    const v = await this.config.get('kb_vague_words', JSON.stringify(DEFAULT_VAGUE));
    try {
      const arr = JSON.parse(v);
      return Array.isArray(arr) ? arr : DEFAULT_VAGUE;
    } catch {
      return DEFAULT_VAGUE;
    }
  }

  /** FR-M10-007 五要素校验 + 建卡（一键建卡带 source，FR-M10-008） */
  async create(body: Record<string, unknown>, userId: bigint) {
    const title = String(body.title ?? '').trim();
    if (!title) throw BizException.param('事项必填');
    const vague = await this.vagueWords();
    const hit = vague.find((w) => title.includes(w));
    if (hit) {
      throw BizException.validation(`卡点事项含模糊词"${hit}"，请写成可验收的具体事项（FR-M10-007）`);
    }
    const ownerId = parseIdOrNull(body.ownerId);
    if (!ownerId) throw BizException.param('责任人必填（唯一到人）');
    const dueDate = body.dueDate ? new Date(String(body.dueDate)) : null;
    if (!dueDate) throw BizException.param('承诺完成日必填（责任人报）');
    const boardId = parseIdOrNull(body.boardId);
    if (!boardId) throw BizException.param('boardId 必填');

    const status = String(body.status ?? 'todo');
    if (!['todo', 'doing', 'blocked'].includes(status)) throw BizException.param('初始状态仅 todo/doing/blocked');
    if (status === 'blocked' && (!body.blockedReason || !body.nextAction)) {
      throw BizException.validation('红灯卡必须填写卡点说明与下一步动作（FR-M10-007）');
    }

    const owner = await this.prisma.sysUser.findFirst({ where: { id: ownerId, deletedAt: null } });
    if (!owner) throw BizException.notFound('责任人不存在');

    const card = await this.prisma.kbCard.create({
      data: {
        boardId,
        title,
        ownerId,
        ownerDeptId: owner.deptId,
        dueDate,
        status,
        blockedReason: status === 'blocked' ? String(body.blockedReason) : null,
        nextAction: status === 'blocked' ? String(body.nextAction) : null,
        blockedAt: status === 'blocked' ? new Date() : null,
        sourceType: (body.sourceType as string) ?? null,
        sourceId: body.sourceId != null ? String(body.sourceId) : null,
        crossBoardId: parseIdOrNull(body.crossBoardId),
        crossFlag: body.crossBoardId ? 'out' : null,
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.prisma.kbCardLog.create({
      data: { cardId: card.id, toStatus: status, byId: userId, note: '建卡' },
    });
    // 跨部门挂卡：目标板红灯区出现并 @责任人（FR-M10-011）
    if (card.crossBoardId) {
      await this.notify.send({
        userId: ownerId,
        type: 'todo',
        title: `跨部门卡点已挂到你：${title}（请 24h 内响应，48h 未响应升级公司周会）`,
        bizType: 'kb_card',
        bizId: card.id.toString(),
      });
    }
    return card;
  }

  /** FR-M10-009/010 状态迁移：留痕 + 红灯必填 + BR-12 补录标记 */
  async transition(cardId: bigint, to: string, userId: bigint, body: Record<string, unknown>) {
    const card = await this.prisma.kbCard.findFirst({ where: { id: cardId, deletedAt: null } });
    if (!card) throw BizException.notFound('卡片不存在');
    const allowed = CARD_TRANSITIONS[card.status] ?? [];
    if (!allowed.includes(to)) throw BizException.validation(`状态不允许：${card.status} → ${to}`);

    if (to === 'blocked' && (!body.blockedReason || !body.nextAction)) {
      throw BizException.validation('拖入红灯区必须填写卡点说明与下一步动作（FR-M10-009）');
    }

    // BR-12 补录标记：业务发生日早于今日 → is_backfill 计入补录率
    let isBackfill = false;
    if (body.actionDate) {
      const actionDate = new Date(String(body.actionDate));
      const today = new Date();
      const dayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
      if (actionDate < dayStart) {
        isBackfill = true;
        await this.audit.log({ action: 'br12_backfill', objectType: 'KbCard', objectId: cardId, after: { actionDate: String(body.actionDate) } });
      }
    }

    const data: Record<string, unknown> = { status: to, updatedBy: userId };
    if (to === 'blocked') {
      data.blockedAt = card.blockedAt ?? new Date();
      data.blockedReason = String(body.blockedReason);
      data.nextAction = String(body.nextAction);
    }
    if (to === 'doing' || to === 'done') {
      // 红灯解除：升级记录关闭（解决才摘 BR-07）
      if (card.status === 'blocked') {
        await this.prisma.kbEscalation.updateMany({
          where: { cardId, resolvedAt: null },
          data: { resolvedAt: new Date() },
        });
        data.respondedAt = new Date();
      }
    }
    if (to === 'done') data.closedAt = new Date();

    const updated = await this.prisma.kbCard.update({ where: { id: cardId }, data });
    await this.prisma.kbCardLog.create({
      data: {
        cardId,
        fromStatus: card.status,
        toStatus: to,
        byId: userId,
        note: (body.note as string) ?? null,
        isBackfill,
      },
    });
    void updated;
    return { id: cardId.toString(), status: to, isBackfill };
  }

  /** FR-M10-007 承诺日可上调不可下调（链主亦然） */
  async changeDue(cardId: bigint, dueDate: Date, userId: bigint, note?: string) {
    const card = await this.prisma.kbCard.findFirst({ where: { id: cardId, deletedAt: null } });
    if (!card) throw BizException.notFound('卡片不存在');
    if (dueDate.getTime() < card.dueDate.getTime()) {
      throw BizException.validation('承诺日只可上调不可下调（FR-M10-007 铁律）');
    }
    await this.prisma.kbCard.update({ where: { id: cardId }, data: { dueDate, updatedBy: userId } });
    await this.prisma.kbCardLog.create({
      data: { cardId, toStatus: card.status, byId: userId, note: note ?? `承诺日上调：${card.dueDate.toLocaleDateString('zh-CN')} → ${dueDate.toLocaleDateString('zh-CN')}` },
    });
    await this.audit.log({ action: 'due_raise', objectType: 'KbCard', objectId: cardId, before: card.dueDate, after: dueDate });
    return { id: cardId.toString(), dueDate };
  }

  /** FR-M10-012 评论（@与附件 R2） */
  async comment(cardId: bigint, userId: bigint, note: string) {
    if (!note.trim()) throw BizException.param('评论内容必填');
    await this.prisma.kbCardLog.create({ data: { cardId, toStatus: 'comment', byId: userId, note } });
    // 评论视为责任方响应（48h 扫描口径）
    await this.prisma.kbCard.updateMany({ where: { id: cardId, respondedAt: null }, data: { respondedAt: new Date() } });
    return null;
  }

  /** FR-M10-013 我的卡：逾期置顶 + 到期升序 */
  async myCards(userId: bigint) {
    const cards = await this.prisma.kbCard.findMany({
      where: { ownerId: userId, deletedAt: null, status: { in: ['todo', 'doing', 'blocked'] } },
      orderBy: [{ dueDate: 'asc' }, { id: 'desc' }],
      take: 100,
    });
    const now = Date.now();
    return cards
      .map((c) => ({ ...c, id: c.id.toString(), boardId: c.boardId.toString(), overdue: c.dueDate.getTime() < now }))
      .sort((a, b) => (a.overdue === b.overdue ? a.dueDate.getTime() - b.dueDate.getTime() : a.overdue ? -1 : 1));
  }

  /** FR-M10-017 升级清单（超期天数降序）+ 一键催办 */
  async escalationList() {
    const cards = await this.prisma.kbCard.findMany({
      where: { deletedAt: null, escalatedAt: { not: null }, status: 'blocked' },
      orderBy: { blockedAt: 'asc' },
      take: 100,
    });
    return cards.map((c) => ({
      ...c,
      id: c.id.toString(),
      boardId: c.boardId.toString(),
      blockedDays: Math.floor((Date.now() - (c.blockedAt?.getTime() ?? Date.now())) / 86400000),
    }));
  }

  async urge(cardId: bigint, userId: bigint) {
    const card = await this.prisma.kbCard.findFirst({ where: { id: cardId, deletedAt: null } });
    if (!card) throw BizException.notFound('卡片不存在');
    await this.notify.send({
      userId: card.ownerId,
      type: 'escalate',
      title: `【要求今日答复】${card.title}`,
      content: card.blockedReason ?? undefined,
      bizType: 'kb_card',
      bizId: card.id.toString(),
    });
    await this.audit.log({ action: 'urge', objectType: 'KbCard', objectId: cardId });
    return null;
  }
}
