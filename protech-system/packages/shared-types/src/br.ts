/**
 * 业务红线 BR-01 ~ BR-12（04 册 §3 唯一事实源）。
 * 前后端同源：服务端硬校验返回 blockCode，前端按 blockCode 渲染 BlockCodeAlert。
 */

export interface BrRule {
  code: string;
  rule: string;
  message: string;
}

export const BR_RULES: BrRule[] = [
  {
    code: 'BR-01',
    rule: '合同评审提交时项目编码必填',
    message: '评审未关联项目编码，请先建档取码或选择已有项目',
  },
  {
    code: 'BR-02',
    rule: '用印后 3 个工作日未建档 → 冻结开票申请',
    message: '该合同已用印超过 3 个工作日未建档，开票申请已被冻结；请先完成建档',
  },
  {
    code: 'BR-03',
    rule: '无项目编码不排产/不派工/不发货/不报销现场差旅',
    message: '该操作需要项目编码，当前单据未关联项目',
  },
  {
    code: 'BR-04',
    rule: '派工单不选风场不许提交',
    message: '该订单尚未关联风场，请补充风场信息或申请新建风场档案',
  },
  {
    code: 'BR-05',
    rule: 'BOM/设计变更必须创建 ECN 正式单并附旧料处置方案',
    message: '检测到 BOM 变更未走 ECN，请创建变更单；变更单必须附旧料清单与处置方案',
  },
  {
    code: 'BR-06',
    rule: '备料超订单需求 20% 走备货评审',
    message: '备料超出订单需求 20%，需总经理审批后方可执行',
  },
  {
    code: 'BR-07',
    rule: '红灯卡超 3 天自动升级，链主无取消/删除权限',
    message: '红灯卡已超 3 天自动升级总板，仅可解决后关闭（无删除入口）',
  },
  {
    code: 'BR-08',
    rule: '主数据引用字段禁止自由文本',
    message: '该项目字段仅支持从项目档案选择；如无对应项目，请提交建档申请',
  },
  {
    code: 'BR-09',
    rule: '业务编码不可改不可复用',
    message: '业务编码由系统生成，不可修改',
  },
  {
    code: 'BR-10',
    rule: '金额分存储、禁浮点',
    message: '金额必须为整数分（amount_cents），禁止浮点数',
  },
  {
    code: 'BR-11',
    rule: '软删除、审计不可删',
    message: '系统不提供物理删除；数据仅可停用或软删除',
  },
  {
    code: 'BR-12',
    rule: '单据当日移动、禁止次日补录（超 23:59 标记 is_backfill）',
    message: '已超过当日，本次变更将标记为次日补录并计入补录率',
  },
];

export const BR_MAP: Record<string, BrRule> = Object.fromEntries(
  BR_RULES.map((r) => [r.code, r]),
);

/** DQ 数据质量规则拦截码（05 册 §八） */
export const DQ = {
  /** DQ-04 任何接口传名称而非 id */
  DQ_04: 'DQ-04',
} as const;

export type BlockCode = string; // 'BR-01'..'BR-12' | 'E-AUTH' | 'E-RATE' | 'AGT-*'
