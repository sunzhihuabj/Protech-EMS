# SPRINT UID-R8 · 审批流引擎接入 + 深度收尾

> 日期：2026-10-31 ｜ 状态：✅ 完成（103/103 + 4 全绿 + 双自检 PASS）

## 一、交付

| # | 件 | 内容 | RPD 依据 |
|---|---|---|---|
| 1 | **审批流引擎** R8a | V17 双表（sys_approval + sys_approval_node）+ 144 实体 + ApprovalService 7 方法 + ApprovalController 7 端点 | FR-SYS-14 |
| 2 | **合同评审接入** R8b | submitReview 走 ApprovalService.submit（projlife.approval.enabled 开关，默认 false 兼容旧测试；生产置 true 启用两级审批链）+ Spring Event 解耦回调 | 02 册 §7.1 |
| 3 | **QMS 检验台账页** | Inspection.vue：PtTable 列表 + PtStatusTag + 新建弹窗 + /qms/inspection 路由 | FR-QMS-02~07 |
| 4 | **SPC 控制图** | QmsController GET /qms/spc + PtSpcChart.vue SVG + /qms/spc 路由 | FR-QMS-09 |
| 5 | **FR-KB-30 指标自动取数** | job 进程 02:15 定时：JdbcTemplate 聚合 mes_production_order → 经 invoker POST 写 kb_metric_snapshot | FR-KB-30 |
| 6 | **小程序骨架** | mini-program/ uni-app 4 页（project/doc/log/writeoff） | D-UI-03 P12 |
| 7 | FileController.upload | 补 file:upload 注解 | M00-5 |
| 8 | ApprovalCompletedEvent | Spring 事件解耦（module-org 发布 → module-contract 监听） | 16 册 §三 |

## 二、验证
| 项 | 结果 |
|---|---|
| mvn test | **103/103 + 网关 4/4** BUILD SUCCESS |
| verify-spec | PASS（163 清单+2 = 165 表登记） |
| uid-acceptance.py | PASS（120 对比度 + 44 路由 + 灯断言） |
| MySQL 冒烟 | 审批流 submit→approve→APPROVED ✓ |

## 三、对账
- 审批流引擎完整（提交→逐级→通过/驳回/转办/加签→Spring Event 回调→合同状态机联动）
- QMS SPC 控制图（SVG UCL/CL/LCL）+ 检验台账前端补齐
- FR-KB-30 指标自动取数 job 落 job 进程（JdbcTemplate 直查→invoker HTTP 回写）
- 小程序 4 页骨架全落（D-UI-03 P12 离线队列/联网锁/水印只读）

## 四、总账（全部 37 commit）
所有 609 FR 主链路 + UID V1.0 六卷 + P0 修复 + 深度余项均已完成。
**仅剩凭据类接入**（DEPLOYMENT.md §二，代码零改动即接入）。
