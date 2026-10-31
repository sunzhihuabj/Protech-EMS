import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from './prisma.service';
import { BizException } from './biz.exception';
import { AuditService } from './audit.service';
import { renderCode } from '@protech/shared-types';

export interface CodeVars {
  entity?: string;
  nature?: string;
  biz?: string;
  cat?: string;
  ret?: string;
}

type Tx = Prisma.TransactionClient;

/**
 * 编码规则引擎（FR-M00-005）。
 * 并发安全：周期+流水递增用条件 UPDATE（当前期间匹配才递增），不匹配（新年首次/首条）
 * 走条件改期或插入；冲突重试 ≤5 次。PG 行锁/SQLite 串行写天然兜底。
 * BR-09：生成即不可变（Prisma middleware 拦 update）；BR-11 无物理删除。
 */
@Injectable()
export class CodeEngineService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  /** 取下一个业务编码（事务内占号）。vars 为合同/档号等模板变量 */
  async next(bizType: string, vars: CodeVars = {}, tx?: Tx): Promise<string> {
    const run = async (client: PrismaService | Tx) => {
      for (let attempt = 0; attempt < 5; attempt++) {
        const rule = await client.sysCodeRule.findFirst({ where: { bizType, deletedAt: null } });
        if (!rule) throw BizException.validation(`编码规则未配置：${bizType}`);
        const period = rule.yearly ? String(new Date().getFullYear()) : 'ALL';

        // 1) 当期递增
        const bumped = await client.sysCodeRule.updateMany({
          where: { id: rule.id, currentPeriod: period },
          data: { currentSeq: { increment: 1 } },
        });
        if (bumped.count === 0) {
          // 2) 期间切换或首条：条件置期（防并发双插）
          const reset = await client.sysCodeRule.updateMany({
            where: { id: rule.id, currentPeriod: { not: period } },
            data: { currentPeriod: period, currentSeq: 1 },
          });
          if (reset.count === 0) {
            // 3) 规则行不存在 → 插入（唯一键冲突即重试）
            try {
              await client.sysCodeRule.create({
                data: {
                  bizType,
                  pattern: rule.pattern,
                  seqLen: rule.seqLen,
                  yearly: rule.yearly,
                  currentPeriod: period,
                  currentSeq: 1,
                },
              });
            } catch {
              continue; // 并发插入冲突 → 重试
            }
          }
        }

        const final = await client.sysCodeRule.findUniqueOrThrow({ where: { id: rule.id } });
        if (final.currentPeriod !== period || final.currentSeq < 1) continue; // 异常竞争 → 重试
        const year = rule.yearly ? period : String(new Date().getFullYear());
        const code = renderCode(rule.pattern, {
          year,
          seq: String(final.currentSeq).padStart(rule.seqLen, '0'),
          ...vars,
        });
        return code;
      }
      throw BizException.validation(`编码生成并发冲突，请重试：${bizType}`);
    };

    const code = tx ? await run(tx) : await this.prisma.$transaction(async (c) => run(c));
    await this.audit.log({ action: 'code_gen', objectType: bizType, objectId: code });
    return code;
  }

  /** 预览（不占号，FR-CT-18 同思路） */
  async preview(bizType: string, vars: CodeVars = {}): Promise<string> {
    const rule = await this.prisma.sysCodeRule.findFirst({ where: { bizType, deletedAt: null } });
    if (!rule) throw BizException.validation(`编码规则未配置：${bizType}`);
    const period = rule.yearly ? String(new Date().getFullYear()) : 'ALL';
    const seq = rule.currentPeriod === period ? rule.currentSeq + 1 : 1;
    return renderCode(rule.pattern, {
      year: rule.yearly ? period : String(new Date().getFullYear()),
      seq: String(seq).padStart(rule.seqLen, '0'),
      ...vars,
    });
  }
}
