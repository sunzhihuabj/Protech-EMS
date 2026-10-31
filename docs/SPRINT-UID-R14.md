# SPRINT UID-R14 · FR-PRJ-22 关键路径算法（CPM）

> 日期：2026-10-31 ｜ 状态：✅ 完成（全绿 + 双自检 PASS）

## 一、交付
| 件 | 内容 |
|---|---|
| CpmService | 前推 ES/EF → 后推 LS/LF → 松弛=0 即关键路径；拓扑排序（Kahn）+ 环检测 |
| 端点 | GET /prj/project/{id}/critical-path |

## 二、算法
1. 构建依赖图（FS 类型 + lagDays 偏移）
2. Kahn 拓扑排序（环检测 → 422"任务依赖存在环"）
3. 前推：ES = max(前置 EF + lag)，EF = ES + duration
4. 后推：LF = min(后继 LS)，LS = LF - duration
5. 松弛 = LS - ES；松弛 = 0 → critical=true

## 三、验证
mvn 全量 + verify-spec + uid-acceptance 全 PASS
