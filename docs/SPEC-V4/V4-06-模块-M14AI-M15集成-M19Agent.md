# V4-06 · 模块规格：M14 AI 能力 / M15 集成 / M19 第三方 Agent 接入

> 固定 8 段结构；通用规范见 V4-01。本卷覆盖全部"对外连接面"：模型、第三方系统、第三方 Agent。

---

# M14 AI 能力（原 D14）

## M14-1 定位与边界

| 项 | 内容 |
|---|---|
| 一句话 | 唯一模型出口（ai-gateway）+ 16 个接口编号 / 20 个能力点 + 质量验收 + 降级链 + 人工复核 |
| 铁律 | ①业务代码不直连模型；②代码无公网 AI SDK/URL；③无模型时可降级人工；④AI 结果必标 `ai_generated` + 四元组 |
| 上游 | 各业务模块（场景） |
| 下游 | 业务模块（结果落库） |
| 不做什么 | 不做模型训练；不做公网大模型 API；不做通用聊天 |
| 数量 | 18 FR（FR-AI-01—18）+ 3 表 + 20 能力点 |

## M14-2 能力清单（16 编号段 / 20 能力点）

| 段 | 编号 | 能力 | 落点 | 首年启用 |
|---|---|---|---|---|
| 数据推理 | AI-001 | 合同风险分析 | `ct_contract_risk` | ✔ |
| | AI-002 | 合同信息提取（12 字段） | `ct_contract` 提取确认页 | ✔ |
| | AI-003 | 合同变更对比 | `ct_contract_change` | — |
| | AI-004 | 安全库存建议 | `md_material.safety_*` | — |
| | AI-005 | 供应商推荐 | PO 选供应商 | — |
| | AI-006 | 物料模糊匹配 | 现场补货/搜索 | ✔ |
| | AI-007 | 文件版本变更描述 | `file_version.change_desc` | — |
| | AI-008 | 施工日志分析 | `eng_daily_log.ai_check_result` | — |
| 图像识别 | AI-101 | 来料外观检测 | `qms_inspection.ai_result_json` | — |
| | AI-102 | 施工质量检测 | `eng_quality_check` | ✔ |
| | AI-103 | 线缆连接检测 | 同上（CABLE） | — |
| | AI-104 | 设备安装检测 | 同上（INSTALL） | — |
| 扩展 | AI-009 | 技术条件分解 | `plm` 条目 | — |
| | AI-010 | 安装指导生成 | 工艺/安装文档草稿 | — |
| | AI-011 | FMEA 相似检索 | `qms_fmea` | — |
| | AI-012 | 8D 根因建议（须人工确认写 D4） | `qms_8d` | — |
| | AI-013 | 售后知识检索 | `eng_after_sales` | — |
| | AI-014 | 验收报告生成 | `eng_acceptance` | — |
| 标讯 | AI-015 | 标讯结构化解析 | `bid_notice`（M18） | ✔（V1.1） |
| | AI-016 | 标讯判读（摘要/初评） | `bid_match.breakdown` | —（V1.2） |

> 算术：8 数据 + 4 图像 + 6 扩展 + 2 标讯 = **20 能力点**；编号段 AI-001—016 = **16 段**（F-11 修正）。

## M14-3 功能清单（FR-AI）

| 功能号 | 功能 | 规则 | 优先级 |
|---|---|---|---|
| FR-AI-01 | 网关统一入口 | 唯一访问内网模型；业务侧只调网关 | Must |
| FR-AI-02 | 统一输出契约 | `{result, confidence, evidence[], modelVersion, promptVersion}` | Must |
| FR-AI-03 | 置信度分级 | ≥0.85 可采纳 / 0.60—0.85 黄标 / <0.60 灰显不可采纳 | Must |
| FR-AI-04 | 降级链 | 模型不可用 → 人工模式（表单可填，AI 区显示"待人工录入"），不阻塞 | Must |
| FR-AI-05 | 超时与重试 | 30s/20s；重试 1 次；超时即降级 | Must |
| FR-AI-06 | 结果标识 | `ai_generated=true` + platform + 置信度 | Must |
| FR-AI-07 | 人工复核留痕 | 修改写 `ai_feedback(ai_value→human_value)` | Must |
| FR-AI-08 | 四元组追溯 | 模型/提示/知识/规则版本 + traceId | Must |
| FR-AI-09 | 调用日志 | 每次入 `ai_call_log`（脱敏） | Must |
| FR-AI-10 | 健康检查 | `/ai/health`；不可用即降级 | Must |
| FR-AI-11 | mock 模式 | dev/test 可跑通全流程；生产禁 mock | Must |
| FR-AI-12 | 提示词契约 | 按能力给输入输出 schema，禁"帮我分析"类开放提示 | Must |
| FR-AI-13 | 质量评测 | 标注样本 ≥100/能力；关键字段准确率 ≥90% | Must |
| FR-AI-14 | 效果看板 | 调用量/降级率/采纳率/修正率 | Should |
| FR-AI-15 | 模型灰度 | 10% 流量；可回滚 | Should |
| FR-AI-16 | 禁公网断言 | 静态扫描 0 命中（CI） | Must |
| FR-AI-17 | 配额与限流 | 按模块/用户限流；防刷 | Should |
| FR-AI-18 | 模型切换 | 配置化切换模型版本，不改代码 | Should |

