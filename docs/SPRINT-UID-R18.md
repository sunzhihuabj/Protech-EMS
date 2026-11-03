# SPRINT-UID-R18：孤儿清账冲刺（流程尾动作 + job 骨架补挂 + 死代码清理）

> 日期：2026-11-04 ｜ 依据：孤儿全量盘点（约 45 孤儿端点 + menu:approval 死菜单 + 5 死方法 + 30 僵尸码）+ R17 下批候选 ｜ 目标：把"后端有、前端无"的流程尾动作接上，让孤儿 job 端点真正有调度调用方，清掉已核实的死代码

## 一、总述

孤儿盘点的根因分三类，本批各清一类：

1. **逻辑死锁**（1 处）：8D 关闭校验库内 D1/D4/D6 非空，但全系统没有任何端点能回填这些字段——8D 一旦创建就永远无法合法关闭
2. **尾动作无入口**（约 10 端点）：检验纠正、MO 挂路线、交底回执、我的任务/部门负载、ECO 影响面、任务依赖、T+ 对账——后端能力完整，Web 端零调用
3. **job 骨架悬空**（6 端点）：BR-05 锁定、T+ 对账、合同两条提醒——端点写好了，projlife-job 里没有任何 @Scheduled 挂它们；且 V16 授权半成品（声称授 141/103，SQL 只落了 137）

后端改动：1 个新 controller + 1 条迁移 + 3 个 service/controller 方法签名调整 + 4 个死方法/死类删除。前端改动：6 页接线 + 1 映射修复。

## 二、8D 逻辑死锁修复（阻断性缺陷）

- **根因**：`close8d(reportId)` 无 body，校验**库内** d1Team/d4RootCause/d6Verify 非空（09 册验收要点 3）；但 `create8d` 只收 problemDesc——**没有任何端点能回填 D1~D8**，8D 流程逻辑上永远无法关闭
- **修复**：`close8d(Long reportId, Map<String,String> steps)`——关闭请求可携带 D1~D8 步骤内容，非空项先落库再校验；`@RequestBody(required=false)` 保持原无 body 调用兼容
- **前端 EightD.vue**（原纯壳页重建）：列表（GET /qms/8ds + status 过滤）+ 新建弹窗 + 关闭弹窗（D1/D4 必填 + D5 选填 + D6 必填，与后端校验口径一致）

## 三、流程尾动作接线（六页）

| 页面 | 接线端点 | 说明 |
|---|---|---|
| qms/Inspection.vue | POST /qms/inspections/{id}/correct | 纠正登记弹窗（新结论+改判理由，human_corrected=1 留痕；来源为工序的检验联动 MES 放行/退回）；新增「结论来源」列 |
| mes/Production.vue | POST /mes/orders/{id}/routes | 「挂路线」弹窗：步骤行编辑（name/code/needInspection/standardHours），默认下料→装配→检验模板；生成后直接跳工序 tab |
| contract/ContractDetail.vue | GET /contract/{id}/dispatches + POST /contract/dispatches/{dispatchId}/read | 财务与交底卡新增「交底台账」：接收人/内容/发送时间/已读态，未读行「确认已读」（回执闭环，交底及时率数据源补全） |
| workbench/MyTask.vue | GET /project/my-tasks + GET /project/load | **假页真实化**：原页面拿项目列表伪装任务行（且调用路径 /prj/project 本身 404）——重写为 FR-PRJ-12 真实我的任务（分页+进度+逾期天数）+ FR-PRJ-25 部门负载（deptId+日期窗口→成员负载率进度条，>120% 红灯） |
| project/ProjectDetail.vue | GET /project/ecos/{ecoId}/affected-tasks + POST /project/tasks/{taskId}/dependencies | ECO 行内「影响面」弹窗（波及任务清单）；右栏新增「任务依赖」卡（前后任务下拉 + FS/SS/FF/SF + 搭接天数，环路由后端 400 阻断） |
| inv/Inventory.vue | POST /inv/reconcile | 「T+ 对账」弹窗（对账日默认昨天 + 法人主体），结果回显平账态/本地事务数/T+ 同步数/差异数/差异金额——FR-INV-41 首次有 Web 入口 |

## 四、job 骨架补挂（含 V16 半成品修正）

### 1. 新端点（InternalContractJobsController）

- `POST /api/internal/jobs/contract/remind-unread`：交底 24h 未读再提醒（`remindUnread` 死方法转正，FR-CT-10）
- `POST /api/internal/jobs/contract/payment-overdue-remind`：逾期收款计划按合同创建人发通知（`overduePlans` 死方法转正，FR-CT-38；`ContractFinanceService` 注入 NotificationService）
- 走 R17 InternalKeyFilter 密钥门（X-Internal-Key）

### 2. 调度挂载（BusinessScanTasks）

