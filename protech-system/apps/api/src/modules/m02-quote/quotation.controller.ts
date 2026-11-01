import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { QuotationService } from './quotation.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M02 报价接口（FR-M02-001~008） */
@Controller('quotations')
export class QuotationController {
  constructor(
    private readonly quotes: QuotationService,
    private readonly prisma: PrismaService,
  ) {}

  @Get()
  @RequirePermission('quote:list')
  async list(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? { OR: [{ quotationCode: { contains: q.keyword } }, { remark: { contains: q.keyword } }] }
        : {}),
      ...(q.status ? { status: q.status } : {}),
      ...(q.partyId ? { partyId: parseId(q.partyId) } : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.quotation.findMany({
        where,
        orderBy: { id: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.quotation.count({ where }),
    ]);
    return {
      list: list.map((x) => ({
        ...x,
        id: x.id.toString(),
        partyId: x.partyId.toString(),
        groupId: x.groupId?.toString() ?? null,
        projectId: x.projectId?.toString() ?? null,
        productLines: JSON.parse(x.productLines || '[]'),
        unitPriceCents: x.unitPriceCents.toString(),
        totalCents: x.totalCents.toString(),
        convertedContractId: x.convertedContractId?.toString() ?? null,
        ownerId: x.ownerId.toString(),
      })),
      total,
      page,
      pageSize,
    };
  }

  @Post('check-multi')
  @RequirePermission('quote:list')
  async checkMulti(@Body() body: { partyId?: string; productLines?: string[] }) {
    if (!body.partyId) throw BizException.param('partyId 必填');
    return {
      hints: await this.quotes.multiQuoteHint(parseId(body.partyId), body.productLines ?? []),
    };
  }

  @Post('price-history')
  @RequirePermission('quote:list')
  async priceHistory(@Body() body: { partyId?: string; productLine?: string }) {
    if (!body.partyId || !body.productLine) throw BizException.param('partyId/productLine 必填');
    return this.quotes.priceHistory(parseId(body.partyId), body.productLine);
  }

  @Post()
  @RequirePermission('quote:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const q = await this.quotes.create(body, me.id);
    return { id: q.id.toString(), quotationCode: q.quotationCode };
  }

  @Post(':id/transition')
  @RequirePermission('quote:decide')
  async transition(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.to) throw BizException.param('to 必填');
    const q = await this.quotes.transition(parseId(id), String(body.to), me.id, body);
    return { id: q.id.toString(), status: q.status };
  }

  @Post(':id/convert')
  @RequirePermission('quote:convert')
  async convert(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const c = await this.quotes.convertToContract(parseId(id), me.id, body);
    return { id: c.id.toString(), contractCode: c.contractCode };
  }

  /** FR-M02-008 报价打印（HTML 模板可配 Q-07） */
  @Post(':id/print')
  async printQuote(@Param('id') id: string) {
    const q = await this.prisma.quotation.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!q) throw BizException.notFound('报价单不存在');
    const party = await this.prisma.customerParty.findFirst({ where: { id: q.partyId }, select: { name: true } });
    const tpl = await this.prisma.sysConfig.findFirst({ where: { key: 'print_tpl_quote', deletedAt: null } });
    const html = (tpl?.value ?? '<h2>报价单</h2><p>编号：{{quotationCode}}</p><p>客户：{{partyName}}</p><p>产品线：{{productLines}}</p><p>数量：{{qty}} × 单价（分）：{{unitPriceCents}}</p><p>有效期至：{{validUntil}}</p><hr/><p>报价人：____ 客户确认：____</p>')
      .replace(/\{\{(\w+)\}\}/g, (_, f: string) =>
        String({ quotationCode: q.quotationCode, partyName: party?.name ?? '', productLines: JSON.parse(q.productLines || '[]').join('/'), qty: q.qty, unitPriceCents: q.unitPriceCents.toString(), validUntil: q.validUntil?.toISOString().slice(0, 10) ?? '' }[f] ?? ''));
    return { html };
  }

  @Get('stats')
  @RequirePermission('quote:list')
  async stats() {
    return this.quotes.stats();
  }
}