## M14-4 数据字段（T-AI 3）

`ai_call_log`（id, capability, input_digest, output_status, confidence, latency_ms, tokens, model_version, prompt_version, trace_id）、`ai_model_config`（capability, model, endpoint, enabled, weight）、`ai_feedback`（biz_type, biz_id, field, ai_value, human_value, operator）。

## M14-5 状态：调用 `PENDING → SUCCESS / DEGRADED / FAILED`；灰度 `OFF → CANARY(10%) → ON`。

## M14-6 权限：`ai:call` 按模块授权；`ai:config` R03；`ai:feedback:view` 产品负责人；结果可见性随业务数据范围。

## M14-7 异常：模型超时 → 降级（UC-AI-01）；低置信 <0.60 → 无采纳按钮（UC-AI-03）；返回非 JSON → 视为失败并降级；同文件重复调用 → 缓存 10 分钟。

## M14-8 接口（节选）

```
POST /ai/analyze-contract-risk   {file_id, contract_id} → {risks:[{type,level,description,suggestion}]}
POST /ai/extract-contract-info   {file_id, contract_id} → {fields:{k:{value,confidence}}, modelVersion}
POST /ai/parse-bid-notice        {raw_text, source_id, url} → {title, owner, deadline_at, …, evidence[]}
POST /ai/assess-bid-notice       {notice, capability_profile} → {summary, fit_hint, risks[]}
```
统一：30s 超时；失败返回 `{code:9001, data:{degraded:true, reason}}`，业务侧转人工。

## M14-9 验收：关模型跑通主链路（UC-AI-01）｜低置信不可采纳（03）｜复核留痕（UC-CT-11）｜禁公网 0 命中（UC-SYS-17）｜准确率达标（UAT-08/UAT-13）。

---

# M15 集成（原 D15）

## M15-1 定位与边界

| 项 | 内容 |
|---|---|
| 一句话 | 所有外部系统走适配器：先 MOCK/FILE 能跑，再 API 好用 |
| 上游 | 各模块业务动作 |
| 下游 | T+（财务）、钉钉（统一入口/通知）、OnlyOffice、微信、快递100、文件服务、Agent 平台 |
| 不做什么 | 不替代 T+ 财务核算；不做钉钉审批引擎（只做打通与回写） |
| 数量 | 8 FR + 5 表 |

## M15-2 集成清单

| # | 集成 | 方式 | 关键约束 |
|---|---|---|---|
| 1 | **T+** | FILE（默认）/ API | 库存权威（C-04）；订单导入、出入库推送、存货查询；每日对账 |
| 2 | **钉钉**（主） | 机器人 + 审批回调 | 统一入口（D-08）；审批状态回写；通知主通道 |
| 3 | 企业微信（备） | 应用消息 | 仅在钉钉不可用时启用 |
| 4 | 微信小程序 | `wx.login`/模板消息 | DMZ `/api/mp/*`；企业认证 |
| 5 | OnlyOffice | 私有化文档服务 | 回调签名；失败可下载编辑 |
| 6 | 快递100 | API（二期） | 未采购则手工录入 |
| 7 | 文件服务 | 进程内/独立 | 水印/PDF/照片 |
| 8 | Agent 平台 | REST + 回调 | 见 M19 |

## M15-3 功能（FR-INT）

