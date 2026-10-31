import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { AiService, CAPABILITIES } from './ai.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';
import { REPLENISH_REASON, isValidCode } from '@protech/shared-types';

/** M16 AI 能力接口（R1 三点；Ask-first；白名单） */
@Controller('ai')
export class AiController {
  constructor(
    private readonly ai: AiService,
    private readonly prisma: PrismaService,
  ) {}

  @Get('capabilities')
  async capabilities() {
    return Object.entries(CAPABILITIES).map(([code, def]) => ({ code, ...def }));
  }

  @Post(':cap/run')
  @RequirePermission('ai:run')
  async run(@Param('cap') cap: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    this.ai.assertEnabled(cap);
    switch (cap) {
      case 'AI-01':
        if (!body.contractId) throw BizException.param('contractId 必填');
        return this.ai.extractContract(parseId(body.contractId), me.id, body.text as string);
      case 'AI-02':
        if (!body.name) throw BizException.param('name 必填');
        return this.ai.dupAssist(me.id, String(body.name), (body.scope as 'party' | 'material') ?? 'party');
      case 'AI-03':
        if (!body.rawName) throw BizException.param('rawName 必填');
        return this.ai.normalizeName(me.id, String(body.rawName), (body.scope as 'project' | 'party' | 'material') ?? 'project');
      default:
        throw BizException.forbidden(`AI 能力未开放：${cap}（R2/R3 路线）`);
    }
  }

  /** Ask-first 确认入库 + 反馈（FR-M16-014） */
  @Post('calls/:callId/confirm')
  @RequirePermission('ai:run')
  async confirm(@Param('callId') callId: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.accepted === undefined) throw BizException.param('accepted 必填');
    return this.ai.confirmExtract(
      parseId(callId),
      me.id,
      body.accepted === true,
      (body.aiFields as Record<string, string>) ?? {},
    );
  }

  @Get('calls')
  async calls(@CurrentUser() me: AuthUser) {
    const rows = await this.prisma.aiCall.findMany({ where: { userId: me.id }, orderBy: { id: 'desc' }, take: 50 });
    return rows.map((r) => ({ ...r, id: r.id.toString(), userId: r.userId?.toString() ?? null }));
  }
}

/** 补货分级审批（FR-M06-013/014） */
@Controller('reissues')
export class ReissueController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly ai: AiService,
  ) {}

  /** 分级路由：≤500 元标准件一级当天出 / 500-5000 二级 2 日 / >5000 或涉变更全链 5 日 / 安全件先发后签 */
  @Post()
  @RequirePermission('reissue:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.materialId || body.amountCents === undefined || !body.reason) {
      throw BizException.param('materialId/amountCents/reason 必填');
    }
    if (!isValidCode(REPLENISH_REASON, String(body.reason))) throw BizException.param('reason 非法（E-08）');
    const amountCents = BigInt(String(body.amountCents).trim());
    let tier = 'tier3';
    if (amountCents <= 50000n) tier = 'tier1';
    else if (amountCents <= 500000n) tier = 'tier2';
    const row = await this.prisma.replenishRequest.create({
      data: {
        projectId: body.projectId ? parseId(body.projectId) : null,
        materialId: parseId(body.materialId),
        qty: Number(body.qty ?? 1),
        amountCents,
        reason: String(body.reason),
        isSafetyPart: body.isSafetyPart === true,
        tier,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    return { id: row.id.toString(), tier };
  }

  @Post(':id/review')
  @RequirePermission('reissue:review')
  async review(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.approved === undefined) throw BizException.param('approved 必填');
    const row = await this.prisma.replenishRequest.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!row) throw BizException.notFound('补货申请不存在');
    if (row.status !== 'pending') throw BizException.validation('申请已处理');
    await this.prisma.replenishRequest.update({
      where: { id: row.id },
      data: { status: body.approved ? 'approved' : 'rejected', approverId: me.id, reviewedAt: new Date() },
    });
    return { status: body.approved ? 'approved' : 'rejected' };
  }

  /** 安全件先发后签：直接出库 + 24h 补签跟踪 */
  @Post(':id/ship-now')
  async shipNow(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    const row = await this.prisma.replenishRequest.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!row) throw BizException.notFound('补货申请不存在');
    if (!row.isSafetyPart) throw BizException.validation('仅安全件可先发后签（FR-M06-013）');
    await this.prisma.replenishRequest.update({
      where: { id: row.id },
      data: { status: 'shipped', updatedBy: me.id },
    });
    return { status: 'shipped', signedBackDeadline: new Date(Date.now() + 24 * 3600000) };
  }

  @Post(':id/sign-back')
  async signBack(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    const updated = await this.prisma.replenishRequest.updateMany({
      where: { id: parseId(id), status: 'shipped' },
      data: { status: 'signed_back', signedBackAt: new Date() },
    });
    return { signedBack: updated.count };
  }

  /** FR-M06-014 原因结构分析（高频品种 TOP + 原因分布） */
  @Get('stats')
  async stats() {
    const byReason = await this.prisma.replenishRequest.groupBy({
      by: ['reason'],
      _count: { _all: true },
      where: { deletedAt: null },
    });
    const byMaterial = await this.prisma.replenishRequest.groupBy({
      by: ['materialId'],
      _count: { _all: true },
      where: { deletedAt: null },
      orderBy: { _count: { materialId: 'desc' } },
      take: 10,
    });
    const materialIds = byMaterial.map((b) => b.materialId);
    const materials = materialIds.length ? await this.prisma.material.findMany({ where: { id: { in: materialIds } } }) : [];
    const mById = new Map(materials.map((m) => [m.id, m]));
    return {
      reasonDist: byReason.map((r) => ({ reason: r.reason, label: REPLENISH_REASON[r.reason] ?? r.reason, count: r._count._all })),
      topMaterials: byMaterial.map((b) => ({
        materialCode: mById.get(b.materialId)?.materialCode ?? '',
        name: mById.get(b.materialId)?.name ?? '',
        count: b._count._all,
      })),
    };
  }
}

