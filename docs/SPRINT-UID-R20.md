# SPRINT-UID-R20：ENG 工程交付三块接线 + SCM 反向断链修复（前端待完成清单收尾）

> 日期：2026-11-04 ｜ 依据：R19 下批候选 ｜ 目标：班组人员准入（UC-ENG-10）/ 机组一机一档（E-29）/ 整改闭环（UC-ENG-08）7+3 端点全接线；SCM 供应商台账反向断链（前端调 GET /scm/suppliers 后端从未提供）

## 一、总述

"前端未完成"清单最后两块：

1. **ENG 三块**：人员/机组/整改此前只有写入与状态推进端点，**连列表端点都没有**（前端无法展示台账、操作全靠盲猜 ID）——本批后端补 3 个列表端点 + 前端 EngPortal 加「班组人员」「机组与整改」两 tab，形成"建档 → 状态机推进 → 闭环 → 验收前检查"完整链路
2. **SCM 反向断链**：Supplier.vue 上线起就调 `GET /scm/suppliers`（后端 404），且页面用 `row.stage` 数字字段（实体根本不存在）、advance 不传 body（后端 @RequestBody 必填，必 400）、register 语义错位（传 supplierName/creditCode 给只收 supplierId 的端点）——本批后端补列表端点 + 前端整页对齐真实五阶准入契约

## 二、后端（4 文件，均为列表补齐）

| 端点 | 权限 | 说明 |
|---|---|---|
| GET /eng/workers?status= | eng:crew:manage(124) | 人员台账（FR-ENG-01/02 准入状态可视） |
| GET /eng/units?projectId=&status= | eng:install(126) | 机组台账（FR-ENG-21 一机一档） |
| GET /eng/rectifications?projectId=&status= | eng:rect:manage(128) | 整改台账（UC-ENG-08 闭环可视） |
| GET /scm/suppliers?status=&keyword= | scm:supplier:manage(85) | 供应商台账（md_supplier，反向断链修复） |

均按 dispatchPage 既有 pattern 实现（MyBatis-Plus 分页 + 条件过滤）。

## 三、前端

### 1. EngPortal.vue（+2 tab，顺修 3 处既有错误）

- **班组人员 tab**：列表（工号/姓名/班组/保险到期/准入状态）+ 建档弹窗（W 编号自动取号；姓名/班组必填 + 保险到期日 + 证书 JSON）+ PENDING 行内「激活授权」（保险过期行红字预警 UC-ENG-10，后端硬校验兜底）
- **机组与整改 tab**：
  - 机组列表（unitNo/unitName/机型/序列号/E-29 状态）+ 建档弹窗（U-P{项目}-{3位}；项目/机位/产品线必填）+ 行内状态推进（TO_INSTALL→INSTALLING→TO_DEBUG→ACCEPTED，按钮按 E-29 合法迁移表动态显示；验收自动起算 24 个月质保提示）
  - 整改列表（rectNo/问题/要求/期限/状态）+ 登记弹窗（RECT- 编号；sourceType=MANUAL）+ 行内推进（PENDING→DOING→TO_REVIEW→PASSED→CLOSED；**TO_REVIEW 复核双分支**：通过 / 驳回回 DOING 必填原因）
  - **验收前检查**：输入项目ID → `GET /eng/projects/{projectId}/rect-open-check`（FR-ENG-08：有未闭环整改则告警）
  - 项目ID 输入 watch 联动机组/整改列表过滤
- **顺修既有错误**：① `components` 重复定义；② 派工状态 dict CREATED/SENT/ACCEPTED/STARTED/CLOSED → 后端真实 E-32 枚举 TO_DISPATCH/DISPATCHED/DOING/TO_ACCEPT/DONE（nextActions 同步对齐，ACCEPTED→start 的错误映射纠正）；③ `danger-text` 样式缺失

### 2. Supplier.vue（整页对齐真实契约）

- 列表接 `GET /scm/suppliers`（status/keyword 过滤；字段用 MdSupplier 真实列：supplierCode/supplierName/creditCode/status/blacklistFlag，删除不存在的 `row.stage` 数字列）
- **准入详情抽屉接真实端点**：`GET /suppliers/{id}/onboarding` → currentStage/stageStatus/auditResult/finalLevel + 五阶 el-steps（REGISTERED→QUALIFIED→VISITED→SAMPLED→APPROVED，与后端 STAGES 常量一致）
- **推进弹窗**：`POST /suppliers/{id}/advance` 传 `{passed, auditResult}`（此前不传 body 必 400）；不通过 → REJECTED 终态
- **注册语义纠正**：md_supplier 建档归主数据工作台（R19 /mdm/suppliers-register），本页「注册准入档案」对已有供应商建准入档案（POST /suppliers/{id}/register，仅 supplierId），页面提供跳转提示

## 四、验证

- 后端：`mvn install -DskipTests` → 全反应堆 `mvn test` **146 用例 0 失败**（与 R17~R19 基线一致）
- 前端：`npm run build` 通过
- 门禁：`verify-spec.py` 7/7 PASS
- 运行态实测（H2 全新库，eng@demo R14 + mdm@demo R18）：
  - **人员准入链（6 步）**：建档（W 编号）×2 → 列表 PENDING=2 → 正常激活 ACTIVE → **保险过期人员激活被 UC-ENG-10 拒绝（400 硬校验）**
  - **机组 E-29 链（4 步）**：建档（U-P1-001）→ 逐阶推进 INSTALLING→TO_DEBUG→ACCEPTED（质保自动落库 2026-11-05~2028-11-05）→ **非法迁移（ACCEPTED→INSTALLING）400 拒绝**
  - **整改闭环链（7 步）**：登记（RECT- 编号）→ DOING → TO_REVIEW → **无理由驳回被拒** → 带理由驳回回 DOING（rejectReason 落库）→ 再 TO_REVIEW→PASSED→CLOSED → rect-open-check「无未闭环整改，可提交整体验收」→ **新登记未闭环整改进 open-check 立即告警（FR-ENG-08）**
  - **SCM 供应商链（7 步，mdm@demo）**：md_supplier 建档 → **GET /scm/suppliers 200（反向断链修复，修复前 404）** → 注册准入 REGISTERED → 逐阶推进 QUALIFIED→VISITED→SAMPLED→APPROVED（md_supplier 自动置 QUALIFIED）→ **越过 APPROVED 再推进 400 拒绝**

## 五、涉及文件

| 层 | 文件 | 变更 |
|---|---|---|
| 后端 | eng/service/EngService.java | +workers/units/rectifications 三个分页方法 |
| 后端 | eng/controller/EngController.java | +3 个 GET 列表端点 |
| 后端 | scm/service/ScmService.java | +suppliers 分页方法 |
| 后端 | scm/controller/ScmController.java | +GET /suppliers 列表端点 |
| 前端 | eng/EngPortal.vue | +2 tab（班组人员/机组与整改）+3 弹窗 + 修 components 重复/派工状态枚举错位 |
| 前端 | scm/Supplier.vue | 整页对齐五阶准入真实契约（列表/详情/推进/注册语义） |
| 文档 | docs/UID-ALIGN.md | §十 对账 |

## 六、未纳入本轮（下批候选）

- SPRINT-SEC3 安全专项：JWT typ 校验、refresh rotation、按 ID 数据范围、SK- 手工取号（审核 P1 最后一块）
- ENG 派工单与机组/人员联动（unitIds/memberIds 选择器）、施工记录照片上传
- 30 僵尸权限码清退迁移；Element UI 按需引入（单独立项）
