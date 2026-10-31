#!/usr/bin/env node
/**
 * NFR-12 FR 编号可追溯检查：04 册 FR-M##-nnn 在代码注释/接口路径可检索。
 * 扫描 apps/ packages/ 源码中的 FR-M##-nnn 标记，输出覆盖清单与缺口。
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const FR_RE = /FR-M\d{2}-\d{3}/g;

// 04 册 FR 总清单（编号：模块.序号）
const ALL = {
  M00: 14, M01: 18, M02: 8, M03: 15, M04: 16, M05: 12, M06: 22, M07: 12,
  M08: 10, M09: 8, M10: 30, M11: 9, M12: 10, M13: 12, M14: 8, M15: 12, M16: 14,
};

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (['node_modules', 'dist', '.git', 'coverage'].includes(name)) continue;
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (/\.(ts|tsx|mjs)$/.test(name)) out.push(p);
  }
  return out;
}

const found = new Set();
for (const base of ['apps', 'packages', 'docs']) {
  const dir = join(root, base);
  if (!existsSync(dir)) continue;
  for (const f of walk(dir)) {
    const src = readFileSync(f, 'utf8');
    for (const m of src.matchAll(FR_RE)) found.add(m[0]);
  }
}

let covered = 0;
let total = 0;
const missing = [];
for (const [mod, count] of Object.entries(ALL)) {
  for (let i = 1; i <= count; i++) {
    const id = `FR-${mod}-${String(i).padStart(3, '0')}`;
    total++;
    if (found.has(id)) covered++;
    else missing.push(id);
  }
}
console.log(`FR 覆盖：${covered}/${total}（${((covered / total) * 100).toFixed(1)}%）`);
console.log(`未在代码中标注的 FR（按分期规划补齐，见 docs/SPRINT-*.md 遗留清单）：`);
console.log(missing.join(' '));
process.exit(0);