| 任务 | cron | 端点 | 依据 |
|---|---|---|---|
| replenishLockOverdue | 每小时 15 分 | /api/v1/inv/replenish/lock-overdue | BR-05 发料 25h 未回填锁定 |
| inventoryDailyReconcile | 每日 04:00 | /api/v1/inv/reconcile | FR-INV-41 T+ 对账（法人主体经 `projlife.job.legal-entity-id` 注入） |
| contractDispatchRemind | 每日 08:30 | /api/internal/jobs/contract/remind-unread | FR-CT-10 |
| contractPaymentOverdueRemind | 每日 08:40 | /api/internal/jobs/contract/payment-overdue-remind | FR-CT-38 |

### 3. V27 迁移（授权半成品修正，幂等 NOT EXISTS）

- V16 注释声称给 R03 补授 137/141/103 三个任务码，**SQL 只落地了 137**——141 arc:manage（档案鉴定扫描）、103 mes:andon:raise（Andon 升级）实际从未授出，对应定时任务默认部署下 403 静默失败
- V27 补授 141/103 + R18 新挂 job 所需的 91 inv:count:manage、96 inv:replenish:approve
- `InternalJobInvoker` 默认账号 admin@demo → **job-runner**（修正 V16 注释明示的错配：admin@demo=R03 缺任务权限码会 403 静默失败）

## 五、死代码清理（全仓库零引用核实后删除）

| 项 | 位置 | 核实 |
|---|---|---|
| `recordSpc`（:265-287）/`spcList`（:289-298） | QmsService | 删除；`spcDataMapper` 仍被 autoCollectSpc/OPS1 使用，无残留 |
| `unitPage` | EngService | 全仓库零引用 |
| ResetPasswordReq.java 整文件 | module-org/dto | 全仓库零引用（重置密码走别的请求体） |

## 六、验证

- 后端：根目录 `mvn install -DskipTests` → `mvn test` 全反应堆 **BUILD SUCCESS**（146 用例 0 失败，与 R17 基线一致无回归）
- 前端：`npm run build` 通过
- 门禁：`python scripts/verify-spec.py` **7/7 PASS**（域名级）
- 运行态实测（H2 profile 全新启动，Administrator 全权限账户）：
  - 8D 闭环：GET /qms/8ds 200 → create id=1 → **无 D1/D4 关闭 400 阻断** → 带 D1/D4/D6 关闭 200 → 复查 status=CLOSED 且 D6 已落库
  - GET /project/my-tasks 200（新库无任务 total=0 属正常）；POST /inv/reconcile 200（status=CLOSED）；GET /contract/1/dispatches 200
  - 内部提醒双端点带 X-Internal-Key 调用 200（reminded=0，新库无逾期/未读数据）
  - **job-runner（E0020）实测 V27 授权链**：登录 0 → inv/reconcile(91)、lock-overdue(96)、andon/escalate(103)、appraisal-scan(141) 全部 code=0——V16 半成品 403 静默失败正式修复

## 七、涉及文件

| 层 | 文件 | 变更 |
|---|---|---|
| 后端-新 | contract/controller/InternalContractJobsController.java | 合同提醒内部端点 ×2（密钥门内） |
| 后端-新 | db/migration/V27__job_runner_perms.sql | R03 补授 91/96/103/141（幂等） |
| 后端-改 | qms/service/QmsService.java | close8d 回填签名 + 删 recordSpc/spcList |
| 后端-改 | qms/controller/QmsController.java | close8d 可选 body + 8ds 列表端点 |
| 后端-改 | contract/service/ContractFinanceService.java | remindOverduePlans（注入 NotificationService） |
| 后端-改 | job/task/BusinessScanTasks.java | +4 调度 + legalEntityId 配置 |
| 后端-改 | job/task/InternalJobInvoker.java | 默认账号 job-runner |
| 后端-改 | file/controller/FileController.java | download 补 file:download 校验 |
| 后端-删 | eng/service/EngService.java（unitPage）、org/dto/ResetPasswordReq.java | 零引用死代码 |
| 前端-改 | qms/EightD.vue | 纯壳重建（列表+新建+关闭回填弹窗） |
| 前端-改 | qms/Inspection.vue、mes/Production.vue、contract/ContractDetail.vue、workbench/MyTask.vue、project/ProjectDetail.vue、inv/Inventory.vue | 六页尾动作接线 |
| 前端-改 | layout/SidebarNode.vue | menu:approval → /workbench/todo（V17 种码死菜单修复） |
| 文档 | docs/UID-ALIGN.md | §八 对账 |

## 八、未纳入本轮（下批候选）

- **R19 主体**：SCM/MDM/ENG 三组模板页转正（最大价值块：供应商台账/主数据档案/工程交付整块后端能力已备）
- SPRINT-SEC3 安全专项：JWT typ 校验、refresh rotation、按 ID 数据范围、SK- 手工取号（审核 P1）
- Element UI 按需引入（单独立项）；GET /pcs/routes 列表端点；UID-05 E2E 脚本化
- 僵尸权限码 30 个（种子登记但无任何端点/菜单引用）的清退迁移
