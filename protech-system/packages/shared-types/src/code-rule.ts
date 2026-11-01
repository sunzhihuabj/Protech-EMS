/**
 * 编码规则总表（05 册 §四，FR-M00-005 编码引擎配置）。
 * pattern 模板变量：{YYYY} 年度 {NNNN}/{NNN}/{N..} 流水 {entity} 主体码 {nature} 性质码 {biz} 业务码 {cat} 门类 {ret} 期限码
 * BR-09：编码一经生成不可修改、不可复用。
 */

export interface CodeRuleDef {
  bizType: string;
  pattern: string;
  seqLen: number;
  /** 流水是否按年度周期重置 */
  yearly: boolean;
  /** 编码前是否查重（默认对唯一索引表天然生效） */
  remark?: string;
}

export const CODE_RULES: CodeRuleDef[] = [
  { bizType: 'project', pattern: 'PJ{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: 'A-6 取 4 位流水口径' },
  { bizType: 'wind_farm', pattern: 'WF{YYYY}-{NNN}', seqLen: 3, yearly: true, remark: '新增（Q-10）' },
  { bizType: 'quotation', pattern: 'QT{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增（Q-10）' },
  {
    bizType: 'contract',
    pattern: '{entity}-{YYYY}-{nature}-{biz}-{NNNN}',
    seqLen: 4,
    yearly: true,
    remark: 'TGL 停用统一 TLD（Q-01）',
  },
  { bizType: 'ecn', pattern: 'ECN{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增' },
  { bizType: 'bom', pattern: 'BOM{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增（Q-10 补位）' },
  { bizType: 'work_order', pattern: 'WO{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增' },
  { bizType: 'purchase_req', pattern: 'PR{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增' },
  { bizType: 'purchase_order', pattern: 'PO{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增' },
  { bizType: 'dispatch', pattern: 'PG{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增' },
  { bizType: 'archive', pattern: '{entity}-{YYYY}-{cat}-{ret}-{NNNN}', seqLen: 4, yearly: true, remark: '沿用制度（PRT-2026-HT-Y-0007）' },
  { bizType: 'party', pattern: 'C{NNNN}', seqLen: 4, yearly: false, remark: '沿用工具表 C0087' },
  { bizType: 'supplier', pattern: 'SU{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '新增（Q-10 补位）' },
  { bizType: 'gov_task', pattern: 'GT{YYYY}-{NNNN}', seqLen: 4, yearly: true, remark: '治理工单（M13，新增）' },
  { bizType: 'stocktake', pattern: 'ST{YYYY}{MM}-{NNN}', seqLen: 3, yearly: true, remark: '盘点任务（M06 R2，新增）' },
];

/** 渲染编码：把模板变量替换为实际值 */
export function renderCode(
  pattern: string,
  vars: { year: string; seq: string; month?: string; entity?: string; nature?: string; biz?: string; cat?: string; ret?: string },
): string {
  return pattern
    .replace('{YYYY}', vars.year)
    .replace('{MM}', vars.month ?? String(new Date().getMonth() + 1).padStart(2, '0'))
    .replace('{entity}', vars.entity ?? '')
    .replace('{nature}', vars.nature ?? '')
    .replace('{biz}', vars.biz ?? '')
    .replace('{cat}', vars.cat ?? '')
    .replace('{ret}', vars.ret ?? '')
    .replace(/\{N+\}/, vars.seq);
}

/** 补充协议号：主合同号 + -BC{n}（FR-M03-002） */
export function bcCode(masterCode: string, n: number): string {
  return `${masterCode}-BC${n}`;
}
