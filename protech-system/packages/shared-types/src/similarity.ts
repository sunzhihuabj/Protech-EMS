/**
 * 名称相似度（查重 FR-M01-003 / FR-M01-010 / DQ-01）。
 * 阈值默认 0.85（可经 sys_config key=dup_similarity_threshold 调整）。
 * 算法：归一化（全角转半角/去空白/去括注/小写/去企业尾缀）后 Levenshtein 归一相似度，
 * 一方完全包含另一方时给予包含加成（中文主体名常见"全称 vs 简称"形态）。
 */

const FULLWIDTH_OFFSET = 0xfee0;

export function normalizeName(input: string): string {
  let s = '';
  for (const ch of input) {
    const c = ch.codePointAt(0)!;
    s += c >= 0xff01 && c <= 0xff5e ? String.fromCharCode(c - FULLWIDTH_OFFSET) : ch;
  }
  s = s.toLowerCase();
  s = s.replace(/\s+/g, '');
  // 先整段去括注（三峡新能源（集团）→ 三峡新能源），再兜底去裸括号
  s = s.replace(/（[^）]*）|\([^)]*\)/g, '');
  s = s.replace(/[（）()\[\]【】]/g, '');
  // 去企业尾缀：循环剥离（集团/有限公司可叠加出现）
  const SUFFIXES = ['有限责任公司', '股份有限公司', '有限公司', '集团', '公司'];
  let changed = true;
  while (changed) {
    changed = false;
    for (const suf of SUFFIXES) {
      if (s.endsWith(suf)) {
        s = s.slice(0, -suf.length);
        changed = true;
      }
    }
  }
  s = s.replace(/[·•・\-—_]/g, '');
  return s;
}

function jaro(a: string, b: string): number {
  if (a === b) return 1;
  const matchWindow = Math.max(0, Math.floor(Math.max(a.length, b.length) / 2) - 1);
  const aFlags = new Array<boolean>(a.length).fill(false);
  const bFlags = new Array<boolean>(b.length).fill(false);
  let matches = 0;
  for (let i = 0; i < a.length; i++) {
    const lo = Math.max(0, i - matchWindow);
    const hi = Math.min(b.length - 1, i + matchWindow);
    for (let j = lo; j <= hi; j++) {
      if (!bFlags[j] && a[i] === b[j]) {
        aFlags[i] = true;
        bFlags[j] = true;
        matches++;
        break;
      }
    }
  }
  if (matches === 0) return 0;
  let transpositions = 0;
  let k = 0;
  for (let i = 0; i < a.length; i++) {
    if (!aFlags[i]) continue;
    while (!bFlags[k]) k++;
    if (a[i] !== b[k]) transpositions++;
    k++;
  }
  transpositions /= 2;
  return (matches / a.length + matches / b.length + (matches - transpositions) / matches) / 3;
}

/** Jaro-Winkler（前缀加权，适合中文主体名"全称 vs 简称"形态）∈ [0,1] */
function jaroWinkler(a: string, b: string): number {
  const j = jaro(a, b);
  let prefix = 0;
  for (let i = 0; i < Math.min(4, a.length, b.length); i++) {
    if (a[i] === b[i]) prefix++;
    else break;
  }
  return j + prefix * 0.1 * (1 - j);
}

/** 归一化相似度 ∈ [0,1]；包含关系给比例加成（封顶 1） */
export function similarity(a: string, b: string): number {
  const na = normalizeName(a);
  const nb = normalizeName(b);
  if (na === nb) return 1;
  if (na.length === 0 || nb.length === 0) return 0;
  let score = jaroWinkler(na, nb);
  if (na.includes(nb) || nb.includes(na)) {
    score = Math.max(score, Math.min(na.length, nb.length) / Math.max(na.length, nb.length) + 0.1);
  }
  return Math.min(1, Math.max(0, score));
}

/** 从候选里找 ≥threshold 的命中组 */
export function findDupCandidates<T>(
  target: string,
  candidates: T[],
  getName: (item: T) => string,
  threshold = 0.85,
): Array<{ item: T; score: number }> {
  const hits: Array<{ item: T; score: number }> = [];
  for (const item of candidates) {
    const score = similarity(target, getName(item));
    if (score >= threshold) hits.push({ item, score });
  }
  return hits.sort((x, y) => y.score - x.score);
}
