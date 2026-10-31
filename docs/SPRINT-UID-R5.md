# SPRINT UID-R5 · R5 遗留收尾（FR-KB-11 纪要落库 + 库龄跑批）

> 日期：2026-10-31 ｜ 状态：✅ 完成（103/103 + 网关 4/4 + build 绿 + MySQL 冒烟通过）

## 一、交付

| # | 件 | 内容 | RPD 依据 |
|---|---|---|---|
| 1 | **FR-KB-11 纪要落库** | KbMeetingService（save/listByBoard）+ CostKbController `POST/GET /kb/meetings` + 前端 Board「结束会议」按钮 → 落库后跳纪要页 | FR-KB-11、V4-05 M12 |
| 2 | **库龄自动跑批** | SlowMovingService.autoScanFromBatches(180)：inv_batch 库龄≥180d 且 qty>0 → 呆滞 CANDIDATE（同物料未闭环去重）+ InventoryJobsController `/api/internal/jobs/inv/age-scan` + job 03:30 调度 | S12 遗留、M07 |
| 3 | 权限 | 纪要端点 `kb:card:manage`；库龄扫描走内部白名单 `/api/internal/jobs/**` | M00-5 |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | 纪要端点 404 | @RequestMapping("/api/v1") + @PostMapping("/meetings") → /api/v1/meetings，缺 /kb 前缀 | 修正为 /kb/meetings（对齐 /kb/cards 模式） |

## 三、验证

| 项 | 结果 |
|---|---|
| mvn test | 103/103 + 网关 4/4 BUILD SUCCESS |
| verify-spec / uid-acceptance / gen --check | PASS / PASS / UP-TO-DATE |
| MySQL 冒烟 | 纪要落库 id=1 → 列表 1 条 board=1 ✓；库龄扫描 0 条（无≥180d 批次，逻辑通）✓ |

## 四、对账
FR-KB-11 完整闭环（会议模式→纪要落库→纪要页→打印亮色）；S12 库龄跑批遗留清零；S11 档案到期鉴定提醒记录于 DEPLOYMENT（需鉴定周期口径，Q 项）。
