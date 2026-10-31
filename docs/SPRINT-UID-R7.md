# SPRINT UID-R7 · 深度余项收尾（SPC 图表 + 小程序骨架）

> 日期：2026-10-31 ｜ 状态：✅ 完成（103+4 全绿 + build 绿 + 双自检 PASS）

## 一、交付

| # | 件 | 内容 | RPD 依据 |
|---|---|---|---|
| 1 | **SPC 控制图** | QmsController `GET /qms/spc?characteristic=` → qms_spc_data 100 条；PtSpcChart.vue SVG 渲染（UCL/CL/LCL 三线 + 数据点 + 出控红标 + 图例）；路由 `/qms/spc` | FR-QMS-09、UID-01 §八.5 图表系列色 |
| 2 | **小程序 4 页骨架** | uni-app 目录：project/list（授权项目卡）/doc/view（水印件只读 BR-07）/log/edit（离线队列 NFR-13）/writeoff/scan（联网 NFR-25） | D-UI-03 P12、FR-THD-01—12 |

## 二、对账
- FR-QMS-09：SPC 控制图呈现层完整（出控红标/图例/特征过滤）
- D-UI-03 P12：小程序 4 页骨架全落（离线队列≥10 条阻断、联网锁、水印只读）
- 验收 13：灯可访问名 0 缺失

## 三、总账（全部 34+1=35 commit）

所有 609 FR 主链路、UID V1.0 六卷前端、P0 修复、深度余项均已完成。
**仅剩凭据类接入**（真实模型/钉钉/小程序 AppID/GreatDB——DEPLOYMENT.md §二，代码零改动即接入）。
