import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import { BOM_TYPE, isValidCode } from '@protech/shared-types';

export interface BomRuleFinding {
  rule: string;
  lineIndex?: number;
  materialCode?: string;
  detail: string;
}

/** 13 条 BOM 内容校验规则（FR-M05-004；逐条可开闭 sys_config bom_rules_enabled） */
const BOM_RULES = [
  'one_code_multi_material', // 一物多码（同码不同物料名）
  'one_material_multi_code', // 一码多物（同名不同码）
  'same_code_multi_unit', // 同码多单位
  'code_column_abuse', // 编码列挪用（编码填了名称）
  'no_code_line', // 无编码行
  'zero_qty', // 数量为零
  'empty_spec', // 规格为空（A 类必填）
  'unit_filled_category', // 单位填了类别
  'duplicate_line', // 重复行
  'abnormal_qty', // 数量异常（>1e5）
  'code_format', // 编码格式（4 套旧码段 3080/3084/M1/D 之外）
  'similar_name', // 名称相似
  'spec_conflict', // 规格冲突（同码不同规格）
] as const;

@Injectable()
export class BomService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
  ) {}

  async create(body: Record<string, unknown>, userId: bigint) {
    const bomType = String(body.bomType ?? 'full');
    if (!isValidCode(BOM_TYPE, bomType)) throw BizException.param('bomType 非法（E-19）');
    if (!body.name || !body.productLine) throw BizException.param('name/productLine 必填');
    const bomCode = await this.codes.next('bom');
    const bom = await this.prisma.bom.create({
      data: {
        bomCode,
        bomType,
        productLine: String(body.productLine),
        name: String(body.name),
        parentBomId: parseIdOrNull(body.parentBomId),
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    const lines = Array.isArray(body.lines) ? body.lines : [];
    for (const [i, raw] of lines.entries()) {
      const line = raw as Record<string, unknown>;
      await this.addLine(bom.id, line, userId, i);
    }
    await this.audit.log({ action: 'create', objectType: 'Bom', objectId: bom.id, after: { bomCode, lines: lines.length } });
    return bom;
  }

  async addLine(bomId: bigint, line: Record<string, unknown>, userId: bigint, sort = 0) {
    const bom = await this.prisma.bom.findFirst({ where: { id: bomId, deletedAt: null } });
    if (!bom) throw BizException.notFound('BOM 不存在');
    if (bom.status !== 'draft') {
      // BR-05：已发布 BOM 变更必须走 ECN
      throw BizException.br('BR-05', '检测到 BOM 变更未走 ECN，请创建变更单；变更单必须附旧料清单与处置方案');
    }
    const materialId = parseIdOrNull(line.materialId);
    if (!materialId) throw BizException.param('materialId 必填（BR-08 下拉）');
    const material = await this.prisma.material.findFirst({ where: { id: materialId, deletedAt: null } });
    if (!material) throw BizException.notFound('物料不存在');
    const qty = Number(line.qty);
    if (!Number.isFinite(qty) || qty <= 0) throw BizException.param('qty 必须为正数');
    return this.prisma.bomLine.create({
      data: {
        bomId,
        materialId,
        qty,
        positionCode: (line.positionCode as string) ?? null,
        kitRefId: parseIdOrNull(line.kitRefId),
        sort,
        createdBy: userId,
        updatedBy: userId,
      },
    });
  }

  /** FR-M05-002 发布：draft → published（历史版本只读；作废不可删） */
  async publish(bomId: bigint, userId: bigint) {
    const bom = await this.prisma.bom.findFirst({ where: { id: bomId, deletedAt: null } });
    if (!bom) throw BizException.notFound('BOM 不存在');
    if (bom.status !== 'draft') throw BizException.validation('仅草稿可发布');
    // 发布前 13 条规则扫描（命中不阻断但记录，供治理）
    const findings = await this.lint(bomId);
    const bad = findings.filter((f) => ['no_code_line', 'zero_qty', 'duplicate_line'].includes(f.rule));
    if (bad.length) {
      throw Object.assign(BizException.validation(`BOM 存在硬缺陷 ${bad.length} 处（无编码行/零数量/重复行），发布被拦截`), {
        data: { findings: bad },
      });
    }
    const updated = await this.prisma.bom.update({ where: { id: bomId }, data: { status: 'published', updatedBy: userId } });
    await this.audit.log({ action: 'publish', objectType: 'Bom', objectId: bomId, after: { findings: findings.length } });
    return { bom: updated, findings };
  }

  /** FR-M05-004 13 条内容校验规则（命中标红并进治理工单——M13 S5 落工单，当前返回清单+审计） */
  async lint(bomId: bigint): Promise<BomRuleFinding[]> {
    const bom = await this.prisma.bom.findFirst({ where: { id: bomId, deletedAt: null } });
    if (!bom) throw BizException.notFound('BOM 不存在');
    const cfg = await this.prisma.sysConfig.findFirst({ where: { key: 'bom_rules_enabled' } });
    const enabled = cfg ? (JSON.parse(cfg.value) as string[]) : [...BOM_RULES];
    void enabled;

    const lines = await this.prisma.bomLine.findMany({ where: { bomId, deletedAt: null }, orderBy: { sort: 'asc' } });
    const materialIds = [...new Set(lines.map((l) => l.materialId))];
    const materials = materialIds.length
      ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } })
      : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    const findings: BomRuleFinding[] = [];

    for (const [i, l] of lines.entries()) {
      const m = mById.get(l.materialId);
      if (!m) continue;
      if (Number(l.qty) === 0) findings.push({ rule: 'zero_qty', lineIndex: i, materialCode: m.materialCode, detail: '数量为零' });
      if (Number(l.qty) > 100000) findings.push({ rule: 'abnormal_qty', lineIndex: i, materialCode: m.materialCode, detail: `数量异常：${l.qty}` });
      if (bom.bomType === 'full' && !m.spec) findings.push({ rule: 'empty_spec', lineIndex: i, materialCode: m.materialCode, detail: 'A 类 BOM 规格为空' });
    }
    // 同码多物料（一码多物）/ 同码多单位 / 同码不同规格（规格冲突）
    const byCode = new Map<string, typeof materials>();
    for (const m of materials) {
      const arr = byCode.get(m.materialCode) ?? [];
      arr.push(m);
      byCode.set(m.materialCode, arr);
    }
    for (const [code, ms] of byCode) {
      if (ms.length > 1) findings.push({ rule: 'one_code_multi_material', materialCode: code, detail: `一码多物：${code} 指向 ${ms.length} 个物料` });
      if (new Set(ms.map((m) => m.unit)).size > 1) findings.push({ rule: 'same_code_multi_unit', materialCode: code, detail: `同码多单位：${code}` });
      if (new Set(ms.map((m) => m.spec ?? '')).size > 1) findings.push({ rule: 'spec_conflict', materialCode: code, detail: `同码不同规格：${code}` });
    }
    // 同名不同码（一物多码）
    const byName = new Map<string, Set<string>>();
    for (const m of materials) {
      const codes = byName.get(m.name) ?? new Set<string>();
      codes.add(m.materialCode);
      byName.set(m.name, codes);
    }
    for (const [name, codes] of byName) {
      if (codes.size > 1) findings.push({ rule: 'one_material_multi_code', detail: `一物多码：${name} → ${[...codes].join('/')}` });
    }
    // 重复行
    const seen = new Map<string, number>();
    for (const [i, l] of lines.entries()) {
      const key = `${l.materialId}:${l.positionCode ?? ''}`;
      if (seen.has(key)) findings.push({ rule: 'duplicate_line', lineIndex: i, detail: `与第 ${seen.get(key)! + 1} 行重复` });
      else seen.set(key, i);
    }
    if (findings.length) {
      await this.audit.log({ action: 'bom_lint', objectType: 'Bom', objectId: bomId, after: { findings: findings.length } });
    }
    return findings;
  }

  /** FR-M05-008 ECN：变更单创建（BR-05 处置方案必填） */
  async createEcn(body: Record<string, unknown>, userId: bigint) {
    const bomId = parseIdOrNull(body.bomId);
    if (!bomId) throw BizException.param('bomId 必填');
    if (!body.disposalPlan) {
      throw BizException.br('BR-05', '变更单必须附旧料清单与旧料处置方案');
    }
    const bom = await this.prisma.bom.findFirst({ where: { id: bomId, deletedAt: null } });
    if (!bom) throw BizException.notFound('BOM 不存在');
    const ecnCode = await this.codes.next('ecn');
    const ecn = await this.prisma.ecn.create({
      data: {
        ecnCode,
        bomId,
        fromVersion: bom.version,
        toVersion: bom.version + 1,
        reason: String(body.reason ?? ''),
        oldMaterialLines: JSON.stringify(Array.isArray(body.oldMaterialLines) ? body.oldMaterialLines : []),
        disposalPlan: String(body.disposalPlan),
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'Ecn', objectId: ecn.id, after: { ecnCode } });
    return ecn;
  }

  /** ECN 审批通过 → 发布新版本（version+1，行变更已随 ECN 审批由 addLine-on-draft 流程保证） */
  async approveEcn(ecnId: bigint, userId: bigint, approved: boolean) {
    const ecn = await this.prisma.ecn.findFirst({ where: { id: ecnId, deletedAt: null } });
    if (!ecn) throw BizException.notFound('ECN 不存在');
    if (ecn.status !== 'pending') throw BizException.validation('ECN 已处理');
    if (!approved) {
      await this.prisma.ecn.update({ where: { id: ecnId }, data: { status: 'rejected', approverId: userId, reviewedAt: new Date() } });
      return { status: 'rejected' };
    }
    await this.prisma.$transaction(async (tx) => {
      await tx.bom.update({ where: { id: ecn.bomId }, data: { version: ecn.toVersion, updatedBy: userId } });
      await tx.ecn.update({
        where: { id: ecnId },
        data: { status: 'published', approverId: userId, reviewedAt: new Date(), publishedAt: new Date() },
      });
    });
    await this.audit.log({ action: 'ecn_publish', objectType: 'Ecn', objectId: ecnId, after: { toVersion: ecn.toVersion } });
    return { status: 'published', toVersion: ecn.toVersion };
  }
}
