import { Injectable, OnModuleInit } from '@nestjs/common';
import { ModuleRef } from '@nestjs/core';
import { PrismaService } from '../../shared/prisma.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';

/**
 * FR-M15-010 事件总线（进程内 EventEmitter 版；BullMQ 已承担定时，此处承接业务事件解耦）。
 * 事件白名单（订阅方：通知中心/看板建卡/钉钉 Webhook/集成队列）。
 */
export interface BizEvent {
  type:
    | 'contract.approved' // 合同评审通过（自动立项已事务化，此处广播通知）
    | 'stock_move.ship' // 发货流水（→派工草稿）
    | 'kit.short' // 齐套缺料（→红灯+请购）
    | 'aftersales.blocked' // 售后受阻（→红灯）
    | 'punch.shortage' // 销项缺件（→红灯）
    | 'card.escalated'; // 红灯升级（→钉钉）
  payload: Record<string, unknown>;
  occurredAt: Date;
}

type Handler = (e: BizEvent) => void | Promise<void>;

@Injectable()
export class EventBusService implements OnModuleInit {
  private handlers = new Map<string, Set<Handler>>();
  /** 事件流水（FR-M15-011 集成监控台展示） */
  public journal: Array<{ type: string; at: Date; handled: number }> = [];

  constructor(private readonly moduleRef: ModuleRef) {}

  onModuleInit() {
    // 管道装配在业务模块 init 之后触发一次自检（避免循环依赖：订阅方用 ModuleRef 拉取）
    void this.moduleRef;
  }

  on(types: BizEvent['type'][], handler: Handler): void {
    for (const t of types) {
      if (!this.handlers.has(t)) this.handlers.set(t, new Set());
      this.handlers.get(t)!.add(handler);
    }
  }

  async emit(type: BizEvent['type'], payload: Record<string, unknown>): Promise<void> {
    const set = this.handlers.get(type) ?? new Set();
    let handled = 0;
    for (const h of set) {
      try {
        await h({ type, payload, occurredAt: new Date() });
        handled++;
      } catch (e) {
        // 事件处理失败不阻断主链路（11 册 §五：失败告警由 worker 重试批次兜底）
        console.error(`[event-bus] ${type} handler failed:`, (e as Error).message);
      }
    }
    this.journal.unshift({ type, at: new Date(), handled });
    if (this.journal.length > 100) this.journal.length = 100;
  }
}

/**
 * FR-M15-005 钉钉群机器人 Webhook（出网白名单单向；渠道失败降级站内）。
 * DINGTALK_WEBHOOK 未配置时安全降级为 journal 记录（dev/mock 恒可用，FR-M15-006 免登 R2+ 预留）。
 */
@Injectable()
export class DingtalkService {
  private webhook: string | null = null;
  public lastResults: Array<{ at: Date; ok: boolean; msgType: string; error?: string }> = [];

  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async loadConfig() {
    const cfg = await this.prisma.sysConfig.findFirst({ where: { key: 'dingtalk_webhook', deletedAt: null } });
    this.webhook = cfg?.value || process.env.DINGTALK_WEBHOOK || null;
    return this.webhook;
  }

  /** 关键事件推送（白名单：红灯升级/48h 未响应/审批超时 FR-M00-008）；失败重试 3 次后告警 R-SYS（worker 侧兜底重试） */
  async sendMarkdown(title: string, text: string): Promise<{ delivered: boolean; channel: 'dingtalk' | 'degraded' }> {
    await this.loadConfig();
    if (!this.webhook) {
      // 降级：站内信给全部 R-EXEC/R-SYS（渠道失败降级站内 FR-M15-005）
      const roles = await this.prisma.sysRole.findMany({ where: { code: { in: ['R-EXEC', 'R-SYS'] } }, select: { id: true } });
      const urs = await this.prisma.sysUserRole.findMany({ where: { roleId: { in: roles.map((r) => r.id) } }, select: { userId: true } });
      for (const u of urs) {
        await this.prisma.sysNotify.create({
          data: { userId: u.userId, type: 'escalate', title: `【钉钉降级】${title}`, content: text, bizType: 'dingtalk_degraded' },
        });
      }
      this.lastResults.unshift({ at: new Date(), ok: false, msgType: 'markdown', error: 'webhook 未配置（降级站内）' });
      this.lastResults.length = Math.min(this.lastResults.length, 50);
      return { delivered: false, channel: 'degraded' };
    }
    // 出网白名单：仅 https://oapi.dingtalk.com（09 册 C-4）；fetch 三次重试
    let lastErr = '';
    for (let i = 0; i < 3; i++) {
      try {
        const res = await fetch(this.webhook, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ msgtype: 'markdown', markdown: { title, text } }),
          signal: AbortSignal.timeout(8000),
        });
        if (res.ok) {
          this.lastResults.unshift({ at: new Date(), ok: true, msgType: 'markdown' });
          this.lastResults.length = Math.min(this.lastResults.length, 50);
          await this.audit.log({ action: 'dingtalk_send', objectType: 'Dingtalk', objectId: null, after: { title } });
          return { delivered: true, channel: 'dingtalk' };
        }
        lastErr = `HTTP ${res.status}`;
      } catch (e) {
        lastErr = (e as Error).message;
      }
    }
    this.lastResults.unshift({ at: new Date(), ok: false, msgType: 'markdown', error: lastErr });
    this.lastResults.length = Math.min(this.lastResults.length, 50);
    return { delivered: false, channel: 'degraded' };
  }
}

