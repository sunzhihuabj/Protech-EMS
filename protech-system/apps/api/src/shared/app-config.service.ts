import { Injectable } from '@nestjs/common';
import { PrismaService } from './prisma.service';

/**
 * 系统配置中心（FR-M00-009）：阈值参数集中可配、改动留痕、默认值=制度值。
 * 制度默认值来源：红灯 3 天（BR-07）/响应 24h+48h（FR-M10-015）/建档 3 日（BR-02）/
 * 分级金额线 500/5000（FR-M06-013）/查重相似度 0.85（FR-M01-003）/备料 20%（BR-06）。
 */
export const CONFIG_DEFAULTS: Record<string, { value: string; type: string; remark: string }> = {
  kb_redlight_days: { value: '3', type: 'number', remark: '红灯卡自动升级天数（BR-07）' },
  respond_hours_first: { value: '24', type: 'number', remark: '跨部门卡点挂出时限（FR-M10-015）' },
  respond_hours_second: { value: '48', type: 'number', remark: '责任方响应时限，超时升级周会（FR-M10-015）' },
  archive_limit_workdays: { value: '3', type: 'number', remark: '用印后建档时限（工作日，BR-02）' },
  replenish_tier1_cents: { value: '50000', type: 'number', remark: '补货一级审批金额线（分）' },
  replenish_tier2_cents: { value: '500000', type: 'number', remark: '补货二级审批金额线（分）' },
  spare_review_over_pct: { value: '20', type: 'number', remark: '备料超订单需求百分比评审线（BR-06）' },
  dup_similarity_threshold: { value: '0.85', type: 'number', remark: '主数据查重相似度阈值（FR-M01-003）' },
  holidays: { value: '[]', type: 'json', remark: '节假日表（YYYY-MM-DD 数组，含调休上班日按周末判定的例外）' },
  meeting_remind_days: { value: '2', type: 'number', remark: '日板未开早会提醒天数（FR-M10-022）' },
};

@Injectable()
export class AppConfigService {
  private cache = new Map<string, { value: string; expiresAt: number }>();
  private static TTL = 30_000;

  constructor(private readonly prisma: PrismaService) {}

  async get(key: string, defaultValue?: string): Promise<string> {
    const hit = this.cache.get(key);
    if (hit && hit.expiresAt > Date.now()) return hit.value;
    const row = await this.prisma.sysConfig.findFirst({ where: { key, deletedAt: null } });
    const value = row?.value ?? defaultValue ?? CONFIG_DEFAULTS[key]?.value ?? '';
    this.cache.set(key, { value, expiresAt: Date.now() + AppConfigService.TTL });
    return value;
  }

  async getNumber(key: string, defaultValue = 0): Promise<number> {
    const v = await this.get(key, String(defaultValue));
    const n = Number(v);
    return Number.isFinite(n) ? n : defaultValue;
  }

  async getHolidays(): Promise<string[]> {
    const v = await this.get('holidays', '[]');
    try {
      const arr = JSON.parse(v);
      return Array.isArray(arr) ? arr.filter((x) => typeof x === 'string') : [];
    } catch {
      return [];
    }
  }

  invalidate(key?: string) {
    if (key) this.cache.delete(key);
    else this.cache.clear();
  }
}
