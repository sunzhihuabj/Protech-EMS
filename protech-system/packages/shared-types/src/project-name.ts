/**
 * 项目名称自动生成器（FR-M01-009：4 进 3 出）与三重查重结构（FR-M01-010）。
 * 标准名 = {业主规范名}-{地名/风场名去尾缀}-{期次}
 * 红色提醒：输入含地名未含业主名（一物多名七类问题的主因，01 册 §4.4）。
 */

/** 风场/地名尾缀表（全称去尾缀，FR-M01-004） */
export const FARM_SUFFIXES = [
  '风力发电场',
  '风力发电站',
  '风电场站',
  '风电场',
  '风电站',
  '风场',
  '发电场',
  '电站',
];

/** 常见地域后缀（用于从风场全称提取地名核心） */
export const REGION_SUFFIXES = ['新能源', '风力发电', '风电'];

export interface NameGenInput {
  ownerName?: string; // 业主规范名（业主技改必填；整机厂初装可为空 → 用整机厂）
  farmName: string; // 风场全称
  phase?: string; // 期次（一期/二期/三期/1 期...）
  orderType?: string; // E-02
}

export interface NameGenOutput {
  standardName: string;
  farmCore: string; // 去尾缀后的地名/风场名
  warnOwnerMissing: boolean; // 红色提醒：含地名未含业主名
  suggestions: string[]; // 查重候选（由服务端填充）
  codeSuggestion: string; // 由服务端填充（nextCode 预览）
}

/** 风场全称去尾缀 → 地名核心。例："三峡新能源姚安大龙口风力发电场" → "姚安大龙口" */
export function stripFarmSuffix(fullName: string): string {
  let s = fullName.trim();
  // 先剥风电后缀，再剥公司类尾缀
  for (const suf of FARM_SUFFIXES) {
    if (s.endsWith(suf)) {
      s = s.slice(0, -suf.length);
      break;
    }
  }
  // 剥企业名尾缀（有限公司等）
  s = s.replace(/(股份)?有限公司|有限责任公司/g, '');
  return s.trim();
}

/** 拆分"业主+地名"混合输入：若输入以业主名开头则去掉（生成器防重） */
export function splitOwnerAndFarm(ownerName: string | undefined, farmName: string): string {
  const core = stripFarmSuffix(farmName);
  if (ownerName && core.startsWith(ownerName)) {
    return core.slice(ownerName.length).trim() || core;
  }
  return core;
}

/** 生成标准名（纯函数，可测） */
export function generateStandardName(input: NameGenInput): NameGenOutput {
  const farmCore = splitOwnerAndFarm(input.ownerName, input.farmName);
  let owner = (input.ownerName ?? '').trim();
  // 业主名去企业尾缀生成规范名："三峡新能源（集团）有限公司"→"三峡新能源"
  owner = owner
    .replace(/(股份)?有限公司|有限责任公司|（集团）|\(集团\)/g, '')
    .trim();
  const parts: string[] = [];
  if (owner) parts.push(owner);
  parts.push(farmCore);
  if (input.phase && input.phase.trim()) parts.push(input.phase.trim());
  const standardName = parts.join('-');
  // 红色提醒：输入包含明显地名（场/旗/县/市/山/口/沟/梁等）但未提供业主名
  const warnOwnerMissing =
    !owner && /(旗|县|市|区|山|口|沟|梁|场)/.test(farmCore);
  return {
    standardName,
    farmCore,
    warnOwnerMissing,
    suggestions: [],
    codeSuggestion: '',
  };
}

/** 三重查重请求结构（FR-M01-010） */
export interface ProjectDupCheck {
  /** 一查：标准名精确 */
  standardName?: string;
  /** 二查：常用简称 + 别名 */
  shortName?: string;
  /** 三查：业主 + 地名组合 */
  ownerName?: string;
  farmName?: string;
}

/** 项目档案关键 12 项必填清单（FR-M01-008：关键 12 项） */
export const PROJECT_KEY_FIELDS = [
  'ownerPartyId',
  'windFarmId',
  'legalEntityId',
  'orderType',
  'productLines',
  'unitCount',
  'pmUserId',
  'engOwnerId',
  'salesOwnerId',
  'collectorId',
  'contractAmountCents',
  'phase',
] as const;

export type ProjectKeyField = (typeof PROJECT_KEY_FIELDS)[number];

/** 待补全标记（pending_flags JSON 合法键） */
export const PENDING_FLAGS = ['pending_owner', 'pending_farm', 'pending_sale'] as const;
export type PendingFlag = (typeof PENDING_FLAGS)[number];
