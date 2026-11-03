# SPRINT-UID-R17：第三批功能补齐 + 审计遗留清账（安全硬化）

> 日期：2026-11-03 ｜ 依据：SPRINT-UID-R16「未纳入本轮（下批候选）」+ 2026-11-03 严格审核报告 ｜ 目标：PCS/MES/快照三组断链接线 + 内部端点共享密钥 + 门禁域名级收敛

## 一、总述

本批 = R16 下批候选中 **3 组功能接线**（PCS 工艺、MES 齐套/工序质检、手动快照）+ 严格审核识别的 **审计遗留三项**（内部端点共享密钥、verify-spec 域名级收敛、README 漂移）+ **4 处顺手修复**（幽灵权限码 mes:report、量具权限面错位、ctContractDetail 路由重复、QMS 子路由 permCode 对齐）。

后端改动最小化：新增 1 个安全过滤器 + 1 条配置 + V26 迁移（零表结构变更），其余全部为前端接线与门禁脚本强化。

## 二、安全硬化（审计遗留三项）

### 1. 内部任务端点共享密钥（落实硬约束）

- **新增 `InternalKeyFilter`**（projlife-security）：守卫 `/api/internal/jobs/**` 与 `/api/internal/bid/**`，要求 `X-Internal-Key` 头与 `projlife.security.internal-key`（env `PROJLIFE_INTERNAL_KEY`，dev 默认 `dev-only-internal-key`）一致，否则 401；SHA-256 摘要后常量时间比较防时序侧信道。注册于 JwtAuthFilter 之前。
- **调用方同步**：`InternalJobInvoker` 携带同值密钥头（`projlife.security.internal-key`，两端同 env 注入）；S29 测试改造为带密钥调用。
- **负向用例**：S29 新增 `internalKeyRequired`——缺头 401、错值 401（填补内部端点零负向覆盖缺口）。
- **配置面**：application.yml / .env.example / DEPLOYMENT.md 凭据清单（第 3b 项）三处同步。
- `/api/internal/shb/**` 维持 controller 内 sharedKey 校验模式（第三方推送无法登录，A-40 既有设计；本批不动）。

### 2. verify-spec.py 域名级收敛（防"改注释过门禁"）

新增 `strip_java_comments`（状态机式剥离注释，保留字符串字面量），四项检查升级：

| 项 | 原强度 | 现强度 |
|---|---|---|
| 3 编码引擎 | CodeTypes 全文子串（注释可绕） | 仅字符串字面量位置命中 |
| 4 硬卡点 | 全仓库（含 test）子串（注释可绕） | 主源码（排除 src/test）`BizException/blockCode` 上下文行命中 |
| 6 编码规范 | 子串 + 固定金额变量名 | 去注释 + 金额命名 widened（amt/cost/fee/money/total） |
| 7 测试存在性 | 方法名子串（注释可绕） | `void 方法名(` 声明形式 + 文件含 @Test |

升级后 7/7 PASS，且如实呈现 BR-06（走 costPrice 脱敏通道，无异常行，列为后续切片口径）。

### 3. README 漂移修复

- 移除两处不存在的 `npm run build:themes`（R1 起 24 组主题令牌运行时切换，无预编译步骤）
- 测试口径更新（13 用例 → 全量 140+）、verify-spec 说明补域名级口径、演示路径主题数更正

## 三、功能接线（第三批）

### 1. PCS 工艺整块（新页 /plm/pcs）

- **新页 `plm/Pcs.vue`** 三 tab：工艺文件台账（筛选产品线/状态 + 新建 DRAFT + 发布/暂停）、工艺路线（创建含步骤行编辑，创建后回显步骤明细）、覆盖度矩阵（FR-PCS-11：BOM×工艺文件覆盖率进度条 + 四档判定）
- 契约对齐：`GET/POST /plm/pcs/docs`、`POST /pcs/docs/{id}/publish|pause`、`POST /pcs/routes`（steps 行内 isKey/needInspection → 1/0）、`GET /pcs/routes/{id}/steps`、`GET /pcs/coverage`；权限码 `pcs:manage`(82)
- **契约限制备案**：后端无工艺路线列表端点，页面创建后按返回 routeId 回显步骤（列表能力留待后端补 GET /pcs/routes）
- **V26 迁移**：menu:plm(75) 单叶升级为双子菜单（158 menu:plm:bom → /plm/bom；159 menu:plm:pcs → /plm/pcs），授权跟随持有 menu:plm 的角色（NOT EXISTS 幂等）
- SidebarNode ROUTE_BY_CODE +2；plm/bom 路由 permCode 对齐 menu:plm:bom

