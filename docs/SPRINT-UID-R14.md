# SPRINT UID-R14 · 审计修复 D1（权限注解补齐）

> 日期：2026-10-31 ｜ 状态：✅ 完成（103/103 + 4 全绿 + build 绿 + 双自检 PASS）

## 一、交付
| # | 修复 | 内容 | 端点数 |
|---|---|---|---|
| 1 | MdmController | 补 9 个 GET 查询端点 @RequirePermission（mdm:customer:view / mdm:material:view / mdm:project:view） | 9 |
| 2 | MesController | 补 4 个 GET 查询端点 @RequirePermission（mes:order:view） | 4 |
| 3 | ApprovalController | 补 2 个 GET 查询端点 @RequirePermission（sys:approval:act） | 2 |

## 二、审计发现修正
前次审计报告（510972b）报告"265 端点缺权限"——**扫描器有误**：只检查了 @Mapping 上方 1 行，未检查下方。修正扫描方向后实际缺口为 **15 个 GET 查询端点**（本批全部补齐）。合理无注解的 34 个端点（Agent 通道/Auth 自有资源/内部 job/公开查询）确认无需修复。

## 三、对账
AUDIT-REPORT F1 从 P0 降级为 P2（实际缺口远小于初报），15 个 GET 查询端点已全部补注解。
