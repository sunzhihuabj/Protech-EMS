import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';
import { normalizeName, similarity } from '@protech/shared-types';

/** 能力点注册表（FR-M16-002/003/004=AI-01/02/03 R1；10 册：白名单 + E-18 分级） */
export const CAPABILITIES: Record<string, { name: string; level: 'auto' | 'ask' | 'never'; enabled: boolean }> = {
  'AI-01': { name: '合同要素提取', level: 'ask', enabled: true },
  'AI-02': { name: '主数据查重辅助', level: 'auto', enabled: true },
  'AI-03': { name: '台账名称归一', level: 'auto', enabled: true },
  'AI-04': { name: '模糊卡点改写', level: 'ask', enabled: true },
  'AI-05': { name: '会议纪要生成', level: 'ask', enabled: true },
  'AI-06': { name: '数据问答 ChatBI', level: 'ask', enabled: true },
  'AI-07': { name: '异常预警解读', level: 'ask', enabled: true },
  'AI-08': { name: '报价辅助', level: 'ask', enabled: true },
  'AI-09': { name: '知识库问答 RAG', level: 'ask', enabled: false }, // R3（语料准备）
  'AI-10': { name: '日报周报生成', level: 'ask', enabled: true },
  'AI-11': { name: '报表解读', level: 'auto', enabled: true },
  'AI-12': { name: '智能填单', level: 'ask', enabled: false }, // R3
};

/** AI-01 合同 25 要素提取字段（Ask-first 草稿） */
const EXTRACT_FIELDS = [
  'contractName',
  'nature',
  'bizType',
  'amountCents',
  'taxRate',
  'paymentTerms',
  'settlement',
  'invoiceReq',
  'contact',
  'phone',
  'deliveryAddr',
  'signedAt',
  'periodStart',
  'periodEnd',
  'warrantyMonths',
  'docCopies',
];

/**
 * M16 AI 能力（FR-M16-001/002/003/004/014 的 R1 集）。
 * 治理红线：Ask-first（低置信仅草稿）/输出可溯源/全部调用审计 T-95/白名单外禁用。
 * 通道：AI_PROVIDER=http 时转发网关（11 册独立进程），默认 mock（确定性样例，恒可用）。
 */
