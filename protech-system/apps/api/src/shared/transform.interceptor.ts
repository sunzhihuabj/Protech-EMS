import {
  CallHandler,
  Catch,
  ExceptionFilter,
  ExecutionContext,
  HttpException,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Observable, map } from 'rxjs';
import { BizException } from './biz.exception';
import { serialize } from './serialize';

export interface Envelope<T = unknown> {
  code: number;
  data: T;
  msg: string;
  blockCode?: string;
}

/** 成功响应包 { code:0, data, msg:'' }（11 册 §4.1），BigInt/Date 全局序列化 */
@Injectable()
export class TransformInterceptor implements NestInterceptor {
  intercept(_ctx: ExecutionContext, next: CallHandler): Observable<unknown> {
    return next.handle().pipe(
      map((data) => ({ code: 0, data: serialize(data === undefined ? null : data), msg: '' })),
    );
  }
}

/** 异常 → 响应包 { code≠0, msg, blockCode? } */
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  catch(exception: unknown, host: ExecutionContext) {
    const res = host.switchToHttp().getResponse();
    let status = 500;
    let code = 5000;
    let msg = '服务内部错误';
    let blockCode: string | undefined;

    if (exception instanceof BizException) {
      status = exception.getStatus();
      const body = exception.getResponse() as { code: number; msg: string; blockCode?: string };
      code = body.code;
      msg = body.msg;
      blockCode = body.blockCode;
      if (exception.data !== undefined) {
        res.status(status).json(serialize({ code, data: exception.data, msg, blockCode }));
        return;
      }
    } else if (exception instanceof HttpException) {
      status = exception.getStatus();
      const body = exception.getResponse();
      const raw = typeof body === 'string' ? body : ((body as { message?: unknown }).message ?? msg);
      msg = Array.isArray(raw) ? raw.join('; ') : String(raw);
      code = status === 401 ? 2003 : status === 403 ? 2002 : 1001;
      if (status === 401) blockCode = 'E-AUTH';
    } else if (exception instanceof Error) {
      msg = exception.message || msg;
    }

    res.status(status).json(serialize({ code, data: null, msg, blockCode }));
  }
}
