/**
 * 接口契约全局约定（11 册 §4.1）。
 * 响应包 { code, data, msg }；业务失败 code≠0 + blockCode。
 */

export interface ApiResponse<T = unknown> {
  code: number; // 0 成功；1001 参数；1002 不存在；2001 业务校验失败；2002 无权限；2003 未认证；5000 服务端
  data: T;
  msg: string;
  blockCode?: string;
}

export interface PageReq {
  page?: number;
  pageSize?: number;
  sort?: string; // '-created_at'
  keyword?: string;
}

export interface PageResp<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 200;

export function normalizePage(req: PageReq): { page: number; pageSize: number; skip: number } {
  const page = Math.max(1, Math.trunc(Number(req.page) || 1));
  const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, Math.trunc(Number(req.pageSize) || DEFAULT_PAGE_SIZE)));
  return { page, pageSize, skip: (page - 1) * pageSize };
}
