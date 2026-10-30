# 阶段进度台账（Phase Ledger）

> 每阶段完成后更新本表；详细记录见 `docs/SPRINT-Sx.md`。

| 阶段 | 状态 | 交付物 | 验证门 | 记录 |
|---|---|---|---|---|
| 阶段 0 · 需求分析与开发计划 | ✅ 完成 | docs/DEVELOPMENT_PLAN.md、DECISIONS.md、本台账 | 计划覆盖 S0-S9 全切片与验证门 | SPRINT-S0.md §0 |
| 阶段 1 · S0 平台底座 | ✅ 完成 | 后端 7 模块 + 17 表迁移/种子 + 认证/RBAC/数据范围/审计/编码引擎/字典/通知/文件水印/AI网关 mock + 前端骨架 + 12 套预编译主题 | mvn 13/13；verify-spec 7/7；vite build；真实 MySQL+Redis E2E；浏览器 GUI 验证 | SPRINT-S0.md |
| 阶段 2 · S1 营销与合同 | ✅ 完成 | T-MDM/CRM/CT 24 表 + 商机全流程 + 合同九节点 + BR-01/BR-02 + AI 提取风险 + 前端 2 页 | mvn 18/18；verify-spec 7/7；真实库 E2E；浏览器 GUI | SPRINT-S1.md |
| 阶段 3 · S2 项目与 ECO | ✅ 完成 | T-PRJ 7 表 + 模板立项/任务树/依赖成环检测/ECO(BR-09)/门控 + 前端项目页 | mvn 25/25；verify-spec 7/7；真实库立项开工 | SPRINT-S2.md |
| 阶段 4 · S3 研发与工艺 | ✅ 完成 | T-PLM/T-PCS 15 表 + BOM 二维矩阵 + 13 条校验 + ECN(BR-09) + 工艺路线 + 覆盖度 + 前端 BOM 页 | mvn 33/33；verify-spec 7/7；真实库冒烟 | SPRINT-S3.md |
| 阶段 5 · S4 供应链与库存 | ✅ 完成 | T-SCM/T-INV 20 表 + 五阶准入 + 采购链 + 幂等库存事务(T+权威) + 补货 BR-05 + 对账 | mvn 41/41；verify-spec 7/7；真实库幂等冒烟 | SPRINT-S4.md |
| 阶段 6 · S5 生产与排程 | ✅ 完成 | T-MES/T-APS 12 表 + MO 三类型 + 工序路由 + E-26 状态机 + 报工 + Andon 分级 | mvn 47/47；verify-spec 7/7 | SPRINT-S5.md |
| 阶段 7 · S6 质量与设备 | ✅ 完成 | T-QMS/T-EAM 14 表 + 检验三关口 + AI 改判留痕 + 8D D6 校验 + 量具超期锁定 + MTBF | mvn 52/52；verify-spec 7/7 | SPRINT-S6.md |
| 阶段 7.5 · V4.0 对齐（新 RPD） | ✅ 完成 | 报价单/商机来源/E-52 枚举/编码 24 类/blockCode 命名空间/SPEC-V4 同步 | mvn 58/58；真实库冒烟 | SPRINT-V4Align.md |
| 阶段 8 · S7 工程与第三方 | ✅ 完成 | T-ENG/T-THD 21 表 + 派工 BR-03·04 + E-32/E-29 状态机 + 整改闭环 + FR-ENG-08 | mvn 64/64；真实库冒烟 | SPRINT-S7.md |
| 阶段 9 · S8 成本看板治理 | ✅ 完成 | T-COST/T-KB/T-ARC 16 表 + 成本七项归集 + 超支预警 + EVM + 卡片五要素 VAGUE 拦截 + 红灯升级 + 三问闭环 | mvn 70/70；verify-spec 7/7；真实库冒烟 | SPRINT-S8.md |
| 阶段 10 · S9 AI 真实接入 | ✅ 完成 | 16 编号段 AI-001~016/20 能力点注册 + 降级链 + 四元组追溯 + M15 适配器（T+ FILE/API、钉钉主企微备） | ai-gateway 4/4；S1/S3 降级断言 | SPRINT-S9-S10.md |
| 阶段 11 · S10 招标情报与 Agent | ✅ 完成 | T-BID 8 表 + T-AGT 3 表（累计 142 实体/141 表）+ 数据源/四层去重/100 分制/一键转商机 + Agent 注册/OAuth2 2h/HMAC/12 能力白名单/202 异步/AGT-FORBIDDEN | mvn 78/78 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S9-S10.md |
| 阶段 12 · S11 收尾批（盘点/差异/档案） | ✅ 完成 | 盘点全链路（E-37 无归因不调账/L1-L3 分级审批）+ 档案借/销/移闭环（在借守卫/鉴定必填/逾期扫描） | mvn 82/82 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S11.md |
| 阶段 13 · S12 收尾批（呆滞/处置） | ✅ 完成 | 呆滞 CANDIDATE→DISPOSED 主链路（E-38 归因必填/E-39 处置/数量守卫/DP 编号/审批分级复用）+ S11 E-37 归因对齐字典修正 | mvn 85/85 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S12.md |
| 阶段 14 · S13 收尾批（工程日常记录） | ✅ 完成 | 施工日志（AI-009 降级不阻塞）/质检 FAIL 缺陷必填/领退料累计守卫/售后 WO 三态闭环（方案必填+客户确认） | mvn 90/90 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S13.md |
| 阶段 15 · S14 收尾批（第三方门户） | ✅ 完成 | 送货单 DN 打印留痕/人员项目授权（黑名单拦截）/四维评价定级/黑名单生效自动冻结+解除（V14 权限补授） | mvn 93/93 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S14.md |
| 阶段 16 · S15 收尾批（APS 排产） | ✅ 完成 | 产能台账 + L1 顺排（冲突清单）+ L2 拖拽调整（版本+1）+ 发布齐套门禁（A-33 简化口径） | mvn 97/97 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S15.md |
| 阶段 17 · S16 收尾批（BOM 跑批） | ✅ 完成 | 13 条校验批量跑批 + 结果快照落 bom_header.qc_result（A-34）+ 回查端点 | mvn 100/100 + 网关 4/4；verify-spec 7/7；真实 MySQL 冒烟 | SPRINT-S16.md |
