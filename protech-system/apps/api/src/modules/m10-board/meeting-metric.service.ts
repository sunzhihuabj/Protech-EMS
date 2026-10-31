import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** 08 册 §六.3 会议模式（FR-M10-018~021）：30 分钟四段议程 + 三问过堂 + 自动纪要 */
export const AGENDA_SEGMENTS = [
  { name: '指标速览', from: 0, to: 3 },
  { name: '红灯过堂', from: 3, to: 22 },
  { name: '今日承诺', from: 22, to: 27 },
  { name: '上报协调', from: 27, to: 30 },
] as const;

@Injectable()
export class MeetingService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  /** 开启会议（默认拉入本板全部红灯卡） */
  async start(boardId: bigint, userId: bigint, attendees: bigint[]) {
    const board = await this.prisma.board.findFirst({ where: { id: boardId, deletedAt: null } });
    if (!board) throw BizException.notFound('看板不存在');
    const attendeeIds = (attendees.length ? attendees : [userId]).map((x) => x.toString());
    const meeting = await this.prisma.kbMeeting.create({
      data: {
        boardId,
        meetingDate: new Date(),
        attendees: JSON.stringify(attendeeIds),
        createdBy: userId,
      },
    });
    const blocked = await this.prisma.kbCard.findMany({
      where: { boardId, status: 'blocked', deletedAt: null },
      orderBy: [{ escalatedAt: 'desc' }, { blockedAt: 'asc' }],
    });
    return {
      id: meeting.id.toString(),
      boardCode: board.boardCode,
      agenda: AGENDA_SEGMENTS,
      redCards: blocked.map((c) => ({ id: c.id.toString(), title: c.title, blockedReason: c.blockedReason })),
    };
  }

  /** 三问过堂：卡在哪/谁解决/何时解决（可跳过但记"未闭环过堂"） */
  async ask(meetingId: bigint, cardId: bigint, body: { askWhere?: string; askWho?: string; askWhen?: string; skip?: boolean }, userId: bigint) {
    const meeting = await this.prisma.kbMeeting.findFirst({ where: { id: meetingId } });
    if (!meeting) throw BizException.notFound('会议不存在');
    if (!body.skip && (!body.askWhere || !body.askWho || !body.askWhen)) {
      throw BizException.validation('三问未填全（卡在哪/谁解决/何时解决），不允许过下一张（FR-M10-019）');
    }
    const item = await this.prisma.kbMeetingItem.upsert({
      where: { meetingId_cardId: { meetingId, cardId } },
      create: {
        meetingId,
        cardId,
        askWhere: body.skip ? null : body.askWhere,
        askWho: body.skip ? null : body.askWho,
        askWhen: body.skip ? null : body.askWhen,
        skipped: body.skip === true,
      },
      update: {
        askWhere: body.skip ? null : body.askWhere,
        askWho: body.skip ? null : body.askWho,
        askWhen: body.skip ? null : body.askWhen,
        skipped: body.skip === true,
      },
    });
    void userId;
    return { id: item.id.toString() };
  }

  /** 结束会议 → 自动生成纪要（参会人/红灯数/过堂结论/今日承诺/上报事项 FR-M10-020） */
  async finish(meetingId: bigint, userId: bigint) {
    const meeting = await this.prisma.kbMeeting.findFirst({ where: { id: meetingId } });
    if (!meeting) throw BizException.notFound('会议不存在');
    const items = await this.prisma.kbMeetingItem.findMany({ where: { meetingId } });
    const closedCards = await this.prisma.kbCardLog.findMany({
      where: { cardId: { in: items.map((i) => i.cardId) }, toStatus: 'done', movedAt: { gte: meeting.startedAt } },
    });
    const minutes = {
      attendees: JSON.parse(meeting.attendees || '[]'),
      redTotal: items.length,
      askedTotal: items.filter((i) => !i.skipped).length,
      skippedTotal: items.filter((i) => i.skipped).length,
      closedToday: closedCards.length,
      items: items.map((i) => ({
        cardId: i.cardId.toString(),
        askWhere: i.askWhere,
        askWho: i.askWho,
        askWhen: i.askWhen,
        skipped: i.skipped,
        conclusion: i.skipped ? '未闭环过堂' : `${i.askWho} 于 ${i.askWhen} 前解决`,
      })),
    };
    const updated = await this.prisma.kbMeeting.update({
      where: { id: meetingId },
      data: { minutes: JSON.stringify(minutes), endedAt: new Date() },
    });
    await this.audit.log({ action: 'meeting_finish', objectType: 'KbMeeting', objectId: meetingId, after: { redTotal: minutes.redTotal } });
    void userId;
    void updated;
    return minutes;
  }

  /** 会议统计（FR-M10-021） */
  async stats(boardId: bigint) {
    const meetings = await this.prisma.kbMeeting.findMany({
      where: { boardId },
      orderBy: { meetingDate: 'desc' },
      take: 20,
    });
    const withItems = await Promise.all(
      meetings.map(async (m) => {
        const items = await this.prisma.kbMeetingItem.findMany({ where: { meetingId: m.id } });
        const durationMin = m.endedAt ? (m.endedAt.getTime() - m.startedAt.getTime()) / 60000 : null;
        return {
          id: m.id.toString(),
          date: m.meetingDate,
          durationMin: durationMin === null ? null : Math.round(durationMin),
          redTotal: items.length,
          askedTotal: items.filter((i) => !i.skipped).length,
          closeRate: items.length ? Number((items.filter((i) => !i.skipped).length / items.length).toFixed(4)) : null,
        };
      }),
    );
    return withItems;
  }
}

