# SPRINT-UID-R19：MDM 主数据全链接线（前端待完成清单转正 + 取号序列种子对齐）

> 日期：2026-11-04 ｜ 依据：孤儿盘点补充调查（用户视角："有些孤儿代码是前端还没有完成导致的"）+ MDM 14 孤儿端点精确差集 ｜ 目标：项目建档全家桶 + 客户治理 + 物料台账 + 供应商注册全链转正

## 一、总述

孤儿盘点补充调查修正了一个认知：剩余孤儿 ≠ 死代码，而是**前端未完成**（R18 删除的 4 项均为后端内部零调用方法/DTO，controller 路由零删除）。本轮把 MDM 模块 14 个孤儿端点全部接上，构成完整主数据治理工作台：

- **项目建档全家桶 8 端点**：name-rule 命名规则（FR-MDM-13/14）、triple-check 三重查重（FR-MDM-12）、风场建档/详情（FR-MDM-09/10）、项目创建（FR-MDM-15 十二项校验）/详情、产品线挂接（FR-MDM-16）、项目别名
- **客户治理 5 端点**：mdm 客户列表（此前误用 /crm/customers）、集团分组汇总、别名登记、resolve 归一解析（FR-MDM-05）、停用
- **物料台账 1 端点**：列表 + keyword 过滤 + BR-06 成本价脱敏渲染（***）
- **供应商注册 1 端点**：POST /suppliers-register（md_supplier 建档此前无 Web 入口）

后端改动：**1 条修正迁移（V28）**，代码零改动。前端改动：MdmConsole.vue 重写为四 tab 主数据工作台（14 端点全接线）。

## 二、V28 取号序列种子对齐（运行态实测发现的真 bug）

### 根因

V4 种子直接插业务行（md_customer C0001~C0007、md_wind_farm WF2026-001），但 sys_code_sequence 无对应 scope 行。`CodeSequenceService.next()` 的 upsert 首次插入 current_value=1 即生成 **C0001**，撞 `uniq_customer_code` 唯一键 → **POST /mdm/customers 建档 500**（H2/生产同炸）。物料 MAT、项目 PJ 无种子行不受影响；registerSupplier（S 序列）因 md_supplier 无种子侥幸通过。

### 修复（幂等）

- `CUST:GLOBAL`：无行插 current_value=7；有行但 <7 校正至 7 → 新客户从 C0008 起
- `WF:<当前年>`：无行插 current_value=1；有行但 <1 校正至 1 → 新风场从 WF<年>-002 起
- 符合工程惯例：NOT EXISTS + 条件 UPDATE 双保险幂等

> 教训：种子直接插编号行的迁移，必须同步预占 sys_code_sequence（后续新增种子编号时需一并写入对应 scope 行）。

## 三、前端：MdmConsole.vue 主数据工作台（四 tab）

### 1. 客户主体 tab

- 列表切至 `GET /mdm/customers`（keyword/level 过滤 + 分页），纠正此前错调 /crm/customers
- **集团分组汇总条**：`GET /customers/groups` 标签条（isInternal 绿标），点击按集团名过滤
- **归一检查器**（FR-MDM-05）：输入任意名称 → `GET /customers/resolve` → 命中显示主体 ID / 未命中告警
- 行内：**别名**（`POST /customers/aliases`，SHORT/FORMER/SITE/OTHER 四型）与**停用**（`POST /{id}/deactivate`，confirm）
- toolbar：**供应商注册**弹窗（`POST /suppliers-register?name=&creditCode=`，mdm:supplier:manage）

### 2. 项目档案 tab（建档向导——本批核心）

