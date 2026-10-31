import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';
import { normalizeName, similarity } from '@protech/shared-types';

/** 能力点注册表（10 册：白名单 + E-18 分级；R1 上 AI-01/02/03） */
export const CAPABILITIES: Record<string, { name: string; level: 'auto' | 'ask' | 'never'; enabled: boolean }> = {
  'AI-01': { name: '合同要素提取', level: 'ask', enabled: true },
  'AI-02': { name: '主数据查重辅助', level: 'auto', enabled: true },
  'AI-03': { name: '台账名称归一', level: 'auto', enabled: true },
  'AI-04': { name: '模糊卡点改写', level: 'ask', enabled: false }, // R2
  'AI-05': { name: '会议纪要生成', level: 'ask', enabled: false }, // R2
  'AI-06': { name: '数据问答 ChatBI', level: 'ask', enabled: false }, // R2
  'AI-07': { name: '异常预警解读', level: 'ask', enabled: false }, // R2
  'AI-08': { name: '报价辅助', level: 'ask', enabled: false }, // R2
  'AI-09': { name: '知识库问答 RAG', level: 'ask', enabled: false }, // R2
  'AI-10': { name: '日报周报生成', level: 'ask', enabled: false }, // R2
  'AI-11': { name: '报表解读', level: 'auto', enabled: false }, // R2
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
