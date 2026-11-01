import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';
import {
  generateStandardName,
  stripFarmSuffix,
  similarity,
  normalizeName,
  ORDER_TYPE_WARRANTY_MONTHS,
  PROJECT_KEY_FIELDS as KEY_FIELDS,
  PROJECT_STATUS,
} from '@protech/shared-types';

/** 项目状态机合法迁移（05 册 §6.1） */
const PROJECT_TRANSITIONS: Record<string, string[]> = {
  pending_farm: ['pending_exec', 'executing'],
  pending_exec: ['executing'],
  executing: ['shipped', 'accepted'],
  shipped: ['accepted'],
  accepted: ['warranty', 'closed'],
  warranty: ['closed'],
  closed: [],
};

export interface ProjectDupResult {
  standardNameHits: Array<{ id: string; code: string; name: string }>;
  shortNameHits: Array<{ id: string; code: string; name: string }>;
  ownerFarmHits: Array<{ id: string; code: string; name: string; score: number }>;
  canCreate: boolean;
}

/**
 * 项目档案服务（FR-M01-008~011、FR-M04-001~010 的 M01 侧）。
 * 全系统锚点：项目编码 PJ{YYYY}-{NNNN}（BR-09）；三重查重；状态机；关键 12 项完整度。
 */
@Injectable()
export class ProjectService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 名称生成与查重 ----------------

  /** FR-M01-009 名称自动生成器（4 进 3 出：标准名+查重结果+编码建议） */
  async namePreview(input: {
    ownerName?: string;
    ownerPartyId?: unknown;
    farmName?: string;
    phase?: string;
    orderType?: string;
    windFarmId?: unknown;
  }) {
    let farmName = input.farmName ?? '';
    if (input.windFarmId) {
      const farm = await this.prisma.windFarm.findFirst({
        where: { id: parseId(input.windFarmId), deletedAt: null },
      });
      if (!farm) throw BizException.notFound('风场不存在');
      farmName = farm.fullName;
    }
    if (!farmName) throw BizException.param('farmName 或 windFarmId 必填');
    // 业主名：显式入参优先，否则按主体档案解析（S-02 口径：业主规范名）
    let ownerName = input.ownerName;
    if (!ownerName && input.ownerPartyId) {
      const party = await this.prisma.customerParty.findFirst({
        where: { id: parseId(input.ownerPartyId), deletedAt: null },
      });
      ownerName = party?.shortName ?? party?.name ?? undefined;
    }
    const gen = generateStandardName({
      ownerName,
      farmName,
      phase: input.phase,
      orderType: input.orderType,
    });
    const dup = await this.checkDup({
      standardName: gen.standardName,
      ownerName: input.ownerName,
      farmName,
    });
    return {
      ...gen,
      standardNameHits: dup.standardNameHits,
      ownerFarmHits: dup.ownerFarmHits,
      codeSuggestion: await this.codes.preview('project'),
    };
  }

  /** FR-M01-010 三重查重：标准名精确 / 简称+别名 / 业主+地名 */
  async checkDup(input: {
    standardName?: string;
    shortName?: string;
    ownerName?: string;
    farmName?: string;
    windFarmId?: unknown;
  }): Promise<ProjectDupResult> {
    const standardNameHits: ProjectDupResult['standardNameHits'] = [];
    const shortNameHits: ProjectDupResult['shortNameHits'] = [];
    const ownerFarmHits: ProjectDupResult['ownerFarmHits'] = [];

    if (input.standardName?.trim()) {
      const exact = await this.prisma.project.findMany({
        where: { standardName: input.standardName.trim(), deletedAt: null },
        take: 10,
      });
      standardNameHits.push(...exact.map((p) => ({ id: p.id.toString(), code: p.projectCode, name: p.standardName })));
    }
    if (input.shortName?.trim()) {
      const sn = input.shortName.trim();
      const [exact, aliasHits] = await Promise.all([
        this.prisma.project.findMany({ where: { shortName: sn, deletedAt: null }, take: 10 }),
        this.prisma.alias.findMany({ where: { ownerType: 'project', alias: sn }, take: 10 }),
      ]);
      shortNameHits.push(
        ...exact.map((p) => ({ id: p.id.toString(), code: p.projectCode, name: p.standardName })),
      );
      for (const a of aliasHits) {
        const p = await this.prisma.project.findFirst({ where: { id: a.ownerId, deletedAt: null } });
        if (p) shortNameHits.push({ id: p.id.toString(), code: p.projectCode, name: p.standardName });
      }
    }
    if (input.ownerName || input.farmName || input.windFarmId) {
      let farmCore = input.farmName ? stripFarmSuffix(input.farmName) : '';
      let owner = input.ownerName ?? '';
      if (input.windFarmId) {
        const farm = await this.prisma.windFarm.findFirst({ where: { id: parseId(input.windFarmId), deletedAt: null } });
        if (farm) farmCore = stripFarmSuffix(farm.fullName);
      }
      if (farmCore || owner) {
        const th = 0.85;
        const projects = await this.prisma.project.findMany({
          where: { deletedAt: null, ...(owner ? {} : {}) },
          take: 2000,
          select: { id: true, projectCode: true, standardName: true },
        });
        for (const p of projects) {
          // 三查之三：标准名需同时包含业主与地名核心
          const hitOwner = owner ? normalizeName(p.standardName).includes(normalizeName(owner)) : true;
          const hitFarm = farmCore ? normalizeName(p.standardName).includes(normalizeName(farmCore)) : false;
          if (hitOwner && hitFarm) {
            ownerFarmHits.push({ id: p.id.toString(), code: p.projectCode, name: p.standardName, score: 1 });
          } else if (farmCore && similarity(p.standardName, `${owner}-${farmCore}`) >= th) {
            ownerFarmHits.push({
              id: p.id.toString(),
              code: p.projectCode,
              name: p.standardName,
              score: similarity(p.standardName, `${owner}-${farmCore}`),
            });
          }
        }
      }
    }
    return {
      standardNameHits,
      shortNameHits,
      ownerFarmHits,
      canCreate: !standardNameHits.length && !shortNameHits.length,
    };
  }

  // ---------------- 建档（FR-M01-008） ----------------

  /** FR-M01-008/FR-M04-003 建档：四类订单情形路径 + 三角色分列（业主/整机厂/我方主体独立字段） */
  async create(body: Record<string, unknown>, userId: bigint, opts: { force?: boolean } = {}) {
    const windFarmId = parseIdOrNull(body.windFarmId);
    const orderType = String(body.orderType ?? '');
    if (!orderType) throw BizException.param('orderType 必填');

    // 四类订单情形建档路径（FR-M01-008）：整机厂初装可暂缺风场 → pending_farm（两段式，FR-M03-015 对齐）
    const pendingFarm = !windFarmId;
    if (pendingFarm && orderType !== 'oem_initial') {
      throw BizException.param('windFarmId 必填（业主技改/备件/定检服务必须关联风场）');
    }
    if (windFarmId) {
      const farm = await this.prisma.windFarm.findFirst({ where: { id: windFarmId, deletedAt: null } });
      if (!farm) throw BizException.notFound('风场不存在');
    }
    const legalEntityId = parseIdOrNull(body.legalEntityId);
    if (!legalEntityId) throw BizException.param('legalEntityId 必填（两司分账）');
    const legalEntity = await this.prisma.legalEntity.findFirst({
      where: { id: legalEntityId, deletedAt: null },
    });
    if (!legalEntity) throw BizException.notFound('法人主体不存在');

    const ownerPartyId = parseIdOrNull(body.ownerPartyId);
    if (ownerPartyId) {
      const party = await this.prisma.customerParty.findFirst({ where: { id: ownerPartyId, deletedAt: null } });
      if (!party) throw BizException.notFound('业主主体不存在');
    }

    const standardName = String(body.standardName ?? '').trim();
    const shortName = String(body.shortName ?? '').trim();
    if (!standardName) throw BizException.param('standardName 必填（可用名称生成器预生成）');
    if (!shortName || shortName.length < 2 || shortName.length > 8) {
      throw BizException.param('shortName 必填（3—6 字常用简称，全公司唯一）');
    }

    // 三重查重（三查皆无方可新建）
    const dup = await this.checkDup({
      standardName,
      shortName,
      ownerName: typeof body.ownerName === 'string' ? body.ownerName : undefined,
      windFarmId,
    });
    if (!dup.canCreate && !opts.force) {
      throw Object.assign(BizException.validation('项目三重查重命中，疑似重复建档'), {
        data: { dup },
      });
    }

    const unitCount = Number(body.unitCount ?? 0);
    if (!Number.isInteger(unitCount) || unitCount < 0) throw BizException.param('unitCount 必须为非负整数');
    const productLines = Array.isArray(body.productLines) ? body.productLines : null;
    if (!productLines || !productLines.length) throw BizException.param('productLines 必填');

    // BR-10：合同金额必须整数分
    let contractAmountCents: bigint | null = null;
    if (body.contractAmountCents !== undefined && body.contractAmountCents !== null && body.contractAmountCents !== '') {
      const v = body.contractAmountCents;
      if (typeof v === 'number' && !Number.isInteger(v)) {
        throw BizException.validation('contractAmountCents 必须为整数分（BR-10 禁止浮点）', 'BR-10');
      }
      if (typeof v === 'string' && !/^-?\d+$/.test(v.trim())) {
        throw BizException.validation('contractAmountCents 必须为整数分（BR-10 禁止浮点）', 'BR-10');
      }
      contractAmountCents = BigInt(String(v).trim());
    }

    // FR-M04-007 项目团队五角色（pm/eng/aftersales/sales/collector 双责任人）字段在建档/编辑落库
    // 质保默认值：初装 60 月 / 技改 12 月（01 册 §2.3），可显式覆盖
    const warrantyMonths = Number(
      body.warrantyMonths ?? ORDER_TYPE_WARRANTY_MONTHS[orderType] ?? 12,
    );

    const projectCode = await this.codes.next('project');
    const pendingFlags: string[] = [];
    if (!ownerPartyId) pendingFlags.push('pending_owner');
    if (pendingFarm) pendingFlags.push('pending_farm');

    const project = await this.prisma.project.create({
      data: {
        projectCode,
        standardName,
        shortName,
        phase: (body.phase as string) ?? null,
        windFarmId,
        ownerPartyId,
        oemPartyIds: JSON.stringify(Array.isArray(body.oemPartyIds) ? body.oemPartyIds : []),
        legalEntityId,
        orderType,
        productLines: JSON.stringify(productLines),
        unitCount,
        contractAmountCents,
        warrantyStart: body.warrantyStart ? new Date(String(body.warrantyStart)) : null,
        warrantyEnd: body.warrantyEnd ? new Date(String(body.warrantyEnd)) : null,
        pmUserId: parseIdOrNull(body.pmUserId),
        engOwnerId: parseIdOrNull(body.engOwnerId),
        aftersalesOwnerId: parseIdOrNull(body.aftersalesOwnerId),
        salesOwnerId: parseIdOrNull(body.salesOwnerId),
        collectorId: parseIdOrNull(body.collectorId),
        status: pendingFarm ? 'pending_farm' : 'pending_exec',
        pendingFlags: JSON.stringify(pendingFlags),
        remark: (body.remark as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });

    // 别名沉淀（清洗五步法第五步，FR-M13-004）
    if (typeof body.alias === 'string' && body.alias.trim()) {
      await this.prisma.alias.create({
        data: { ownerType: 'project', ownerId: project.id, alias: body.alias.trim(), source: 'manual', createdBy: userId },
      });
    }
    await this.audit.log({ action: 'create', objectType: 'Project', objectId: project.id, after: { projectCode, standardName } });
    return project;
  }

  // ---------------- 完整度（关键 12 项，FR-M01-008） ----------------

  async completeness(project: {
    ownerPartyId: bigint | null;
    windFarmId: bigint | null;
    legalEntityId: bigint | null;
    orderType: string;
    productLines: string | null;
    unitCount: number;
    pmUserId: bigint | null;
    engOwnerId: bigint | null;
    salesOwnerId: bigint | null;
    collectorId: bigint | null;
    contractAmountCents: bigint | null;
    phase: string | null;
  }): Promise<{ filled: number; total: number; percent: number; missing: string[] }> {
    const values: Record<string, unknown> = {
      ownerPartyId: project.ownerPartyId,
      windFarmId: project.windFarmId,
      legalEntityId: project.legalEntityId,
      orderType: project.orderType,
      productLines: project.productLines,
      unitCount: project.unitCount || null,
      pmUserId: project.pmUserId,
      engOwnerId: project.engOwnerId,
      salesOwnerId: project.salesOwnerId,
      collectorId: project.collectorId,
      contractAmountCents: project.contractAmountCents,
      phase: project.phase,
    };
    const missing = KEY_FIELDS.filter((f) => {
      const v = values[f];
      return v === null || v === undefined || v === '' || v === '[]';
    });
    const filled = KEY_FIELDS.length - missing.length;
    return {
      filled,
      total: KEY_FIELDS.length,
      percent: Math.round((filled / KEY_FIELDS.length) * 100),
      missing,
    };
  }

  // ---------------- 状态机（E-04 / 05 册 §6.1） ----------------

  async transition(projectId: bigint, to: string, userId: bigint, note?: string) {
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    if (!isValidCodeKey(PROJECT_STATUS, to)) throw BizException.param(`非法目标状态：${to}`);
    const allowed = PROJECT_TRANSITIONS[project.status] ?? [];
    if (!allowed.includes(to)) {
      throw BizException.validation(`状态不允许迁移：${project.status} → ${to}`);
    }
    // 守卫条件（05 册 §6.1）
    if (to === 'shipped' && project.status === 'executing') {
      // 发货齐套守卫在 S3 发货单落地后接入；当前要求显式 note
      if (!note) throw BizException.validation('迁入已发货需附发货说明');
    }
    if (to === 'closed') {
      // 关闭触发：档案归档提醒 + R-PM 权限 7 天回收（FR-M04-013）
      await this.schedulePmRevoke(projectId, project.pmUserId);
      if (project.pmUserId) {
        await this.notify.send({
          userId: project.pmUserId,
          type: 'remind',
          title: `项目 ${project.projectCode} 已关闭，归档资料请在 30 日内移交（FR-M12-006）`,
          bizType: 'project',
          bizId: projectId.toString(),
        });
      }
    }
    const updated = await this.prisma.project.update({
      where: { id: projectId },
      data: { status: to, updatedBy: userId },
    });
    await this.audit.log({
      action: 'project_status',
      objectType: 'Project',
      objectId: projectId,
      before: { status: project.status },
      after: { status: to, note: note ?? null },
    });
    return updated;
  }

  /** 项目关闭后 7 天自动回收 R-PM（FR-M00-003/FR-M04-013）：置 expiredAt=+7 天 */
  private async schedulePmRevoke(projectId: bigint, pmUserId: bigint | null) {
    if (!pmUserId) return;
    const expiredAt = new Date(Date.now() + 7 * 24 * 3600 * 1000);
    await this.prisma.projectPmGrant.updateMany({
      where: { projectId, userId: pmUserId, revokedAt: null },
      data: { expiredAt },
    });
  }

  /** 待回填任务清单（FR-M04-008）：待关联风场/待补业主项目 */
  async pendingTasks() {
    const list = await this.prisma.project.findMany({
      where: { deletedAt: null, pendingFlags: { not: '[]' } },
      orderBy: { updatedAt: 'asc' },
      take: 200,
    });
    return list.map((p) => ({
      id: p.id.toString(),
      projectCode: p.projectCode,
      standardName: p.standardName,
      status: p.status,
      pendingFlags: JSON.parse(p.pendingFlags ?? '[]'),
      updatedAt: p.updatedAt,
    }));
  }
}

function isValidCodeKey(def: Record<string, string>, code: string): boolean {
  return Object.prototype.hasOwnProperty.call(def, code);
}
