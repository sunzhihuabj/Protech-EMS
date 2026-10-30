# SPRINT S12 · 轨道一 A1（呆滞与处置服务落地）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/V4-04`（M07 T-INV-07/08）+ E-38/E-39 种子字典

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S12-1 | 呆滞登记 | SlowMovingService.create：CANDIDATE（库龄跑批/ECN 来源） | ✅ |
| S12-2 | 呆滞确认 | confirm：E-38 六类归因必填（DESIGN/PURCHASE_OVER/PRODUCTION_LEFT/ORDER_CANCEL/QUALITY/HISTORY），缺失 422+INV-NO-RESPONSIBILITY | ✅ |
| S12-3 | 处置发起 | createDisposal：E-39 六类方式（REUSE/SERVICE/REMODEL/SELL/SCRAP/CUSTOMER）+ 数量不超余量守卫 + DP 编号 + 未确认拦截 422 | ✅ |
| S12-4 | 处置审批 | approve：L1/L2/L3 动态权限（沿 A-31 阈值，实测 store@demo 调 L2 被 2002 正确拦截） | ✅ |
| S12-5 | 处置完成 | complete：未审批拦截 422+INV-DISPOSAL-NOT-APPROVED → 回填实际金额 → 台账 DISPOSED | ✅ |

## 二、问题与解决（含 S11 偏差修正）

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | **S11 盘点归因枚举与字典不符** | 自造 SCRAP/LOSS 等，种子 E-37 实为 DOC_LAG/REPLENISH_UNBOOKED/MIX_ENTITY/PICK_ERROR/LOST/MEASURE | 校验正则与测试对齐字典（比对自检发现） |
| P2 | InvDisposal 缺 approved_at 字段 | 生成器规格漏登记（DDL 有列） | spec 补 + 重生成 |
| P3 | V7 DDL inv_disposal 缺 approved_at 列 | 同 P2 源头 | V7 补列 |
| P4 | 处置超量误判级别 | 测试断言用错码 | 修正断言（1001） |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test（全仓） | **85/85**（S12 3 用例：无归因确认拦截/E-38 确认/未确认处置拦截/超量守卫/DP 编号+L1 分级/未审批完成拦截/审批闭环/DISPOSED）+ 网关 4/4 |
| verify-spec.py | 7 项 PASS；gen_entity --check UP-TO-DATE |
| 真实 MySQL 冒烟 | 登记(PURCHASE_OVER)→确认→L2 处置被 2002 正确拦截（权限分离生效）→REUSE L1 链路 DP-202610-0002→审批→完成回填 7500→台账 DISPOSED |

## 四、需求比对

- M07：呆滞 CANDIDATE→DISPOSED 主链路已实现；库龄自动跑批识别（job 定时扫 inv_batch 库龄）随运维批次；呆滞损失成本口径（SLOWLOSS）随财务联审。
- E-38/E-39 全字典对齐；A-31 阈值复用差异分级。

## 五、汇报四件事

1. **建表**：无新增（V7 补 inv_disposal.approved_at 列）。
2. **接口**：新增 6 个（/api/v1/inv/slow-moving*）。
3. **自测**：85/85 + 网关 4/4 + verify-spec 7/7 + 真实库冒烟全绿。
4. **下一批**：A2 工程日常记录（daily_log/quality_check/material_issue/after_sales）。
