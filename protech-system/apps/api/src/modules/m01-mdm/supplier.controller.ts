import { Body, Controller, Get, Param, Post, Put, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { BizException } from '../../shared/biz.exception';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId } from '../../shared/serialize';
import { similarity } from '@protech/shared-types';

/** FR-M01-007 供应商库（349 家种子导入；准交率/质量视图随 M06 落地） */
@Controller('mdm/suppliers')
export class SupplierController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  @RequirePermission('mdm:supplier:list')
  async list(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? {
            OR: [
              { name: { contains: q.keyword } },
              { shortName: { contains: q.keyword } },
              { supplierCode: { contains: q.keyword } },
            ],
          }
        : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.supplier.findMany({
        where,
        orderBy: { supplierCode: 'asc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.supplier.count({ where }),
    ]);
    return { list: list.map((s) => ({ ...s, id: s.id.toString() })), total, page, pageSize };
  }

  @Post()
  @RequirePermission('mdm:supplier:manage')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const name = String(body.name ?? '').trim();
    if (!name) throw BizException.param('name 必填');
    const th = 0.85;
    const similar = await this.prisma.supplier.findMany({ where: { deletedAt: null } });
    const hits = similar
      .map((s) => ({ s, score: similarity(name, s.name) }))
      .filter((x) => x.score >= th);
    if (hits.length && body.force !== true) {
      throw Object.assign(BizException.validation('疑似已存在供应商'), {
        data: { candidates: hits.map((h) => ({ id: h.s.id.toString(), code: h.s.supplierCode, name: h.s.name, score: h.score })) },
      });
    }
    // 供应商编码独立规则（种子注册 SU{YYYY}-{NNNN}，避免与采购单共用序列跳号）
    const code = await this.codes.next('supplier');
    const supplier = await this.prisma.supplier.create({
      data: {
        supplierCode: code,
        name,
        shortName: (body.shortName as string) ?? null,
        category: (body.category as string) ?? null,
        contact: (body.contact as string) ?? null,
        phone: (body.phone as string) ?? null,
        remark: (body.remark as string) ?? null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'Supplier', objectId: supplier.id, after: { supplierCode: code } });
    return { id: supplier.id.toString(), supplierCode: code };
  }

  @Get(':id')
  @RequirePermission('mdm:supplier:list')
  async detail(@Param('id') id: string) {
    const s = await this.prisma.supplier.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!s) throw BizException.notFound('供应商不存在');
    return { ...s, id: s.id.toString() };
  }

  @Put(':id')
  @RequirePermission('mdm:supplier:manage')
  async update(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const sid = parseId(id);
    const s = await this.prisma.supplier.findFirst({ where: { id: sid, deletedAt: null } });
    if (!s) throw BizException.notFound('供应商不存在');
    const data: Record<string, unknown> = { updatedBy: me.id };
    for (const k of ['name', 'shortName', 'category', 'contact', 'phone', 'remark', 'status'] as const) {
      if (body[k] !== undefined) data[k] = body[k];
    }
    await this.prisma.supplier.update({ where: { id: sid }, data });
    await this.audit.log({ action: 'update', objectType: 'Supplier', objectId: sid, before: s, after: data });
    return null;
  }
}
