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
