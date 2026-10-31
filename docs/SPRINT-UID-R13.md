# SPRINT UID-R13 · SPC 自动采集 + 3 页填充

> 日期：2026-10-31 ｜ 状态：✅ 完成（103+4 全绿 + build 绿 + 双自检 PASS）

| # | 件 | 内容 | RPD 依据 |
|---|---|---|---|
| 1 | SPC 自动采集 | QmsService.recordSpc：POST /qms/spc 录入测量值→自动出控判定→写 qms_spc_data；QmsController GET /qms/spc 查询 | FR-QMS-09 |
| 2 | 我的任务 | /workbench/my-task → MyTask.vue（项目任务视图） | FR-PRJ-12 |
| 3 | 我的看板订阅 | /kb/my → KbMy.vue（九板入口卡片） | FR-KB-16 |
| 4 | 导入向导 | /sys/import → ImportWizard.vue（P9 族 4 步：模板→上传→干跑→确认） | FR-SYS-11 |

Stub 余量：0 条（全部 13 条已消减完毕！）
