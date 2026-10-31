# SPRINT UID-R3c · P4 台账迁移收尾（第三批）

> 日期：2026-10-31 ｜ 状态：✅ 完成（build 绿 + /ct/contract GUI 确认）

## 一、迁移清单（本轮 8 页 10 表，全部按 R3b 示范模式）

| 页 | 表 | 亮点 |
|---|---|---|
| contract/Contracts | 合同台账 | 状态流转 Tag 冻结并排、（未用印）兜底文案、E-32 分状态操作组 |
| project/Projects | 项目台账 | 状态字典对齐 E-19（CREATED→CLOSED） |
| bid/BidIntel | 标讯台账 | 空态主行动"去入库"直连 ingest 弹窗 |
| mdm/MdmConsole | 客户主体 | 状态字典 ACTIVE/FROZEN/PENDING |
| mes/Production | 生产订单 | MO 类型 Tag（STANDARD/BATCH/ENGINEERING） |
| mes/Aps | 排产明细 | 工时右对齐 tabular |
| kb/Kanban | 卡片台账 | E-52 灯色字典全量 |
| eng/EngPortal | 派工单 + 售后工单 | E-32 状态字典、紧急度 Tag |

## 二、会议三问自动暂存（UID-03 §2.2(g) 高风险降级）

- threeQ deep watch → 500ms 节流写 sessionStorage（键含 boardCode）；切段/刷新后恢复并提示"已恢复未提交的过堂内容"

## 三、验证

| 项 | 结果 |
|---|---|
| vite build | ✓（每页迁移后均构建） |
| GUI | /ct/contract 暗态确认：筛选区/工具栏主行动/金额 tabular 右对齐/流转 Tag/分页+跳页/分状态操作组 全渲染 |
| 守卫 | admin(R03) 无 menu:crm → 回工作台（菜单级校验）✓ |

## 四、对账
UID-05 §二：P4 模板覆盖的主台账页 10/10 已迁；余模板页（/scm/*、/qms/*、/eam/* 等 69 条）按同模式生成（无需新组件）。
