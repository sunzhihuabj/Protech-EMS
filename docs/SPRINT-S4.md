# SPRINT S4 · 供应链与库存（阶段记录）

> 日期：2026-10-30 ｜ 状态：✅ 完成并通过全部验证门
> 范围：02 册 §4.9 + §4.20 + 07 册全册 + 00 册 C-04（T+ 权威）；对应 16 册 S4-1~S4-13（核心链路）。

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S4-1 | 供应商五阶准入 | ScmService：REGISTERED→QUALIFIED→VISITED→SAMPLED→APPROVED 状态机（可 REJECTED 重申请）；APPROVED 联动 md_supplier 置合格；BR-SCM-01 未准入下单 422 | ✅ |
| S4-2 | 采购需求四来源 | BOM_SHORTAGE/SAFETY_STOCK/MANUAL/STOCK；①③必挂项目；PR-{YYYYMM}-{4位} | ✅ |
| S4-3 | 采购订单 + 价格脱敏 | PO 合并需求（MERGED 标记）/多项目分行/下发状态机；**BR-SCM-03**：GET 接口服务端按 scm:order:view_price 裁剪 unitPrice/amount/totalAmount | ✅ |
| S4-5 | T+ 适配器（MOCK） | TPlusAdapter 接口 + MockTPlusAdapter（恒成功回写 TP-MOCK 凭证号）；FILE/API 模式 S9 接入 | ✅ |
| S4-6 | 库存事务 + 幂等 + 状态机 | **核心**：幂等键 UNIQUE（3 提交仅 1 笔，重复返回首次）；PENDING_SYNC→SYNCED（回写凭证）/EXCEPTION（重试入口）；出库可用量校验（BR-INV-02）；**AuditedEntity**（不软删基类，A-20） | ✅ |
| S4-7 | 货位 + 缓存 | inv_stock_cache 只读副本（TTL 300s）；缓存缺失时按事务流水只读推算（不建权威表，BR-INV-01） | ✅ |
| S4-9 | 盘点/差异（表就绪） | inv_count/count_line/diff 三级调账表建；服务随 S5 MES 联动补齐（记 BACKLOG） | 📋 表就绪 |
| S4-10 | 呆滞（表就绪） | inv_slow_moving/disposal 表建；90/180 预警 job 随 S8 批次 | 📋 表就绪 |
| S4-11 | 补货（BR-05） | 分级审批（金额服务端判定 <500 L1 / <5000 L1-L2 / ≥5000 全链）；安全件先发后签（need_backfill + 24h 期限）；lockOverdueBackfills 锁定入口（BR-05 25h） | ✅ |
| S4-12 | 每日对账 | dailyReconcile：本地笔数 vs T+ SYNCED 笔数 vs 差异金额；23:00 job 入口就绪 | ✅ |
| S4-13 | E2E | S4FlowTest 8 用例 | ✅ |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | inv_transaction 查询报 Column deleted not found | 流水表不软删但实体继承了含 deleted 的 BaseEntity | **A-20**：新增 AuditedEntity 基类（审计字段无软删），生成器支持 'AUDITED' 标记 |
| P2 | 测试 2002 连锁（5 次） | 权限矩阵细化：R18 补 scm:supplier:manage；新增 scm:order:view（R09/R11/R12/R14）；R11 补 replenish:approve；R14 补 replenish:create | V7 授权修正（03 册口径：R11 可见订单但价格脱敏、R14 发起补货） |
| P3 | 真实库 BR-SCM-01 拦截冒烟 | 冒烟脚本漏了五阶注册 | 补 register+advance×5（拦截本身验证了 BR-SCM-01 生效） |
| P4 | JSON 字段名 tplusVoucherNo | Jackson 对 getTPlusVoucherNo 的属性名推断 | 测试按实际序列化名断言 |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test | **41/41**（S4 8 用例：五阶准入+BR-SCM-01 拦截/PR·PO 编号格式/MERGED 合并/**幂等 3 提交仅 1 笔**/SYNCED+凭证号/负库存拒绝 BR-INV-02/价格脱敏 BR-SCM-03/补货全流程 BR-05/每日对账） |
| verify-spec.py | 7 项 PASS |
| 真实 MySQL E2E | 注册供应商 → 五阶推进 APPROVED → PO 下发 → 入库事务 SYNCED+TP-MOCK 凭证 → **第 2 次提交返回同一 TX 号** → 可用量 50.0（只加 1 次） |

## 四、需求比对（07 册勾稽）

- FR-SCM-01/02/11/12/13/15/17 已实现；FR-SCM-03/04 资质到期提醒、07~10 评价、19 超收、21 退货、22 价格库表就绪/服务随 S5-S8 批次。
- FR-INV-07~14 扫码链路核心已实现（离线队列 H5 随 S7 小程序批次）；FR-INV-15~25 盘点差异表就绪；FR-INV-26~32 呆滞表就绪；FR-INV-33~38 补货已实现（38 分析、39 课题 → S8）；FR-INV-41 对账已实现。
- BR-SCM-01/03、BR-INV-01/02/03 已生效（E2E 断言）；BR-INV-04 无单据不发货由 source_type 必填承载 ✓。

## 五、汇报四件事

1. **建表清单**：累计 83 张（+20 T-SCM/T-INV）。
2. **接口清单**：新增 15 个（/api/v1/scm/*、/api/v1/inv/*），见 docs/API.md。
3. **自测结果**：41/41 + verify-spec 7/7 + 真实环境幂等/准入/对账冒烟，全绿。
4. **下一片确认**：S5 生产与排程（02 §4.10、08 册）：生产订单/工序路由/报工/Andon/排产/齐套（消费 S3 工艺路线 + S4 库存）。