- 列表：keyword 过滤 + 订单类型 E-07 中文渲染 + 行点击详情抽屉
- **建档向导弹窗五段式**：
  - ① 基础与商务：订单类型（E-07 四值）、签约主体（客户下拉）、我方法人、台数、期次、工程/商务责任人（`GET /org/users` 下拉）
  - ② 风场：farmId 输入 + farmNameRaw + 「暂无风场」checkbox（OEM_INSTALL 自动 farm_pending）+ **新建风场**弹窗（`POST /farms`，建档成功自动回填 farmId 与 farmNameRaw/farmFullName）
  - ③ 命名助手：业主全称 + 风场全称 → 「生成建议名」→ `GET /projects/name-rule` 回填 stdName/shortName + namingWarning 红字（FR-MDM-14 地名含业主名校验）
  - ④ 三重查重：「执行查重」→ `GET /projects/triple-check` 命中对比卡（projectCode/stdName/rule/reason）→ 命中时 forceNewReason 必填 ≥10 字（SYS-DUP-CHECK 硬卡点前端预检）
  - ⑤ 产品线与业主：E-08 十一线多选、业主/OEM 下拉、owner_pending checkbox
  - 提交按钮由 wizReady 计算属性按 12 项必填实时置灰（FR-MDM-15 前端预检）
- **详情抽屉**：`GET /projects/{id}` + 挂产品子项（`POST /{id}/products`，productLine+setQty）+ 加别名（`POST /{id}/aliases`）

### 3. 物料主数据 tab

- 补列表 `GET /mdm/materials`（keyword 过滤）；costPrice 列服务端 null → 前端渲染 ***/BR-06
- 保留新建弹窗（E-20 八种受控单位）

### 4. 权限面（免迁移核实）

- R18 数据治理角色（mdm@demo E0009）：持 V2 写码 22-35 全量 + V23 view 码 146/147/148（跟随 edit 持有者自动补授）+ menu:mdm(65)（V4）
- `org:user:manage`(10) 在 R18 持有包内（1-21）→ 责任人下拉可达
- 结论：**全部端点 mdm@demo 可达，无需 V28 之外的角色迁移**

## 四、验证

- 前端：`npm run build` 通过（MdmConsole 34.91 kB）
- 门禁：`verify-spec.py` 7/7 PASS
- 后端回归：全反应堆 `mvn test` **146 用例 0 失败**（V28 迁移后与 R17/R18 基线一致）
- **运行态实测（H2 全新库，mdm@demo R18 账户，14 步全通）**：
  1. 客户列表 total=7（种子）；2. 分组 n=3；3. 建客户 → **C0008**（V28 生效，修复前此处 500 撞 C0001）；4. resolve「华能新能源」→ 归一到 #8；5. name-rule 生成 stdName=华能新能源-如东二期风电场-一期；6. 建风场 → **WF2026-002**；7. triple-check 按需命中；8. 项目建档（无 forceNewReason 被 SYS-DUP-CHECK 拦截 → 补理由放行）id=1；9. 挂产品线 CCTV；10. 加别名「如东项目」；14. 再查重 hits=1（**别名相似度回响闭环**）；11. 详情 SIGNED_PENDING + productLines 落库；12. 物料列表；13. 供应商注册 S0001
  - 负向：12 项校验（1001 缺业主拦截）、三重查重（未填理由阻断）均按预期工作

## 五、涉及文件

| 层 | 文件 | 变更 |
|---|---|---|
| 迁移-新 | db/migration/V28__seed_code_sequence_align.sql | CUST:GLOBAL→7、WF:<年>→1（幂等，修建档 500） |
| 前端-重写 | mdm/MdmConsole.vue | 四 tab 主数据工作台，14 端点全接线 |
| 文档 | docs/UID-ALIGN.md | §九 对账 |

## 六、未纳入本轮（下批候选）

- ENG 工程交付三块 7 端点（人员准入 UC-ENG-10/机组一机一档 E-29/整改闭环 UC-ENG-08）——EngPortal 加 tab
- SCM 2 项：反向断链修复（前端调 GET /scm/suppliers 后端不存在 → 后端补列表端点）+ onboarding 接线
- 其他模块种子编号与序列对齐排查（CT/CRM/BID 等创建链路已被 S 系列测试覆盖风险低；新增种子编号时须同步预占 sys_code_sequence）
- JWT typ 校验等 4 项审核 P1（SPRINT-SEC3）
