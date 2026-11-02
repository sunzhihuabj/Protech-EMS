# SPRINT-PRJ1：项目甘特详情页 + 合同关联单据链（审计 F11/F9）

> 日期：2026-11-03 · 分支：`feature/dev`

## 一、实施内容

### F11 项目甘特详情页（/prj/project/:id，新建 ProjectDetail.vue）
- 三数据源：GET /project/{id}（主数据）、/project/{id}/tasks（WBS）、/project/{id}/critical-path（CPM，422 容错）
- **甘特**：日刻度 14px/天，条形按 planStart/planEnd 定位；**关键任务红条**（CPM critical 联动）；拖拽条形 → 松手 POST /project/tasks/{taskId}/adjust-date {planEnd}（project:task:adjust_date 权限门控，无权限不可拖）→ 成功重载（CPM 重算）
- **CPM 表**：wbsCode/taskName/es/ef/slack/关键标红 + 总工期与关键任务计数
- 路由 `prj/project/:id`（prjProjectDetail）注册；Projects.vue「任务/甘特」按钮改跳详情页

### F9 合同关联单据链（ContractRelatedService + GET /ct/contract/{id}/related）
- 只读聚合（JdbcTemplate 直查，跨模块不引实体）：projects（prj_project.contract_id）/invoices（ct_invoice）/paymentPlans（ct_contract_payment_plan）
- 空链返回空数组（P5 禁假数据口径）；contract:view 权限
- 前端 ContractDetail.vue 关联单据区接真实数据（三分段列表）

## 二、验证
- S30 E2E 2/2：合同创建 → related 三段结构 + 新合同空链断言
- 前端 npm run build 通过；全反应堆 BUILD SUCCESS（见提交）
- 踩坑重演备案：String.valueOf(泛型) 命中 char[] 重载 → (Object) 转换（S30）

## 三、遗留
- 甘特依赖列（前置任务连线）随深度批次；任务树（父子折叠）并入甘特行分组
- eng_install_plan 纳入关联链待工程域列名实证
