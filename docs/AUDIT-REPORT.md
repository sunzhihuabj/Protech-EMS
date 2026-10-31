# 全项目深度审计报告（代码逐行 · 业务逻辑逐条）

> 审计日期：2026-10-31 ｜ 方法：静态扫描 + 动态测试交叉验证 + 业务规则逐条比对
> 范围：后端 292 端点 / 149 实体 / 18 模块 / 44 commit + 前端 32 页 / 8 Pt 组件

---

## 总评

| 维度 | 得分 | 说明 |
|---|---|---|
| **功能完整性** | ★★★★☆ | 609 FR 主链路全通，163 表全建，但深度功能（审批链/甘特/SPC 采集）有缺口 |
| **安全性** | ★★☆☆☆ | **265/292 端点缺权限注解**，数据范围拦截器仅 1 处使用，越权风险高 |
| **并发安全** | ★☆☆☆☆ | **0/149 实体使用 @Version 乐观锁**，142 处 updateById 全部裸写 |
| **数据一致性** | ★★★☆☆ | 幂等键（库存/Agent）已覆盖，但其余模块无幂等保护 |
| **业务规则** | ★★★★☆ | 9 硬卡点全部有代码引用且测试覆盖，状态机基本正确 |
| **前端** | ★★★★☆ | 32 页 / 8 Pt 组件 / 24 主题 / 阻断条三段式，但 401 无 refresh 重试 |

---

## P0 · 必须立即修复（安全漏洞 + 数据丢失风险）

### F1【安全】265/292 端点缺 @RequirePermission

**现状**：仅 28 个端点有权限注解，其余 265 个（91%）任何已登录用户均可调用。

**实测**：tech@demo(R06 设计) 可成功创建生产订单（应 R12 生产专属）。

**受影响最严重的 Controller**：
| Controller | 缺失端点数 | 影响范围 |
|---|---|---|
| ContractController | 24 | 合同全部操作（创建/评审/用印/建档/变更/开票/收款） |
| PlmController | 22 | BOM/工艺全部操作 |
| MdmController | 18 | 主数据全部操作（含客户/物料/项目/风场） |
| MesController | 11 | 生产订单/工序/报工/Andon |
| ProjectController | 14 | 项目/ECO/成员/门控 |
| InventoryController | 11 | 库存事务/可用量/补货/对账 |
| QmsController | 11 | 检验/8D/量具/SPC |
| EngController | 13 | 派工/机组/日志/整改 |
| BidController | 12 | 标讯源/公告/转商机/推送 |
| ApprovalController | 7 | 审批发起/通过/驳回/转办/加签 |
| OpportunityController | 9 | 商机/跟进/转化 |

**修复方案**：为每个 Controller 方法补 `@RequirePermission`，权限码从 V2/V4 种子的 sys_permission 表查取。预计 265 个注解，按模块分 4 批（每批 1 小时）。

### F2【数据丢失】0/149 实体使用 @Version 乐观锁

**现状**：所有实体继承 BaseEntity/VersionedEntity，DDL 有 `version` 列，但 **Java 实体字段无 @Version 注解**，MyBatis-Plus 不会在 UPDATE 时检查 version，142 处 updateById 全部为"最后写入者胜"。

**影响**：两个用户同时编辑同一条合同/卡片/盘点单，后提交的会静默覆盖先提交的修改。

**修复方案**：在 VersionedEntity 的 version 字段加 `@Version` 注解（MyBatis-Plus OptimisticLockerInnerInterceptor 已配置）。一行代码修复 142 处。

### F3【安全】数据范围拦截器仅 1 处 @DataScopeRule

**现状**：DataScopeInterceptorConfig 已配置 DataPermissionInterceptor，但业务 Mapper 上仅 1 处使用 @DataScopeRule 注解。绝大多数查询**不过数据范围过滤**。

**影响**：R11 仓储可以看到全部仓库的库存（应仅本仓库）；R04 商务可以看到全部商机（应仅本人）。

**修复方案**：为每个需要数据范围的 Mapper 查询方法加 @DataScopeRule 注解，指定过滤列。

---

## P1 · 本周必须修复（业务逻辑缺陷）

### F4【并发竞态】库存可用量检查存在 TOCTOU

**现状**：`InventoryService.submit()` 先查 `availableQty()` 再 `insert(transaction)`，两步之间无锁。并发两笔出库同时通过校验，各自扣减后**库存变负数**。

**修复方案**：`SELECT ... FOR UPDATE` 悲观锁 + 重试，或改为 `UPDATE inv_stock_cache SET qty = qty - ? WHERE qty >= ?` 原子操作。

### F5【前端】401 无 refresh token 重试