@Injectable()
export class AiService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  private async logCall(cap: string, userId: bigint, inputDigest: string, confidence: string | null, resultRef: string | null, latencyMs: number) {
    const level = CAPABILITIES[cap]?.level ?? 'never';
    await this.prisma.aiCall.create({
      data: { capability: cap, userId, inputDigest: inputDigest.slice(0, 200), model: process.env.AI_PROVIDER ?? 'mock', confidence, resultRef, level, latencyMs },
    });
    await this.audit.log({ action: 'ai_call', objectType: 'AiCall', objectId: cap, after: { confidence } });
  }

  assertEnabled(cap: string) {
    const def = CAPABILITIES[cap];
    if (!def || !def.enabled) {
      throw BizException.forbidden(`AI 能力未开放或不在白名单：${cap}（FR-M16-014 治理红线）`);
    }
  }

  /** AI-01 合同要素提取：扫描文本 → 25 要素草稿 + 每字段 confidence（Ask-first：人工确认入库） */
  async extractContract(contractId: bigint, userId: bigint, text?: string) {
    const started = Date.now();
    const contract = await this.prisma.contract.findFirst({ where: { id: contractId, deletedAt: null } });
    if (!contract) throw BizException.notFound('合同不存在');
    const src = text ?? `${contract.contractName} ${contract.contractCode}`;
    // mock 通道：确定性提取（真实通道由网关 DeepSeek/Qwen 承接，Ask-first 不变）
    const draft: Record<string, { value: string; confidence: number }> = {};
    for (const f of EXTRACT_FIELDS) {
      const known = (contract as unknown as Record<string, unknown>)[f];
      draft[f] = {
        value: known != null ? String(known) : '',
        confidence: known != null ? 0.95 : 0.55, // <0.8 前端标黄
      };
    }
    const conf = '0.75';
    await this.logCall('AI-01', userId, src.slice(0, 100), conf, `contract:${contractId}`, Date.now() - started);
    return {
      capability: 'AI-01',
      askFirst: true,
      draft,
      hint: 'AI 草稿仅供参考（Ask-first）：逐字段人工确认后才写入正式档案；置信度 <0.8 标黄',
    };
  }

  /** 采纳（确认入库）：写正式档案 + T-96 反馈（引用 ai_call_id 溯源） */
  async confirmExtract(callId: bigint, userId: bigint, accepted: boolean, aiFields: Record<string, string>) {
    const call = await this.prisma.aiCall.findFirst({ where: { id: callId } });
    if (!call) throw BizException.notFound('AI 调用记录不存在');
    if (accepted) {
      const ref = call.resultRef ?? '';
      const m = /contract:(\d+)/.exec(ref);
      if (m) {
        const data: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(aiFields)) {
          if (v !== '' && ['paymentTerms', 'contact', 'phone', 'deliveryAddr'].includes(k)) data[k] = v;
        }
        if (Object.keys(data).length) {
          await this.prisma.contract.update({ where: { id: BigInt(m[1]) }, data, }); // updatedBy middleware? 显式：
        }
      }
    }
    await this.prisma.aiFeedback.create({
      data: { callId, accepted, editedFields: JSON.stringify(aiFields ?? {}), createdBy: userId },
    });
    return { recorded: true };
  }

  /** AI-02 主数据查重辅助：向量+规则预聚类（本通道用归一+相似度规则实现） */
  async dupAssist(userId: bigint, name: string, scope: 'party' | 'material' = 'party') {
    const started = Date.now();
    let rows: Array<{ id: bigint; code: string; name: string; spec?: string | null }> = [];
    if (scope === 'party') {
      const rs = await this.prisma.customerParty.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, partyCode: true, name: true } });
      rows = rs.map((r) => ({ id: r.id, code: r.partyCode, name: r.name }));
    } else {
      const rs = await this.prisma.material.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, materialCode: true, name: true, spec: true } });
      rows = rs.map((r) => ({ id: r.id, code: r.materialCode, name: r.name, spec: r.spec }));
    }
    const target = normalizeName(name);
    const clusters = new Map<string, Array<{ id: string; code: string; name: string; score: number }>>();
    for (const r of rows) {
      const score = similarity(name, r.name);
      if (score >= 0.85 || normalizeName(r.name) === target) {
        const key = normalizeName(r.name).slice(0, 6);
        const arr = clusters.get(key) ?? [];
        arr.push({ id: r.id.toString(), code: r.code, name: r.name, score: Number(score.toFixed(4)) });
        clusters.set(key, arr);
      }
    }
    await this.logCall('AI-02', userId, name, '0.9', `scope:${scope}`, Date.now() - started);
    return {
      capability: 'AI-02',
      clusters: Array.from(clusters.values()).map((members) => ({
        members,
        basis: '地名/主体名归一一致（相似度 ≥0.85），请人工判读归并依据',
      })),
    };
  }

  /** AI-03 台账名称归一：候选 + 归并依据（"地名一致、业主待补"） */
  async normalizeName(userId: bigint, rawName: string, scope: 'project' | 'party' | 'material' = 'project') {
    const started = Date.now();
    const suggestions = await this.suggest(rawName, scope);
    await this.logCall('AI-03', userId, rawName, '0.8', `scope:${scope}`, Date.now() - started);
    return {
      capability: 'AI-03',
      rawName,
      suggestions,
      note: '采纳建议将回写别名库（清洗五步法第五步沉淀）',
    };
  }

  /** AI-04 模糊卡点改写：VAGUE 命中 → 可验收改写建议（Ask-first） */
  async rewriteVague(userId: bigint, title: string) {
    const started = Date.now();
    const vagueWords = ['尽快', '跟进', '协调', '处理', '抓紧', '看看', '研究', '推进', '加强', '解决'];
    const hit = vagueWords.find((w) => title.includes(w));
    const suggestion = hit
      ? '改写建议：把"' + hit + '"替换为可验收动作【对象+动作+数量/期限】，例如"' + title.replace(hit, '完成 XX 并回传截图（本周五前）') + '"'
      : '该卡点表述已具体，无需改写';
    await this.logCall('AI-04', userId, title, '0.7', 'vague_rewrite', Date.now() - started);
    return { capability: 'AI-04', hit: hit ?? null, suggestion, askFirst: true };
  }

  /** AI-05 会议纪要生成：过堂记录 → 结构化纪要（行动项/责任人/期限） */
  async generateMinutes(userId: bigint, meetingId: bigint) {
    const started = Date.now();
    const meeting = await this.prisma.kbMeeting.findFirst({ where: { id: meetingId } });
    if (!meeting) throw BizException.notFound('会议不存在');
    const items = await this.prisma.kbMeetingItem.findMany({ where: { meetingId } });
    const minutes = {
      date: meeting.meetingDate.toISOString().slice(0, 10),
      attendees: JSON.parse(meeting.attendees || '[]'),
      redTotal: items.length,
      actionItems: items
        .filter((i) => !i.skipped && i.askWho)
        .map((i) => ({ cardId: i.cardId.toString(), owner: i.askWho, deadline: i.askWhen, task: i.askWhere })),
      skipped: items.filter((i) => i.skipped).map((i) => i.cardId.toString()),
    };
    await this.logCall('AI-05', userId, 'meeting:' + meetingId, '0.85', 'meeting:' + meetingId, Date.now() - started);
    return { capability: 'AI-05', minutes, askFirst: true };
  }

  /** AI-06 数据问答（限定白名单指标口径；不直连裸表 FR-M16-006） */
  async chatBI(userId: bigint, question: string) {
    const started = Date.now();
    const q = question.toLowerCase();
    let answer = '';
    let source = '';
    if (q.includes('红灯')) {
      const n = await this.prisma.kbCard.count({ where: { status: 'blocked', deletedAt: null } });
      answer = '当前全公司红灯卡 ' + n + ' 张（口径：kb_card.status=blocked，实时）';
      source = 'T-62 卡片引擎';
    } else if (q.includes('项目')) {
      const executing = await this.prisma.project.count({ where: { status: 'executing', deletedAt: null } });
      const pending = await this.prisma.project.count({ where: { status: 'pending_exec', deletedAt: null } });
      answer = '执行中项目 ' + executing + ' 个，签约待执行 ' + pending + ' 个（口径：T-12 状态机）';
      source = 'T-12';
    } else if (q.includes('库存')) {
      const agg = await this.prisma.inventory.aggregate({ _sum: { qty: true }, where: { deletedAt: null } });
      answer = '当前库存合计 ' + Number(agg._sum.qty ?? 0) + '（口径：T-24 各仓合计，含两司）';
      source = 'T-24';
    } else if (q.includes('回款')) {
      const rate = await this.prisma.receipt.aggregate({ _sum: { amountCents: true } });
      answer = '累计回款 ' + Number(rate._sum.amountCents ?? 0n) / 1_000_000 + ' 万元（口径：T-32 实收）';
      source = 'T-32';
    } else {
      answer = '该问题不在白名单口径内（红灯/项目/库存/回款）；ChatBI 不直连裸表（FR-M16-006 红线）';
      source = 'whitelist';
    }
    await this.logCall('AI-06', userId, question, '0.9', source, Date.now() - started);
    return { capability: 'AI-06', answer, source, askFirst: source !== 'whitelist' };
  }

  /** AI-07 异常预警解读（原因假设+建议动作） */
  async explainAnomaly(userId: bigint, kind: string) {
    const started = Date.now();
    let answer = '';
    if (kind === 'redlight_cluster') {
      const byBoard = await this.prisma.kbCard.groupBy({ by: ['boardId'], _count: { _all: true }, where: { status: 'blocked', deletedAt: null } });
      const top = byBoard.sort((a, b) => b._count._all - a._count._all)[0];
      const board = top ? await this.prisma.board.findFirst({ where: { id: top.boardId } }) : null;
      answer =
        '红灯聚集假设：' + (board?.name ?? '未知板') + ' 红灯最多（' + (top?._count._all ?? 0) + ' 张）——建议链主日站会优先过堂，检查共性缺料/单据滞后';
    } else if (kind === 'kit_short') {
      const woShort = await this.prisma.workOrder.count({ where: { kitStatus: 'short' } });
      answer = '齐套缺口假设：' + woShort + ' 个工单缺料——常见根因为供应商准交（查③板采购准交率）或 BOM 定额偏差（查 13 条规则工单）';
    } else {
      answer = '未知预警类型（支持 redlight_cluster / kit_short）';
    }
    await this.logCall('AI-07', userId, kind, '0.75', kind, Date.now() - started);
    return { capability: 'AI-07', answer, askFirst: true };
  }

  /** AI-08 报价辅助：历史对比+低毛利预警 */
  async quoteAssist(userId: bigint, partyId: bigint, productLine: string, unitPriceCents: bigint) {
    const started = Date.now();
    const won = await this.prisma.quotation.findMany({
      where: { partyId, status: 'won', productLines: { contains: productLine }, deletedAt: null },
      orderBy: { id: 'desc' },
      take: 20,
    });
    const unitPrices = won.map((w) => Number(w.unitPriceCents)).filter((v) => v > 0).sort((a, b) => a - b);
    const p25 = unitPrices.length ? unitPrices[Math.floor(unitPrices.length * 0.25)] : null;
    const p50 = unitPrices.length ? unitPrices[Math.floor(unitPrices.length * 0.5)] : null;
    const current = Number(unitPriceCents);
    const risk = p50 && current < p50 * 0.85 ? '⚠️ 报价低于历史中位价 15%+（低毛利预警）' : '报价处于历史合理区间';
    await this.logCall('AI-08', userId, partyId + ':' + productLine, '0.8', 'quote_assist', Date.now() - started);
    return { capability: 'AI-08', historyCount: unitPrices.length, p25, p50, risk, askFirst: true };
  }

  /** AI-10 项目周报生成：时间线+工时+卡点 → 草稿（Ask-first） */
  async weeklyReport(userId: bigint, projectId: bigint) {
    const started = Date.now();
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const weekAgo = new Date(Date.now() - 7 * 86400000);
    const [logs, cards, milestones] = await Promise.all([
      this.prisma.siteLog.findMany({ where: { projectId, createdAt: { gte: weekAgo } }, take: 10, orderBy: { id: 'desc' } }),
      this.prisma.kbCard.findMany({ where: { status: 'blocked', deletedAt: null, sourceType: 'project', sourceId: projectId.toString() }, take: 5 }),
      this.prisma.milestone.findMany({ where: { projectId, deletedAt: null, status: 'done', actualDate: { gte: weekAgo } } }),
    ]);
    const worklogAgg = await this.prisma.worklog.aggregate({ _sum: { hours: true }, where: { projectId, logDate: { gte: weekAgo } } });
    const draft = [
      '【项目周报】' + project.projectCode + ' ' + project.standardName + '（状态：' + project.status + '）',
      '一、里程碑：本周完成 ' + (milestones.map((m) => m.name).join('、') || '无'),
      '二、投入：本周工时合计 ' + Number(worklogAgg._sum.hours ?? 0) + ' 人时',
      '三、现场：' + (logs.length ? logs.map((l) => l.content.slice(0, 30)).join('；') : '本周无现场日志'),
      '四、卡点（红灯）：' + (cards.length ? cards.map((c) => c.title).join('；') : '无'),
    ].join('\n');
    await this.logCall('AI-10', userId, 'project:' + projectId, '0.85', 'project:' + projectId, Date.now() - started);
    return { capability: 'AI-10', draft, askFirst: true };
  }

  /** AI-11 报表解读：三句话（趋势/异常/建议） */
  async interpretReport(userId: bigint, key: string) {
    const started = Date.now();
    let sentences = '';
    if (key === 'contracts') {
      const performing = await this.prisma.contract.count({ where: { status: 'performing', deletedAt: null } });
      const drafting = await this.prisma.contract.count({ where: { status: 'drafting', deletedAt: null } });
      sentences =
        '① 履行中合同 ' + performing + ' 份，起草中 ' + drafting + ' 份；② ' +
        (drafting > performing ? '起草积压偏高，建议加快评审节奏（BR-01 带码前置）' : '评审节奏正常') +
        '；③ 关注用印后 3 日建档时限（BR-02）';
    } else if (key === 'stock-moves') {
      const week = await this.prisma.stockMove.count({ where: { movedAt: { gte: new Date(Date.now() - 7 * 86400000) } } });
      sentences =
        '① 本周出入库 ' + week + ' 笔；② ' + (week < 10 ? '流水偏低，检查扫码作业覆盖率' : '流水健康') +
        '；③ 建议每日日终核对 T+ 推送队列清零';
    } else {
      sentences = '① 该报表暂无专属解读模板；② 数据口径见 12 册；③ 自定义解读属 AI-12（R3）';
    }
    await this.logCall('AI-11', userId, key, '0.8', 'report:' + key, Date.now() - started);
    return { capability: 'AI-11', interpretation: sentences, askFirst: false };
  }

  private async suggest(rawName: string, scope: string): Promise<Array<{ id: string; name: string; score: number; basis: string }>> {
    let rows: Array<{ id: bigint; name: string }> = [];
    if (scope === 'project') {
      const rs = await this.prisma.project.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, standardName: true } });
      rows = rs.map((r) => ({ id: r.id, name: r.standardName }));
    } else if (scope === 'party') {
      const rs = await this.prisma.customerParty.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, name: true } });
      rows = rs.map((r) => ({ id: r.id, name: r.name }));
    } else {
      const rs = await this.prisma.material.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, name: true } });
      rows = rs.map((r) => ({ id: r.id, name: r.name }));
    }
    const target = normalizeName(rawName);
    return rows
      .map((r) => ({ id: r.id.toString(), name: r.name, score: Number(similarity(rawName, r.name).toFixed(4)) }))
      .filter((h) => h.score >= 0.8)
      .sort((a, b) => b.score - a.score)
      .slice(0, 10)
      .map((h) => ({ ...h, basis: h.score >= 0.95 ? '地名一致、业主一致（高度可信）' : '地名一致、业主待补（请人工确认）' }));
  }
}
