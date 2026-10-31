# SPRINT S2 · 看板与早会核心（M10）

> 切片 S2（06 册 §4.3）｜分支 protech-v2｜2026-11-01
> DoD：早会红灯过堂演示可跑；Vitest 56 全绿（S0 34 + S1 10 + S2 12）。

## 交付内容

### 数据域（T-61~T-66 + 会议两表）
board / kb_card / kb_card_log / kb_escalation / metric_def / metric_snapshot / kb_meeting / kb_meeting_item

### 机制
| FR | 实现 | 测试 |
|---|---|---|
| FR-M10-001 | 九板幂等预置（1 总板+4 日板+4 周板，08 册 §二主责/节奏） | 1 |
| FR-M10-003 | 四区一栏视图端点（指标/进行/红灯/闭环 + 跨板挂卡区） | 契约 |
| FR-M10-004 | 指标引擎：3 个真实计算器（红灯总数/闭环率/补录率）+ 10 个 sql:* 占位（empty→"—"），快照表 upsert | 2 |
| FR-M10-005 | 状态灯判定（绿≥目标/黄 80-99%/红<80%/反向） | 服务端+前端双实现 |
| FR-M10-007 | 五要素：VAGUE 词库拦截（可配置）/唯一责任人/承诺日/红灯必填卡点+下一步 | 3 |
| FR-M10-008 | 一键建卡 source_type/source_id | 字段 |
| FR-M10-009 | 拖入红灯强制表单（服务端同样强校验） | 1 |
| FR-M10-010 | BR-12 is_backfill（actionDate 早于今日）计入补录率 | 1 |
| FR-M10-013 | 我的卡（逾期置顶） | 1 |
| FR-M10-014/**BR-07** | 红灯 3 天自动升级（内部扫描端点）；无删除接口仅解决 | 2 |
| FR-M10-015 | 48h 未响应→meeting 级升级；评论视为响应 | 扫描 |
| FR-M10-016 | 升级推送（链主+R-EXEC 站内信；钉钉 M15） | 联动 |
| FR-M10-017 | 升级清单（超期天数降序）+一键催办"要求今日答复" | 2 |
| FR-M10-018~020 | 会议模式：四段议程/三问必填（未填不允许下一张，可跳过记未闭环）/自动纪要 | 2 |
| FR-M10-022 | 未开会提醒扫描（日板 2 工作日） | worker |
| FR-M10-026 | BV-02 聚合（指标区/红灯总览/九板健康度含最近开会/风险区占位） | 1 |
| FR-M10-021/023/024/029/030 | 会议统计端点/闭环统计/总板汇总/配置/手工指标 → 部分 S5 深化 | — |

### worker（apps/worker，BullMQ + Memurai）
5 个 repeatable job：redlight-scan(30m)/no-response-scan(30m)/metric-snapshot(2h)/metric-daily(06:00)/meeting-remind(工作日 07:30)。
任务体=调 api 内部端点（INTERNAL_TOKEN 保护）——扫描逻辑单一来源。

### Web
- /kb/:code 四区看板：板头指标条（状态灯配色/手工角标/无数据"—"）+ 三列卡片区（红灯红底+🔥升级标）+ 建卡抽屉（五要素）+ 拖入红灯强制弹表单 + 会议模式 Modal（三问逐卡/跳过/纪要）
- /kb/executive BV-02：经营指标区（不脱敏）/红灯总览（按板分布 Tag 点击直达）/风险预警区（占位）/九板健康度表

## 修复记录
- BigInt 不可 JSON.stringify（kbMeeting.attendees）→ 先转 string（全局规范补充：任何 JSON.stringify 前不得含 BigInt）
- 路由/类型：metrics 方法名与注入字段冲突；ExecutiveService select 缺 createdAt

## 遗留
- [ ] 拖拽跨区（当前按钮式移动，拖拽在 S5 前端深化）
- [ ] FR-M10-011 跨部门挂卡 Web 入口、FR-M10-025 BV-01（S5）
- [ ] 10 个 sql:* 指标计算器随 S3/S4 模块上线接入