| 功能号 | 功能 | 规则 |
|---|---|---|
| FR-INT-01 | 适配器框架 | `adapter_type` 可配；MOCK/FILE/API 三实现 |
| FR-INT-02 | T+ 订单导入 | 文件 500 行；干跑报告；幂等（PO 号） |
| FR-INT-03 | 出入库推送 | 事务→T+；失败队列重试；回写凭证号 |
| FR-INT-04 | 每日对账 | 23:00；差异入 `int_reconcile_diff` |
| FR-INT-05 | 钉钉审批打通 | 发起→回调→状态回写；超时提醒 |
| FR-INT-06 | 通知适配 | 模板映射；失败重试 3 次 |
| FR-INT-07 | 同步日志 | 每次同步入 `int_sync_log`（请求/响应摘要/状态） |
| FR-INT-08 | 降级矩阵 | 任一依赖断 → 主链路可跑（见 V4-07 §4.4） |

## M15-4 字段（T-INT 5）：`int_adapter`（类型/配置/状态）、`int_sync_log`、`int_mapping`（字段映射）、`int_outbox`（待发队列）、`int_reconcile_diff`（差异/处理）。

## M15-5 状态：`PENDING → SENDING → SUCCESS / FAILED → RETRY → EXCEPTION`。

## M15-6 权限：`int:config` R03；`int:log:view` R03/R15；T+ 凭证 `int:tplus:run` R15 授权。

## M15-7 异常：T+ 无接口 → FILE（UAT-11）；导入重复 → 幂等；钉钉回调丢失 → 主动查询补偿；OnlyOffice 挂 → 下载/上传降级。

## M15-8 验收：全 MOCK 跑 S1（UC-INT-01）｜500 行 3 错报告（02）｜重复导入不重（03）｜T+ 挂不阻塞（UC-INV-03）｜对账定位（UC-INV-14）。

---

# M19 第三方 Agent 接入（原 D19）

## M19-1 定位与边界

| 项 | 内容 |
|---|---|
| 一句话 | 统一接口让 WorkBuddy/Trae Work/豆包工作/Zcode/扣子等以**专家/员工/助理**三身份接入，权限永远服务端判定 |
| 铁律 | ①Agent 无匿名调用；②有效权限 = min(身份基线, 被代理人权限, 能力白名单)；③输出标 `ai_generated`；④9 卡点与 BR-06/07 对 Agent 同样生效 |
| 上游 | M00（RBAC/审计）、M14（模型） |
| 下游 | 各业务模块（受控读写） |
| 不做什么 | 不允许审批/资金/主数据写/删除；不做 Agent 自主长任务编排 |
| 数量 | 10 FR + 3 表 + 8 接口 + 12 能力 |

## M19-2 三身份模型

| 身份 | 映射 | 数据范围 | 读 | 写 | 确认 |
|---|---|---|---|---|---|
| EXPERT 专家 | 虚拟角色 `R-AGT-E` | ALL | ✔ 查询/分析 | ✘ | — |
| WORKER 员工 | 影子角色 = 被代理角色 ∩ 白名单 | = 被代理人 | ✔ | ✔ 白名单动作 | **24h 内被代理人确认**，超时失效 |
| ASSISTANT 助理 | 绑定个人只读子集 | **SELF** | ✔ | 仅个人任务/提醒 | 无需 |

## M19-3 功能（FR-AGT）

| 功能号 | 功能 | 规则 | 优先级 |
|---|---|---|---|
| FR-AGT-01 | 应用注册 | `client_id/secret(AES)`、平台公钥、IP 白名单、配额、允许身份 | Must |
| FR-AGT-02 | Token 与签名 | client_credentials 2h；HMAC(ts+nonce+body)；偏移 >300s 拒绝；nonce 去重 10 分钟 | Must |
| FR-AGT-03 | 能力目录 | `/capabilities` 动态（DB + JSON Schema）；含身份要求与确认标记 | Must |
| FR-AGT-04 | 身份映射 | 三身份按上表；WORKER 强制 `on_behalf_of` + 用户级授权记录 | Must |
| FR-AGT-05 | 同步问答 | `/messages` 只读；10s 超时；≤200 行；脱敏 | Must |
| FR-AGT-06 | 异步任务 | 创建/查询/回调；`Idempotency-Key`；状态机 | Must |
| FR-AGT-07 | 写操作确认 | 确认时二次过 RBAC + BR；失败退草稿并回调 | Must |
| FR-AGT-08 | 调用日志 | 每次入 `agt_call_log`；422 拦截统计 | Must |
| FR-AGT-09 | 配额 | 平台 5 QPS / 日 1 万 / 月 token；429 带剩余 | Must |
| FR-AGT-10 | 管理看板 | 按平台/身份/能力：调用量、成功率、拦截 Top、确认率 | Should |

