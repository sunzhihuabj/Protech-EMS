import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { CardService } from './card.service';
import { ScanService } from './scan.service';
import { MeetingService, MetricService, ExecutiveService } from './meeting-metric.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** M10 看板与早会接口（FR-M10-001~030 的 R1 集） */
@Controller('kb')
export class BoardController {
  constructor(
    private readonly cards: CardService,
    private readonly scans: ScanService,
    private readonly meetings: MeetingService,
    private readonly metricSvc: MetricService,
    private readonly execSvc: ExecutiveService,
    private readonly prisma: PrismaService,
  ) {}

  // ---------------- 板与四区 ----------------

  @Get('boards')
  @RequirePermission('kb:view')
  async boards() {
    const boards = await this.prisma.board.findMany({ where: { deletedAt: null }, orderBy: { boardCode: 'asc' } });
    const blocked = await this.prisma.kbCard.groupBy({
      by: ['boardId'],
      where: { status: 'blocked', deletedAt: null },
      _count: { _all: true },
    });
    return boards.map((b) => ({
      ...b,
      id: b.id.toString(),
      redCount: blocked.find((x) => x.boardId === b.id)?._count._all ?? 0,
    }));
  }

  /** 四区一栏视图（FR-M10-003）：指标区/进行区/红灯区/闭环区 + 仅红灯过滤 */
  @Get('boards/:code/view')
  @RequirePermission('kb:view')
  async boardView(@Param('code') code: string) {
    const board = await this.prisma.board.findFirst({ where: { boardCode: code, deletedAt: null } });
    if (!board) throw BizException.notFound('看板不存在');
    const cards = await this.prisma.kbCard.findMany({
      where: { boardId: board.id, deletedAt: null, status: { in: ['todo', 'doing', 'blocked', 'done'] } },
      orderBy: { updatedAt: 'desc' },
      take: 200,
    });
    const sevenDaysAgo = new Date(Date.now() - 7 * 86400000);
    return {
      board: { ...board, id: board.id.toString() },
      metrics: await this.metricSvc.boardMetrics(code),
      zones: {
        doing: cards.filter((c) => c.status === 'todo' || c.status === 'doing').map((c) => this.presentCard(c)),
        red: cards.filter((c) => c.status === 'blocked').map((c) => this.presentCard(c)),
        closed: cards.filter((c) => c.status === 'done' && c.closedAt && c.closedAt >= sevenDaysAgo).map((c) => this.presentCard(c)),
      },
      crossIn: cards.filter((c) => c.crossBoardId && c.crossBoardId !== board.id && c.status === 'blocked').map((c) => this.presentCard(c)),
    };
  }

  // ---------------- 卡片 ----------------

  @Post('cards')
  @RequirePermission('kb:card:create')
  async createCard(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const card = await this.cards.create(body, me.id);
    return { id: card.id.toString() };
  }

