/** 输出序列化：BigInt→String（前端精度安全，沿用 Java 版全局 Long→String 教训）、Date→ISO */
export function serialize(value: unknown): unknown {
  if (value === null || value === undefined) return value;
  if (typeof value === 'bigint') return value.toString();
  if (value instanceof Date) return value.toISOString();
  if (Array.isArray(value)) return value.map(serialize);
  if (value instanceof Map) {
    const o: Record<string, unknown> = {};
    for (const [k, v] of value) o[String(k)] = serialize(v);
    return o;
  }
  if (value instanceof Set) return Array.from(value).map(serialize);
  if (typeof value === 'object') {
    const o: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value)) o[k] = serialize(v);
    return o;
  }
  return value;
}

import { BizException } from './biz.exception';

/** 输入 id 解析：string/number → bigint；传自由文本即 403 DQ-04（BR-08 能选不填） */
export function parseId(value: unknown, field = 'id'): bigint {
  if (typeof value === 'bigint') return value;
  if (typeof value === 'number' && Number.isInteger(value)) return BigInt(value);
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) return BigInt(value.trim());
  throw Object.assign(
    BizException.forbidden(
      `${field} 仅支持从主数据下拉选择 ID，禁止自由文本（BR-08）；如无对应档案请先提交建档申请`,
      'BR-08',
    ),
    { dqCode: 'DQ-04' },
  );
}

export function parseIdOrNull(value: unknown, field = 'id'): bigint | null {
  if (value === null || value === undefined || value === '') return null;
  return parseId(value, field);
}

/** 逗号分隔 id 列表 */
export function parseIds(value: unknown): bigint[] {
  if (value === null || value === undefined) return [];
  const arr = Array.isArray(value) ? value : String(value).split(',');
  return arr.map((v) => parseId(v)).filter((v) => v !== null);
}
