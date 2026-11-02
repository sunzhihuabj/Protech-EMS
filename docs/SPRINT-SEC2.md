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

---

# SPRINT-OPS1（同日追加）：审计 F10/F14/F15 + 幽灵权限码

## 修复内容
| 项 | 内容 |
|---|---|
| F10 SPC 自动采集 | createInspection 自动写缺陷率点位（characteristic=类型:DR:物料，4 位小数，采集失败不阻塞检验）；/qms/spc 列表端按序列计算 X 单值图控制限 mean±3σ（≥3 点，lcl 截 0）+ isOutOfControl 标记；服务端补 sourceType/sourceId/result 必填校验（MySQL strict 防线） |
| F14 全局搜索键盘导航 | MainLayout："/" 聚焦（非输入态）、Esc 清空关闭，beforeDestroy 合并清理 |
| F15 库存台账求和 | availableQty 冷键回退改 DB 端 SUM 聚合（InvTransactionMapper.sumQtyBy），替代全量加载实体 |
| 幽灵权限码 | 6 个注解码不在 sys_permission（恒 2002 死端点，含 sys:config——V19 冒烟 2 个失败实为它）：V23 补种 146-151，读码授予"持同级写码的角色"+R03/R20 全量，MySQL 已直导（9 语句） |

## 过程发现与修复
1. **种子 ID/正则双坑**：权限码收集正则漏带数字码（qms:8d:manage）→ 误判幽灵；种子 id 实际占用 144/145（V23 初版撞主键）→ 重排 146-151。
2. **共享状态清理**：MySQL 半程失败的 V23 种子/授权行按 created_by='V23' 清理后重放（幂等 NOT EXISTS 保证可重跑）。
3. **qms_inspection NOT NULL**（source_id/result 无默认）：H2 严格暴露 → 服务端 400 校验补齐。

## 验证
- S28SpcAutoCollectTest 2/2（3 检验→3 点位→控制限 ucl≥cl≥lcl、lcl 截 0、未出控标记）
- 前端 npm build 通过；全反应堆回归见提交说明

---

# SPRINT-PRJ2（同日追加）：审计 F3 定向落地 + F18 审批 MDC + 指标目录 16 项

## 修复内容
| 项 | 内容 |
|---|---|
| F3 数据范围（定向） | CrmOpportunityMapper selectList/selectPage 覆写 + @DataScopeRule(selfIdColumn=owner_id)——R04 商务(SELF) 商机仅本人；ALL/DEPT/PROJECT 角色不受影响（规则未声明 dept/project 列时条件不生成）。R11 仓储「本仓库」口径需框架扩 WAREHOUSE 范围类型，随部署批次 |
| F18 审批 MDC | ApprovalService approve/reject 取得实例后 MDC.put(approvalType/targetId)，logback PATTERN 增 [A:type#id] 段——审批日志与业务单据可关联 |
| 指标目录 16 项 | +CRM.FUNNEL.AMOUNT / CRM.WIN.RATE / BID.PIPELINE.AMOUNT / SCM.PO.PENDING / QMS.8D.OPEN / CT.PAYMENT.RATE（列名逐一实证），V23 同步种 6 定义（累计 16） |
| P5 审批记录真实数据 | ContractDetail 七区第 7 区接真实审批流：GET /approval/target/CONTRACT_REVIEW/{id} + /{approvalId}/nodes → 节点时间线（状态灯/意见/时间），未走流显空态说明 |

## 验证
- S1/S2/S3/V4Align CRM 流 24 测试全绿（SELF 范围不破坏既有断言）
- 编译 + 全反应堆回归（见提交）
