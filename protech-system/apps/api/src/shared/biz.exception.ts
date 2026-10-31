import { HttpException } from '@nestjs/common';

/** 业务错误码（11 册 §4.1：0 成功；4xx 参数/权限；blockCode 标红线/边界） */
export const BIZ = {
  PARAM: 1001,
  NOT_FOUND: 1002,
  VALIDATION: 2001,
  FORBIDDEN: 2002,
  UNAUTHORIZED: 2003,
  SERVER: 5000,
} as const;

export type BizCode = (typeof BIZ)[keyof typeof BIZ];

export class BizException extends HttpException {
  /** 附带数据（如查重候选列表），经响应包 data 字段返回 */
  data?: unknown;

  constructor(
    readonly bizCode: number,
    msg: string,
    readonly blockCode?: string,
    httpStatus = 422,
  ) {
    super({ code: bizCode, msg, blockCode }, httpStatus);
  }

  with(data: unknown): this {
    this.data = data;
    return this;
  }

  static param(msg: string, blockCode?: string) {
    return new BizException(BIZ.PARAM, msg, blockCode);
  }

  static notFound(msg = '数据不存在') {
    return new BizException(BIZ.NOT_FOUND, msg, undefined, 404);
  }

  static validation(msg: string, blockCode?: string) {
    return new BizException(BIZ.VALIDATION, msg, blockCode);
  }

  static forbidden(msg = '无权限执行该操作', blockCode?: string) {
    return new BizException(BIZ.FORBIDDEN, msg, blockCode, 403);
  }

  static unauthorized(msg = '未登录或会话已过期', blockCode = 'E-AUTH') {
    return new BizException(BIZ.UNAUTHORIZED, msg, blockCode, 401);
  }

  static br(code: string, msg: string) {
    return new BizException(BIZ.VALIDATION, msg, code);
  }
}
