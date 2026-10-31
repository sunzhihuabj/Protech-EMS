import 'reflect-metadata';
import 'dotenv/config';
import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { AppModule } from './app.module';
import { TransformInterceptor, AllExceptionsFilter } from './shared/transform.interceptor';
import { runWithContext } from './shared/request-context';
import type { NextFunction, Request, Response } from 'express';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { logger: ['error', 'warn'] });

  app.setGlobalPrefix('api/v1');
  app.useGlobalInterceptors(new TransformInterceptor());
  app.useGlobalFilters(new AllExceptionsFilter());
  app.useGlobalPipes(
    new ValidationPipe({ whitelist: false, transform: false, validateCustomDecorators: false }),
  );
  app.enableCors({ origin: true, credentials: true });

  // 审计上下文兜底（公开路由无 JWT 时也有 ip）
  app.use((req: Request, res: Response, next: NextFunction) => {
    const ip = (req.headers['x-forwarded-for'] as string)?.split(',')[0]?.trim() ?? req.ip;
    runWithContext({ ip }, () => next());
  });

  const port = Number(process.env.PORT ?? 8080);
  await app.listen(port);
  console.log(`[protech-api] listening on :${port} (env=${process.env.NODE_ENV ?? 'dev'})`);
}

void bootstrap();
