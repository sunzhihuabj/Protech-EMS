import { Test } from '@nestjs/testing';
import { AppModule } from '../src/app.module';
import { INestApplication } from '@nestjs/common';
import { TransformInterceptor, AllExceptionsFilter } from '../src/shared/transform.interceptor';
import supertest from 'supertest';

let app: INestApplication | undefined;

/** 契约测试共享 App 实例（seed 完成后构建） */
export async function getApp(): Promise<INestApplication> {
  if (app) return app;
  const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
  app = moduleRef.createNestApplication({ logger: ['error'] });
  app.setGlobalPrefix('api/v1');
  app.useGlobalInterceptors(new TransformInterceptor());
  app.useGlobalFilters(new AllExceptionsFilter());
  await app.init();
  return app;
}

export function http() {
  if (!app) throw new Error('app 未初始化（先 await getApp()）');
  return supertest(app.getHttpServer());
}

export interface TestResponse {
  statusCode: number;
  body: Record<string, unknown>;
  // 测试断言便捷访问（响应体结构由契约测试保证）
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  json: () => any;
}

function wrap(r: supertest.Response): TestResponse {
  return {
    statusCode: r.status,
    body: r.body,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    json: (): any => r.body,
  };
}

export async function req(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  payload?: unknown,
  token?: string,
): Promise<TestResponse> {
  await getApp();
  let r = http()[method.toLowerCase() as 'get' | 'post' | 'put' | 'delete'](url);
  if (token) r = r.set('authorization', `Bearer ${token}`);
  if (payload !== undefined) r = r.send(payload as string | object);
  return wrap(await r);
}

export async function login(username: string, password = '12345678'): Promise<string> {
  const res = await req('POST', '/api/v1/auth/login', { username, password });
  if (res.statusCode !== 201) {
    throw new Error(`login ${username} failed: ${res.statusCode} ${JSON.stringify(res.body)}`);
  }
  return (res.json() as { data: { accessToken: string } }).data.accessToken;
}
