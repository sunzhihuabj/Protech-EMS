/**
 * 工作日计算（BR-02 用印后 3 个工作日、借阅逾期 5 工作日等）。
 * 节假日表由 sys_config(key=holidays) 配置，格式 YYYY-MM-DD 逗号分隔；
 * 默认仅跳过周六周日（公司无明确调休数据前保持保守）。
 */

export function parseDate(s: string): Date {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) throw new Error(`日期格式非法：${s}（需 YYYY-MM-DD）`);
  return new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]));
}

export function fmtDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

export function isWeekend(d: Date): boolean {
  const day = d.getUTCDay();
  return day === 0 || day === 6;
}

/** 是否节假日（配置表优先于周末判断，调休补班日holiday=false则不算节假日） */
export function isHoliday(d: Date, holidays: string[] = []): boolean {
  const iso = fmtDate(d);
  if (holidays.includes(iso)) return true;
  return isWeekend(d);
}

/** 加 n 个工作日（n>=0）；signDate 当日不计为第一个工作日 */
export function addBusinessDays(start: Date, n: number, holidays: string[] = []): Date {
  const d = new Date(start.getTime());
  let remain = n;
  while (remain > 0) {
    d.setUTCDate(d.getUTCDate() + 1);
    if (!isHoliday(d, holidays)) remain--;
  }
  return d;
}

/** start 到 end 之间（不含 start，含 end）的工作日数 */
export function businessDaysBetween(start: Date, end: Date, holidays: string[] = []): number {
  const s = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), start.getUTCDate()));
  const e = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), end.getUTCDate()));
  if (e < s) return -businessDaysBetween(end, start, holidays);
  let count = 0;
  const cur = new Date(s.getTime());
  while (cur < e) {
    cur.setUTCDate(cur.getUTCDate() + 1);
    if (!isHoliday(cur, holidays)) count++;
  }
  return count;
}

/** 用印后建档截止：用印日 + 3 个工作日（BR-02） */
export function archiveDeadline(sealedAt: Date, holidays: string[] = []): Date {
  return addBusinessDays(sealedAt, 3, holidays);
}
