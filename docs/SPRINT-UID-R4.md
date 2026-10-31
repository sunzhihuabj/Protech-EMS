# SPRINT UID-R4 · 深度测试 P0 整改（F1/F2/F3/F6 —— RPD 合规修复）

> 日期：2026-10-31 ｜ 状态：✅ 完成并复测通过
> 依据：docs/TEST-REPORT.md §四 + V4-02 M00-5（服务端双拦截）+ BR-06 + V4-01 §6.2

## 一、整改内容与复测

| # | 问题 | 整改 | 复测（MySQL 真实库） |
|---|---|---|---|
| F1 高 | MesController 7 写端点缺 @RequirePermission | 全部补注解：orders/routes→`mes:order:create`(100)；start/finish→`mes:process:work`(101)；qc→`mes:qc:release`(102)；andon/escalate→`mes:andon:raise`(103)——**对齐 V8 种子实际码，不用文档推导码** | tech(R06) 建 MO → **2002** ✅；prod(R12) → 0 ✅ |
| F2 高 | BR-06 物料成本价对 R11 暴露 | `/mdm/materials` 出参清洗：无 `cost:view`(134)/`scm:order:view_price`(89) → costPrice=null（UID-00 A-07：API null 即权限落地，前端渲染 `***`，列位保留） | store(R11) → **null** ✅；purchase(R09) → 1250.0 ✅（种子补 cost_price） |
| F3 高 | MySQL strict NOT NULL 列缺校验 → 500 | InventoryService.submit 补 unit/sourceType 必填；ContractService.validateDraft 补 partyA/partyB/businessType——**缺失返回 1001 字段级提示** | 缺 unit → **1001 计量单位必填** ✅；缺 partyA → **1001 甲乙方主体必填** ✅；全字段 → 0 ✅ |
| F6 中 | job 账号 admin(R03) 缺 kb:card:manage → 红灯升级任务 403 | V16：job-runner(E0020) 专用账号（R03）+ 补授 137；V16 同时兼容 H2/MySQL（幂等 NOT EXISTS） | job-runner 登录 0 ✅ |

## 二、回归

| 项 | 结果 |
|---|---|
| mvn test | **103/103 + 网关 4/4** BUILD SUCCESS |
| verify-spec / gen --check | PASS / UP-TO-DATE |
| V16 真实库 | 已应用（id=20，避让 V7 qc@demo id=12——初版冲突已修） |

## 三、与 RPD 对应
- M00-5 权限：注解补齐 = 服务端双拦截闭环（v-permission 仅显隐）
- BR-06：A-07/L-3 落地——R11 无权限 → null → 前端 `***`（R3 前端承接）
- V4-01 §6.2：500 → 400(1001) 字段级文案（"填什么"而非"填错了"）
- UID-04 §五：fail-closed 权限判定复用 UserContextHolder

## 四、遗留
- FileController.upload / ContractController.dispatches/{id}/read 注解 → R3 随 Pt 上传组件与 THD 页面一起收敛
- 8080 运行实例已重启加载新代码
