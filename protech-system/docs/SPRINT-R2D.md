# SPRINT R2-D · AI R2 能力点 + 报表订阅/经营底稿（M16 + M14）

> R2 深化切片 4｜分支 protech-v2｜2026-11-02
> DoD：AI 十点开放（AI-09 R3 除外）；Vitest 120 全绿（+R2D 7 条）。

## 交付内容

### M16 AI R2 集（FR-M16-004~008/010/011）
| 能力 | 实现 | 白名单口径/红线 |
|---|---|---|
| AI-04 模糊卡点改写 | VAGUE 命中 → 可验收改写建议（Ask-first） | 前端采纳后才可提交 |
| AI-05 会议纪要生成 | 过堂记录 → 结构化纪要（行动项/责任人/期限/跳过清单） | 引用 meetingId 溯源 |
| AI-06 ChatBI | 白名单四口径（红灯/项目/库存/回款）**不直连裸表**；越界明示红线 | source 标注 |
| AI-07 异常预警解读 | 红灯聚集/齐套缺口 → 原因假设+建议 | ask |
| AI-08 报价辅助 | 历史 P25/P50 + 低毛利预警（<中位 85%） | ask |
| AI-10 日报周报 | 项目时间线+工时+卡点 → 周报草稿 | Ask-first |
| AI-11 报表解读 | 三句话（趋势/异常/建议）contracts/stock-moves 模板 | auto |
| AI-09 RAG | 暂不开放（R3 语料） | 白名单外 403 |

### M14（FR-M14-003/005）
- 报表订阅：reportKey+userIds（渠道 inapp/钉钉预留）；worker 周一 08:00 推送站内 ✅ 测试（通知到达断言）
- 月度经营底稿：/reports/executive-pack（新签/回款/红灯/补录/项目分布一键聚合）✅

### worker 新增（合计 14 jobs）
slowmoving-scan(03:00) / as-inspections(04:00) / warranty-watch(04:00) / archive-remind(05:00) / report-push(周一 08:00)

## 修复记录
- Nest 路由声明顺序：executive-pack 必须先于 :key 注册（否则 422）
- S6 白名单断言随 R2 更新（AI-06 已开放，拦截改测 AI-09）
