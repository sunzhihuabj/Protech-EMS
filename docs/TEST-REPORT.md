# 深度测试报告（多角色权限模拟 · 黑盒 API 全流程）

> 日期：2026-10-31 ｜ 环境：本机 MySQL 5.7 + 主应用 :8080（真实库 V1-V15 已导入）
> 方法：以 11 个种子账户登录，按 RPD 角色权限矩阵逐角色执行业务流程 + 越权探测 + BR 硬卡点抽查 + 基础性能。
> 账号：admin@demo(R03) biz@demo(R04) boss@demo(R05) eng@demo(R14) store@demo(R11) fin@demo(R15) tech@demo(R06) purchase@demo(R09) prod@demo(R12) mdm@demo(R18) worker@demo(R17)

## 一、测试结论

**总体：核心业务链路与权限体系工作正常，硬卡点（BR-01/02/08/09、BR-INV-02）全部生效；发现 3 个高危问题（越权写、价格泄露、跨库必填校验缺失）与 6 个中低危问题，均在报告 §四 给出修改意见。**

| 维度 | 结果 |
|---|---|
| 登录矩阵（11 账号） | ✅ 10 账号通过；worker@demo(R17) PC 登录被拒（BR-08 ✓）；错误密码 1001 并计防爆破数 |
| 越权探测（10 组） | ✅ 9 组正确 403；1 组发现高危越权（见 F1） |
| 商务全链（R04） | ✅ 商机→跟进→报价（金额一致性校验正确拦截不一致行）→提交→定标→**合同草稿自动生成**（PRT-2026-XS-NEW-0001） |
| 库存链（R11） | ✅ 幂等重放同单号 / 可用量 / BR-INV-02 负库存拦截 / 盘点 E-37 无归因不调账 / 差异 L1 审批 / 呆滞 E-38 归因必填 |
| 研发链（R06） | ✅ BOM 建/行/13 条实时校验/跑批快照/发布；BR-09 ECN 无处置计划 422 ✅ |
| 生产+APS（R12） | ✅ MO→路由→开工→报工→Andon；产能→L1 顺排（2 明细 0 冲突）→发布 |
| 工程链（R14） | ✅ 建档后派工可见（PROJECT 数据范围）→ E-32 状态机推进 → 施工日志（AI-009 降级不阻塞落库）→ 售后闭环 |
| 成本链（R15） | ✅ 归集/合计/EVM（SPI=0.8 CPI=0.8889）/越权派工 403 |
| BR 硬卡点 | ✅ BR-01 评审无项目 422/带项目通过；BR-02 filing 26 要素缺 9 项正确拒绝、未用印不得变更；BR-08/BR-09 如上 |
| 数据范围 | ✅ SELF 本人可见 / DEPT 同部门可见 / 跨部门不可见（经权限+范围双闸） |
| 性能 | ✅ 列表类 avg 92-96ms（max≤130ms）；登录 159ms（bcrypt）；20 并发突发登录/查询均 <420ms 总耗时 |

## 二、发现的问题（按严重级）

### F1【高·越权写】生产模块 7 个写端点缺失权限注解
`MesController` 的 `POST /orders、/orders/{id}/routes、/processes/{id}/start、/processes/{id}/finish、/processes/{id}/qc、/andon、/andon/escalate` 均无 `@RequirePermission`。
**实测**：tech@demo(R06 设计) 成功创建生产订单并生成工序路由。全仓静态扫描（见附件脚本口径）另确认：`ArcController.overdue-scan`、`CostKbController.escalate`、`InventoryController.lock-overdue`、`ContractController.dispatches/{id}/read`、`FileController.upload` 亦无注解（其中 job 入口类可接受，但应收敛为内部端点）。

### F2【高·数据泄露】BR-06 价格未对仓储脱敏
`GET /mdm/materials` 对 store@demo(R11 仓管员，角色明示"不得见价格") 返回含 `costPrice` 字段的完整物料记录。

