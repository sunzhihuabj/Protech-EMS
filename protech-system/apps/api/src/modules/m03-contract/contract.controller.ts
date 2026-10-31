import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { ContractService } from './contract.service';
import { PrismaService } from '../../shared/prisma.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** M03 合同接口（FR-M03-001~015；BR-01/02 硬卡点） */
@Controller('contracts')
export class ContractController {
  constructor(
    private readonly contracts: ContractService,
    private readonly prisma: PrismaService,
  ) {}

  @Get()
  @RequirePermission('contract:list')
  async list(@Query() q: Record<string, string>) {
    const page = Math.max(1, Number(q.page) || 1);
    const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 20));
    const where = {
      deletedAt: null,
      ...(q.keyword
        ? {
            OR: [
              { contractCode: { contains: q.keyword } },
              { contractName: { contains: q.keyword } },
            ],
          }
        : {}),
      ...(q.status ? { status: q.status } : {}),
      ...(q.counterpartyId ? { counterpartyId: parseId(q.counterpartyId) } : {}),
    };
    const [list, total] = await Promise.all([
      this.prisma.contract.findMany({
        where,
        orderBy: { id: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.contract.count({ where }),
    ]);
    return {
      list: list.map((c) => this.contracts.present(c)),
      total,
      page,
      pageSize,
    };
  }

  @Get(':id')
  @RequirePermission('contract:list')
  async detail(@Param('id') id: string) {
    return this.contracts.detail(parseId(id));
  }

  @Post()
  @RequirePermission('contract:create')
  async create(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const c = await this.contracts.create(body, me.id);
    return { id: c.id.toString(), contractCode: c.contractCode };
  }

  @Post(':id/projects')
  @RequirePermission('contract:create')
  async attachProject(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.projectId) throw BizException.param('projectId 必填');
    await this.contracts.attachProject(parseId(id), parseId(body.projectId), me.id, body.orderLine as string);
    return null;
  }

  /** BR-01 卡点：无项目编码 → 422 + blockCode=BR-01（附建档引导） */
  @Post(':id/review/submit')
  @RequirePermission('contract:review:submit')
  async submitReview(@Param('id') id: string, @CurrentUser() me: AuthUser) {
    return this.contracts.submitReview(parseId(id), me.id);
  }

  @Post(':id/review/act')
  @RequirePermission('contract:review:act')
  async reviewAct(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (body.approved === undefined) throw BizException.param('approved 必填');
    return this.contracts.reviewAct(
      parseId(id),
      String(body.role ?? ''),
      me.id,
      body.approved === true,
      body.opinion as string,
    );
  }

  @Post(':id/seal')
  @RequirePermission('contract:seal')
  async seal(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const c = await this.contracts.seal(parseId(id), me.id, body);
    return { id: c.id.toString(), status: c.status, archiveDeadlineAt: c.archiveDeadlineAt };
  }

  /** BR-02 判定源 */
  @Get(':id/archive-status')
  @RequirePermission('contract:list')
  async archiveStatus(@Param('id') id: string) {
    return this.contracts.archiveStatus(parseId(id));
  }

  @Post(':id/filing')
  @RequirePermission('contract:filing')
  async filing(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const c = await this.contracts.filing(parseId(id), me.id, body);
    return { id: c.id.toString(), status: c.status, filedAt: c.filedAt, invoicingLocked: c.invoicingLocked };
  }

  @Post(':id/changes')
  @RequirePermission('contract:change')
  async createChange(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const ch = await this.contracts.createChange(parseId(id), me.id, body);
    return { id: ch.id.toString(), changeCode: ch.changeCode };
  }

  @Post('changes/:changeId/review')
  @RequirePermission('contract:review:act')
  async approveChange(
    @Param('changeId') changeId: string,
    @Body() body: Record<string, unknown>,
    @CurrentUser() me: AuthUser,
  ) {
    if (body.approved === undefined) throw BizException.param('approved 必填');
    return this.contracts.approveChange(parseId(changeId), me.id, body.approved === true, body.note as string);
  }
}