/** 指标引擎（FR-M10-004~006）：S2 落 3 个真实计算器，其余 empty（无数据→"—"） */
export const METRIC_DEFS = [
  { metricCode: 'redlight_total', boardCode: 'exec', name: '红灯总数', formula: 'redlight_total', unit: 'count', target: '下降', direction: 'reverse', source: 'auto' },
  { metricCode: 'redlight_close_rate', boardCode: 'exec', name: '红灯闭环率(7天)', formula: 'redlight_close_rate', unit: 'pct', target: '0.8', direction: 'forward', source: 'auto' },
  { metricCode: 'backfill_rate', boardCode: 'exec', name: '补录率', formula: 'backfill_rate', unit: 'pct', target: '0.1', direction: 'reverse', source: 'auto' },
  { metricCode: 'new_contract_wan', boardCode: 'exec', name: '新签合同额(万元/月)', formula: 'sql:new_contract_wan', unit: 'wan_cents', direction: 'forward', source: 'auto' },
  { metricCode: 'receipt_rate', boardCode: 'exec', name: '回款率', formula: 'sql:receipt_rate', unit: 'pct', target: '0.9', direction: 'forward', source: 'auto' },
  { metricCode: 'order_ontime', boardCode: 'order', name: '订单准时交付率', formula: 'sql:order_ontime', unit: 'pct', target: '0.95', direction: 'forward', source: 'auto' },
  { metricCode: 'kit_rate', boardCode: 'mfg', name: '开工齐套率', formula: 'sql:kit_rate', unit: 'pct', target: '0.9', direction: 'forward', source: 'auto' },
  { metricCode: 'po_ontime', boardCode: 'supply', name: '采购准交率', formula: 'sql:po_ontime', unit: 'pct', target: '0.9', direction: 'forward', source: 'auto' },
  { metricCode: 'inv_accuracy', boardCode: 'supply', name: '账实相符率', formula: 'sql:inv_accuracy', unit: 'pct', target: '0.95', direction: 'forward', source: 'auto' },
  { metricCode: 'disclosure_rate', boardCode: 'delivery', name: '方案交底及时率', formula: 'sql:disclosure_rate', unit: 'pct', target: '1', direction: 'forward', source: 'auto' },
  { metricCode: 'site_close_days', boardCode: 'delivery', name: '现场问题闭环时长(天)', formula: 'sql:site_close_days', unit: 'days', target: '7', direction: 'reverse', source: 'auto' },
  { metricCode: 'quote_win_rate', boardCode: 'market', name: '报价转化率', formula: 'sql:quote_win_rate', unit: 'pct', direction: 'forward', source: 'auto' },
  { metricCode: 'aging_overdue', boardCode: 'fin', name: '逾期应收(万元)', formula: 'sql:aging_overdue', unit: 'wan_cents', direction: 'reverse', source: 'auto' },
] as const;

@Injectable()
export class MetricService {
  constructor(private readonly prisma: PrismaService) {}

  /** 指标定义幂等预置 */
  async seedDefs() {
    for (const d of METRIC_DEFS) {
      await this.prisma.metricDef.upsert({
        where: { metricCode: d.metricCode },
        create: { ...d },
        update: {},
      });
    }
  }

  /** 计算全部指标并落快照（worker 每日 06:00 全量 / 每 2h 增量 FR-M10-004） */
  async computeAll(): Promise<{ ok: number; empty: number; error: number }> {
    await this.seedDefs();
    const defs = await this.prisma.metricDef.findMany({ where: { deletedAt: null } });
    const period = new Date().toISOString().slice(0, 10);
    let ok = 0;
    let empty = 0;
    let error = 0;
    for (const def of defs) {
      let status = 'ok';
      let value: string | null = null;
      try {
        const computed = await this.compute(def.formula, def.metricCode);
        if (computed === null) {
          status = 'empty';
          empty++;
        } else {
          value = String(computed);
          ok++;
        }
      } catch {
        // 失败置"数据异常"不影响其他指标（FR-M10-004）
        status = 'error';
        error++;
      }
      await this.prisma.metricSnapshot.upsert({
        where: { metricCode_period: { metricCode: def.metricCode, period } },
        create: { metricCode: def.metricCode, period, value, status },
        update: { value, status, computedAt: new Date() },
      });
    }
    return { ok, empty, error };
  }

