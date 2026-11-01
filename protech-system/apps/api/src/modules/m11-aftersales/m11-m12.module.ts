import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { AfterArchiveService } from './archive.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M11 售后 + M12 档案接口（R2-B） */
@Controller()
export class AfterArchiveController {
  constructor(
    private readonly svc: AfterArchiveService,
    private readonly prisma: PrismaService,
  ) {}

  // ---- 售后（FR-M11-001~004/007） ----

  @Post('aftersales')
  @RequirePermission('as:create')
  async createOrder(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const o = await this.svc.createAfterSalesOrder(body, me.id);
    return { id: o.id.toString(), orderCode: o.orderCode };
  }

  @Post('aftersales/:id/transition')
  @RequirePermission('as:manage')
  async transition(
    @Param('id') id: string,
    @Body() body: Record<string, unknown>,
    @CurrentUser() me: AuthUser,
  ) {
    if (!body.to) throw BizException.param('to 必填');
    const costCents = body.costCents !== undefined && body.costCents !== null && body.costCents !== ''
      ? BigInt(String(body.costCents).trim())
      : undefined;
    return this.svc.transitionOrder(parseId(id), String(body.to), me.id, body.blockedReason as string, costCents);
  }

  @Get('aftersales')
  @RequirePermission('as:view')
  async list(@Query() q: Record<string, string>) {
    const list = await this.prisma.afterSalesOrder.findMany({
      where: {
        deletedAt: null,
        ...(q.projectId ? { projectId: parseId(q.projectId) } : {}),
        ...(q.status ? { status: q.status } : {}),
        ...(q.type ? { type: q.type } : {}),
      },
      orderBy: { id: 'desc' },
      take: 100,
    });
    return list.map((o) => ({ ...o, id: o.id.toString(), projectId: o.projectId.toString(), ownerId: o.ownerId.toString(), costCents: o.costCents.toString() }));
  }

  @Post('maintenance-plans')
  @RequirePermission('as:manage')
  async createPlan(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.projectId || !body.nextDueAt) throw BizException.param('projectId/nextDueAt 必填');
    const plan = await this.prisma.maintenancePlan.create({
      data: {
        projectId: parseId(body.projectId),
        cycleMonths: Number(body.cycleMonths ?? 12),
        nextDueAt: new Date(String(body.nextDueAt)),
        ownerTeam: (body.ownerTeam as string) ?? null,
        createdBy: me.id,
      },
    });
    return { id: plan.id.toString() };
  }

  @Get('maintenance-plans')
  @RequirePermission('as:view')
  async plans() {
    const list = await this.prisma.maintenancePlan.findMany({ orderBy: { nextDueAt: 'asc' }, take: 100 });
    return list.map((p) => ({ ...p, id: p.id.toString(), projectId: p.projectId.toString() }));
  }

  @Post('internal/aftersales/generate-inspections')
  async genInspections(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.svc.generateDueInspections(BigInt(1));
  }

  @Post('internal/aftersales/warranty-watch')
  async warrantyWatch(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.svc.warrantyWatch();
  }

  @Get('aftersales/fault-stats')
  @RequirePermission('as:view')
  async faultStats() {
    return this.svc.faultStats();
  }

  // ---- 档案（FR-M12-001~006/009） ----

  @Post('archives')
  @RequirePermission('archive:manage')
  async createArchive(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const a = await this.svc.createArchive(body, me.id);
    return { id: a.id.toString(), archiveNo: a.archiveNo };
  }

  @Get('archives')
  @RequirePermission('archive:view')
  async archives(@Query() q: Record<string, string>) {
    const list = await this.prisma.archive.findMany({
      where: {
        deletedAt: null,
        ...(q.keyword ? { title: { contains: q.keyword } } : {}),
        ...(q.category ? { category: q.category } : {}),
        ...(q.projectId ? { projectId: parseId(q.projectId) } : {}),
      },
      orderBy: { id: 'desc' },
      take: 100,
    });
    return list.map((a) => ({
      ...a,
      id: a.id.toString(),
      fileId: a.fileId?.toString() ?? null,
      contractId: a.contractId?.toString() ?? null,
      projectId: a.projectId?.toString() ?? null,
    }));
  }

  @Post('archives/:id/borrow')
  @RequirePermission('archive:borrow')
  async requestBorrow(@Param('id') id: string, @Body() body: { purpose?: string }, @CurrentUser() me: AuthUser) {
    return this.svc.requestBorrow({ archiveId: id, purpose: body.purpose }, me.id);
  }

  @Post('borrows/:id/approve')
  @RequirePermission('archive:approve')
  async approveBorrow(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.approved === undefined || !body.seq) throw BizException.param('approved/seq 必填');
    return this.svc.approveBorrow(parseId(id), Number(body.seq) as 1 | 2, me.id, body.approved === true);
  }

  @Post('borrows/:id/out')
  @RequirePermission('archive:approve')
  async markOut(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.svc.markOut(parseId(id), me.id);
  }

  @Post('borrows/:id/return')
  @RequirePermission('archive:borrow')
  async markReturn(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.svc.markReturned(parseId(id), me.id);
  }

  @Get('borrows')
  @RequirePermission('archive:view')
  async borrows(@Query() q: Record<string, string>) {
    const list = await this.prisma.archiveBorrow.findMany({
      where: { ...(q.status ? { status: q.status } : {}) },
      orderBy: { id: 'desc' },
      take: 100,
    });
    return list.map((b) => ({
      ...b,
      id: b.id.toString(),
      archiveId: b.archiveId.toString(),
      borrowerId: b.borrowerId.toString(),
      approver1Id: b.approver1Id?.toString() ?? null,
      approver2Id: b.approver2Id?.toString() ?? null,
    }));
  }

  @Get('archives/expiry-list')
  @RequirePermission('archive:manage')
  async expiryList() {
    return this.svc.expiryList();
  }

  @Post('internal/archives/remind-scan')
  async remindScan(@Body() body: { token?: string }) {
    const expected = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
    if (body.token !== expected) throw BizException.forbidden('内部端点');
    return this.svc.archiveRemindScan();
  }

  @Post('backup-records')
  @RequirePermission('archive:manage')
  async addBackup(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.kind || !body.target || !body.fileRef) throw BizException.param('kind/target/fileRef 必填');
    const row = await this.prisma.backupRecord.create({
      data: {
        kind: String(body.kind),
        target: String(body.target),
        fileRef: String(body.fileRef),
        sha256: (body.sha256 as string) ?? null,
        createdBy: me.id,
      },
    });
    return { id: row.id.toString() };
  }

  @Get('backup-records')
  @RequirePermission('archive:view')
  async backupRecords() {
    const list = await this.prisma.backupRecord.findMany({ orderBy: { id: 'desc' }, take: 100 });
    return list.map((r) => ({ ...r, id: r.id.toString(), createdBy: r.createdBy?.toString() ?? null }));
  }
}
