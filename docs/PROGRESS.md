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
| 阶段 7 · S6 质量与设备 | 🚧 进行中 | — | AQL 抽样正确；D6 未填 8D 不可关闭；MTBF 计算 | — |
| 阶段 8 · S7 工程与第三方 | ⏳ 待开始 | — | — | — |
| 阶段 9 · S8 成本看板治理 | ⏳ 待开始 | — | — | — |
| 阶段 10 · S9 AI 真实接入 | ⏳ 待开始 | — | — | — |
