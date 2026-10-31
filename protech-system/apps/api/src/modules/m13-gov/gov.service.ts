import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';
import { normalizeName, similarity } from '@protech/shared-types';

/**
 * M13 数据治理（FR-M13-001~006/009 S5 集）：治理工单闭环/查重工作台/质量看板/治理权限。
 * 归并/回写仅数据治理组（R-GA/R-SYS）可执行，全程审计（PERM_ROLES 控制入口）。
 */
@Injectable()
export class GovService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
  ) {}

  /** FR-M13-001 工单创建（BOM lint / 扫码未映射 / 手工） */
  async createTask(body: Record<string, unknown>, userId: bigint) {
    const type = String(body.type ?? '');
    if (!['dup_code', 'name_clean', 'bom_rule', 'naming', 'unmapped_code'].includes(type)) {
      throw BizException.param('type 非法');
    }
    const taskCode = await this.codes.next('gov_task');
    const task = await this.prisma.govTask.create({
      data: {
        taskCode,
        type,
        payload: JSON.stringify(body.payload ?? {}),
        assigneeId: body.assigneeId ? parseId(body.assigneeId) : null,
        sourceType: (body.sourceType as string) ?? 'manual',
        sourceId: body.sourceId != null ? String(body.sourceId) : null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'GovTask', objectId: task.id, after: { taskCode, type } });
    return task;
  }

  async reviewTask(taskId: bigint, userId: bigint, action: 'process' | 'verify', result: Record<string, unknown>) {
    const task = await this.prisma.govTask.findFirst({ where: { id: taskId, deletedAt: null } });
    if (!task) throw BizException.notFound('工单不存在');
    if (action === 'process' && !['open', 'processing'].includes(task.status)) {
      throw BizException.validation('工单当前状态不可处理');
    }
    if (action === 'verify' && task.status !== 'done') {
      throw BizException.validation('仅 done 工单可复核');
    }
    const status = action === 'process' ? 'done' : 'verified';
    await this.prisma.govTask.update({
      where: { id: taskId },
      data: { status, result: JSON.stringify(result ?? {}), updatedBy: userId },
    });
    await this.audit.log({ action: `gov_${action}`, objectType: 'GovTask', objectId: taskId, after: result ?? {} });
    return { status };
  }

  /** FR-M13-002 查重工作台：宽松口径候选组（名称一致）人工判读 */
  async dupWorkbench() {
    const materials = await this.prisma.material.findMany({ where: { deletedAt: null }, take: 3000 });
    const byName = new Map<string, typeof materials>();
    for (const m of materials) {
      const key = normalizeName(m.name);
      const arr = byName.get(key) ?? [];
      arr.push(m);
      byName.set(key, arr);
    }
    const groups: Array<{ nameKey: string; members: Array<{ id: string; code: string; name: string; spec: string | null; unit: string; status: string }> }> = [];
    for (const [key, ms] of byName) {
      if (ms.length > 1) {
        groups.push({
          nameKey: key,
          members: ms.map((m) => ({ id: m.id.toString(), code: m.materialCode, name: m.name, spec: m.spec, unit: m.unit, status: m.status })),
        });
      }
    }
    return { groups: groups.slice(0, 100), totalGroups: groups.length };
  }

  /** FR-M13-002 归并操作：生成 md_code_map（旧码→主码，只增不改）+ 旧料 frozen */
  async mergeMaterial(masterId: bigint, oldIds: bigint[], userId: bigint) {
    const master = await this.prisma.material.findFirst({ where: { id: masterId, deletedAt: null } });
    if (!master) throw BizException.notFound('主码物料不存在');
    let merged = 0;
    for (const oldId of oldIds) {
      if (oldId === masterId) continue;
      const old = await this.prisma.material.findFirst({ where: { id: oldId, deletedAt: null } });
      if (!old) continue;
      await this.prisma.mdCodeMap.upsert({
        where: { oldCode: old.materialCode },
        create: { oldCode: old.materialCode, masterCode: master.materialCode, source: 'manual' },
        update: {}, // 只增不改
      });
      await this.prisma.material.update({ where: { id: oldId }, data: { status: 'frozen', updatedBy: userId } });
      merged++;
    }
    await this.audit.log({
      action: 'gov_merge',
      objectType: 'Material',
      objectId: masterId,
      after: { merged, oldIds: oldIds.map(String) },
    });
    return { merged };
  }

  /** FR-M13-006 数据质量看板（⑨板指标源） */
  async qualityBoard() {
    const [materialTotal, mappedOld, frozenOld, partiesNoAlias, govOpen] = await Promise.all([
      this.prisma.material.count({ where: { deletedAt: null } }),
      this.prisma.mdCodeMap.count(),
      this.prisma.material.count({ where: { status: 'frozen', deletedAt: null } }),
      this.prisma.customerParty.count({ where: { deletedAt: null } }),
      this.prisma.govTask.count({ where: { status: { in: ['open', 'processing'] }, deletedAt: null } }),
    ]);
    // 别名未挂靠（有别名但主体已软删——近似口径：别名 owner 无法解析）
    const aliases = await this.prisma.alias.findMany({ take: 1000, select: { ownerId: true, ownerType: true } });
    let orphanAliases = 0;
    for (const a of aliases.slice(0, 200)) {
      const exists =
        a.ownerType === 'party'
          ? await this.prisma.customerParty.findFirst({ where: { id: a.ownerId, deletedAt: null } })
          : a.ownerType === 'project'
            ? await this.prisma.project.findFirst({ where: { id: a.ownerId, deletedAt: null } })
            : await this.prisma.material.findFirst({ where: { id: a.ownerId, deletedAt: null } });
      if (!exists) orphanAliases++;
    }
    // 一物多码存量（名称归一后同组 ≥2）
    const bench = await this.dupWorkbench();
    return {
      materialTotal,
      mappedOldCodes: mappedOld,
      frozenOldCodes: frozenOld,
      partyTotal: partiesNoAlias,
      orphanAliases,
      dupGroups: bench.totalGroups,
      govOpenTasks: govOpen,
    };
  }

  /** FR-M13-003 归一词典 CRUD（录入端前置校验共用） */
  async addNormDict(type: string, raw: string, normalized: string, userId: bigint) {
    if (!['typo', 'symbol', 'unit', 'sep'].includes(type)) throw BizException.param('type 非法');
    const row = await this.prisma.normDict.upsert({
      where: { type_raw: { type, raw } },
      create: { type, raw, normalized, source: 'manual', createdBy: userId, updatedBy: userId },
      update: { normalized, updatedBy: userId },
    });
    return row;
  }

  /** FR-M13-005 存量清洗相似候选（名称归一候选，人工确认后回写别名库） */
  async cleanCandidates(target: string, scope: 'party' | 'project' | 'material', threshold = 0.85) {
    let rows: Array<{ id: bigint; name: string }> = [];
    if (scope === 'party') {
      rows = await this.prisma.customerParty.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, name: true } });
    } else if (scope === 'project') {
      rows = await this.prisma.project
        .findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, standardName: true } })
        .then((rs) => rs.map((r) => ({ id: r.id, name: r.standardName })));
    } else {
      rows = await this.prisma.material.findMany({ where: { deletedAt: null }, take: 2000, select: { id: true, name: true } });
    }
    const hits = rows
      .map((r) => ({ id: r.id.toString(), name: r.name, score: similarity(target, r.name) }))
      .filter((h) => h.score >= threshold)
      .sort((a, b) => b.score - a.score)
      .slice(0, 20);
    return hits;
  }
}
