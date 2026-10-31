import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { PrismaClient, Prisma } from '@prisma/client';
import { currentContext } from './request-context';
import { BizException } from './biz.exception';

/**
 * BR-09/BR-11 服务端硬闸门（Prisma middleware，绕不过去的第一道防线）：
 *  - 物理删除：除白名单（会话表清理）外全部拒绝（BR-11 审计不可删/软删除）
 *  - 业务编码修改：*Code/*No 字段出现在 update 数据中即拒绝（BR-09 编码不可变）
 *  - 审计：create/update/updateMany 写 sys_audit_log（改前改后值，FR-M00-006）
 */
const HARD_DELETE_ALLOWED = new Set<string>(['SysSession']);

const IMMUTABLE_CODE_FIELDS = new Set([
  'projectCode',
  'materialCode',
  'partyCode',
  'farmCode',
  'supplierCode',
  'contractCode',
  'groupCode',
  'entityCode',
  'archiveNo',
  'ecnCode',
  'woCode',
  'poCode',
  'prCode',
  'pgCode',
  'oldCode',
]);

const AUDITED_ACTIONS = new Set(['create', 'update', 'updateMany', 'upsert', 'createMany']);
/** 噪音大的系统表不逐条审计（会话/通知已读由显式业务审计覆盖） */
const AUDIT_SKIP = new Set(['SysAuditLog', 'SysSession', 'SysNotify']);

@Injectable()
export class PrismaService extends PrismaClient implements OnModuleInit, OnModuleDestroy {
  constructor() {
    super({ log: ['warn', 'error'] });
  }

  async onModuleInit() {
    await this.$connect();
    // BR-09/BR-11 硬闸门 + 审计（见 guard/auditWrap）
    this.$use(((params: Prisma.MiddlewareParams, next: Prisma.Middleware) => {
      return this.guard(params, next as unknown as (p: unknown) => Promise<unknown>);
    }) as Prisma.Middleware);
  }

  async onModuleDestroy() {
    await this.$disconnect();
  }

  private async guard(
    params: { model?: string; action: string; args: Record<string, unknown> },
    next: (p: unknown) => Promise<unknown>,
  ): Promise<unknown> {
    const { model, action } = params;

    // BR-11 物理删除拦截
    if (model && (action === 'delete' || action === 'deleteMany') && !HARD_DELETE_ALLOWED.has(model)) {
      throw BizException.validation(`系统不提供物理删除（BR-11），${model} 仅可停用或软删除`, 'BR-11');
    }

    // BR-09 编码不可变
    if (
      model &&
      (action === 'update' || action === 'updateMany' || action === 'upsert') &&
      params.args?.data &&
      typeof params.args.data === 'object'
    ) {
      const data = params.args.data as Record<string, unknown>;
      const touched = Object.keys(data).find((k) => IMMUTABLE_CODE_FIELDS.has(k));
      if (touched) {
        throw BizException.validation(`业务编码不可修改（BR-09）：${touched}`, 'BR-09');
      }
    }

    // 审计（改前改后）
    if (model && AUDITED_ACTIONS.has(action) && !AUDIT_SKIP.has(model)) {
      return this.auditWrap(params, next);
    }
    return next(params);
  }

  private async auditWrap(
    params: { model?: string; action: string; args: Record<string, unknown> },
    next: (p: unknown) => Promise<unknown>,
  ): Promise<unknown> {
    const { model, action, args } = params;
    let before: unknown = undefined;
    try {
      if ((action === 'update' || action === 'upsert') && args.where) {
        const delegate = (this as unknown as Record<string, { findFirst: (a: unknown) => Promise<unknown> }>)[
          model!
        ];
        before = await delegate.findFirst({ where: args.where });
        if (before === null && action === 'update') return next(params);
      }
    } catch {
      // 审计前置读失败不阻断业务
    }

    const result = await next(params);

    try {
      const ctx = currentContext();
      const after =
        action === 'updateMany'
          ? { data: args.data, count: result }
          : action === 'upsert'
            ? { after: args.create, updatedWith: args.update }
            : args.data;
      await this.sysAuditLog
        .create({
          data: {
            userId: ctx.userId ?? null,
            username: ctx.username ?? null,
            action: action === 'updateMany' ? 'update_many' : action,
            objectType: model!,
            objectId:
              before && typeof before === 'object' && 'id' in (before as object)
                ? String((before as { id: unknown }).id)
                : args.where
                  ? JSON.stringify(serialize(args.where))
                  : null,
            before: before === undefined ? null : JSON.stringify(serialize(before)),
            after: JSON.stringify(serialize(after)) ?? null,
            ip: ctx.ip ?? null,
          },
        })
        .catch(() => undefined); // 审计失败不阻断业务，但记录
    } catch {
      // 同上
    }
    return result;
  }
}

function serialize(v: unknown): unknown {
  if (v === null || v === undefined) return v;
  if (typeof v === 'bigint') return v.toString();
  if (v instanceof Date) return v.toISOString();
  if (Array.isArray(v)) return v.map(serialize);
  if (typeof v === 'object') {
    const o: Record<string, unknown> = {};
    for (const [k, val] of Object.entries(v)) o[k] = serialize(val);
    return o;
  }
  return v;
}
