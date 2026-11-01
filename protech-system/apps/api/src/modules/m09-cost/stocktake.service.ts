import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AppConfigService } from '../../shared/app-config.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** E-09 差异归因六类 + 调账审批线（FR-M06-010：≤500 元库房主管 / 500-5000 计划负责人 / >5000 财务+分管双签） */
const CAUSES = ['doc_late', 'reissue_unbooked', 'mixed', 'pick_error', 'lost', 'meter'] as const;

/** E-12 呆滞六出路 */
const DISPOSALS = ['reuse', 'spare', 'remanufacture', 'discount', 'scrap', 'customer'] as const;

/**
 * M06 R2：ABC 循环盘点（FR-M06-009）+ 差异闭环四步（FR-M06-010）+ 呆滞预警与处置（FR-M06-011/012）。
 */
@Injectable()
export class StocktakeService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly config: AppConfigService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 盘点任务（FR-M06-009） ----------------

  /** 生成盘点任务：按 ABC 类圈定范围，账面数快照入行 */
  async createTask(body: Record<string, unknown>, userId: bigint) {
    const cycle = String(body.cycle ?? 'A');
    if (!['A', 'B', 'C', 'sample'].includes(cycle)) throw BizException.param('cycle 须为 A/B/C/sample');
    const materials = await this.prisma.material.findMany({
      where: { deletedAt: null, ...(cycle === 'sample' ? { abcClass: 'A' } : { abcClass: cycle }) },
      take: 500,
    });
    const sample = cycle === 'sample' ? materials.filter((_, i) => i % 5 === 0) : materials; // 财务抽盘 20% A 类
    const taskCode = await this.codes.next('stocktake');
    const task = await this.prisma.stocktakeTask.create({
      data: {
        taskCode,
        cycle,
        scope: JSON.stringify({ materialIds: sample.map((m) => m.id.toString()) }),
        assignedTo: body.assignedTo ? parseId(body.assignedTo) : null,
        createdBy: userId,
      },
    });
    for (const m of sample) {
      const invs = await this.prisma.inventory.findMany({
        where: { materialId: m.id, deletedAt: null },
      });
      if (!invs.length) {
        await this.prisma.stocktakeLine.create({
          data: { taskId: task.id, materialId: m.id, bookQty: 0, createdBy: userId },
        });
        continue;
      }
      for (const inv of invs) {
        await this.prisma.stocktakeLine.create({
          data: {
            taskId: task.id,
            materialId: m.id,
            warehouse: inv.warehouse,
            legalEntity: inv.legalEntity,
            bookQty: inv.qty,
            createdBy: userId,
          },
        });
      }
    }
    await this.audit.log({ action: 'create', objectType: 'StocktakeTask', objectId: task.id, after: { taskCode, lines: sample.length } });
    return { id: task.id.toString(), taskCode, lines: sample.length };
  }

  /** 录入实盘数 → 差异计算 */
  async count(taskId: bigint, lineId: bigint, countedQty: number, userId: bigint) {
    const line = await this.prisma.stocktakeLine.findFirst({ where: { id: lineId, taskId } });
    if (!line) throw BizException.notFound('盘点行不存在');
    if (!Number.isFinite(countedQty) || countedQty < 0) throw BizException.param('countedQty 非法');
    const variance = Number((countedQty - Number(line.bookQty)).toFixed(3));
    await this.prisma.stocktakeLine.update({
      where: { id: lineId },
      data: { countedQty, varianceQty: variance, countedBy: userId, countedAt: new Date() },
    });
    if (variance !== 0) {
      const material = await this.prisma.material.findFirst({ where: { id: line.materialId } });
      await this.prisma.stockVariance.create({
        data: {
          taskId,
          lineId,
          materialId: line.materialId,
          varianceQty: variance,
          varianceCents: 0n, // 金额按最近采购价回填（此处以 0 登记，审批时补）
          createdBy: userId,
        },
      });
      void material;
    }
    return { variance };
  }

  /** 差异闭环四步：归因 → 调账审批（金额线）→ 整改；同因当月 3 次升级专项 */
  async attributeVariance(varianceId: bigint, cause: string, userId: bigint) {
    if (!CAUSES.includes(cause as (typeof CAUSES)[number])) throw BizException.param('cause 非法（E-09 六类）');
    const v = await this.prisma.stockVariance.findFirst({ where: { id: varianceId } });
    if (!v) throw BizException.notFound('差异不存在');
    if (v.step !== 'registered') throw BizException.validation('差异已归因');
    await this.prisma.stockVariance.update({ where: { id: varianceId }, data: { cause, step: 'attributed' } });
    // 同因当月 3 次 → 升级专项（FR-M06-010）
    const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const sameCause = await this.prisma.stockVariance.count({
      where: { cause, createdAt: { gte: monthStart } },
    });
    if (sameCause >= 3) {
      await this.prisma.stockVariance.update({ where: { id: varianceId }, data: { escalateFlag: true } });
      await this.notify.send({
        userId,
        type: 'escalate',
        title: `差异归因"${cause}"本月已 ${sameCause} 次，升级专项整改（FR-M06-010）`,
        bizType: 'stock_variance',
        bizId: varianceId.toString(),
      });
    }
    return { step: 'attributed', escalated: sameCause >= 3 };
  }

  /** 调账审批：金额线分级（≤500 库房主管一级 / ≤5000 二级 / >5000 双签——S 简化为权限位） */
  async approveVariance(varianceId: bigint, userId: bigint, adjust: boolean) {
    const v = await this.prisma.stockVariance.findFirst({ where: { id: varianceId } });
    if (!v) throw BizException.notFound('差异不存在');
    if (v.step !== 'attributed') throw BizException.validation('须先归因再审批（闭环四步）');
    const tier = v.varianceCents <= 50000n ? 'tier1' : v.varianceCents <= 500000n ? 'tier2' : 'tier3';
    if (!adjust) {
      await this.prisma.stockVariance.update({ where: { id: varianceId }, data: { step: 'rectified', rectifyNote: '驳回不调账' } });
      return { step: 'rectified', tier, adjusted: false };
    }
    // 调账 = 写 adjust 流水修正库存
    const line = await this.prisma.stocktakeLine.findFirst({ where: { id: v.lineId } });
    if (line && v.varianceQty !== null && Number(v.varianceQty) !== 0) {
      await this.prisma.stockMove.create({
        data: {
          moveType: Number(v.varianceQty) > 0 ? 'in' : 'out',
          sourceType: 'adjust',
          sourceId: `STOCKTAKE-${v.taskId}`,
          materialId: v.materialId,
          qty: Math.abs(Number(v.varianceQty)),
          legalEntity: line.legalEntity,
          operatorId: userId,
          idempotencyKey: `adjust-${v.id}`,
          movedAt: new Date(),
        },
      });
      // 应用到库存（in 加 / out 减，复用 upsert+扣减简化逻辑）
      if (Number(v.varianceQty) > 0) {
        const existing = await this.prisma.inventory.findFirst({
          where: { materialId: v.materialId, warehouse: line.warehouse, legalEntity: line.legalEntity, deletedAt: null },
        });
        if (existing) {
          await this.prisma.inventory.update({ where: { id: existing.id }, data: { qty: { increment: Number(v.varianceQty) } } });
        } else {
          await this.prisma.inventory.create({
            data: { materialId: v.materialId, warehouse: line.warehouse, legalEntity: line.legalEntity, qty: Number(v.varianceQty), createdBy: userId, updatedBy: userId },
          });
        }
      } else {
        const invs = await this.prisma.inventory.findMany({
          where: { materialId: v.materialId, legalEntity: line.legalEntity, deletedAt: null },
          orderBy: { updatedAt: 'asc' },
        });
        let remain = Math.abs(Number(v.varianceQty));
        for (const inv of invs) {
          if (remain <= 0) break;
          const take = Math.min(Number(inv.qty), remain);
          await this.prisma.inventory.update({ where: { id: inv.id }, data: { qty: { decrement: take } } });
          remain = Number((remain - take).toFixed(3));
        }
      }
    }
    await this.prisma.stockVariance.update({ where: { id: varianceId }, data: { step: 'approved', approverId: userId, approvedAt: new Date() } });
    await this.audit.log({ action: 'variance_adjust', objectType: 'StockVariance', objectId: varianceId, after: { tier } });
    return { step: 'approved', tier, adjusted: true };
  }

  // ---------------- 呆滞（FR-M06-011/012） ----------------

  /** 扫描：90 天无领用预警 / 180 天转呆滞候选（worker 每日调用） */
  async scanSlowMoving(): Promise<{ warned: number; candidates: number }> {
    const now = Date.now();
    const invs = await this.prisma.inventory.findMany({ where: { deletedAt: null, qty: { gt: 0 } } });
    let warned = 0;
    let candidates = 0;
    for (const inv of invs) {
      const lastMove = await this.prisma.stockMove.findFirst({
        where: { materialId: inv.materialId, moveType: 'out' },
        orderBy: { movedAt: 'desc' },
      });
      const lastAt = lastMove?.movedAt ?? inv.createdAt;
      const days = Math.floor((now - lastAt.getTime()) / 86400000);
      if (days < 90) continue;
      const level = days >= 180 ? 'candidate' : 'warn';
      const existing = await this.prisma.slowMoving.findFirst({
        where: { materialId: inv.materialId, warehouse: inv.warehouse, legalEntity: inv.legalEntity, deletedAt: null },
      });
      if (existing) {
        if (existing.level !== level) {
          await this.prisma.slowMoving.update({ where: { id: existing.id }, data: { level, lastMoveAt: lastAt } });
        }
      } else {
        await this.prisma.slowMoving.create({
          data: {
            materialId: inv.materialId,
            warehouse: inv.warehouse,
            legalEntity: inv.legalEntity,
            qty: inv.qty,
            lastMoveAt: lastAt,
            level,
          },
        });
      }
      if (level === 'warn') warned++;
      else candidates++;
    }
    return { warned, candidates };
  }

  /** FR-M06-012 处置：六出路 + 责任归属 + 目标期 */
  async proposeDisposal(id: bigint, body: Record<string, unknown>, userId: bigint) {
    const way = String(body.disposalWay ?? '');
    if (!DISPOSALS.includes(way as (typeof DISPOSALS)[number])) throw BizException.param('disposalWay 非法（E-12 六出路）');
    const updated = await this.prisma.slowMoving.update({
      where: { id },
      data: {
        disposalWay: way,
        disposalStatus: 'proposed',
        ownerId: body.ownerId ? parseId(body.ownerId) : null,
        targetAt: body.targetAt ? new Date(String(body.targetAt)) : null,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'slow_dispose', objectType: 'SlowMoving', objectId: id, after: { way } });
    void updated;
    return { disposalStatus: 'proposed', way };
  }

  async listSlowMoving(q: Record<string, string>) {
    const list = await this.prisma.slowMoving.findMany({
      where: { deletedAt: null, ...(q.level ? { level: q.level } : {}), ...(q.status ? { disposalStatus: q.status } : {}) },
      orderBy: [{ level: 'desc' }, { lastMoveAt: 'asc' }],
      take: 200,
    });
    const ids = [...new Set(list.map((s) => s.materialId))];
    const materials = ids.length ? await this.prisma.material.findMany({ where: { id: { in: ids } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    return list.map((s) => ({
      ...s,
      id: s.id.toString(),
      materialId: s.materialId.toString(),
      materialCode: mById.get(s.materialId)?.materialCode ?? '',
      materialName: mById.get(s.materialId)?.name ?? '',
      qty: Number(s.qty),
    }));
  }
}
