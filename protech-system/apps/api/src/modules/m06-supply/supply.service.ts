import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** E-10 工单状态机 */
const WO_TRANSITIONS: Record<string, string[]> = {
  planned: ['kit_checked', 'started'],
  kit_checked: ['started'],
  started: ['reported'],
  reported: ['finished'],
  finished: ['closed'],
  closed: [],
};

/**
 * M06 采购与库房（FR-M06-001~007/016/019~021 的 S3 集）。
 * 源头单据驱动+扫码过账：无单据不可入库/出库挂项目（BR-03）；幂等键防重；T+ 推送队列。
 */
@Injectable()
export class SupplyService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 工单与齐套（FR-M05-005） ----------------

  async createWorkOrder(body: Record<string, unknown>, userId: bigint) {
    const woCode = await this.codes.next('work_order');
    const projectId = parseIdOrNull(body.projectId);
    if (projectId) {
      const p = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
      if (!p) throw BizException.notFound('项目不存在');
    } else {
      // BR-03：无项目编码不排产
      throw BizException.br('BR-03', '该操作需要项目编码，当前单据未关联项目');
    }
    const qty = Number(body.qty ?? 0);
    if (!Number.isInteger(qty) || qty <= 0) throw BizException.param('qty 必须为正整数');
    const wo = await this.prisma.workOrder.create({
      data: {
        woCode,
        projectId,
        contractId: parseIdOrNull(body.contractId),
        bomId: parseIdOrNull(body.bomId),
        qty,
        planStart: body.planStart ? new Date(String(body.planStart)) : null,
        planEnd: body.planEnd ? new Date(String(body.planEnd)) : null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'WorkOrder', objectId: wo.id, after: { woCode } });
    return wo;
  }

  /** 齐套检查：按工单 BOM 展开比对可用库存 → pass/short；缺料自动生成请购草稿 + 红灯预警 */
  async kitCheck(workOrderId: bigint, userId: bigint) {
    const wo = await this.prisma.workOrder.findFirst({ where: { id: workOrderId, deletedAt: null } });
    if (!wo) throw BizException.notFound('工单不存在');
    if (!wo.bomId) throw BizException.param('工单未关联 BOM');
    const lines = await this.prisma.bomLine.findMany({ where: { bomId: wo.bomId, deletedAt: null } });
    const project = wo.projectId ? await this.prisma.project.findFirst({ where: { id: wo.projectId } }) : null;

    const shorts: Array<{ materialId: string; materialCode: string; name: string; need: number; have: number; short: number }> = [];
    for (const line of lines) {
      const need = Number(line.qty) * wo.qty;
      const invs = await this.prisma.inventory.findMany({ where: { materialId: line.materialId, deletedAt: null } });
      const have = invs.reduce((sum, inv) => sum + Number(inv.qty), 0);
      if (have < need) {
        const m = await this.prisma.material.findFirst({ where: { id: line.materialId } });
        shorts.push({
          materialId: line.materialId.toString(),
          materialCode: m?.materialCode ?? '?',
          name: m?.name ?? '?',
          need,
          have,
          short: Number((need - have).toFixed(3)),
        });
      }
    }

    if (shorts.length === 0) {
      await this.prisma.workOrder.update({
        where: { id: workOrderId },
        data: { kitStatus: 'pass', status: 'kit_checked', updatedBy: userId },
      });
      await this.audit.log({ action: 'kit_check', objectType: 'WorkOrder', objectId: workOrderId, after: { result: 'pass' } });
      return { result: 'pass' as const, shorts: [] };
    }

    // 缺料 → 请购草稿（FR-M06-002 挂项目码）
    const prCode = await this.codes.next('purchase_req');
    const req = await this.prisma.purchaseReq.create({
      data: {
        prCode,
        source: 'kit_short',
        projectId: wo.projectId,
        workOrderId: wo.id,
        lines: JSON.stringify(shorts.map((s) => ({ materialId: s.materialId, qty: s.short, note: `齐套缺口（工单 ${wo.woCode}）` }))),
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.prisma.workOrder.update({
      where: { id: workOrderId },
      data: { kitStatus: 'short', kitShortLines: JSON.stringify(shorts), updatedBy: userId },
    });
    // 红灯预警：③ 供应链板自动建卡（人工只维护红灯的例外——系统自动挂）
    const supplyBoard = await this.prisma.board.findFirst({ where: { boardCode: 'supply' } });
    if (supplyBoard) {
      await this.prisma.kbCard.create({
        data: {
          boardId: supplyBoard.id,
          title: `工单 ${wo.woCode} 齐套缺料 ${shorts.length} 项（请购 ${prCode}）`,
          ownerId: userId,
          dueDate: new Date(Date.now() + 3 * 86400000),
          status: 'blocked',
          blockedReason: `缺料：${shorts.map((s) => `${s.materialCode}×${s.short}`).join('；')}`,
          nextAction: `处理请购单 ${prCode}（到货后重检齐套）`,
          sourceType: 'work_order',
          sourceId: wo.id.toString(),
          createdBy: userId,
          updatedBy: userId,
        },
      });
    }
    if (project?.pmUserId) {
      await this.notify.send({
        userId: project.pmUserId,
        type: 'remind',
        title: `工单 ${wo.woCode} 齐套缺料 ${shorts.length} 项，已生成请购 ${prCode} 并挂红灯`,
        bizType: 'work_order',
        bizId: wo.id.toString(),
      });
    }
    await this.audit.log({ action: 'kit_check', objectType: 'WorkOrder', objectId: workOrderId, after: { result: 'short', prCode } });
    return { result: 'short' as const, shorts, purchaseReqCode: prCode };
  }

  async workOrderTransition(workOrderId: bigint, to: string, userId: bigint) {
    const wo = await this.prisma.workOrder.findFirst({ where: { id: workOrderId, deletedAt: null } });
    if (!wo) throw BizException.notFound('工单不存在');
    const allowed = WO_TRANSITIONS[wo.status] ?? [];
    if (!allowed.includes(to)) throw BizException.validation(`状态不允许：${wo.status} → ${to}`);
    if (to === 'started' && wo.kitStatus !== 'pass') {
      throw BizException.validation('开工前齐套通过为卡点（FR-M05-005 可配）：请先执行齐套检查并通过');
    }
    await this.prisma.workOrder.update({ where: { id: workOrderId }, data: { status: to, updatedBy: userId } });
    await this.audit.log({ action: 'wo_status', objectType: 'WorkOrder', objectId: workOrderId, before: { status: wo.status }, after: { status: to } });
    return { status: to };
  }

  // ---------------- 请购 / 采购（FR-M06-001/003） ----------------

  async createPurchaseReq(body: Record<string, unknown>, userId: bigint) {
    const prCode = await this.codes.next('purchase_req');
    const req = await this.prisma.purchaseReq.create({
      data: {
        prCode,
        source: 'manual',
        projectId: parseIdOrNull(body.projectId),
        lines: JSON.stringify(Array.isArray(body.lines) ? body.lines : []),
        createdBy: userId,
        updatedBy: userId,
      },
    });
    return req;
  }

  async reviewPurchaseReq(reqId: bigint, userId: bigint, approved: boolean) {
    const req = await this.prisma.purchaseReq.findFirst({ where: { id: reqId, deletedAt: null } });
    if (!req) throw BizException.notFound('请购单不存在');
    if (req.status !== 'pending') throw BizException.validation('请购单已处理');
    await this.prisma.purchaseReq.update({
      where: { id: reqId },
      data: { status: approved ? 'approved' : 'rejected', approverId: userId, reviewedAt: new Date() },
    });
    await this.audit.log({ action: 'pr_review', objectType: 'PurchaseReq', objectId: reqId, after: { approved } });
    return { status: approved ? 'approved' : 'rejected' };
  }

  async createPurchaseOrder(body: Record<string, unknown>, userId: bigint) {
    const supplierId = parseIdOrNull(body.supplierId);
    if (!supplierId) throw BizException.param('supplierId 必填（BR-08 下拉）');
    const supplier = await this.prisma.supplier.findFirst({ where: { id: supplierId, deletedAt: null } });
    if (!supplier) throw BizException.notFound('供应商不存在');
    const lines = Array.isArray(body.lines) ? (body.lines as Array<Record<string, unknown>>) : [];
    if (!lines.length) throw BizException.param('lines 必填');
    for (const l of lines) {
      if (!parseIdOrNull(l.materialId)) throw BizException.param('lines[].materialId 必填');
    }
    const poCode = await this.codes.next('purchase_order');
    const po = await this.prisma.purchaseOrder.create({
      data: {
        poCode,
        supplierId,
        projectId: parseIdOrNull(body.projectId),
        reqId: parseIdOrNull(body.reqId),
        promisedAt: body.promisedAt ? new Date(String(body.promisedAt)) : null,
        lines: JSON.stringify(lines.map((l) => ({ ...l, arrivedQty: 0 }))),
        status: 'ordered',
        createdBy: userId,
        updatedBy: userId,
      },
    });
    if (body.reqId) {
      await this.prisma.purchaseReq.updateMany({
        where: { id: parseId(body.reqId), status: 'approved' },
        data: { status: 'converted' },
      });
    }
    await this.audit.log({ action: 'create', objectType: 'PurchaseOrder', objectId: po.id, after: { poCode } });
    return po;
  }

  // ---------------- 扫码过账（FR-M06-004/005，幂等，11 册 §5.2） ----------------

  /**
   * 扫码过账：无单据不可入库；旧码→md_code_map 换主码（映射外旧码拦截 FR-M06-021）；
   * 幂等键=单据行+批号；事务：库存更新 + stock_move + T+ 推送队列。
   */
  async stockMove(body: Record<string, unknown>, userId: bigint) {
    const moveType = String(body.moveType ?? '');
    if (!['in', 'out'].includes(moveType)) throw BizException.param('moveType 必须为 in/out');
    const sourceType = String(body.sourceType ?? '');
    if (!['arrival', 'ship', 'pick', 'adjust', 'return'].includes(sourceType)) {
      throw BizException.param('sourceType 必须为 arrival/ship/pick/adjust/return');
    }
    const sourceId = String(body.sourceId ?? '');
    if (!sourceId) throw BizException.param('源头单据必填（无单据不可过账，FR-M06-004）');

    // 幂等：同键直接返回原流水（断网重传去重）
    const idempotencyKey = String(body.idempotencyKey ?? `${sourceType}:${sourceId}:${String(body.materialCode ?? '')}:${String(body.batch ?? '')}:${String(body.qty ?? '')}`);
    const existing = await this.prisma.stockMove.findUnique({ where: { idempotencyKey } });
    if (existing) {
      return { id: existing.id.toString(), duplicated: true, tPlusStatus: existing.tPlusStatus };
    }

    // 旧码解析（FR-M01-005/FR-M06-021）：映射表外旧码 → 拦截+记录
    let materialCode = String(body.materialCode ?? '').trim();
    if (!materialCode) throw BizException.param('materialCode（扫码输入）必填');
    let material = await this.prisma.material.findFirst({ where: { materialCode, deletedAt: null } });
    if (!material) {
      const mapped = await this.prisma.mdCodeMap.findFirst({ where: { oldCode: materialCode } });
      if (mapped) {
        materialCode = mapped.masterCode;
        material = await this.prisma.material.findFirst({ where: { materialCode, deletedAt: null } });
      } else {
        await this.audit.log({
          action: 'dq_unmapped_code',
          objectType: 'StockMove',
          objectId: null,
          after: { oldCode: materialCode, sourceId },
        });
        throw BizException.validation(
          `扫码编码 ${materialCode} 不在主数据且不在归并映射表（FR-M06-021 防脏数据拦截）；请先治理归并或改用主码`,
        );
      }
    }
    if (!material) throw BizException.notFound(`物料不存在：${materialCode}`);

    const qty = Number(body.qty);
    if (!Number.isFinite(qty) || qty <= 0) throw BizException.param('qty 必须为正数');
    const legalEntity = String(body.legalEntity ?? 'PRT');
    if (!['PRT', 'TLD'].includes(legalEntity)) throw BizException.param('legalEntity 必须为 PRT/TLD（两司隔离 FR-M06-016）');

    let projectId = parseIdOrNull(body.projectId);

    if (moveType === 'in') {
      // 入库：必须挂到货单（采购单行匹配）
      if (sourceType !== 'arrival') throw BizException.validation('入库必须源自到货单（arrival，源头单据驱动）');
      const po = await this.prisma.purchaseOrder.findFirst({ where: { poCode: sourceId, deletedAt: null } });
      if (!po) {
        throw BizException.validation(`到货来源采购单不存在：${sourceId}（无单据不可入库，FR-M06-004 源头单据驱动）`);
      }
      const lines = JSON.parse(po.lines || '[]') as Array<{ materialId: string; qty: number; arrivedQty: number }>;
      const line = lines.find((l) => l.materialId === material.id.toString());
      if (!line) throw BizException.validation(`物料 ${material.materialCode} 不在采购单 ${sourceId} 行内（无单据不可入库）`);
      const pending = line.qty - line.arrivedQty;
      if (qty > pending + 1e-9) {
        throw BizException.validation(`入库数量超采购单剩余：待收 ${pending}，扫码 ${qty}`);
      }
      line.arrivedQty = Number((line.arrivedQty + qty).toFixed(3));
      const allArrived = lines.every((l) => l.arrivedQty >= l.qty - 1e-9);
      const anyArrived = lines.some((l) => l.arrivedQty > 0);
      await this.prisma.purchaseOrder.update({
        where: { id: po.id },
        data: {
          lines: JSON.stringify(lines),
          status: allArrived ? 'arrived' : anyArrived ? 'partial_arrived' : po.status,
          updatedBy: userId,
        },
      });
    } else {
      // 出库：发货/领料必须挂项目码（BR-03 无项目编码不发货）
      if (sourceType === 'ship' && !projectId) {
        throw BizException.br('BR-03', '该操作需要项目编码，当前单据未关联项目');
      }
      if (!projectId && sourceType === 'pick') {
        // 领料允许无项目（车间通用件），但发货必须挂项目
        projectId = null;
      }
      const invs = await this.prisma.inventory.findMany({
        where: { materialId: material.id, legalEntity, deletedAt: null },
      });
      const have = invs.reduce((s, i) => s + Number(i.qty), 0);
      if (sourceType !== 'adjust' && have < qty) {
        throw BizException.validation(`可用库存不足：账面 ${have}，出库 ${qty}（BR 负库存拦截）`);
      }
    }

    // 事务：库存更新 + 流水写入
    const batch = (body.batch as string) ?? null;
    const warehouse = String(body.warehouse ?? 'YL');
    const locationCode = (body.locationCode as string) ?? null;
    const move = await this.prisma.$transaction(async (tx) => {
      if (moveType === 'in') {
        const existing = await tx.inventory.findFirst({
          where: { materialId: material!.id, warehouse, locationCode, batch, legalEntity, deletedAt: null },
        });
        if (existing) {
          await tx.inventory.update({ where: { id: existing.id }, data: { qty: { increment: qty } } });
        } else {
          await tx.inventory.create({
            data: { materialId: material!.id, warehouse, locationCode, batch, legalEntity, qty, createdBy: userId, updatedBy: userId },
          });
        }
      } else {
        // 先进先出扣减
        const invs = await tx.inventory.findMany({
          where: { materialId: material!.id, legalEntity, deletedAt: null },
          orderBy: { updatedAt: 'asc' },
        });
        let remain = qty;
        for (const inv of invs) {
          if (remain <= 0) break;
          const take = Math.min(Number(inv.qty), remain);
          if (take > 0) {
            await tx.inventory.update({ where: { id: inv.id }, data: { qty: { decrement: take } } });
            remain = Number((remain - take).toFixed(3));
          }
        }
      }
      return tx.stockMove.create({
        data: {
          moveType,
          sourceType,
          sourceId,
          sourceLine: (body.sourceLine as string) ?? null,
          materialId: material!.id,
          qty,
          projectId,
          legalEntity,
          operatorId: userId,
          idempotencyKey,
          movedAt: new Date(),
        },
      });
    });

    await this.audit.log({ action: 'stock_move', objectType: 'StockMove', objectId: move.id, after: { moveType, sourceId, materialCode, qty } });
    return { id: move.id.toString(), duplicated: false, materialCode, tPlusStatus: move.tPlusStatus };
  }

  // ---------------- T+ 推送队列（FR-M06-007） ----------------

  /** worker 调用：pending → pushed（生成 T+ 可导入行集） */
  async pushQueueBatch(): Promise<{ pushed: number }> {
    const pending = await this.prisma.stockMove.findMany({ where: { tPlusStatus: 'pending' }, take: 100 });
    for (const m of pending) {
      await this.prisma.stockMove.update({
        where: { id: m.id },
        data: { tPlusStatus: 'pushed', tPlusRef: `T+${m.id}` },
      });
    }
    return { pushed: pending.length };
  }

  /** 财务下载导入包（T+ 可导入 CSV 行集） */
  async exportPushQueue(): Promise<{ rows: Array<Record<string, string | number>> }> {
    const moves = await this.prisma.stockMove.findMany({
      where: { tPlusStatus: 'pushed' },
      orderBy: { id: 'asc' },
      take: 500,
    });
    const materialIds = [...new Set(moves.map((m) => m.materialId))];
    const materials = materialIds.length ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    const rows = moves.map((m) => ({
      单据号: m.sourceId,
      方向: m.moveType === 'in' ? '入库' : '出库',
      存货编码: mById.get(m.materialId)?.materialCode ?? '',
      存货名称: mById.get(m.materialId)?.name ?? '',
      数量: Number(m.qty),
    	仓库: 'YL',
      日期: m.movedAt.toISOString().slice(0, 10),
      流水号: m.id.toString(),
    }));
    return { rows };
  }

  /** 财务回执确认：pushed → confirmed */
  async confirmPushed(ids: string[], userId: bigint): Promise<{ confirmed: number }> {
    const res = await this.prisma.stockMove.updateMany({
      where: { id: { in: ids.map((x) => parseId(x)) }, tPlusStatus: 'pushed' },
      data: { tPlusStatus: 'confirmed' },
    });
    await this.audit.log({ action: 'tplus_confirm', objectType: 'StockMove', objectId: null, after: { count: res.count } });
    void userId;
    return { confirmed: res.count };
  }

  // ---------------- 库存查询（FR-M06-006/019） ----------------

  async inventoryList(q: Record<string, string>) {
    let materialCodeFilter: bigint[] | null = null;
    if (q.materialCode) {
      const ms = await this.prisma.material.findMany({
        where: { OR: [{ materialCode: { contains: q.materialCode } }, { name: { contains: q.materialCode } }] },
        select: { id: true },
      });
      materialCodeFilter = ms.map((m) => m.id);
    }
    const where = {
      deletedAt: null,
      ...(q.materialId ? { materialId: parseId(q.materialId) } : {}),
      ...(materialCodeFilter ? { materialId: { in: materialCodeFilter } } : {}),
      ...(q.legalEntity ? { legalEntity: q.legalEntity } : {}),
      ...(q.warehouse ? { warehouse: q.warehouse } : {}),
    };
    const list = await this.prisma.inventory.findMany({
      where,
      take: 200,
      orderBy: { id: 'asc' },
    });
    const materialIds = [...new Set(list.map((i) => i.materialId))];
    const materials = materialIds.length ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    return list.map((i) => {
      const m = mById.get(i.materialId);
      const qty = Number(i.qty);
      const low = m?.safetyStockMin ? qty < Number(m.safetyStockMin) : false;
      return {
        id: i.id.toString(),
        materialId: i.materialId.toString(),
        materialCode: m?.materialCode ?? '',
        materialName: m?.name ?? '',
        spec: m?.spec ?? null,
        unit: m?.unit ?? '',
        warehouse: i.warehouse,
        locationCode: i.locationCode,
        batch: i.batch,
        legalEntity: i.legalEntity,
        qty,
        threeColor: i.threeColor,
        lowStockAlert: low,
      };
    });
  }

  /** FR-M06-019 每日日终快照（worker 23:30 调用） */
  async dailySnapshot(): Promise<{ period: string; totalSkus: number }> {
    const period = new Date().toISOString().slice(0, 10);
    const invs = await this.prisma.inventory.findMany({ where: { deletedAt: null, qty: { not: 0 } } });
    const payload = invs.map((i) => ({
      materialId: i.materialId.toString(),
      warehouse: i.warehouse,
      legalEntity: i.legalEntity,
      qty: Number(i.qty),
    }));
    await this.prisma.inventorySnapshot.upsert({
      where: { period },
      create: { period, payload: JSON.stringify(payload), totalSkus: payload.length },
      update: { payload: JSON.stringify(payload), totalSkus: payload.length },
    });
    return { period, totalSkus: payload.length };
  }

  /** 出入库流水（FR-M06-020） */
  async moveList(q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      ...(q.materialId ? { materialId: parseId(q.materialId) } : {}),
      ...(q.moveType ? { moveType: q.moveType } : {}),
      ...(q.tPlusStatus ? { tPlusStatus: q.tPlusStatus } : {}),
      ...(q.projectId ? { projectId: parseId(q.projectId) } : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.stockMove.findMany({ where, orderBy: { id: 'desc' }, skip: (page - 1) * pageSize, take: pageSize }),
      this.prisma.stockMove.count({ where }),
    ]);
    return {
      list: list.map((m) => ({ ...m, id: m.id.toString(), materialId: m.materialId.toString(), projectId: m.projectId?.toString() ?? null, qty: Number(m.qty) })),
      total,
      page,
      pageSize,
    };
  }
}
