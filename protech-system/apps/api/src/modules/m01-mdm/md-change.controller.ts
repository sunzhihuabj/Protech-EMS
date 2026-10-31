import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId } from '../../shared/serialize';

/**
 * FR-M01-011/012 主数据变更审批：
 * 项目三类结构变更（风场补全/主体变更/期次拆分）与主体/风场/物料关键字段变更；
 * 建档专员提交 → 商务部主管（R-CHAIN）审批 → 审批通过自动应用；历史留痕。
 */
@Controller('mdm/changes')
export class MdChangeController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  @Get()
  @RequirePermission('mdm:project:list')
  async list(@Query() q: Record<string, string>) {
    const where = {
      deletedAt: null,
      ...(q.status ? { status: q.status } : {}),
      ...(q.objectType ? { objectType: q.objectType } : {}),
    };
    const list = await this.prisma.mdChangeRequest.findMany({
      where,
      orderBy: { id: 'desc' },
      take: 100,
    });
    return list.map((c) => ({
      ...c,
      id: c.id.toString(),
      objectId: c.objectId.toString(),
      submittedBy: c.submittedBy.toString(),
      reviewedBy: c.reviewedBy?.toString() ?? null,
      changes: JSON.parse(c.changes || '[]'),
    }));
  }

  @Post()
  @RequirePermission('mdm:change:submit')
  async submit(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.objectType || !body.objectId || !body.reason) {
      throw BizException.param('objectType/objectId/reason 必填');
    }
    if (!['party', 'wind_farm', 'material', 'project'].includes(String(body.objectType))) {
      throw BizException.param('objectType 非法');
    }
    const changes = Array.isArray(body.changes) ? body.changes : [];
    if (!changes.length) throw BizException.param('changes 必填（[{field, before, after}]）');

    const oid = parseId(body.objectId);
    // 变更前值留痕：从对应表读取
    const table = String(body.objectType);
    let before: Record<string, unknown> = {};
    if (table === 'party') {
      const r = await this.prisma.customerParty.findFirst({ where: { id: oid, deletedAt: null } });
      before = r ? { name: r.name, groupId: r.groupId?.toString() ?? null } : {};
    } else if (table === 'wind_farm') {
      const r = await this.prisma.windFarm.findFirst({ where: { id: oid, deletedAt: null } });
      before = r ? { fullName: r.fullName, province: r.province, city: r.city, county: r.county } : {};
    } else if (table === 'material') {
      const r = await this.prisma.material.findFirst({ where: { id: oid, deletedAt: null } });
      before = r ? { name: r.name, spec: r.spec, unit: r.unit, category: r.category } : {};
    } else if (table === 'project') {
      const r = await this.prisma.project.findFirst({ where: { id: oid, deletedAt: null } });
      before = r ? { windFarmId: r.windFarmId?.toString() ?? null, ownerPartyId: r.ownerPartyId?.toString() ?? null, phase: r.phase } : {};
    }
    if (!Object.keys(before).length) throw BizException.notFound('变更对象不存在');

    const req = await this.prisma.mdChangeRequest.create({
      data: {
        objectType: table,
        objectId: oid,
        changes: JSON.stringify(changes),
        reason: String(body.reason),
        submittedBy: me.id,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'md_change_submit', objectType: 'MdChangeRequest', objectId: req.id, after: { before, changes } });
    return { id: req.id.toString(), status: req.status };
  }

  /** 审批（通过即应用变更；拒绝需说明）；仅 R-CHAIN/R-SYS */
  @Post(':id/review')
  @RequirePermission('mdm:change:review')
  async review(
    @Param('id') id: string,
    @Body() body: { approved?: boolean; note?: string },
    @CurrentUser() me: AuthUser,
  ) {
    const cid = parseId(id);
    const req = await this.prisma.mdChangeRequest.findFirst({ where: { id: cid, deletedAt: null } });
    if (!req) throw BizException.notFound('变更单不存在');
    if (req.status !== 'pending') throw BizException.validation('变更单已处理');
    if (!body.approved && !body.note) throw BizException.param('拒绝必须填写理由');

    if (body.approved) {
      const changes = JSON.parse(req.changes || '[]') as Array<{ field: string; after: unknown }>;
      await this.apply(req.objectType, req.objectId, changes, me.id);
    }
    const updated = await this.prisma.mdChangeRequest.update({
      where: { id: cid },
      data: {
        status: body.approved ? 'approved' : 'rejected',
        reviewedBy: me.id,
        reviewedAt: new Date(),
        rejectNote: body.approved ? null : (body.note ?? null),
        updatedBy: me.id,
      },
    });
    await this.audit.log({
      action: 'md_change_review',
      objectType: 'MdChangeRequest',
      objectId: cid,
      after: { approved: body.approved, note: body.note ?? null },
    });
    await this.notify.send({
      userId: req.submittedBy,
      type: 'remind',
      title: `主数据变更单 #${cid} 已${body.approved ? '通过并应用' : '被拒绝'}`,
      content: body.note ?? undefined,
      bizType: 'md_change',
      bizId: cid.toString(),
    });
    return { id: updated.id.toString(), status: updated.status };
  }

  private async apply(objectType: string, objectId: bigint, changes: Array<{ field: string; after: unknown }>, userId: bigint) {
    const data: Record<string, unknown> = { updatedBy: userId };
    for (const c of changes) data[c.field] = c.after;
    if (objectType === 'party') {
      if (data.groupId === null || typeof data.groupId === 'string') data.groupId = data.groupId ? parseId(data.groupId) : null;
      await this.prisma.customerParty.update({ where: { id: objectId }, data });
    } else if (objectType === 'wind_farm') {
      await this.prisma.windFarm.update({ where: { id: objectId }, data });
    } else if (objectType === 'material') {
      await this.prisma.material.update({ where: { id: objectId }, data });
    } else if (objectType === 'project') {
      for (const k of ['windFarmId', 'ownerPartyId'] as const) {
        if (data[k] === null || typeof data[k] === 'string') data[k] = data[k] ? parseId(data[k]) : null;
      }
      await this.prisma.project.update({ where: { id: objectId }, data });
    }
  }
}
