# SPRINT UID-R6 · 深度余项（鉴定提醒/纪要 PDF/甘特拖拽）

> 日期：2026-10-31 ｜ 状态：✅ 完成（103+4 全绿 + build 绿 + 双自检 PASS）

## 一、交付

| # | 件 | 内容 | RPD 依据 |
|---|---|---|---|
| 1 | **档案鉴定提醒** | ArchiveAppraisalService.appraisalScan()：P10/P30/PERM 周期 + archive_year → 到期 ≤365 天产提醒；job 04:00 调度 `/api/v1/arc/appraisal-scan` | S11 遗留、A-36 |
| 2 | **纪要 PDF 导出** | KbMeetingService.meetingPdf()：PDFBox 2.x showText API 生成 A4 白底；`GET /kb/meetings/{id}/pdf` 下载 | FR-KB-11、验收 11 |
| 3 | **APS 拖拽甘特** | Aps.vue 甘特区：bar 左右拖拽 → 松手调 /adjust 端点（版本+1）；GANTT_PX_PER_MIN=0.4；已发布不可拖 | FR-PRJ-23、FR-APS-10 |
| 4 | 编译修复 | PDFBox 2.x showText（非 3.x fillText）；ResponseEntity import 补齐 | — |

## 二、验证
| 项 | 结果 |
|---|---|
| mvn test | **103/103 + 网关 4/4** BUILD SUCCESS |
| verify-spec / uid-acceptance / gen --check | PASS / PASS / UP-TO-DATE |

## 三、对账
- 档案鉴定提醒：S11 遗留清零（A-36 周期口径备案）
- 会议纪要 PDF：FR-KB-11 完整（前端打印 + 后端 PDF 双通道）
- 甘特拖拽：FR-PRJ-23 呈现层落地；服务端 adjust 端点 + 版本+1 已有
