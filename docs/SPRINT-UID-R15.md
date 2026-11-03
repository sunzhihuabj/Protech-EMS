# SPRINT-UID-R15 · 断链修复 + 流程闭环补齐 + 响应式适配（2026-11-03）

> 轮次背景：全站"后端能力面 vs Web 端展示面"对照调查发现 4 处断链/壳页、14 组"后端已实现但 Web 无入口"的流程缺口、全站无响应式断点。本轮全部修复。
> 口径：仅引用既有令牌变量（UID-01 禁自创 hex）；页面改动保持 Pt 组件契约与 v-permission 门控；新后端端点全部 @RequirePermission 对齐。

## 一、断链/壳页修复（调查报告 B 类，4/4）

| # | 页面 | 原问题 | 修复 |
|---|---|---|---|
| 1 | sys/CodeRules.vue | 调 `GET /org/codes` 后端不存在（404 死页，恒空数据） | 后端 CodeController 新增 `GET /api/v1/system/codes/rules`（18 类编码目录，02 册 §五口径）；页面改调该端点 + 行内"预览下一号"（复用既有 preview 端点，8 类演示类型可用） |
| 2 | kb/KbMy.vue | 9 个看板硬编码在 data()，零 API 调用（壳页） | 改调 `GET /kb/boards`（真实 KbBoard 实体），卡片网格（xs/sm/lg 栅格自适应）+ 点击跳 /kb/board/:code，无权限空态给出授权指引 |
| 3 | bid/BidRule.vue | 表格列定义了规则字段但 records 恒为 []，无任何调用（壳页） | 重定义为"标讯订阅规则"编辑页：后端新增 `GET /bid/subscribe`（当前用户订阅，可为 null），保存复用既有 POST /bid/subscribe（每用户一条，覆盖式） |
| 4 | eng/ThdPortal.vue | 文案写着"解除走 /release"但无按钮；授权无撤销入口 | 后端新增 `GET /thd/worker-projects`（status 过滤）与 `GET /thd/blacklist`；页面两个 tab 补列表 + 行内"撤销"（reason 必填 $prompt）/"解除"（$confirm）动作 |

## 二、流程闭环补齐（调查报告 A 类，核心演示路径优先）

| # | 缺口 | 补齐方式 |
|---|---|---|
| 1 | 商机赢单后无法转合同（FR-CRM-02 断点） | Opportunities.vue TRACKING 行加"赢单转合同"（v-permission=opportunity:convert，$prompt 输合同 ID → POST convert；TRACKING→WON + 漏斗转阶段） |
| 2 | 合同财务闭环（开票/回款/交底/解冻/开票确认） | ContractDetail.vue 新增"财务与交底"卡：登记开票（POST /invoices）、登记回款（POST /receipts，可选"登记并确认"一步闭环）、开票确认（PENDING 行内按钮）、发起交底（receiverIds 多人）、解除冻结（FROZEN 态显示，reason 必填） |
| 3 | 档案归还无入口 | 后端新增 `GET /arc/borrows`（status 过滤）；Archives.vue 借阅 tab 补借阅单列表 + OUT/OVERDUE 行内"归还" |
| 4 | 呆滞处置审批/完成无入口 | 后端新增 `GET /inv/slow-moving/disposals`；Inventory.vue 呆滞 tab 补处置单台账（待审批/待完成/已完成三态）+ 行内"审批"（L1/L2/L3 $prompt）/"完成"（实际金额回填 $prompt） |
| 5 | 补货全链（BR-05）整块无入口 | Inventory.vue 新增"补货"tab：列表（status 过滤）+ 申请弹窗（明细行可增删）+ 审批/发料/回填三动作；提示 25h 锁定口径 |
| 6 | 标讯 100 分制评分无入口 | BidIntel.vue 公告行加"评分"（POST /notices/{id}/score，弹层展示 totalScore/100 + 匹配要点） |
| 7 | 呆滞列表 status=null 查询 bug | SlowMovingService.list 原 `.eq(status)` 空值时生成 `status IS NULL` 全空结果——改为条件 eq（顺手修复） |

**未纳入本轮（后续批次候选）**：PLM PCS 工艺整块、QMS 量具管理、项目成员/门控通过、MES 齐套/工序质检、集成运维台（/integ/ops）、文件上传中心（/files，ContractDetail 附件区已预留提示）、合同变更审批行内化、手动快照。

## 三、UI 美化与响应式（调查发现的"全站无断点"）

1. **MainLayout.vue**：全局搜索框由固定 320px 改 flex 自适应（0 1 320px，min 160px）；新增 ≤1200 / ≤768 断点（边距收窄、面包屑隐藏、角标区收窄、用户名溢出省略）；新增 resize 监听——≤1024 自动折叠侧栏、≥1200 恢复用户持久化偏好（不覆盖 localStorage）。
2. **base.css**：
   - 字体渲染：antialiased + optimizeLegibility；
   - 数字等宽：el-table/el-descriptions/statistic 全局 tabular-nums（金额列不再跳动）；
   - 小屏弹窗：el-dialog 92vw / el-message-box 88vw / el-drawer 88%，不溢出视口；
   - 细滚动条：webkit 8px 令牌色圆角 + Firefox scrollbar-width:thin（随主题联动）；
   - el-card 统一 180ms box-shadow 过渡。

## 四、验证

| 项 | 结果 |
|---|---|
| mvn compile（module-org/bid/cost/eng/inv 受影响模块） | BUILD SUCCESS |
| 全量 mvn test | 见本轮提交信息（测试基线 145 用例不回退） |
| 前端 vite build | 通过（无编译错误） |
| verify-spec | PASS（硬卡点/禁公网 AI/编码规范不受影响） |
| 权限口径 | 新端点 6 个全部 @RequirePermission（复用既有权限码，无新增码 → 无需种子迁移） |

## 五、涉及文件

后端 6：CodeController、BidController、BidService、ArcController、ArchiveService、ThdPortalController、ThdPortalService、SlowMovingController、SlowMovingService（9 个类）
前端 9：CodeRules、KbMy、BidRule、ThdPortal、Opportunities、ContractDetail、Archives、Inventory、BidIntel + MainLayout、base.css（2 个全局样式载体）
文档 2：SPRINT-UID-R15.md（本文）、UID-ALIGN.md（对账表更新）
