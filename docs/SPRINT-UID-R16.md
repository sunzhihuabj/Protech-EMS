# SPRINT-UID-R16：第二批功能补齐（调查遗留清账）

> 日期：2026-11-03 ｜ 依据：SPRINT-UID-R15「未竟清单」+ 审计遗留 ｜ 目标：把后端已有但 Web 无入口的能力全部接通

## 一、总述

R15 轮调查发现约 20% 后端端点无 Web 入口。本轮按优先级清掉其中 6 组（文件上传中心、集成运维台、变更审批行内化、项目成员/门控/ECO、QMS 量具、QMS 菜单结构），**后端仅新增 2 个只读端点 + 1 个 Flyway 迁移（V25，零表结构变更）**，其余全部为前端接线。

## 二、模块明细

### 1. 集成运维台（Monitor.vue 重写）

原页面调用不存在的 `/int/adapters`（404 死链）。重写为接入 SPRINT-INT1 真实端点：

- `GET /integ/ops/dingtalk/health`：启用/可用/工作通知/机器人配置/映射数，健康检查按钮
- `POST /integ/ops/dingtalk/test-notify`：三通道（AUTO 全链路降级/WORK/ROBOT）测试发送弹窗
- `POST /integ/ops/dingtalk/refresh-token`：凭据更换后手动刷新
- `GET /integ/ops/shb/health` + `POST /integ/ops/shb/sync-events`：50/天纪律展示 + 手动增量对账（结果卡）
- `GET /integ/ops/tplus/health` + `POST /integ/ops/tplus/query`：门户探测 + 通用查询透传（JSON 结果展示）
- 权限口径：全部 `sys:config`（对齐后端 @RequirePermission）

### 2. 文件上传中心（此前前端零调用）

- **新页 sys/Files.vue**（路由 `/sys/file`）：拖拽上传（el-upload 携带 Bearer 头）+ 按业务类型/单号查询 + blob 下载（BR-07 仅水印合成件）
- **ContractDetail 附件区接活**：原「上传入口随 file-service 批次」占位 → 真实上传（bizType=CONTRACT 自动挂接本合同）+ 附件列表 + 行内下载
- **权限修正（V25）**：`file:upload`(20) 自 V2 种子以来**从未授予任何角色**，全员 403（与 FileController 注释"上传登录即可"相悖）→ V25 对齐 `file:download`(19) 持有面补授
- 技术点：下载必须走 axios blob（拦截器注入 Authorization；`<a href>` 直链无法带 token 会 401）

### 3. 合同变更审批行内化（ContractDetail.vue）

- 新增「变更台账」卡：`GET /contract/{id}/changes` 列表（单号/类型/金额增减着色/生效日/approvalStatus）
- PENDING 行内「通过/驳回」双按钮 → `POST /contract/changes/{changeId}/approve`（v-permission `contract:change:approve`），不再依赖审批中心检索

### 4. 项目成员 + 门控 + ECO（ProjectDetail.vue）

- **成员**：`GET+POST /project/{id}/members`、`POST /{id}/members/{userId}/remove`；角色字典 OWNER/LEAD/MEMBER；在册/退出态（isActive）
- **门控**：顶层任务即阶段节点；加载后并发拉 `GET /project/{id}/gate/{stageTaskId}`（totalChildren/notDone/gateOpen）；子任务未清零禁用「通过门控」按钮（FR-PRJ-38/39）；`POST /project/gate/{stageTaskId}/pass` 放行
- **ECO**：`GET /project/{id}/ecos` 只读列表（BR-09 处置方案卡点在后端）

### 5. QMS 量具台账（新页 QmsGauges.vue + 后端 1 端点）

- **后端新增**：`GET /api/v1/qms/gauges`（分页，status 过滤，`@RequirePermission("qms:gauge:manage")`，QmsService.pageGauges）——原只有 usable（下拉数据源）无台账
- **前端**：路由 `/qms/gauges`；超期行红色「（超期）」标注；行内「校准登记」弹窗（下次校准日按 calibrateCycleDays 预填）→ `POST /qms/gauges/{id}/calibrate`

### 6. QMS 菜单结构修复（V25，顺带修 2 个孤儿页）

8D 报告与 SPC 控制图此前**无任何菜单入口**（仅可手输 URL）。V25 把 menu:qms(105) 从单叶升级为四子菜单：

| id | perm_code | 名称 | 路由 |
|---|---|---|---|
| 153 | menu:qms:inspection | 检验台账 | /qms/inspection |
| 154 | menu:qms:8d | 8D 报告 | /qms/8d |
| 155 | menu:qms:spc | SPC 控制图 | /qms/spc |
| 156 | menu:qms:gauges | 量具台账 | /qms/gauges |
| 157 | menu:system:file | 文件中心 | /sys/file |

- 授权：QMS 四项跟随已持有 menu:qms(105) 的角色自动复制；文件中心对齐 V24 口径（R01/R03/R20）；全部 NOT EXISTS 幂等
- SidebarNode.vue ROUTE_BY_CODE 同步补 5 个映射

## 三、验证

- 后端：`mvn -q compile`（module-qms 通过）+ 全量 `mvn test`
- 前端：`npm run build`
- 规格自检：`python scripts/verify-spec.py`（V25 不含新表，表完整性/枚举/编码口径不受影响）

## 四、涉及文件

| 层 | 文件 | 变更 |
|---|---|---|
| 后端 | QmsService.java / QmsController.java | +pageGauges 分页端点 |
| 迁移 | db/migration/V25__qms_file_menus.sql | 5 菜单 + 3 组授权（QMS 子菜单/file:upload 补授/文件中心） |
| 前端-重写 | sys/Monitor.vue | /int/adapters 死链 → /integ/ops/* 真实运维台 |
| 前端-新页 | sys/Files.vue、qms/QmsGauges.vue | 文件中心 / 量具台账 |
| 前端-改造 | contract/ContractDetail.vue | 变更台账+行内审批 / 附件上传下载 |
| 前端-改造 | project/ProjectDetail.vue | 成员/门控/ECO 三卡 |
| 前端-接线 | router/index.js（+2 路由）、layout/SidebarNode.vue（+5 映射） | 路由与菜单 |
| 文档 | docs/UID-ALIGN.md、docs/SPRINT-UID-R16.md | 对账同步 |

## 五、未纳入本轮（下批候选）

- PLM PCS 工艺整块（后端齐，前端无页面）
- MES 齐套检查 `/material-check`、工序质检 `/processes/{id}/qc`
- 手动快照（`/cost/snapshot`、`/kb/metrics/{id}/snapshots`）
- 审计遗留三项（README build:themes 漂移、/api/internal/jobs 共享密钥、verify-spec 域名级收敛）
- 主包瘦身（Element UI 按需引入）
