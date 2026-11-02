# SPRINT-KPI1：九板指标自动取数（审计 F7）

> 日期：2026-11-03 · 分支：`feature/dev` · 前置：OPS1（V23 幽灵码补种）

## 一、重构内容

| 项 | 旧 | 新 |
|---|---|---|
| 取数位置 | job 进程内裸 DriverManagerDataSource（口令还写死空串，root:root 环境必炸） | 后端 `KbMetricService.autoFetchAll(period)`（复用容器数据源）+ `/api/internal/jobs/kb/metric-fetch`（InternalKbJobsController，白名单） |
| 覆盖面 | 仅 S17.MO.DELIVERY 1 项 | **10 项目录**（列名逐一实证）：交付率/完工率/一次合格率/当月签约额+数/项目平均进度/逾期项目数/呆滞批次率/红灯数/红灯闭环率 |
| 定义依赖 | 无 | V23 种 10 个 kb_metric_def（is_manual=0，看板卡与指标定义页可见；阈值 A-35 口径显式化便于调整） |
| 幂等 | 单指标硬编码 | snapshot UK（code+period）修正口径，job 可重跑 |
| 容错 | 一处异常全断 | 单指标失败不阻塞（failed 计数 + warn 日志）；分母 0 跳过 |

## 二、跨库兼容要点（H2 ↔ MySQL）
- DATE_FORMAT / CURDATE / DATE_SUB…INTERVAL 为 MySQL 专属 → 月边界与呆滞基准日在 **Java 端算好作字面量**；CURDATE → CURRENT_DATE（双库兼容）。

## 三、验证
- S29 E2E：批量取数 fetched≥5 + 幂等重跑一致 + catalogSize=10（空库分母 0 项跳过属预期）
- S4/S27/S28 回归不受影响；全反应堆 BUILD SUCCESS（见提交）
- MySQL：V23 幂等重放 10 语句 OK（权限行 INSERT IGNORE + NOT EXISTS 授权 + 指标定义 INSERT IGNORE）

## 四、遗留
- 其余九板指标（销售漏斗/回款率/毛利率等）待对应业务表列名实证后按同模式扩目录（catalog 加行即可）
- 指标阈值 JSON 精细化（同环比）随看板深度批次
