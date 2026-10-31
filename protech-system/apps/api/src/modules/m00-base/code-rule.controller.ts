import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { CodeEngineService, CodeVars } from '../../shared/code-engine.service';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';

/** FR-M01-017 编码规则配置页：格式预览与占用流水查询（R-SYS） */
@Controller('sys/code-rules')
export class CodeRuleController {
  constructor(
    private readonly engine: CodeEngineService,
    private readonly prisma: PrismaService,
  ) {}

  @Get()
  async list() {
    const rules = await this.prisma.sysCodeRule.findMany({ where: { deletedAt: null }, orderBy: { bizType: 'asc' } });
    return rules.map((r) => ({
      ...r,
      id: r.id.toString(),
      preview: `${r.pattern.replace('{YYYY}', String(new Date().getFullYear())).replace(/\{N+\}/, '0'.repeat(1))}…`,
      usedSeq: r.currentPeriod === String(new Date().getFullYear()) || !r.yearly ? r.currentSeq : 0,
    }));
  }

  @Post(':bizType/preview')
  async preview(@Param('bizType') bizType: string, @Body() vars: CodeVars = {}) {
    return { code: await this.engine.preview(bizType, vars ?? {}) };
  }

  @Post(':bizType/next')
  @RequirePermission('sys:code:manage')
  async next(@Param('bizType') bizType: string, @Body() vars: CodeVars = {}) {
    return { code: await this.engine.next(bizType, vars ?? {}) };
  }
}
