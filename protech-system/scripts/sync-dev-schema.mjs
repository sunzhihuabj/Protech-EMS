#!/usr/bin/env node
/**
 * 生成本机开发/测试用 MySQL 镜像 schema（docs/V2-ALIGNMENT.md §四）。
 * 事实源永远是 prisma/schema.prisma（PostgreSQL 16 交付物）。
 * 为何选 MySQL 而非 SQLite 做本地镜像：Prisma 在 SQLite 上 BIGINT 主键无法成为
 * rowid 别名（"BIGINT PRIMARY KEY"≠"INTEGER PRIMARY KEY"），autoincrement 直接失效；
 * MySQL 的 BIGINT AUTOINCREMENT 与 PG 语义一致，且本机已有 MySQL 5.7 服务。
 * 镜像差异仅：
 *   1. provider "postgresql" → "mysql"
 *   2. @db.Timestamptz(6) → 剥离（Prisma MySQL DateTime → DATETIME(3)）
 *   3. 其余（@db.Text/@db.Decimal/BigInt）两端通用
 * 禁止手工编辑 schema.mysql.prisma（gitignore），一切改动进 schema.prisma 后重跑本脚本。
 */
import { readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = readFileSync(join(root, 'prisma', 'schema.prisma'), 'utf8');

const out = src
  .replace(/provider\s*=\s*"postgresql"/, 'provider = "mysql"')
  .replace(/\s+@db\.Timestamptz\(\d+\)/g, '')
  .replace(/^\/\/ 注意：本文件为 PostgreSQL[\s\S]*?差异仅 provider 与 @db\.\* 原生类型标注。\n/m, '');

const header = `// ⚠️ 自动生成（scripts/sync-dev-schema.mjs）——本机开发/测试镜像（MySQL），勿编辑；事实源 schema.prisma（PostgreSQL）\n`;

const target = join(root, 'prisma', 'schema.mysql.prisma');
writeFileSync(target, header + out);
// 清理历史产物（早期 SQLite 镜像）
try { unlinkSync(join(root, 'prisma', 'schema.sqlite.prisma')); } catch {}
console.log('schema.mysql.prisma 已生成');
