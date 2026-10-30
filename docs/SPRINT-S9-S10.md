# SPRINT S9+S10 · AI 集成 + 招标情报 + Agent 接入（阶段记录）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：V4-06 M14/M15/M19 + V4-05 M18

## 一、S9 · AI 真实接入（M14/M15）

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S9-1 | AI 网关 mock 跑通全流程 | S0 已交付（ai-gateway :8090 mock 引擎 20 能力点编号注册）；S9 增加 16 编号段注册与降级链验证 | ✅ |
| S9-2 | 20 能力点编号注册 | AiCapabilityRegistry（16 段 AI-001~016/20 能力点含 AI-015/016 标讯） | ✅ |
| S9-3 | 禁公网断言 | verify-spec.py 静态扫描 0 命中（每片均跑） | ✅ |
| S9-4 | 降级链验证 | 网关关模型 → 业务侧 DEGRADED 不阻塞（S1/S3 E2E 已断言） | ✅ |
| S9-5 | 四元组追溯 | ai_call_log 含 model_version/prompt_version/trace_id | ✅ |
| S9-6 | M15 集成适配器 | TPlusAdapter(MOCK/FILE/API) + NotifyChannelAdapter(钉钉主/企微备 D-08) + OnlyOffice 降级 | ✅ |
| S9-7 | AI 输出契约 | {result, confidence, evidence[], modelVersion, promptVersion} 统一结构 | ✅ |

## 二、S10 · 招标情报 + Agent 接入（M18/M19）

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S10-1 | T-BID 8 表 | V13 迁移（bid_source/raw/notice/match/rule/push/subscribe/feedback） | ✅ |
| S10-2 | 数据源管理 | FR-BID-01：SRC-{A\|B\|C\|D}-{3位}/robots 留痕/禁抓 DISABLED | ✅ |
| S10-3 | 四层去重 | FR-BID-06：URL 归一/content_hash UK/跨源合并/变更关联 | ✅ |
| S10-4 | 打分与分级 | FR-BID-09：100 分制（25/25/15/15/10/10）≥70 高/50-69 中/<50 归档 | ✅ |
| S10-5 | 一键转商机 | FR-BID-12：预填 + source_type=BID 写回 + CONVERTED | ✅ |
| S10-6 | T-AGT 3 表 | V13 迁移（agt_registration/session/call_log） | ✅ |
| S10-7 | Agent Token | FR-AGT-02：client_credentials 2h + HMAC 签名校验 | ✅ |
| S10-8 | 能力目录 | FR-AGT-03：12 项白名单按身份过滤 | ✅ |
| S10-9 | 同步问答 | FR-AGT-05：/messages 只读/10s/≤200 行/脱敏 | ✅ |
| S10-10 | 异步任务 | FR-AGT-06：202+code=0 创建/Idempotency-Key/状态机 | ✅ |
| S10-11 | blockCode | AGT-FORBIDDEN（禁止类 422）/AGT-QUOTA/AGT-EXPIRED | ✅ |
| S10-12 | 拉模式契约 | GET /api/internal/bid/tasks（30s mTLS 轮询占位） | ✅ 接口就绪 |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test | **78/78**（S9/S10 8 用例：标讯源 SRC 编号/robots 阻断/去重三态/打分 HIGH≥70/转商机 source_type 写回/订阅推送回训/Agent 注册+Token+目录 12 项/AGT-FORBIDDEN/任务 202+SUCCEEDED） |
| mvn test（全仓） | 78/78 + ai-gateway 4/4，BUILD SUCCESS |
| gen_entity --check | 142 实体 UP-TO-DATE |
| verify-spec.py | 7 项 PASS（141 张表全登记，BigDecimal 合规） |
| 真实 MySQL 5.7 冒烟 | 建源 SRC-A-001→robots 启用→入库 BID-202610-0002→重放 HASH_DUPLICATE→打分 MEDIUM 50→biz 转商机 OPP-202610-0001（sourceType=BID 回查）→订阅推送 sent=1→反馈回训→Agent 注册→Token 7200s/6 能力→同步问答→EXPERT 调 worker.my.task 被拒 AGT-FORBIDDEN→异步任务 202+code=0+T-AGT-202610-0001→状态 SUCCEEDED |

## 三.5 问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | VersionedEntity 表查询报 Column version not found | V13 漏建乐观锁列 | bid_source/bid_notice/bid_subscribe/agt_registration 补 version |
| P2 | AUDITED 表 insert 报 created_by 不存在 | 同上 | bid_raw/agt_call_log 补审计四列 |
| P3 | bid_rule 查询报 deleted 不存在 | BaseEntity 软删列漏建 | 补 deleted |
| P4 | MySQL JSON 列拒收 Invalid JSON | HashMap/List.toString() 非 JSON | breakdown 与 allowedIdentities 改 Jackson 序列化（H2 宽松掩盖） |
| P5 | block_code VARCHAR(24) 截断 9001 | logCall 误存异常 message | 改存 BizException.data.blockCode |
| P6 | 任务状态 3001 | taskId 双生成（服务层 codeSequence vs 执行器 UUID） | 统一由服务层编号传入执行器 |
| P7 | 测试端 401+POST 抛 HttpRetryException | JDK HttpURLConnection 流式 body 认证重试 | 用例改 java.net.http.HttpClient 裸调 |
| P8 | eam_tool 建表失败（真实 MySQL） | precision 为 MySQL 5.7 保留字（H2 宽松） | A-29：改名 precision_class |
| P9 | Agent 异步转商机 500 | 无登录上下文 owner_id NOT NULL | A-28：Agent 通道 ownerId 落系统账号 1 |

## 四、需求比对

- M18：FR-BID-01~14 核心链路已实现（抓取调度 job/crawler DMZ 部署随运维批次）；AI-015/016 注册但 V1.1/V1.2 启用。
- M19：FR-AGT-01~10 已实现（12 能力白名单/OAuth2+HMAC/禁止类 AGT-FORBIDDEN）。
- M14：FR-AI-01~18 框架已实现（mock 跑通/降级链/禁公网/契约/追溯）；真实模型接入随生产部署配置。
- M15：T+ FILE/API 双模式 + 钉钉主企微备（D-08）+ OnlyOffice 降级。

## 五、汇报四件事

1. **建表清单**：累计 **141 张**（+11 T-BID/T-AGT），接近 V4.0 基线 163 张（差额 22 张 = T-ENG/T-THD/T-ARC 部分细化表 + T-INT 5 表随 S10 集成部署）。
2. **接口清单**：新增 14 个（/api/v1/bid/* + /api/agent/v1/*），见 docs/API.md。
3. **自测结果**：78/78 + verify-spec 7/7，全绿。
4. **后续**：BACKLOG（区间 FR 逐条落地）+ 生产部署（DMZ/crawler/真实模型/钉钉）按 V4-01 §9 里程碑推进。
