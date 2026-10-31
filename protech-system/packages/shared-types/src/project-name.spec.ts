import { describe, it, expect } from 'vitest';
import { generateStandardName, stripFarmSuffix, similarity, normalizeName } from '../src';

describe('FR-M01-009 项目名称自动生成器', () => {
  it('标准名 = 业主-地名-期次（S-02 示例）', () => {
    const out = generateStandardName({
      ownerName: '三峡新能源',
      farmName: '姚安大龙口风力发电场',
      phase: '二期',
      orderType: 'owner_retrofit',
    });
    expect(out.standardName).toBe('三峡新能源-姚安大龙口-二期');
    expect(out.farmCore).toBe('姚安大龙口');
    expect(out.warnOwnerMissing).toBe(false);
  });
  it('全称去尾缀', () => {
    expect(stripFarmSuffix('华能安阳龙安风力发电场')).toBe('华能安阳龙安');
    expect(stripFarmSuffix('国电投某风电场站')).toBe('国电投某');
  });
  it('缺业主时含地名 → 红色提醒', () => {
    const out = generateStandardName({ farmName: '姚安大龙口风电场', phase: '一期' });
    expect(out.warnOwnerMissing).toBe(true);
  });
});

describe('FR-M01-003 相似度查重', () => {
  it('归一化：全角/空格/括注/尾缀', () => {
    expect(normalizeName('三峡新能源（集团）有限公司')).toBe('三峡新能源');
    expect(normalizeName('远 景 能 源')).toBe('远景能源');
  });
  it('同主体不同写法 ≥0.85', () => {
    expect(similarity('三峡新能源（集团）有限公司', '三峡新能源')).toBeGreaterThanOrEqual(0.85);
    expect(similarity('远景能源科技有限公司', '远景能源')).toBeGreaterThanOrEqual(0.85);
  });
  it('不同主体 <0.85', () => {
    expect(similarity('三峡新能源', '华能新能源')).toBeLessThan(0.85);
  });
});