  private async compute(formula: string, metricCode: string): Promise<number | null> {
    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    switch (formula) {
      case 'redlight_total': {
        const n = await this.prisma.kbCard.count({ where: { status: 'blocked', deletedAt: null } });
        return n;
      }
      case 'redlight_close_rate': {
        const total = await this.prisma.kbCard.count({
          where: { status: { in: ['blocked', 'done'] }, deletedAt: null, OR: [{ blockedAt: { not: null } }, { closedAt: { gte: new Date(now.getTime() - 7 * 86400000) } }] },
        });
        if (!total) return null;
        const closed = await this.prisma.kbCard.count({
          where: { status: 'done', deletedAt: null, closedAt: { gte: new Date(now.getTime() - 7 * 86400000) } },
        });
        return Number((closed / total).toFixed(4));
      }
      case 'backfill_rate': {
        const total = await this.prisma.kbCardLog.count({ where: { movedAt: { gte: monthStart } } });
        if (!total) return null;
        const bf = await this.prisma.kbCardLog.count({ where: { movedAt: { gte: monthStart }, isBackfill: true } });
        return Number((bf / total).toFixed(4));
      }
      case 'new_contract_wan': {
        const agg = await this.prisma.contract.aggregate({
          _sum: { amountCents: true },
          where: { status: { in: ['signed', 'performing', 'partial_invoiced'] }, sealAt: { gte: monthStart }, deletedAt: null },
        });
        const cents = agg._sum.amountCents ?? 0n;
        return Number((Number(cents) / 1_000_000).toFixed(2));
      }
      default:
        // 业务模块未上线（S3/S4）→ 无数据（前端"—"，FR-M10-004）
        void metricCode;
        return null;
    }
  }

  /** 板头指标读数（最近快照，P95<3s 走快照不现算） */
  async boardMetrics(boardCode: string) {
    const defs = await this.prisma.metricDef.findMany({ where: { boardCode, deletedAt: null } });
    const snaps = await this.prisma.metricSnapshot.findMany({
      where: { metricCode: { in: defs.map((d) => d.metricCode) } },
      orderBy: { computedAt: 'desc' },
    });
    return defs.map((d) => {
      const snap = snaps.find((s) => s.metricCode === d.metricCode);
      return {
        metricCode: d.metricCode,
        name: d.name,
        unit: d.unit,
        target: d.target,
        direction: d.direction,
        source: d.source,
        period: snap?.period ?? null,
        value: snap?.value ?? null, // null → 前端"—"
        status: snap?.status ?? 'empty',
        computedAt: snap?.computedAt ?? null,
      };
    });
  }

  /** 状态灯判定（FR-M10-005：绿≥目标；黄 80—99%；红<80%；反向反向判定） */
  lightOf(value: number | null, target: string | null | undefined, direction: string): 'green' | 'yellow' | 'red' | 'none' {
    if (value === null) return 'none';
    const t = Number(target);
    if (!Number.isFinite(t) || t === 0) return 'none';
    const ratio = direction === 'reverse' ? (value <= 0 ? 1 : t / value) : value / t;
    if (ratio >= 1) return 'green';
    if (ratio >= 0.8) return 'yellow';
    return 'red';
  }
}

/** BV-02 全局看板聚合（FR-M10-026，08 册 §四五区） */
@Injectable()
export class ExecutiveService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly metrics: MetricService,
  ) {}

  async overview() {
    const [metrics, boards, blockedCards] = await Promise.all([
      this.metrics.boardMetrics('exec'),
      this.prisma.board.findMany({ where: { deletedAt: null }, orderBy: { boardCode: 'asc' } }),
      this.prisma.kbCard.findMany({
        where: { status: 'blocked', deletedAt: null },
        select: { id: true, boardId: true, title: true, blockedAt: true, escalatedAt: true, crossBoardId: true, respondedAt: true, createdAt: true },
      }),
    ]);
    const now = Date.now();
    const redByBoard = boards.map((b) => ({
      boardCode: b.boardCode,
      name: b.name,
      red: blockedCards.filter((c) => c.boardId === b.id).length,
    }));
    const escalated = blockedCards.filter((c) => c.escalatedAt).length;
    const noResponse = blockedCards.filter((c) => c.crossBoardId && !c.respondedAt && now - c.createdAt.getTime() > 48 * 3600000).length;
    const health = await Promise.all(
      boards.map(async (b) => {
        const lastMeeting = await this.prisma.kbMeeting.findFirst({
          where: { boardId: b.id },
          orderBy: { meetingDate: 'desc' },
          select: { meetingDate: true },
        });
        return {
          boardCode: b.boardCode,
          name: b.name,
          type: b.type,
          red: blockedCards.filter((c) => c.boardId === b.id).length,
          lastMeetingAt: lastMeeting?.meetingDate ?? null,
        };
      }),
    );
    return {
      metrics,
      redTotal: blockedCards.length,
      escalated,
      noResponse,
      redByBoard,
      health,
      // 风险预警区（齐套/呆滞/账龄）随 S3/S4 模块上线逐项接入（FR-M10-006 下钻同源）
      risks: [],
    };
  }
}
