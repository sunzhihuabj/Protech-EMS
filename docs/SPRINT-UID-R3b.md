# SPRINT UID-R3b · Pt 组件第二批（P4 范式迁移 + 会议三问 + 打印亮色）

> 日期：2026-10-31 ｜ 状态：✅ 完成（build 绿）

## 一、交付

| 件 | 内容 | UID 依据 |
|---|---|---|
| **PtTable** | columns 配置驱动（type: text/num/money/date/enum/light/action；masked → `***`+aria+列头后缀）；工具栏/批量条（未选禁用不隐藏，跨页"已选 N 条"）；分页 56（20/50/100/200、跳页、>5 万"约 N 条"）；**空态三型+主行动**（no-data/no-result/no-scope）；aria-busy | UID-04 §四、UID-02 §七、FR-SYS-24 |
| **PtFilterBar** | 折叠 48/展开、最多 2 行 × 4 项、**"已选："Tag 行**（折叠隐藏但生效的条件可见）、重置 | UID-01 §7.1 |
| **PtFormSection** | 分组折叠 + errorCount + 锚点 | UID-04 §四 |
| P4 迁移示范 | Quotations.vue（筛选/工具栏/表格/分页四段 + URL 即状态 router.replace query + 空态）与 Opportunities.vue（actions 列 + 状态/意向 Tag） | UID-02 §七、UID-03 §2.1 P4 |
| **会议三问**（FR-KB-10） | Board.vue 红灯过堂段：三问纵向一行一问（卡在哪 textarea min-88 / 谁解决默认责任人 / 何时解决默认承诺日）；`记录并下一张`（三问齐才可点，组合 solution 走 /close）/ `记为未闭环过堂`；当前卡左竖条高亮 | UID-03 §2.2(e) |
| **打印 t1 亮色** | print.css：@media print 强制 t1 light 73 令牌 + 隐藏侧栏/顶栏/水印/分页/筛选（UID-01 §八.9） | UID-01 §八.9、验收 11 |
| FileController.upload | 补 `file:upload` 注解（V2 种子码 20） | M00-5 |

## 二、验证
- vite build ✓；GUI 走查（biz@demo 暗态）：报价单台账 P4 四段全渲染——PtFilterBar/PtTable（金额 tabular 右对齐、状态流转 Tag"已定标"、空值"—"、分页共 N 条+跳页）/菜单级守卫（admin 无 menu:crm 回工作台）
- 修复：示范页首迁时 Pt 组件未注册 components → 空白；已补注册并复测

## 三、待续（第三批）
- 其余 9 页面迁移（同示范页模式）；会议模式三问自动暂存 30s；131 路由模板页批量；视觉走查截图对比
