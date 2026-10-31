import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AppConfigService } from '../../shared/app-config.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import { similarity, normalizeName } from '@protech/shared-types';

export interface DupCandidate {
  id: string;
  code: string;
  name: string;
  score: number;
  matchedBy: 'exact' | 'alias' | 'similar';
}

/**
 * 主数据服务（FR-M01-001~007、M13-002/003 录入端共用）。
 * 查重三查：名称精确 → 别名命中 → 归一相似度 ≥阈值（sys_config dup_similarity_threshold）。
 */
@Injectable()
export class MdmService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: AppConfigService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
  ) {}

  private async threshold(): Promise<number> {
    return this.config.getNumber('dup_similarity_threshold', 0.85);
  }

  // ---------------- 客户主体（T-01/T-02/T-93） ----------------

  async checkPartyDup(name: string): Promise<DupCandidate[]> {
    const th = await this.threshold();
    const parties = await this.prisma.customerParty.findMany({ where: { deletedAt: null } });
    const aliases = await this.prisma.alias.findMany({ where: { ownerType: 'party' } });
    const partyById = new Map(parties.map((p) => [p.id, p]));
    const hits = new Map<string, DupCandidate>();

    const target = normalizeName(name);
    for (const p of parties) {
      if (normalizeName(p.name) === target || normalizeName(p.shortName ?? '') === target) {
        hits.set(p.id.toString(), {
          id: p.id.toString(),
          code: p.partyCode,
          name: p.name,
          score: 1,
          matchedBy: 'exact',
        });
      } else {
        const score = similarity(name, p.name);
        if (score >= th) {
          hits.set(p.id.toString(), {
            id: p.id.toString(),
            code: p.partyCode,
            name: p.name,
            score: Number(score.toFixed(4)),
            matchedBy: 'similar',
          });
        }
      }
    }
    for (const a of aliases) {
      if (normalizeName(a.alias) === target) {
        const p = partyById.get(a.ownerId);
        if (p && !hits.has(p.id.toString())) {
          hits.set(p.id.toString(), { id: p.id.toString(), code: p.partyCode, name: p.name, score: 1, matchedBy: 'alias' });
        }
      }
    }
    return Array.from(hits.values()).sort((a, b) => b.score - a.score);
  }

  async createParty(
    body: Record<string, unknown>,
    userId: bigint,
    opts: { force?: boolean } = {},
  ) {
    const name = String(body.name ?? '').trim();
    if (!name) throw BizException.param('name 必填');
    const candidates = await this.checkPartyDup(name);
    if (candidates.length && !opts.force) {
      // FR-M01-003：命中即提示"疑似已存在"并列出候选
      throw Object.assign(BizException.validation('疑似已存在，请先确认或关联已有主体'), {
        data: { candidates },
      });
    }
    const partyCode = await this.codes.next('party');
    const groupId = parseIdOrNull(body.groupId);
    if (groupId) {
      const g = await this.prisma.customerGroup.findFirst({ where: { id: groupId, deletedAt: null } });
      if (!g) throw BizException.param('集团不存在');
    }
    const types = Array.isArray(body.types) ? body.types : [];
    const party = await this.prisma.customerParty.create({
      data: {
        partyCode,
        groupId,
        name,
        shortName: (body.shortName as string) ?? null,
        types: JSON.stringify(types),
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    if (typeof body.alias === 'string' && body.alias.trim()) {
      await this.prisma.alias.create({ data: { ownerType: 'party', ownerId: party.id, alias: body.alias.trim(), source: 'manual', createdBy: userId } });
    }
    return party;
  }

  async addAlias(ownerType: 'party' | 'project' | 'material', ownerId: bigint, alias: string, userId: bigint) {
    if (!alias.trim()) throw BizException.param('alias 必填');
    // 只增不删（FR-M01-002）：重复别名幂等跳过
    const dup = await this.prisma.alias.findFirst({
      where: { ownerType, ownerId, alias: alias.trim() },
    });
    if (dup) return { id: dup.id.toString(), duplicated: true };
    const row = await this.prisma.alias.create({
      data: { ownerType, ownerId, alias: alias.trim(), source: 'manual', createdBy: userId },
    });
    return { id: row.id.toString(), duplicated: false };
  }

  // ---------------- 风场库（T-03） ----------------

  async createFarm(body: Record<string, unknown>, userId: bigint, opts: { force?: boolean } = {}) {
    const fullName = String(body.fullName ?? '').trim();
    if (!fullName) throw BizException.param('fullName 必填');
    // 一地一档查重（同全称不重复建档）
    const exact = await this.prisma.windFarm.findFirst({ where: { fullName, deletedAt: null } });
    if (exact && !opts.force) {
      throw BizException.validation(`该风场已存在：${exact.farmCode} ${exact.fullName}`);
    }
    const farmCode = await this.codes.next('wind_farm');
    const farm = await this.prisma.windFarm.create({
      data: {
        farmCode,
        fullName,
        province: (body.province as string) ?? null,
        city: (body.city as string) ?? null,
        county: (body.county as string) ?? null,
        capacityMw: body.capacityMw !== undefined && body.capacityMw !== null ? Number(body.capacityMw) : null,
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    return farm;
  }

  // ---------------- 物料（T-05/T-06/T-92） ----------------

  /** 归一化词典前置校验（FR-M01-006/M13-003）：错别字/符号/单位/分隔符 */
  normalizeMaterialName(name: string, dict: Array<{ type: string; raw: string; normalized: string }>): {
    name: string;
    applied: Array<{ type: string; from: string; to: string }>;
  } {
    let s = name;
    const applied: Array<{ type: string; from: string; to: string }> = [];
    for (const d of dict) {
      if (d.type === 'sep' || d.type === 'symbol') {
        if (s.includes(d.raw)) {
          s = s.split(d.raw).join(d.normalized);
          applied.push({ type: d.type, from: d.raw, to: d.normalized });
        }
      } else if (s.includes(d.raw)) {
        s = s.split(d.raw).join(d.normalized);
        applied.push({ type: d.type, from: d.raw, to: d.normalized });
      }
    }
    return { name: s, applied };
  }

  async checkMaterialDup(name: string, spec?: string): Promise<DupCandidate[]> {
    const th = await this.threshold();
    const materials = await this.prisma.material.findMany({ where: { deletedAt: null } });
    const hits: DupCandidate[] = [];
    for (const m of materials) {
      const target = `${name}${spec ? ' ' + spec : ''}`;
      const score = similarity(target, `${m.name}${m.spec ? ' ' + m.spec : ''}`);
      if (score >= th) {
        hits.push({
          id: m.id.toString(),
          code: m.materialCode,
          name: m.spec ? `${m.name}（${m.spec}）` : m.name,
          score: Number(score.toFixed(4)),
          matchedBy: normalizeName(m.name) === normalizeName(name) ? 'exact' : 'similar',
        });
      }
    }
    return hits.sort((a, b) => b.score - a.score);
  }

  async createMaterial(body: Record<string, unknown>, userId: bigint, opts: { force?: boolean } = {}) {
    if (!body.name || !body.unit) throw BizException.param('name/unit 必填');
    const dict = await this.prisma.normDict.findMany({ where: { deletedAt: null } });
    const norm = this.normalizeMaterialName(String(body.name), dict);
    const name = norm.name;
    const spec = (body.spec as string) ?? null;
    const candidates = await this.checkMaterialDup(name, spec ?? undefined);
    if (candidates.length && !opts.force) {
      // DQ-01 一物多码新增：拦截
      await this.audit.log({
        action: 'dq_block',
        objectType: 'Material',
        objectId: null,
        after: { name, spec, candidates },
      });
      const e = BizException.validation('疑似一物多码（DQ-01），已拦截并记录治理工单线索');
      throw Object.assign(e, { data: { normalized: norm, candidates } });
    }
    const materialCode = String(body.materialCode ?? '').trim();
    if (!materialCode) throw BizException.param('materialCode 必填（归一后主码）');
    const material = await this.prisma.material.create({
      data: {
        materialCode,
        name,
        spec,
        unit: String(body.unit),
        category: (body.category as string) ?? null,
        legalEntityScope: String(body.legalEntityScope ?? 'common'),
        abcClass: (body.abcClass as string) ?? null,
        safetyStockMin: body.safetyStockMin != null ? Number(body.safetyStockMin) : null,
        safetyStockMax: body.safetyStockMax != null ? Number(body.safetyStockMax) : null,
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    return { material, normalized: norm };
  }

  /** 旧码解析（FR-M01-005：4 套旧码经 md_code_map 映射兼容；映射表只增不改无软删） */
  async resolveOldCode(oldCode: string): Promise<{ masterCode: string; materialId: string } | null> {
    const map = await this.prisma.mdCodeMap.findFirst({ where: { oldCode } });
    if (!map) return null;
    const m = await this.prisma.material.findFirst({ where: { materialCode: map.masterCode, deletedAt: null } });
    return m ? { masterCode: map.masterCode, materialId: m.id.toString() } : null;
  }
}
