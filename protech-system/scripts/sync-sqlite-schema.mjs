#!/usr/bin/env node
/**
 * 生成本机开发/测试用 SQLite 镜像 schema（docs/V2-ALIGNMENT.md §四）。
 * 事实源永远是 prisma/schema.prisma（PostgreSQL）；镜像差异仅：
 *   1. provider "postgresql" → "sqlite"
 *   2. 剥离全部 @db.* 原生类型标注（Timestamptz/Text/Decimal(x,y) 等）
 *   3. Decimal 列 SQLite 保留（Prisma 映射 DECIMAL 文本亲和）
 * 禁止手工编辑 schema.sqlite.prisma（gitignore），一切改动进 schema.prisma 后重跑本脚本。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = readFileSync(join(root, 'prisma', 'schema.prisma'), 'utf8');

const out = src
  .replace(/provider\s*=\s*"postgresql"/, 'provider = "sqlite"')
  .replace(/\s+@db\.\w+(\([\d,\s]+\))?/g, '')
  .replace(/^\/\/ 注意：本文件为 PostgreSQL[\s\S]*?差异仅 provider 与 @db\.\* 原生类型标注。\n/m, '');

// 剥离后可能出现 "  DateTime  " 多空格，不影响合法性；头注说明来源
const header = `// ⚠️ 自动生成（scripts/sync-sqlite-schema.mjs）——本机开发/测试镜像，勿编辑；事实源 schema.prisma（PostgreSQL）\n`;

writeFileSync(join(root, 'prisma', 'schema.sqlite.prisma'), header + out);
console.log('schema.sqlite.prisma 已生成');