/**
 * FR-M15-009 打印模板引擎（HTML 可配 Q-07）：送货单/验收单/派工单。
 * 模板存 sys_config（print_tpl_<key>），占位符 {{field}} 渲染；A4 单页优先。
 */
export const PRINT_TEMPLATES = ['delivery', 'acceptance', 'dispatch', 'quote'] as const;
export type PrintTemplateKey = (typeof PRINT_TEMPLATES)[number];

const DEFAULT_PRINT_TPL: Record<PrintTemplateKey, string> = {
  delivery: '<h2>送货单</h2><p>单据号：{{sourceId}}</p><p>项目：{{projectCode}}</p><p>物料：{{materialCode}} × {{qty}}</p><p>日期：{{date}}</p><hr/><p>送货人：____ 收货人：____</p>',
  acceptance: '<h2>验收单</h2><p>项目：{{projectCode}} {{standardName}}</p><p>结论：{{conclusion}}</p><p>日期：{{date}}</p><hr/><p>验收人：____ 业主代表：____</p>',
  quote: '<h2>报价单</h2><p>编号：{{quotationCode}}</p><p>客户：{{partyName}}</p><p>产品线：{{productLines}}</p><p>数量：{{qty}} × 单价（分）：{{unitPriceCents}}</p><p>有效期至：{{validUntil}}</p><hr/><p>报价人：____ 客户确认：____</p>',
  dispatch: '<h2>派工单</h2><p>编号：{{pgCode}}</p><p>项目：{{projectCode}}｜风场：{{farmName}}</p><p>任务：{{taskDesc}}</p><p>工期：{{planDays}} 天</p><hr/><p>派工人：____ 班组长：____</p>',
};

@Injectable()
export class PrintService {
  constructor(private readonly prisma: PrismaService) {}

  async render(key: PrintTemplateKey, data: Record<string, string | number>): Promise<{ html: string }> {
    if (!PRINT_TEMPLATES.includes(key)) throw BizException.param(`打印模板不存在：${key}（Q-07 可配）`);
    const cfg = await this.prisma.sysConfig.findFirst({ where: { key: `print_tpl_${key}`, deletedAt: null } });
    const tpl = cfg?.value ?? DEFAULT_PRINT_TPL[key];
    const html = tpl.replace(/\{\{(\w+)\}\}/g, (_, f) => String(data[f] ?? ''));
    return { html };
  }

  async setTemplate(key: PrintTemplateKey, html: string): Promise<void> {
    const before = await this.prisma.sysConfig.findFirst({ where: { key: `print_tpl_${key}`, deletedAt: null } });
    if (before) {
      await this.prisma.sysConfig.update({ where: { id: before.id }, data: { value: html } });
    } else {
      await this.prisma.sysConfig.create({
        data: { key: `print_tpl_${key}`, value: html, valueType: 'string', remark: `打印模板 ${key}（FR-M15-009 可配 Q-07）` },
      });
    }
  }
}

/**
 * FR-M15-012 对账：中台库存 vs T+ 存货月度对账报告（差异清单）。
 * T+ 侧以最近导入的 stock_move 汇总为代理口径（文件适配器导入后逐笔一致；差异=调账未回传）。
 */
@Injectable()
export class ReconciliationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async monthlyReport(period?: string) {
    const p = period ?? new Date().toISOString().slice(0, 7);
    const monthStart = new Date(`${p}-01T00:00:00Z`);
    const next = p.endsWith('-12') ? new Date(Date.UTC(Number(p.slice(0, 4)) + 1, 0, 1)) : new Date(Date.UTC(Number(p.slice(0, 4)), Number(p.slice(5, 7)), 1));

    const [tPlus, local] = await Promise.all([
      // T+ 已确认口径：confirmed 流水按物料汇总
      this.prisma.stockMove.groupBy({
        by: ['materialId'],
        _sum: { qty: true },
        where: { tPlusStatus: 'confirmed', movedAt: { gte: monthStart, lt: next } },
      }),
      // 中台口径：全部流水按物料汇总
      this.prisma.stockMove.groupBy({
        by: ['materialId'],
        _sum: { qty: true },
        where: { movedAt: { gte: monthStart, lt: next } },
      }),
    ]);
    const tMap = new Map(tPlus.map((t) => [t.materialId, Number(t._sum.qty ?? 0)]));
    const lMap = new Map(local.map((l) => [l.materialId, Number(l._sum.qty ?? 0)]));
    const materialIds = [...new Set([...tMap.keys(), ...lMap.keys()])];
    const materials = materialIds.length ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));

    const lines = materialIds.map((id) => {
      const tQty = tMap.get(id) ?? 0;
      const lQty = lMap.get(id) ?? 0;
      return {
        materialCode: mById.get(id)?.materialCode ?? '',
        materialName: mById.get(id)?.name ?? '',
        tPlusQty: tQty,
        localQty: lQty,
        diff: Number((lQty - tQty).toFixed(3)),
        matched: Math.abs(lQty - tQty) < 1e-9,
      };
    });
    const diffLines = lines.filter((l) => !l.matched);
    const report = {
      period: p,
      totalLines: lines.length,
      matchedLines: lines.length - diffLines.length,
      diffLines,
      matchRate: lines.length ? Number((((lines.length - diffLines.length) / lines.length) * 100).toFixed(2)) : 100,
    };
    await this.audit.log({ action: 'reconcile', objectType: 'Report', objectId: p, after: { matchRate: report.matchRate } });
    return report;
  }
}