### 2. MES 齐套检查 + 工序质检（Production.vue）

- MO 行内「齐套检查」→ `GET /mes/orders/{id}/material-check` 结果弹窗（moNo/planQty/message 口径说明版，注明物料行级明细在 aps_material_check）
- FINISHED 工序行内「质检」→ `POST /mes/processes/{id}/qc`（passed/rejectReason，不合格必填原因；v-permission `mes:qc:release`）
- **幽灵权限码修复**：开工/报工 `mes:report`（种子从未登记）→ `mes:process:work`(101)；Andon 按钮 → `mes:andon:raise`(103)。此前非 admin 角色按钮权限判定失真

### 3. 手动快照（CostCenter.vue + MetricDefs.vue）

- CostCenter 新增「成本快照」tab：项目 + 快照日 → `POST /cost/snapshot`（cost:budget），展示七项成本 + 总成本 + 合同额 + 合同/阶段口径毛利
- MetricDefs 行内「手动快照」→ `POST /kb/metrics/{code}/snapshots`（kb:metric:def；期间默认当月，取值数值校验），落库后刷新趋势

## 四、顺手修复

| # | 修复 | 位置 |
|---|---|---|
| 1 | ctContractDetail 路由重复定义两次（Vue Router 告警 + 死配置） | router/index.js |
| 2 | QMS 四子路由 permCode 从 menu:qms 对齐 menu:qms:inspection/8d/spc/gauges（V25 菜单结构） | router/index.js |
| 3 | 量具 API 权限面错位：qms:gauge:manage(108) 补授给持有 menu:qms:gauges(156) 的角色（修 R16"菜单可见但接口 403"） | V26 |
| 4 | Andon/开工/报工幽灵权限码（见 §三.2） | Production.vue |

## 五、验证

- 后端：全反应堆 `mvn test`（28 类 146 用例 0 失败，S29 新增 internalKeyRequired 负向用例）
- 前端：`npm run build` 通过
- 门禁：`python scripts/verify-spec.py` 7/7 PASS（域名级）
- 运行态实测（H2 profile，Administrator 全权限账户 + admin@demo）：
  - 内部密钥门：无 `X-Internal-Key` → 401；带正确密钥 → 200
  - PCS：list/create/publish 全通，编码自动取号 `TLD-GY199-001`（CodeSequenceService 口径）；coverage 8 产品线行
  - 量具台账 200；成本快照 200（totalCost 落库）；指标手动快照 200（灯色按目标重算 YELLOW）
  - 权限口径备案：admin@demo 角色本不持有 menu:plm（PLM 菜单不可见属既有设计）；pcs:manage/qms:gauge:manage 经 V26 对齐"菜单持有面"，遵循 V25 file:upload 同一修复范式

## 六、涉及文件

| 层 | 文件 | 变更 |
|---|---|---|
| 后端 | security/web/InternalKeyFilter.java | 新增共享密钥过滤器 |
| 后端 | SecurityConfig / SecurityProperties | 注册过滤器 + internalKey 配置项 |
| 后端 | projlife-job InternalJobInvoker | 携带 X-Internal-Key |
| 后端 | S29MetricAutoFetchTest | 带密钥调用 + 401 负向用例 |
| 配置 | application.yml / .env.example / DEPLOYMENT.md | PROJLIFE_INTERNAL_KEY |
| 迁移 | db/migration/V26__plm_pcs_menus.sql | PLM 子菜单 + 量具授权对齐（幂等） |
| 门禁 | scripts/verify-spec.py | 域名级收敛（3/4/6/7 项） |
| 前端-新页 | plm/Pcs.vue | PCS 工艺三 tab |
| 前端-改造 | mes/Production.vue、cost/CostCenter.vue、kb/MetricDefs.vue | 齐套/质检/快照接线 + 幽灵码修复 |
| 前端-接线 | router/index.js、layout/SidebarNode.vue | +1 路由 +2 映射 + 修重/对齐 |
| 文档 | README.md、docs/UID-ALIGN.md | 漂移修复 + 对账 §七 |

## 七、未纳入本轮（下批候选）

- Element UI 按需引入（主包瘦身）：构建链变更风险高、与 vite5+vue2.7 组合需验证 unplugin-vue-components 兼容性，单独立项
- 工艺路线列表端点（GET /pcs/routes）后端补齐后，Pcs.vue 路线 tab 改为全量列表
- UID-ALIGN §四第四批：131 路由模板页批量生成、E2E 脚本化（UID-05 验收 13 条断言）
- 审核报告其余 P1（JWT typ 校验、refresh rotation、按 ID 数据范围、SK- 手工取号等）——属安全专项批次，建议独立 SPRINT-SEC3