  @Post('cards/:id/transition')
  @RequirePermission('kb:card:move')
  async transition(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.to) throw BizException.param('to 必填');
    return this.cards.transition(parseId(id), String(body.to), me.id, body);
  }

  @Post('cards/:id/due')
  @RequirePermission('kb:card:move')
  async changeDue(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.dueDate) throw BizException.param('dueDate 必填');
    return this.cards.changeDue(parseId(id), new Date(String(body.dueDate)), me.id, body.note as string);
  }

  @Post('cards/:id/comment')
  @RequirePermission('kb:card:move')
  async comment(@Param('id') id: string, @Body() body: { note?: string }, @CurrentUser() me: AuthUser) {
    return this.cards.comment(parseId(id), me.id, body.note ?? '');
  }

  @Get('my-cards')
  @RequirePermission('kb:view')
  async myCards(@CurrentUser() me: AuthUser) {
    const list = await this.cards.myCards(me.id);
    return list.map((c) => ({ ...c, ownerId: c.ownerId.toString(), ownerDeptId: c.ownerDeptId?.toString() ?? null }));
  }

  @Get('escalations')
  @RequirePermission('kb:view')
  async escalations() {
    const list = await this.cards.escalationList();
    return list.map((c) => ({ ...c, ownerId: c.ownerId.toString(), ownerDeptId: c.ownerDeptId?.toString() ?? null }));
  }

  /** FR-M10-017 一键催办"要求今日答复" */
  @Post('cards/:id/urge')
  @RequirePermission('kb:card:urge')
  async urge(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.cards.urge(parseId(id), me.id);
  }

  @Get('cards/:id/logs')
  @RequirePermission('kb:view')
  async logs(@Param('id') id: string) {
    const rows = await this.prisma.kbCardLog.findMany({
      where: { cardId: parseId(id) },
      orderBy: { id: 'desc' },
      take: 50,
    });
    return rows.map((r) => ({ ...r, id: r.id.toString(), cardId: r.cardId.toString(), byId: r.byId?.toString() ?? null }));
  }

  /** FR-M10-023 闭环统计：准时关闭率/红灯滞留/补录率，按人/部门排名（KPI 过程指标） */
  @Get('stats/closure')
  @RequirePermission('kb:view')
  async closureStats() {
    const now = Date.now();
    const doneCards = await this.prisma.kbCard.findMany({
      where: { status: 'done', closedAt: { not: null }, deletedAt: null },
      select: { ownerId: true, dueDate: true, closedAt: true, blockedAt: true },
      take: 500,
    });
    const byOwner = new Map<string, { total: number; onTime: number; blockedDays: number[] }>();
    for (const c of doneCards) {
      const k = c.ownerId.toString();
      const e = byOwner.get(k) ?? { total: 0, onTime: 0, blockedDays: [] };
      e.total++;
      if ((c.closedAt as Date).getTime() <= c.dueDate.getTime()) e.onTime++;
      if (c.blockedAt) e.blockedDays.push(((c.closedAt as Date).getTime() - c.blockedAt.getTime()) / 86400000);
      byOwner.set(k, e);
    }
    const ownerIds = [...byOwner.keys()].map((x) => BigInt(x));
    const users = ownerIds.length
      ? await this.prisma.sysUser.findMany({ where: { id: { in: ownerIds } }, select: { id: true, displayName: true } })
      : [];
    const rank = users.map((u) => {
      const e = byOwner.get(u.id.toString())!;
      const avgBlocked = e.blockedDays.length
        ? Number((e.blockedDays.reduce((a, b) => a + b, 0) / e.blockedDays.length).toFixed(1))
        : null;
      return {
        userId: u.id.toString(),
        displayName: u.displayName,
        closedTotal: e.total,
        onTimeRate: e.total ? Number((e.onTime / e.total).toFixed(4)) : null,
        avgBlockedDays: avgBlocked,
      };
    }).sort((a, b) => (b.onTimeRate ?? 0) - (a.onTimeRate ?? 0));

    const logs = await this.prisma.kbCardLog.count({ where: { isBackfill: true, movedAt: { gte: new Date(now - 30 * 86400000) } } });
    const logsTotal = await this.prisma.kbCardLog.count({ where: { movedAt: { gte: new Date(now - 30 * 86400000) } } });
    return {
      rank,
      backfillRate30d: logsTotal ? Number((logs / logsTotal).toFixed(4)) : 0,
      redAvgStayDays: rank.filter((r) => r.avgBlockedDays !== null).length
        ? Number((rank.reduce((s, r) => s + (r.avgBlockedDays ?? 0), 0) / rank.filter((r) => r.avgBlockedDays !== null).length).toFixed(1))
        : null,
    };
  }

  /** FR-M10-024 总板汇总：指标来自四链板（不重复计算）；红灯区=各板升级卡+跨链卡 */
  @Get('executive/summary')
  @RequirePermission('kb:view')
  async executiveSummary() {
    const chainBoards = await this.prisma.board.findMany({ where: { boardCode: { in: ['order', 'supply', 'mfg', 'delivery'] } } });
    const perBoard = await Promise.all(
      chainBoards.map(async (b) => ({
        boardCode: b.boardCode,
        metrics: await this.metricSvc.boardMetrics(b.boardCode),
        red: await this.prisma.kbCard.count({ where: { boardId: b.id, status: 'blocked', deletedAt: null } }),
        escalated: await this.prisma.kbCard.count({ where: { boardId: b.id, status: 'blocked', escalatedAt: { not: null }, deletedAt: null } }),
      })),
    );
    return { perBoard };
  }

  /** FR-M10-029 看板配置：板改名/指标阈值调整（R-GA+链主） */
  @Post('boards/:code/config')
  @RequirePermission('kb:admin')
  async configBoard(@Param('code') code: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const board = await this.prisma.board.findFirst({ where: { boardCode: code, deletedAt: null } });
    if (!board) throw BizException.notFound('看板不存在');
    if (body.name !== undefined) {
      await this.prisma.board.update({ where: { id: board.id }, data: { name: String(body.name), updatedBy: me.id } });
    }
    if (Array.isArray(body.metricTargets)) {
      for (const mt of body.metricTargets as Array<{ metricCode: string; target: string }>) {
        await this.prisma.metricDef.updateMany({
          where: { metricCode: mt.metricCode, deletedAt: null },
          data: { target: mt.target, updatedBy: me.id },
        });
      }
    }
    return null;
  }

  /** FR-M10-030 手工指标：每板 ≤1 个手工录入（带"手工"角标） */
  @Post('boards/:code/manual-metric')
  @RequirePermission('kb:admin')
  async manualMetric(@Param('code') code: string, @Body() body: { name?: string; value?: string }, @CurrentUser() me: AuthUser) {
    if (!body.name || body.value === undefined) throw BizException.param('name/value 必填');
    const existing = await this.prisma.metricDef.count({ where: { boardCode: code, source: 'manual', deletedAt: null } });
    if (existing >= 1) throw BizException.validation('每板仅允许 1 个手工指标（FR-M10-030）');
    const def = await this.prisma.metricDef.create({
      data: {
        metricCode: `manual_${code}_${Date.now()}`,
        boardCode: code,
        name: body.name,
        formula: 'manual',
        unit: 'count',
        direction: 'forward',
        source: 'manual',
        updatedBy: me.id,
      },
    });
    const period = new Date().toISOString().slice(0, 10);
    await this.prisma.metricSnapshot.upsert({
      where: { metricCode_period: { metricCode: def.metricCode, period } },
      create: { metricCode: def.metricCode, period, value: String(body.value), status: 'ok' },
      update: { value: String(body.value), status: 'ok', computedAt: new Date() },
    });
    return { metricCode: def.metricCode };
  }

  // ---------------- 会议模式 ----------------

  @Post('boards/:id/meetings')
  @RequirePermission('kb:meeting:run')
  async startMeeting(@Param('id') id: string, @Body() body: { attendees?: string[] }, @CurrentUser() me: AuthUser) {
    return this.meetings.start(
      parseId(id),
      me.id,
      (body.attendees ?? []).map((x) => parseId(x)),
    );
  }

  @Post('meetings/:id/ask')
  @RequirePermission('kb:meeting:run')
  async ask(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.cardId) throw BizException.param('cardId 必填');
    return this.meetings.ask(
      parseId(id),
      parseId(body.cardId),
      {
        askWhere: body.askWhere as string,
        askWho: body.askWho as string,
        askWhen: body.askWhen as string,
        skip: body.skip === true,
      },
      me.id,
    );
  }

  @Post('meetings/:id/finish')
  @RequirePermission('kb:meeting:run')
  async finish(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.meetings.finish(parseId(id), me.id);
  }

  @Get('boards/:id/meeting-stats')
  @RequirePermission('kb:view')
  async meetingStats(@Param('id') id: string) {
    return this.meetings.stats(parseId(id));
  }

  // ---------------- 指标 ----------------

  @Get('boards/:code/metrics')
  @RequirePermission('kb:view')
  async metricsOf(@Param('code') code: string) {
    return this.metricSvc.boardMetrics(code);
  }

  /** BV-02 全局看板（R-EXEC；FR-M10-026） */
  @Get('executive')
  @RequirePermission('kb:view')
  async executive() {
    return this.execSvc.overview();
  }

  // ---------------- worker 内部扫描端点（内部令牌保护） ----------------

  @Post('internal/scans/redlight')
  async scanRedlight(@Body() body: { token?: string }) {
    this.assertInternal(body.token);
    return this.scans.redlightScan();
  }

  @Post('internal/scans/no-response')
  async scanNoResponse(@Body() body: { token?: string }) {
    this.assertInternal(body.token);
    return this.scans.noResponseScan();
  }

  @Post('internal/scans/meeting-remind')
  async scanMeetingRemind(@Body() body: { token?: string }) {
    this.assertInternal(body.token);
    return this.scans.meetingRemindScan();
  }

  @Post('internal/metrics/compute')
  async computeMetrics(@Body() body: { token?: string }) {
    this.assertInternal(body.token);
    return this.metricSvc.computeAll();
  }

  private assertInternal(token?: string) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (token !== expected) throw BizException.forbidden('内部端点，禁止外部调用');
  }

  private presentCard(c: {
    id: bigint;
    boardId: bigint;
    title: string;
    ownerId: bigint;
    dueDate: Date;
    status: string;
    blockedReason: string | null;
    nextAction: string | null;
    escalatedAt: Date | null;
    crossBoardId: bigint | null;
    crossFlag: string | null;
    sourceType: string | null;
    sourceId: string | null;
    closedAt: Date | null;
    updatedAt: Date;
  }) {
    return {
      id: c.id.toString(),
      boardId: c.boardId.toString(),
      title: c.title,
      ownerId: c.ownerId.toString(),
      dueDate: c.dueDate,
      status: c.status,
      blockedReason: c.blockedReason,
      nextAction: c.nextAction,
      escalated: !!c.escalatedAt,
      crossBoardId: c.crossBoardId?.toString() ?? null,
      crossFlag: c.crossFlag,
      sourceType: c.sourceType,
      sourceId: c.sourceId,
      closedAt: c.closedAt,
      updatedAt: c.updatedAt,
    };
  }
}
