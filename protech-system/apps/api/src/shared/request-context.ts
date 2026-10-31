import { AsyncLocalStorage } from 'node:async_hooks';

/** 请求级上下文（审计落 createdBy/updatedBy、IP 用） */
export interface RequestContext {
  userId?: bigint;
  username?: string;
  ip?: string;
}

const als = new AsyncLocalStorage<RequestContext>();

export function runWithContext<T>(ctx: RequestContext, fn: () => T): T {
  return als.run(ctx, fn);
}

export function currentContext(): RequestContext {
  return als.getStore() ?? {};
}

export function currentUserId(): bigint | undefined {
  return als.getStore()?.userId;
}