### F3【高·跨库必填】MySQL NOT NULL 列缺服务端校验 → 500 而非 400
实测三例（H2 测试宽松掩盖）：`inv_transaction.unit / source_type`、`ct_contract.party_a / business_type`——缺失时接口 9001"系统繁忙"。同类风险面：所有以 H2 为唯一测试库、真实 MySQL 上多列 NOT NULL 无默认的表。

### F4【中】空盘点单可复核通过
盘点单未录入任何实盘行时 `POST /inv/counts/{id}/review` 返回成功并推进 DIFF_PROCESS（应为 400/5001 拦截）。

### F5【中】JSON 列入参未校验
`POST /bid/subscribe` 的 keywords 传非法 JSON（如 `[DT]`）直接 500（MySQL JSON 列拒收）。同类：所有以 JSON 列承接自由入参的接口（V4-05/06 多处）。

### F6【中】job 调用账号权限不足
`InternalJobInvoker` 默认用 admin@demo(R03)，但 R03 无 `kb:card:manage`(137) → 02:00 红灯升级任务将 403 静默失败。

### F7【低】契约与文档不一致
`POST /crm/quotations/{id}/decide` 实现契约为 `{"won":true,"lostReason":"..."}`，docs/API.md 描述为 `result`——按文档调用会误入未中标分支。

### F8【低】错误码不精确
未用印发起合同变更返回 9001（应 1001 附明确提示）；F3/F5 同类"应 400 却 500"。

### F9【低·体验】30 分钟 Access Token 有效期
长流程测试中 3 次遭遇 2001 中断（已有 /auth/refresh，前端可加无感刷新）。

## 三、验证未覆盖（如实说明）

- SCB 采购五阶准入/物流/退货链（purchase@demo）仅做了列表演证，未跑完整建单链
- file-service 独立进程（BR-07 水印下载）本次未拉起，留待部署批次验证
- 大数据量分页/深翻页、长时稳定性、内存泄漏类观测未做（本机开发库数据量小）
- GUI 层仅做过 S23 抽查，本次为 API 黑盒

## 四、修改意见（按优先级）

1. **[P0] 补齐 MES 权限注解**：7 个端点加 `@RequirePermission`（orders/routes→`mes:order:create`；start/finish/qc→`mes:report`；andon→`mes:report`；andon/escalate→内部化或 `mes:report`）；job 入口类（arc overdue-scan / kb escalate / inv lock-overdue）迁到 `/api/internal/jobs/**` 统一白名单，业务端点一律带注解。
2. **[P0] BR-06 价格脱敏**：`/mdm/materials` 响应按权限过滤——无 `fin:*`/采购价格权限的角色剔除 `costPrice`（序列层 Map 清洗或 @JsonIgnore + DTO 双出口）；同查 `/scm/*` 与报价接口对 R11 的暴露面。
3. **[P0] 补服务端必填校验**：对 MySQL strict 下 NOT NULL 无默认列（至少 inv_transaction、ct_contract、bid_subscribe）在服务层逐字段校验，缺失返回 `1001` 明确字段名；并将「真实 MySQL 冒烟 = 每片验证门」固化到 verify-spec 第 8 点（新增）。
4. **[P1] 盘点复核前置校验**：review 时要求 ≥1 行已录实盘，否则 1001"盘点尚未录入实盘"。
5. **[P1] JSON 入参归一**：keywords/regions/threshold_json 等自由入参在服务端 parse→normalize（非法即 1001），或列改 VARCHAR（A-30 口径）。
6. **[P1] job 专用账号**：建 `job-runner` 系统账号授 137/126 等任务所需权限，`JOB_SYSTEM_USERNAME` 指向它；避免借用 admin。
7. **[P2] 错误码精确化**：F7 文档改为 `{"won":...}`；F8 变更未归档返回 1001"合同尚未归档，不可变更"。
8. **[P2] 无感刷新**：前端 axios 401 拦截先试 `/auth/refresh` 再跳登录。

## 五、复测建议

修复后按本报告 §一 全部用例重跑（预计 30 分钟），重点回归 F1（越权矩阵）、F3（MySQL strict 全量写路径）、F6（job 实际触发一轮）。