## M19-4 能力白名单（12 项）

| 能力 | 类别 | 身份 |
|---|---|---|
| `query.project.summary` / `query.order.status` / `query.bid.notice.search` | 查询 | 全部 |
| `query.inventory.position` / `query.kb.metric` | 查询 | EXPERT/WORKER |
| `analyze.contract.risk` / `analyze.dashboard.explain` | 分析 | 全部 |
| `opportunity.draft.create` / `kb.card.draft.create` / `followup.record.append` | 草稿 | WORKER（需确认） |
| `notify.person.remind` | 通知 | WORKER/ASSISTANT |
| `task.my.summary` | 查询 | ASSISTANT |

**禁止类**（一律 422 `AGT-FORBIDDEN`）：审批、开票/收款确认、主数据建档/合并、删除/停用、价格明文、原文件下载、绕过 BR-01—09。

## M19-5 数据字段（T-AGT 3）

`agt_registration`（`client_id` UK, platform, public_key, ip_whitelist JSON, allowed_identities JSON, quota_*, secret_cipher, status）、`agt_session`（session_ref UK, identity_type, on_behalf_of, granted_role_code, data_scope, status）、`agt_call_log`（registration_id, session_id, capability, task_id, request_digest, response_status, block_code, latency_ms, trace_id, tokens_used）。

## M19-6 状态机

```
任务：CREATED → ACCEPTED → RUNNING → SUCCEEDED / FAILED / TIMEOUT
      SUCCEEDED(需确认) → CONFIRMED（落业务库）/ REJECTED（留痕回训）
      旁支：CANCELLED（被代理人撤销 / 24h 超时自动）
会话：ACTIVE → CLOSED / REVOKED（授权撤销立即失效）
```

## M19-7 接口（8 个）

| # | 接口 | 说明 |
|---|---|---|
| 1 | `POST /api/agent/v1/token` | client_credentials → access(2h)/refresh(24h) |
| 2 | `GET /api/agent/v1/capabilities` | 能力目录（按身份过滤） |
| 3 | `POST /api/agent/v1/tasks` | 创建任务（**202 + code=0**，F-19） |
| 4 | `GET /api/agent/v1/tasks/{id}` | 状态与结果（含 evidence[]） |
| 5 | `POST /api/agent/v1/messages` | 同步问答（只读，10s） |
| 6 | `POST /api/agent/v1/callbacks/{platform}/{event}` | 我方→平台回调（签名） |
| 7 | `GET /api/agent/v1/notifications` | 拉取模式（回调不可达降级） |
| 8 | `POST /api/agent/v1/feedback` | 采纳/拒绝反馈（回训） |

**统一信封**：`{protocol:"protech-agent-1.0", agent{platform,agent_id,identity_type}, on_behalf_of?, capability, payload, deadline?, callback_url?, idempotency_key?}`。

## M19-8 异常与验收

| 场景 | 行为 | 用例 |
|---|---|---|
| EXPERT 调草稿能力 | 422 `AGT-FORBIDDEN`，零写入 | UC-AGT-02 |
| WORKER 24h 未确认 | 自动 CANCELLED + 回调失效 | UC-AGT-03 |
| 确认时权限已回收 | 真实写 403，退草稿 | UC-AGT-04 |
| R11 范围 ASSISTANT 查他部门 | 403，名称不可见 | UC-AGT-05 |
| Agent 触发缺编码合同动作 | 422 + `BR-01` | UC-AGT-06 |
| 幂等键重复 | 返回首次 task_id | UC-AGT-07 |
| 月 token 耗尽 | 429 + 配额；查询类不受影响 | UC-AGT-08 |
| 平台清单未定 | 首期仅 WorkBuddy + EXPERT 只读（Q-29） | — |

**能力目录/回调节奏/密钥轮换**：`agt_capability:manage` 由 R03 + 产品会签；密钥 90 天轮换提醒；泄露一键吊销。

**声明：本方案为产品设计参考，具体实施请结合公司合规与法务要求确认。**
