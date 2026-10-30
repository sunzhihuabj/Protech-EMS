# SPRINT S18 · 轨道二 W1（前端：报价单 + 招标情报台）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过构建门
> 依据：FR-CRM-17~20 + V4-05 M18（FR-BID-01~14）

## 一、交付

| 页面 | 内容 |
|---|---|
| crm/Quotations.vue | 报价单列表/新建（主行+行集，金额服务端算）/明细抽屉/提交评审/定标转合同（FR-CRM-19 桥接提示）/价格基准/来源转化统计（FR-CRM-20） |
| bid/BidIntel.vue | 数据源登记+robots 审核启用/公告入库（四层去重结果通知）/匹配打分抽屉（维度明细+回训反馈）/一键转商机/推送订阅/我的订阅 |
| V15 迁移 | menu:bid / menu:agt 菜单权限 + R03/R04/R05 授予 |
| 路由/侧边栏 | /crm/quotations、/bid/intel 注册；SidebarNode ROUTE_BY_CODE 扩展（mdm/scm/inv/mes/eng/cost/kb/arc/qms/eam/agt 占位映射随后续切片落页） |

## 二、验证

- vite build ✓（2.5s）；端点/载荷逐一对齐后端控制器（create 行集结构 materialId/qty/price/remark、dedup 四态文案）。
- GUI 走查（S23 批次补记，vite dev :8088 + 真实 MySQL）：登录（强制改密弹窗跳过）→ 侧边栏出现「招标情报/经营看板/Agent 接入/档案管理」→ 招标情报台三页签与表格渲染 → Agent 台注册（clientId=agt-a313ecf7）→ Token 7200s/6 能力 → bid.notice.search 同步调用成功（返回 0 行为正常业务态）——全链路通过。

## 三、汇报四件事

1. 建表：V15 仅菜单权限。
2. 页面：新增 2 页 + 路由/侧边栏。
3. 构建：vite build 全绿。
4. 下一片：S19 库存中心（事务/盘点/呆滞）。
