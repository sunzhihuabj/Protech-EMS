/**
 * 枚举止典 E-01 ~ E-20（05 册 §5 唯一事实源）。
 * 规范（NFR-10）：枚举一律存英文 code，展示中文 label。
 * Prisma 侧统一用 String 列存储 code（见 docs/V2-ALIGNMENT.md §四），
 * 本文件是 code 的唯一合法来源 —— 服务端校验、前端下拉、种子数据三处同源。
 */

export type EnumDef = Record<string, string>; // code -> label

/** E-01 主体类型 */
export const PARTY_TYPE: EnumDef = {
  owner: '业主',
  oem: '整机厂',
  related: '关联方',
};

/** E-02 订单类型（决定建档路径与质保默认值：初装 60 月 / 技改 12 月） */
export const ORDER_TYPE: EnumDef = {
  owner_retrofit: '业主技改',
  oem_initial: '整机厂初装',
  spare: '备件',
  inspection: '定检服务',
};

/** 各订单类型默认质保月数（FR-M01-008） */
export const ORDER_TYPE_WARRANTY_MONTHS: Record<string, number> = {
  owner_retrofit: 12,
  oem_initial: 60,
  spare: 12,
  inspection: 12,
};

/** E-03 产品线 */
export const PRODUCT_LINE: EnumDef = {
  climber: '免爬器',
  lift: '升降机',
  ladder: '爬梯',
  rail: '导轨',
  video: '视频监控',
  fire: '自动消防',
};

/** E-04 项目状态（05 册 §6.1 状态机） */
export const PROJECT_STATUS: EnumDef = {
  pending_farm: '待关联风场',
  pending_exec: '签约待执行',
  executing: '执行中',
  shipped: '已发货',
  accepted: '已验收',
  warranty: '质保期中',
  closed: '已关闭',
};

/** E-05 合同状态（05 册 §6.2 状态机） */
export const CONTRACT_STATUS: EnumDef = {
  drafting: '起草中',
  reviewed: '已评审',
  signed: '已签署',
  performing: '履行中',
  changed: '已变更',
  partial_invoiced: '部分开票回款',
  accepted: '已验收',
  warranty: '质保期中',
  closed: '已关闭',
};

/** E-06 合同性质码 */
export const CONTRACT_NATURE: EnumDef = {
  XS: '销售',
  CG: '采购外包',
  KJ: '框架',
};

/** E-07 卡片状态（M10） */
export const CARD_STATUS: EnumDef = {
  todo: '待办',
  doing: '进行中',
  blocked: '受阻(红灯)',
  done: '已完成',
};

/** E-08 补货原因（M06） */
export const REPLENISH_REASON: EnumDef = {
  wear: '正常损坏',
  qty_short: '数量不足',
  quality: '零件质量',
  pre_issue: '维修预领',
  lost_site: '现场丢失',
  lost_customer: '客户损坏丢失',
  design_add: '设计追加',
  other: '其它',
};

/** E-09 差异归因（M06 盘点） */
export const VARIANCE_CAUSE: EnumDef = {
  doc_late: '单据滞后',
  reissue_unbooked: '补发货未记账',
  mixed: '两司串料',
  pick_error: '领退料错账',
  lost: '丢失',
  meter: '计量误差',
};

/** E-10 工单状态（05 册 §6.4） */
export const WORK_ORDER_STATUS: EnumDef = {
  planned: '已计划',
  kit_checked: '齐套通过',
  started: '已开工',
  reported: '已报工',
  finished: '已完工',
  closed: '已关闭',
};

/** E-11 采购单状态 */
export const PO_STATUS: EnumDef = {
  draft: '草稿',
  ordered: '已下单',
  partial_arrived: '部分到货',
  arrived: '已到货',
  closed: '已关闭',
};

/** E-12 呆滞处置出路 */
export const DISPOSAL_WAY: EnumDef = {
  reuse: '优先领用',
  spare: '备件利用',
  remanufacture: '改制',
  discount: '折价',
  scrap: '报废',
  customer: '客户消化',
};

/** E-13 档案门类（M12） */
export const ARCHIVE_CATEGORY: EnumDef = {
  XZ: '行政',
  ZZ: '党务',
  HT: '合同',
  CW: '财务',
  RS: '人事',
  GC: '工程',
  JY: '经营',
  SX: '声像',
};

/** E-14 保管期限 */
export const RETENTION: EnumDef = {
  Y: '永久',
  S30: '30年',
  S10: '10年',
};

/** E-15 法人主体（两司分账，Q-01：TGL 停用统一 TLD） */
export const LEGAL_ENTITY: EnumDef = {
  PRT: '普若泰克',
  TLD: '特固兰',
};

/** E-16 数据范围（六档，FR-M00-002） */
export const DATA_SCOPE: EnumDef = {
  self: '本人',
  dept: '本部门',
  chain: '本链',
  dept_projects: '本部门参与项目',
  all: '全公司',
};

/** E-17 升级层级（M10 红灯引擎） */
export const ESCALATION_LEVEL: EnumDef = {
  board: '板内',
  executive: '总板',
  meeting: '公司周会',
};

/** E-18 AI 能力等级（FR-M16-014 治理红线） */
export const AI_LEVEL: EnumDef = {
  auto: '直执行',
  ask: '先确认',
  never: '禁止',
};

/** E-19 BOM 类型 */
export const BOM_TYPE: EnumDef = {
  full: '完整BOM',
  param: '参数化BOM',
  eng: '工程BOM',
};

/** E-20 三色标识（货位/物料状态） */
export const THREE_COLOR: EnumDef = {
  blue: '高流动',
  yellow: '通用待用',
  red: '呆滞',
};

/** 系统角色（03 册 §3.2，RBAC 种子同源） */
export const ROLE: EnumDef = {
  'R-EXEC': '总经理/高管',
  'R-CHAIN': '链主',
  'R-DEPT': '部门负责人',
  'R-PM': '项目负责人',
  'R-OP': '业务操作员',
  'R-FIN': '财务',
  'R-QA': '质量安全',
  'R-GA': '企管/档案',
  'R-SYS': '系统管理员',
};

/** 归一词典类型（FR-M13-003） */
export const NORM_DICT_TYPE: EnumDef = {
  typo: '错别字',
  symbol: '符号',
  unit: '单位',
  sep: '分隔符',
};

/** 取枚举全部合法 code */
export function codes(def: EnumDef): string[] {
  return Object.keys(def);
}

/** 校验 code 合法性 */
export function isValidCode(def: EnumDef, code: unknown): boolean {
  return typeof code === 'string' && Object.prototype.hasOwnProperty.call(def, code);
}

/** code -> label；未知 code 原样返回（防御脏数据） */
export function labelOf(def: EnumDef, code: string): string {
  return def[code] ?? code;
}
