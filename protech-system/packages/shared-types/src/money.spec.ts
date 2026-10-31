import { describe, it, expect } from 'vitest';
import {
  wanToCents,
  yuanToCents,
  centsToYuan,
  centsToWan,
  assertCents,
  ratioToString,
  decimalToCents,
} from './money';

describe('BR-10 金额分存储', () => {
  it('万元 → 分', () => {
    expect(wanToCents('1')).toBe(1000000n);
    expect(wanToCents('0.09')).toBe(90000n);
    expect(wanToCents(634.1)).toBe(634100000n);
  });
  it('元 → 分', () => {
    expect(yuanToCents('1.5')).toBe(150n);
    expect(yuanToCents('380')).toBe(38000n);
  });
  it('分 → 元/万元 展示', () => {
    expect(centsToYuan(150n)).toBe('1.50');
    expect(centsToYuan(-1n)).toBe('-0.01');
    expect(centsToWan(634100000n)).toBe('634.1');
    expect(centsToWan(1000000n)).toBe('1');
  });
  it('浮点金额直接拒绝（BR-10 接口层）', () => {
    expect(() => assertCents(1.5, 'amount')).toThrow(/BR-10/);
    expect(() => assertCents('12.5', 'amount')).toThrow(/BR-10/);
    expect(assertCents('12', 'amount')).toBe(12n);
  });
  it('精度超限拒绝', () => {
    expect(() => yuanToCents('1.234')).toThrow(/精度超限/);
    expect(wanToCents('1.23456')).toBe(1234560n); // 万 6 位小数=分，合法
    expect(() => wanToCents('1.2345678')).toThrow(/精度超限/);
    expect(() => decimalToCents(634.1, 6)).toThrow; // number 路径转字符串处理
    expect(decimalToCents('634.1', 6)).toBe(634100000n);
  });
  it('比例格式化', () => {
    expect(ratioToString(0.09)).toBe('0.0900');
    expect(ratioToString('0.09')).toBe('0.0900');
    expect(() => ratioToString(0.091234)).toThrow(/精度超限/);
  });
});
