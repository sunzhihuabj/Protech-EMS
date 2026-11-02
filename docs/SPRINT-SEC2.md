# SPRINT-SEC2：审计 F1 权限注解复核闭环

> 日期：2026-11-03 · 分支：`feature/dev` · 输入：AUDIT-REPORT F1「265/292 端点缺 @RequirePermission」

## 一、复核结论（重要：审计数字是扫描伪象）

- 审计的扫描口径只看 Mapping **行前 3 行**，而本代码库注解约定在 Mapping **行后**（且部分为全限定名 `@com.projlife.security.auth.RequirePermission`）→ 265 为误报。
- FQN 感知双向重扫：**真实缺口 33 → 其中写端点 17、读端点 10、BR-07 保护输出 6**（水印 2 + 文件下载等）。

## 二、本批落位（17 处新注解）

| Controller | 端点 | 权限码 |
|---|---|---|
| MesController | orders POST / routes / processes start / finish | mes:order:create / mes:process:work（7 处，含与既有 FQN 注解冲突清理） |
| ProjectController | /my-tasks、/{id}/critical-path | project:view |
| MdmController | /customers/groups、/customers/resolve | mdm:customer:edit |
| MdmController | /projects/name-rule、/projects/triple-check | mdm:project:create / mdm:project:edit |
| ArcController | /borrows/overdue-scan | arc:manage（job-runner V16 已授） |
| CostKbController | /kb/cards/escalate | kb:card:manage（job-runner 已授） |
| CostKbController | /arc/appraisal-scan | arc:manage |
| InventoryController | /replenish/lock-overdue | inv:replenish:approve |

## 三、豁免清单与依据（读=登录、写=权限码 全库策略）

- **读端点 10 个**（/projects/{id}、orders/{id}/processes、approval byTarget/nodes、dept tree、bom qc-result、templates、mtbf 等）：所属域无 view 种子码，全库一致策略为"读=登录即可，写=权限码"；强行造码会破坏既有角色授权。
- **BR-07 输出 6 个**（WatermarkController pdf/imageWatermark、FileController download）：水印合成输出本身就是保护手段，全员（登录）可预览/下载。
- **架构性豁免**：Auth（登录/自助）、内部回调（/api/internal/** 白名单）、Agent（自身 OAuth2+HMAC）、ThdPortal（门户 token）、Dict 读（全员下拉）。

## 四、验证
- 全反应堆 `mvn test` BUILD SUCCESS（注解不改变既有授权路径，E2E 全绿佐证码对齐种子）
- 过程修复：扫描漏检导致的 7 处重复注解（短形式+FQN 并存）→ 编译暴露 → 已清理