/** M14 预置报表（FR-M14-001/002/006：口径统一+导出水印+权限） */
@Controller('reports')
export class ReportController {
  constructor(private readonly prisma: PrismaService) {}

  private async rows(key: string): Promise<Array<Record<string, string | number>>> {
    if (key === 'contracts') {
      const list = await this.prisma.contract.findMany({ where: { deletedAt: null }, orderBy: { id: 'desc' }, take: 500 });
      return list.map((c) => ({
        合同编号: c.contractCode,
        名称: c.contractName,
        性质: c.nature,
        金额_万元: Number(c.amountCents) / 1_000_000,
        状态: c.status,
        评审: c.reviewStatus,
      }));
    }
    if (key === 'projects') {
      const list = await this.prisma.project.findMany({ where: { deletedAt: null }, orderBy: { id: 'desc' }, take: 500 });
      return list.map((p) => ({
        项目编码: p.projectCode,
        标准名: p.standardName,
        简称: p.shortName,
        状态: p.status,
        台数: p.unitCount,
        金额_万元: p.contractAmountCents ? Number(p.contractAmountCents) / 1_000_000 : 0,
      }));
    }
    if (key === 'stock-moves') {
      const list = await this.prisma.stockMove.findMany({ orderBy: { id: 'desc' }, take: 500 });
      return list.map((m) => ({
        流水号: m.id.toString(),
        方向: m.moveType === 'in' ? '入库' : '出库',
        来源: m.sourceType,
        单据号: m.sourceId,
        数量: Number(m.qty),
        T加状态: m.tPlusStatus,
        日期: m.movedAt.toISOString().slice(0, 10),
      }));
    }
    if (key === 'receipts') {
      const list = await this.prisma.receipt.findMany({ orderBy: { id: 'desc' }, take: 500 });
      return list.map((r) => ({
        合同ID: r.contractId.toString(),
        回款_元: Number(r.amountCents) / 100,
        日期: r.receivedAt.toISOString().slice(0, 10),
        方式: r.method ?? '',
      }));
    }
    throw BizException.param(`报表不存在：${key}（FR-M14-001 预置库）`);
  }

  @Get(':key')
  @RequirePermission('report:view')
  async report(@Param('key') key: string, @CurrentUser() me: AuthUser) {
    const rows = await this.rows(key);
    // FR-M14-002 导出水印：生成人/时间注入每行首列
    const stamped = rows.map((r, i) => ({
      _水印: `${me.displayName} ${new Date().toISOString().slice(0, 16)}`,
      _序号: i + 1,
      ...r,
    }));
    return { key, count: stamped.length, rows: stamped };
  }

  @Get(':key/csv')
  @RequirePermission('report:export')
  async csv(@Param('key') key: string, @CurrentUser() me: AuthUser) {
    const rows = await this.rows(key);
    const headers = ['_水印', '_序号', ...Object.keys(rows[0] ?? {})];
    const lines = [headers.join(',')];
    for (const [i, r] of rows.entries()) {
      lines.push([`${me.displayName} ${new Date().toISOString().slice(0, 16)}`, String(i + 1), ...Object.values(r).map((v) => String(v).replace(/,/g, '，'))].join(','));
    }
    return { filename: `${key}-${Date.now()}.csv`, content: lines.join('\n') };
  }
}
