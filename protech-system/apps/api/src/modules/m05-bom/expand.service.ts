import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/**
 * M05 R2：B 类参数化展开（FR-M05-006）+ C 类工程 BOM/套件包（FR-M05-007）+ M07 点位销项（FR-M07-003）。
 */
@Injectable()
export class ExpandService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- B 类参数化（FR-M05-006） ----------------

  async createParamTemplate(body: Record<string, unknown>, userId: bigint) {
    if (!body.templateCode || !body.productLine || !body.standardSectionBomId) {
      throw BizException.param('templateCode/productLine/standardSectionBomId 必填');
    }
    const tpl = await this.prisma.paramTemplate.create({
      data: {
        templateCode: String(body.templateCode),
        productLine: String(body.productLine),
        standardSectionBomId: parseId(body.standardSectionBomId),
        accessoryPackBomId: parseIdOrNull(body.accessoryPackBomId),
        params: JSON.stringify(Array.isArray(body.params) ? body.params : []),
        createdBy: userId,
      },
    });
    return tpl;
  }

  /**
   * 参数展开：录入塔高/塔型参数 → 标准节×数量 + 定制段 + 附件包 → 生成 BOM（B 类，含行）。
   * 展开规则：sectionCount = ceil(塔高 / 每节高度)（params 定义 perSection 键）。
   */
  async expand(body: Record<string, unknown>, userId: bigint) {
    const tpl = await this.prisma.paramTemplate.findFirst({
      where: { templateCode: String(body.templateCode ?? '') },
    });
    if (!tpl) throw BizException.notFound('参数模板不存在');
    const paramDefs = JSON.parse(tpl.params || '[]') as Array<{ key: string; label: string; perSection?: boolean }>;
    const values = (body.params ?? {}) as Record<string, number>;
    const height = Number(values.height ?? 0);
    if (height <= 0) throw BizException.param('params.height（塔高 m）必填且 >0');
    const sectionHeight = paramDefs.find((p) => p.perSection)?.key
      ? Number(values[paramDefs.find((p) => p.perSection)!.key] ?? 0)
      : 0;
    const perSection = sectionHeight > 0 ? sectionHeight : 3; // 默认每节 3m
    const sectionCount = Math.ceil(height / perSection);

    // 标准节 BOM 行 × sectionCount
    const sectionBom = await this.prisma.bom.findFirst({ where: { id: tpl.standardSectionBomId, deletedAt: null } });
    if (!sectionBom) throw BizException.notFound('标准节 BOM 不存在');
    const sectionLines = await this.prisma.bomLine.findMany({ where: { bomId: sectionBom.id, deletedAt: null } });

    const bomCode = await this.codes.next('bom');
    const bom = await this.prisma.bom.create({
      data: {
        bomCode,
        bomType: 'param',
        productLine: tpl.productLine,
        name: `${tpl.templateCode} 参数化展开（塔高 ${height}m × ${sectionCount} 节）`,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    for (const [i, line] of sectionLines.entries()) {
      await this.prisma.bomLine.create({
        data: {
          bomId: bom.id,
          materialId: line.materialId,
          qty: Number((Number(line.qty) * sectionCount).toFixed(3)),
          sort: i,
          createdBy: userId,
          updatedBy: userId,
        },
      });
    }
    // 附件包行（1 套）
    if (tpl.accessoryPackBomId) {
      const accLines = await this.prisma.bomLine.findMany({ where: { bomId: tpl.accessoryPackBomId, deletedAt: null } });
      for (const [i, line] of accLines.entries()) {
        await this.prisma.bomLine.create({
          data: {
            bomId: bom.id,
            materialId: line.materialId,
            qty: Number(line.qty),
            sort: sectionLines.length + i,
            createdBy: userId,
            updatedBy: userId,
          },
        });
      }
    }
    await this.audit.log({
      action: 'param_expand',
      objectType: 'Bom',
      objectId: bom.id,
      after: { bomCode, height, sectionCount },
    });
    return { id: bom.id.toString(), bomCode, sectionCount, note: `标准节×${sectionCount} + 附件包×1` };
  }

  // ---------------- C 类工程 BOM（FR-M05-007） ----------------

  async createKitPack(body: Record<string, unknown>, userId: bigint) {
    if (!body.kitCode || !body.name) throw BizException.param('kitCode/name 必填');
    const kit = await this.prisma.kitPack.create({
      data: {
        kitCode: String(body.kitCode),
        name: String(body.name),
        lines: JSON.stringify(Array.isArray(body.lines) ? body.lines : []),
        createdBy: userId,
      },
    });
    return kit;
  }

  /** C 类工程 BOM：点位清单（机舱/塔底/塔筒/升压站 × 物料）生成 BOM；支持套件包引用 */
  async createEngBom(body: Record<string, unknown>, userId: bigint) {
    if (!body.name || !body.projectId) throw BizException.param('name/projectId 必填');
    const positions = Array.isArray(body.positions) ? (body.positions as Array<Record<string, unknown>>) : [];
    if (!positions.length) throw BizException.param('positions 必填（[{positionCode, materialId, qty}] 或 [{kitCode}]）');
    const bomCode = await this.codes.next('bom');
    const bom = await this.prisma.bom.create({
      data: {
        bomCode,
        bomType: 'eng',
        productLine: String(body.productLine ?? 'video'),
        name: String(body.name),
        createdBy: userId,
        updatedBy: userId,
      },
    });
    let sort = 0;
    for (const pos of positions) {
      if (pos.kitCode) {
        const kit = await this.prisma.kitPack.findFirst({ where: { kitCode: String(pos.kitCode) } });
        if (!kit) throw BizException.notFound(`套件包不存在：${pos.kitCode}`);
        const kitLines = JSON.parse(kit.lines || '[]') as Array<{ materialId: string; qty: number }>;
        for (const kl of kitLines) {
          await this.prisma.bomLine.create({
            data: {
              bomId: bom.id,
              materialId: parseId(kl.materialId),
              qty: kl.qty,
              positionCode: String(pos.positionCode ?? ''),
              kitRefId: kit.id,
              sort: sort++,
              createdBy: userId,
              updatedBy: userId,
            },
          });
        }
      } else {
        await this.prisma.bomLine.create({
          data: {
            bomId: bom.id,
            materialId: parseId(pos.materialId),
            qty: Number(pos.qty ?? 1),
            positionCode: String(pos.positionCode ?? ''),
            sort: sort++,
            createdBy: userId,
            updatedBy: userId,
          },
        });
      }
    }
    await this.audit.log({ action: 'create', objectType: 'Bom(eng)', objectId: bom.id, after: { bomCode, positions: positions.length } });
    return { id: bom.id.toString(), bomCode };
  }

  // ---------------- 点位销项（FR-M07-003） ----------------

  /** 从工程 BOM 生成派工单点位清单 */
  async generatePunchList(dispatchId: bigint, bomId: bigint, userId: bigint) {
    const dispatch = await this.prisma.dispatch.findFirst({ where: { id: dispatchId, deletedAt: null } });
    if (!dispatch) throw BizException.notFound('派工单不存在');
    const lines = await this.prisma.bomLine.findMany({ where: { bomId, deletedAt: null }, orderBy: { sort: 'asc' } });
    if (!lines.length) throw BizException.validation('工程 BOM 无行，无法生成点位清单');
    const existing = await this.prisma.punchItem.count({ where: { dispatchId } });
    if (existing > 0) throw BizException.validation('该派工单已生成点位清单');
    for (const line of lines) {
      await this.prisma.punchItem.create({
        data: {
          dispatchId,
          bomLineId: line.id,
          positionCode: line.positionCode ?? '',
          materialId: line.materialId,
          requiredQty: Math.ceil(Number(line.qty)),
          createdBy: userId,
        },
      });
    }
    return { generated: lines.length };
  }

  /** 销项确认：装一个勾一个；缺件 → 红灯（⑤板） */
  async punch(punchItemId: bigint, doneQty: number, userId: bigint, shortage?: string) {
    const item = await this.prisma.punchItem.findFirst({ where: { id: punchItemId } });
    if (!item) throw BizException.notFound('点位不存在');
    if (doneQty < 0 || doneQty > item.requiredQty) throw BizException.param('doneQty 超出需求数');
    await this.prisma.punchItem.update({
      where: { id: punchItemId },
      data: { doneQty, doneAt: new Date(), doneBy: userId, shortageFlag: !!shortage, note: shortage ?? null },
    });
    if (shortage) {
      const deliveryBoard = await this.prisma.board.findFirst({ where: { boardCode: 'delivery' } });
      if (deliveryBoard) {
        const material = await this.prisma.material.findFirst({ where: { id: item.materialId } });
        const dispatch = await this.prisma.dispatch.findFirst({ where: { id: item.dispatchId } });
        await this.prisma.kbCard.create({
          data: {
            boardId: deliveryBoard.id,
            title: `销项缺件：${material?.name ?? ''}（点位 ${item.positionCode}，派工 ${dispatch?.pgCode ?? ''}）`,
            ownerId: userId,
            dueDate: new Date(Date.now() + 3 * 86400000),
            status: 'blocked',
            blockedReason: shortage,
            nextAction: '补发物料后继续销项',
            sourceType: 'dispatch',
            sourceId: item.dispatchId.toString(),
            createdBy: userId,
            updatedBy: userId,
          },
        });
      }
    }
    const all = await this.prisma.punchItem.findMany({ where: { dispatchId: item.dispatchId } });
    const total = all.reduce((s, i) => s + i.requiredQty, 0);
    const done = all.reduce((s, i) => s + i.doneQty, 0);
    await this.audit.log({ action: 'punch', objectType: 'PunchItem', objectId: punchItemId, after: { doneQty, shortage } });
    return { progress: total ? Number((done / total).toFixed(4)) : 0, done, total };
  }
}
