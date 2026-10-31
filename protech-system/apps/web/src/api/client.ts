import axios, { AxiosError, AxiosInstance } from 'axios';
import { message } from 'antd';
import { useAuth } from '../stores/auth';

export interface Envelope<T = unknown> {
  code: number;
  data: T;
  msg: string;
  blockCode?: string;
}

/** API 客户端：响应包解包 + 401 刷新重试一次（旧 Java 版审计 F5 项的修复） */
export const http: AxiosInstance = axios.create({ baseURL: '/', timeout: 15000 });

let refreshing: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const rt = useAuth.getState().refreshToken;
        if (!rt) return false;
        const res = await axios.post<Envelope<{ accessToken: string; refreshToken: string }>>(
          '/api/v1/auth/refresh',
          { refreshToken: rt },
        );
        if (res.data.code !== 0) return false;
        useAuth.getState().setTokens(res.data.data.accessToken, res.data.data.refreshToken);
        return true;
      } catch {
        return false;
      } finally {
        setTimeout(() => (refreshing = null), 0);
      }
    })();
  }
  return refreshing;
}

http.interceptors.request.use((cfg) => {
  const token = useAuth.getState().accessToken;
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

http.interceptors.response.use(
  (res) => res,
  async (err: AxiosError<Envelope>) => {
    const cfg = err.config as (typeof err.config & { _retried?: boolean }) | undefined;
    if (err.response?.status === 401 && cfg && !cfg._retried && !cfg.url?.includes('/auth/')) {
      cfg._retried = true;
      if (await tryRefresh()) return http(cfg);
      useAuth.getState().clear();
      if (!location.pathname.startsWith('/login')) location.href = '/login';
    }
    return Promise.reject(err);
  },
);

/** 请求并解包响应包；业务错误抛出带 blockCode 的异常供 BlockCodeAlert 渲染 */
export class ApiError extends Error {
  constructor(
    readonly code: number,
    msg: string,
    readonly blockCode?: string,
    readonly data?: unknown,
  ) {
    super(msg);
  }
}

export async function api<T>(method: 'get' | 'post' | 'put' | 'delete', url: string, body?: unknown, params?: unknown): Promise<T> {
  try {
    const res = await http.request<Envelope<T>>({ method, url, data: body, params });
    return res.data.data;
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.data) {
      const env = e.response.data as Envelope;
      throw new ApiError(env.code, env.msg, env.blockCode, env.data);
    }
    throw e;
  }
}

/** 统一错误提示（I-05：blockCode 全局一致展示） */
export function toastError(e: unknown): void {
  if (e instanceof ApiError) {
    message.error(`${e.blockCode ? `[${e.blockCode}] ` : ''}${e.message}`, 4);
  } else {
    message.error((e as Error).message || '网络异常', 4);
  }
}