**现状**：access token 30 分钟过期，过期后前端直接跳登录页，正在填写的表单数据全部丢失。

**修复方案**：axios 响应拦截器在 401 时先调 `/auth/refresh` 换新 token 重试原请求，refresh 也失败才跳登录。

### F6【审批】biz@demo 调审批端点返回 2002（权限缓存）

**现状**：V17 已授予 R04 sys:approval:act 权限（DB 确认），但 memurai 中的用户上下文缓存未刷新导致 2002。重启应用可暂时解决，但**每次新增权限都会遇到此问题**。

**修复方案**：`AuthController.me` 接口在返回前刷新 memurai 缓存；或在 PermissionAspect 中降级查 DB 而非缓存。

### F7【业务】FR-KB-30 指标取数仅覆盖交付率

**现状**：metricAutoFetch job 只聚合 mes_production_order 的交付率。其余指标（回款率/毛利率/准交率/齐套率等 20+ 指标）无取数逻辑。

**修复方案**：按 V4-07 §5.2 九板指标清单逐指标实现取数 SQL（每板 4-6 个指标）。

---

## P2 · 建议修复（功能完善）

| # | 问题 | 影响 | 修复建议 |
|---|---|---|---|
| F8 | FileService/NotificationService 缺 @Transactional | 文件上传/通知发送非原子 | 加注解 |
| F9 | 合同详情 P5 关联单据只有 AI 风险计数 | 无法追溯全链路 | 接合同→项目→派工→开票→收款链 |
| F10 | SPC 数据无自动采集 | qms_spc_data 表空，SPC 图表无数据 | 检验录入时自动写入（已在 recordSpc 预留） |
| F11 | 项目 WBS/甘特页 /prj/project/:id 未建 | FR-PRJ-23 拖拽甘特无法使用 | 建 P5 详情页（复用 APS 甘特组件） |
| F12 | 26 要素建档页 /ct/contract/:id/filing 未独立路由 | 完整度实时% 无法展示 | 建 P6 分步表单 |
| F13 | 会议纪要无服务端持久化 | 刷新丢失三问记录 | FR-KB-11 已有 save 端点，前端接通 |
| F14 | 全局搜索无键盘导航（/ 聚焦、Esc 关闭） | UID-02 §四 要求 | 加 keydown listener |
| F15 | 库存事务无分页（每次全量查） | 数据量大时性能问题 | InventoryController.transactions 加分页 |

---

## P3 · 建议改进（体验/性能）

| # | 建议 |
|---|---|
| F16 | PtTable 虚拟滚动（>200 行窗口化）已预留 prop 但未实现 |
| F17 | 前端路由 44 条中 4 条仍指 Stub（/workbench/my-task 已改 /kb/my 已改 /sys/import 已改 /kb/metric 已改——确认无残留） |
| F18 | 后端日志缺少业务上下文（建议 MDC 加 approvalType/targetId） |
| F19 | 前端 export 功能全部为占位（FR-SYS-12 异步导出） |

---

## 已确认正常的项（无需修复）

| 项 | 验证方法 | 结果 |
|---|---|---|
| SQL 注入 | grep last() 非LIMIT用法 | 0 处 ✅ |
| 金额精度 | 全部 BigDecimal，无 double/float | 1 处 float 但为 PDF 坐标非金额 ✅ |
| EVM 除零 | pv/ac signum > 0 检查 | ✅ |
| filing deadline | seal 时 addBusinessDays(signDate, 3) | ✅ |
| BR-01~09 | 全部有代码引用且测试覆盖 | ✅ |
| JSON 序列化 | Jackson 替代 toString（R10 修复后） | ✅ |
| 幂等键 | 库存事务/Agent/IntOutbox 覆盖 | ✅ |
| 审批引擎 | submit→approve→APPROVED→reject→addSign 全通 | ✅ |
| 主题 24 组 | tokens.json 对比度 120 项全过 | ✅ |
| 空态三型 | PtTable 统一出口 | ✅ |

---

## 修复排期建议

| 天 | 修复项 | 预计工作量 |
|---|---|---|
| D1 | F1 权限注解（按模块分 4 批：org+contract / plm+mdm+mes / inv+qms+eng+scm / cost+bid+approval） | 4h |
| D2 | F2 @Version 乐观锁（一行注解 + 回归测试）+ F3 DataScope 补齐 | 3h |
| D3 | F4 库存并发锁 + F5 前端 refresh + F6 权限缓存刷新 | 3h |
| D4 | F7 指标取数 SQL（按九板逐指标）+ F8 @Transactional + F9 关联单据 | 4h |
| D5 | F10-F15 低优先级 + 全量回归 + SPRINT 记录 | 2h |
