import { execSync } from 'node:child_process';
import { join } from 'node:path';

// vitest globalSetup 以 vitest.config.ts 所在目录为 cwd（apps/api）
const cwd = process.cwd();
const SCHEMA = '../../prisma/schema.mysql.prisma'; // Monorepo 根 prisma/（11 册 §三结构）
const TEST_DB = 'protech_v2_test';

export default async function setup() {
  const env = {
    ...process.env,
    DATABASE_URL: `mysql://root:root@localhost:3306/${TEST_DB}`,
    JWT_SECRET: 'test-secret-do-not-use-in-prod',
  };
  console.log('[global-setup] 生成 MySQL 镜像 schema…');
  execSync('node ../../scripts/sync-dev-schema.mjs', { cwd, stdio: 'inherit' });
  console.log('[global-setup] prisma generate（mysql client）…');
  execSync(`npx prisma generate --schema ${SCHEMA}`, { cwd, stdio: 'inherit', env });

  // 建库（utf8mb4；generate 完成后再动态加载 client，连接系统库 mysql 执行 DDL）
  const { PrismaClient } = await import('@prisma/client');
  const admin = new PrismaClient({ datasources: { db: { url: 'mysql://root:root@localhost:3306/mysql' } } });
  try {
    await admin.$executeRawUnsafe(
      `CREATE DATABASE IF NOT EXISTS ${TEST_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
    );
  } finally {
    await admin.$disconnect();
  }

  console.log('[global-setup] prisma db push（重建表结构）…');
  execSync(`npx prisma db push --force-reset --skip-generate --accept-data-loss --schema ${SCHEMA}`, {
    cwd,
    stdio: 'inherit',
    env,
  });
  console.log('[global-setup] 种子数据…');
  execSync('npx tsx seed.ts', { cwd, stdio: 'inherit', env });
  console.log('[global-setup] 完成');
}
