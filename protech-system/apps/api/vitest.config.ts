import { defineConfig } from 'vitest/config';
import swc from 'unplugin-swc';

export default defineConfig({
  // NestJS 依赖 emitDecoratorMetadata（esbuild 不支持），测试走 SWC 转换
  plugins: [swc.vite({ module: { type: 'es6' } })],
  test: {
    include: ['src/**/*.spec.ts', 'test/**/*.spec.ts'],
    globalSetup: ['./test/global-setup.ts'],
    testTimeout: 120_000,
    hookTimeout: 120_000,
    pool: 'threads',
    poolOptions: { threads: { singleThread: true } }, // 单测试库，串行执行
    env: {
      NODE_ENV: 'test',
      DATABASE_URL: 'mysql://root:root@localhost:3306/protech_v2_test',
      JWT_SECRET: 'test-secret-do-not-use-in-prod',
      PORT: '0',
    },
  },
});
