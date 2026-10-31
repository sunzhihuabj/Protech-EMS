/**
 * BR-10 金额分存储、禁止浮点。
 * - 库内一律 amount_cents BIGINT（整数分）
 * - 录入单位万元 → 落库 ×1,000,000（05 册 §一）
 * - 比例 DECIMAL(6,4) 存小数；税率 DECIMAL(5,4)
 * 本模块是唯一允许的金额换算出口；禁止在业务代码里对金额做浮点运算。
 */

/** 万 → 分（整数）；输入必须是可精确表示的十进制字符串/数字 */
export function wanToCents(wan: string | number): bigint {
  return decimalToCents(wan, 6); // 1 万 = 1,000,000 分
}

/** 元 → 分（整数） */
export function yuanToCents(yuan: string | number): bigint {
  return decimalToCents(yuan, 2);
}

/** 分 → 元（展示用，返回字符串避免浮点） */
export function centsToYuan(cents: bigint | number): string {
  const c = typeof cents === 'number' ? BigInt(Math.trunc(cents)) : cents;
  const neg = c < 0n;
  const abs = neg ? -c : c;
  const yuan = abs / 100n;
  const fen = abs % 100n;
  return `${neg ? '-' : ''}${yuan}.${fen.toString().padStart(2, '0')}`;
}

/** 分 → 万元（展示用字符串，最多 4 位小数） */
export function centsToWan(cents: bigint | number): string {
  const c = typeof cents === 'number' ? BigInt(Math.trunc(cents)) : cents;
  const neg = c < 0n;
  let abs = neg ? -c : c;
  // 1 万 = 1,000,000 分
  const wan = abs / 1000000n;
  const rem = abs % 1000000n;
  abs = rem;
  let frac = '';
  for (let i = 0; i < 4; i++) {
    abs *= 10n;
    const d = abs / 1000000n;
    frac += d.toString();
    abs %= 1000000n;
    if (abs === 0n) break;
  }
  frac = frac.replace(/0+$/, '');
  return `${neg ? '-' : ''}${wan}${frac ? '.' + frac : ''}`;
}

/** 十进制字符串 → 分（scale 指定输入的小数位换算：元 scale=2，万 scale=6） */
export function decimalToCents(value: string | number, scale: number): bigint {
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) throw new Error('金额非法：非有限数字');
    // 数字路径仅接受整数或安全小数位；统一转字符串处理
    value = value.toString();
  }
  const s = value.trim();
  if (!/^[+-]?\d*(\.\d*)?$/.test(s) || s === '' || s === '.' || s === '-' || s === '+') {
    throw new Error(`金额非法：${value}`);
  }
  const neg = s.startsWith('-');
  const body = s.replace(/^[+-]/, '');
  const [intPart = '', fracPartRaw = ''] = body.split('.');
  const fracPart = (fracPartRaw + '0'.repeat(scale)).slice(0, scale);
  const rest = fracPartRaw.slice(scale);
  // 超出精度的尾数必须为 0，否则拒绝（禁浮点，避免静默截断）
  if (rest && /[1-9]/.test(rest)) {
    throw new Error(`金额精度超限：${value}（最多 ${scale} 位小数）`);
  }
  const digits = (intPart === '' ? '0' : intPart) + fracPart;
  const result = BigInt(digits);
  return neg ? -result : result;
}

/** 校验接口入参是"整数分"（BR-10 接口层硬校验） */
export function assertCents(value: unknown, field: string): bigint {
  if (typeof value === 'bigint') return value;
  if (typeof value === 'number' && Number.isInteger(value)) return BigInt(value);
  if (typeof value === 'string' && /^-?\d+$/.test(value.trim())) return BigInt(value.trim());
  throw new Error(`${field} 必须为整数分（BR-10 禁止浮点金额）`);
}

/** 比例输入（0.09 或 "0.09"）→ DECIMAL(6,4) 字符串 */
export function ratioToString(value: string | number, maxScale = 4): string {
  const s = typeof value === 'number' ? value.toString() : value.trim();
  if (!/^[+-]?\d*(\.\d*)?$/.test(s) || s === '' || s === '.') throw new Error(`比例非法：${value}`);
  const [, frac = ''] = s.split('.');
  if (frac.length > maxScale) throw new Error(`比例精度超限：${value}（最多 ${maxScale} 位小数）`);
  return Number(s).toFixed(maxScale);
}
