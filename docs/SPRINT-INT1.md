# SPRINT-INT1：外部系统接入批次（钉钉 / 售后宝 / 捷信通T+）

> 日期：2026-11-03 · 分支：`feature/dev`（projlife-dev 克隆仓库）· 输入：三份调研报告（2026-11-02，`D:\12、workbuddy\业务总台开发资料\`）
> 流程：分析→推论→规划→实施开发→验证测试→修改→再次验证→记录→递交（全自动闭环模式）

---

## 一、分析与验证（三份调研交叉验证）

### 1.1 数据源与身份确认

| 报告 | 对象 | 身份结论 | 可信度 |
|---|---|---|---|
| 报告①钉钉 | 钉钉开放平台（含 dws CLI） | 官方平台，双 API 体系（新版 api.dingtalk.com REST / 旧版 oapi.dingtalk.com topapi） | 高（全部标注【官方】，文档 2026-08/2026-07 更新） |
| 报告②售后宝 | 钉钉生态 ISV「售后宝」（众联成业，FW_GOODS-1000446990） | 四条集成通道：OpenAPI / Webhook 主动推送 / 集成中心 iPaaS / 钉钉平台能力 | 高，但工单模块逐字段、推送报文格式、错误码全表【未公开可查】（语雀附录需登录） |
| 报告③T+ | 畅捷通 T+ 13.000 专属云实例（hbtgl.a4.cjtmsp.com） | v2 OpenAPI 网关实测存活且强制鉴权（EXSM0001），走标准 OpenAPI，无需页面抓取 | 高（含 2026-11-02 实测证据） |

### 1.2 交叉验证：彼此一致的结论（可放心采纳）

1. **Token 纪律三家一致**：全部为「短时效 Token + 客户端缓存 + 提前刷新」——钉钉 7200s（重复获取自动续期但频繁调用限流）；售后宝 2h 且 **每应用每 24h 仅 50 次获取**（最严格）；T+ openToken 6 天 / 直连 token 一天一次。→ 推论：**三目标共用一套"Token 中心化缓存"组件模式**（各自实现、同一纪律：synchronized 双检 + 过期前 10 分钟刷新 + 获取次数纪律）。
2. **事件驱动 + 对账兜底双通道**：钉钉「300+ 事件订阅优先于轮询」、售后宝「Webhook 实时 + 定时对账（报告②§七.6）」、T+「订阅消息 + 每日列表对账（报告③§七.2）」。→ 推论：**本次全部按 Webhook/事件收口 + 增量拉取对账双通道设计**。
3. **失败可观测性**：钉钉工作通知「超限假成功（errcode=0 但用户收不到）须回查 getsendresult」；售后宝推送「失败预警但无重试承诺」；T+ 外部编码一次性。→ 推论：**所有出站调用持久化 remoteId/task_id 并支持回查，全部落 int_sync_log**（现有 T-INT 层表结构正合用）。
4. **降级不阻塞**：三家均为外部依赖，任一失联不得阻塞业务主流程——与本项目既有铁律一致（00 册 C-12 / AiGatewayClient 先例）。
5. **凭据最小化与密钥管理**：报告①§七.3 明确建议「各 SaaS 凭据统一纳入业务总台密钥管理」——与本项目「生产必须经环境变量注入，禁止入库入码」既有约定一致。

### 1.3 交叉验证：矛盾与张力点（裁决见 §二.3）

| # | 矛盾/张力 | 报告①钉钉 | 报告②售后宝 | 裁决 |
|---|---|---|---|---|
| C1 | 新旧 API 体系 Token 是否通用 | §六.1「新旧体系勿混用：新 token 调旧接口（或反之）会报错」 | —（不涉及） | **采纳"新版 accessToken 全端通用"**：报告①自己的示例（§5.1）即用同一 token 调旧版 asyncsend_v2，且其 §四.1 只给出新端点取 token 一种方式——若新旧不通用其最小调用链（§七.2）自相矛盾。「勿混用」采信为「域名与调用方式不得错配」（新接口=Header 头，旧 topapi=Query 参数）。工程上再加兜底：旧通道遇 401/errcode 认证失败时自动回退 gettoken 旧端点重取一次 |
| C2 | 售后宝网关域名 | — | §二「钉钉端 oapi.shb.ltd / 企微端 oapi.linker.ltd，两域名路径一致」 | **采纳 oapi.shb.ltd 为默认**并做成可配置（贵司为钉钉生态用户，从钉钉端开通是大概率；配置可切换兜底） |
| C3 | 售后宝"钉钉侧集成要不要自建" | §七.1 建议 CLI+事件订阅+REST 全套自建 | §六结论「企业侧无需自行开发钉钉侧集成，对接点选 OpenAPI+Webhook」 | **不矛盾，分层采纳**：售后宝的业务数据集成走其 OpenAPI/Webhook（报告②）；组织协同触达（通知/待办/审批）走钉钉开放平台自建（报告①）。两者对接的是不同系统，均需建设 |
| C4 | T+ 通道 A（开放平台）vs 通道 B（直连实例） | — | §三并列给出，§五落地路径偏开放平台 | **采纳通道 B 直连**：报告③§二实测实例域名 `/tplus/api/v2/` 网关已开启且 EXSM0001 在线；13.0 专属云形态正是通道 B 的适用场景；通道 A 需开放平台入驻+绑定云企业，前置流程更长。通道 A 留配置切换位（A-38） |
| C5 | T+ 版本能力边界 | — | §七.1「13.0 是最大约束，16.0/18.0+ 新接口不可按最新文档照抄」 | **采纳 13.0 红线**：实现中禁用 `type=Asyn` 异步、报表查询辅助等高版本特性；仅实现基础档案/存货/往来单位查询等 13.0 已确认接口；每个方法注释标注版本依据 |

### 1.4 与本项目的相关性评估

| 目标 | 相关性 | 判断依据 |
|---|---|---|
| 钉钉 | **P0 高** | ① DEPLOYMENT.md 凭据清单第 5 项「通知主通道 D-08」即钉钉；② 项目已有 `NotifyChannelAdapter` SPI + `DingtalkStubNotifyAdapter` 桩（A-37 预留位），本批次正是把桩换真；③ P8 待办中心/审批引擎的移动端触达依赖钉钉待办与工作通知；④ BR-08 工人端小程序的替代触达通道 |
| 售后宝 | **P1 中高** | ① M13 工程交付与第三方/售后服务域（eng 模块：售后服务、派工、售后 portal）需要工单级 SaaS 联动；② 事件→工单模型与本项目工单域同构，事件↔工单关联键（taskNo）可直接映射；③ 备件出入库与 M05 库存联动是报告②§八.4 点明的场景 |
| 捷信通T+ | **P1 中高** | ① M15 集成适配层（int_adapter/int_mapping/int_sync_log/int_outbox/int_reconcile_diff 五表 + IntegrationController）就是为此而建（FR-INT-01~08）；② M03 物料/往来单位主数据与 M05 库存的 ERP 源头同步；③ 实测网关在线，协议证据充分 |

**结论**：三份数据全部高相关，无一份需要排除；钉钉最接近可上线（凭据仅 AppKey/AppSecret），T+ 凭据链最长（开放平台入驻+三件套+orgId+账套授权+IP 白名单）。

---

## 二、接入方案规划

### 2.1 方案总览

| 项 | 钉钉 INT-DINGTALK | 售后宝 INT-SHB | 捷信通T+ INT-TPLUS |
|---|---|---|---|
| 优先级 | P0 | P1 | P2 |
| 接入点 | 通知主通道（替换 notify 桩）+ 待办预留 | 工单/事件同步（T-INT 适配器）+ Webhook 接收 | 主数据/存货查询（T-INT 适配器） |
| 代码位置 | `module-integration/notify` | `module-integration/shb` | `module-integration/tplus` |
| Token | POST /v1.0/oauth2/accessToken，7200s，提前 10min 刷新 | /service/auth/get_access_token，MD5(appSecret_timestamp)，2h + **50 次/天纪律** | GetRealNameTPlusToken（用户名密码）或 GetAnonymousTPlusToken（orgId），一天一次 |
| 出站 | 工作通知 asyncsend_v2（回查防假成功）+ 群机器人 HMAC 加签 + 待办 API | get_events / get_event_types / get_*_fields 增量拉取 | Authorization=Base64(JSON{appKey,authInfo(JWT-SHA256withRSA),orgId,access_token}) + _args 请求体 |
| 入站 | —（事件订阅需公网回调，本批次不做，A-41） | POST /api/internal/shb/webhook（PUBLIC_PATHS 放行，原始报文落 int_sync_log） | — |
| 健康检查 | 配置齐备性 + 可选真实探测 | 配置齐备性（**禁止**用 token 端点探测——50 次/天纪律） | GET 门户 URL 探测（有 HTTP 响应即 UP） |
| 凭据 | AppKey/AppSecret（或仅机器人 webhook+secret） | appKey/appSecret + 固定公网 IP 白名单 | appKey/appSecret/私钥文件/orgId/账套号 |

### 2.2 优先级排序依据

1. **钉钉 P0**：凭据最简、桩位已预留、通知是全系统刚需（投标提醒/审批待办/预警），且 webhook 通道仅需群机器人即可先跑通端到端。
2. **售后宝 P1**：业务价值高但有两项硬前置（企业版确认、固定公网 IP），凭据不可控因素多。
3. **T+ P2**：凭据链最长（开发者入驻→三件套→orgId→账套授权→白名单），13.0 版本红线需要逐接口核对补丁号，先建好协议层等凭据到位即插即用。

### 2.3 矛盾采纳汇总（详见 §1.3）

C1 采纳「新版 accessToken 全端通用 + 认证失败回退旧端点」；C2 采纳 oapi.shb.ltd 默认可切；C3 分层并行建设；C4 采纳通道 B 直连；C5 采纳 13.0 红线。

### 2.4 风险与应对

| 风险 | 目标 | 等级 | 应对 |
|---|---|---|---|
| 凭据未到位，无法真实联调 | 全部 | 高 | **协议桩测试**：JDK HttpServer 仿真实端点（鉴权/错误码/请求体结构逐项断言），协议正确性不依赖凭据；真实凭据到位后仅改配置即可切换（DEPLOYMENT.md 增加切换 Runbook） |
| 工作通知假成功 | 钉钉 | 高 | task_id 持久化 int_sync_log.remote_id + 发送后 getsendprogress 回查，FAILED/UNKNOWN 显式降级标记 |
| SHB token 50 次/天超限 | 售后宝 | 中 | 当日获取计数器（AtomicInteger 按天重置）+ 到 45 次告警日志；健康检查禁用 token 端点 |
| SHB 读接口 2 QPS | 售后宝 | 中 | 批量拉取分页间隔 ≥600ms（客户端节流） |
| Webhook 无签名可验（公开资料未含） | 售后宝 | 中 | 可选共享密钥 query 校验 + 原始报文全量落 int_sync_log 供人工对账（A-40） |
| 钉钉 userid 映射缺失（sys_user 无钉钉 ID 列） | 钉钉 | 中 | 本批次：yml 静态映射 user-mapping（测试期）+ OPS 测试发送端点直传 userid；生产批次上通讯录同步（A-39） |
| T+ 私钥文件格式差异（PEM/DER） | T+ | 中 | TplusJwtSigner 支持 PEM PKCS#8；签名失败显式报「私钥不可用」而非降级为未签名请求 |
| T+ 13.0 缺补丁导致「服务名称不正确」 | T+ | 中 | 错误码速查表内置于异常消息；逐接口版本标注 |
| 限流（钉钉机器人 20/min / 工作通知每人每天 500） | 钉钉 | 低 | 降级链：工作通知失败→机器人兜底→站内通知恒可达；全部失败不抛异常 |
| 共享 MySQL | 工程 | 低 | 本批次 V21 仅 INSERT 种子行（幂等），无 DDL，原项目数据不受影响 |

### 2.5 不做范围（明确排除）

- 钉钉事件订阅（Stream/HTTP 回调）——需公网回调地址与加解密 AES key，随生产部署批次（A-41）
- 钉钉通讯录同步与免登——随小程序凭据批次
- 售后宝工单**写入**（create_task）——字段映射需租户实际表单元数据（报告②§八.2 建议字段映射先行），先只读
- T+ 写入类（单据创建）——13.0 补丁号未核对前不冒险，只做查询类
- 集成中心（iPaaS）与凭证通——评估项非本批次

---

## 三、实施切片与提交清单

| 切片 | 内容 | 测试 | commit |
|---|---|---|---|
| INT1-0 | 分析与方案（本文档 §一/§二） | — | docs 提交 |
| INT1-1 | 公共 SPI `IntegrationAuditRecorder`（common）+ module-cost 落库实现（IntSyncRecorderImpl，异常吞掉不阻塞）+ IntSyncLog 实体扩展 payloadJson | 随各目标测试 | 钉钉提交 |
| INT1-2 钉钉 | DingTalkConfig / DingTalkTokenManager（7200s 缓存+提前 10min 刷新+双检）/ DingTalkClient（工作通知分批≤100+task_id 持久化+进度回查防假成功；机器人 HMAC 加签；新版待办 Header 鉴权；认证 errcode 清缓存重试）/ DingTalkNotifyAdapter（替换 Stub，L1→L2 降级链 + A-39 静态映射）/ OPS 四端点 | 桩测试 8/8 | 钉钉提交 |
| INT1-3 售后宝 | ShbConfig / ShbTokenManager（MD5 verifyCode + 2h TTL + 50 次/天计数 45 告警）/ ShbClient（get_events 分页归一 + 元数据 + 详情/日志 + 600ms 节流）/ ShbWebhookController（/api/internal/shb/webhook 白名单 + sharedKey + 原始落库 A-40）/ OPS health+sync-events | 桩测试 7/7 | 售后宝提交 |
| INT1-4 T+ | TplusConfig（13.0 红线）/ TplusJwtSigner（RS256 零依赖，PKCS#8，私钥缺失显式报错）/ TplusTokenManager（RealName/Anonymous + 20h 缓存 + A-43 容错）/ TplusClient（Authorization Base64 四字段 + EXSM0001 重试 + 缺补丁提示 + 实例探测）/ OPS health+query | 桩测试 6/6（含 JWT RSA 验签互证） | T+ 提交 |
| INT1-5 接线 | V21（payload_json 列 + 三适配器种子，已直导共享 MySQL 验证）/ application.yml integ 三段 / .env.example / deploy 示例 / DEPLOYMENT.md 凭据清单 5b/5c + Runbook 步骤 2/2b/2c / ASSUMPTIONS A-36~A-43 | 全量回归 | 接线提交 |

## 四、实施与验证记录（2026-11-03）

### 4.1 自测（协议桩，无需真实凭据）

- 桩：JDK HttpServer 仿真三目标官方端点（token/错误码/请求体捕获），断言粒度为请求结构级：
  - 钉钉：agent_id 必填、userid_list、markdown 结构、msg≤2048 字节截断（中文按 3 字节）、Query token（旧版）/Header token（新版）、HmacSHA256(timestamp+
+secret) 服务端重算可验、150 人自动分 2 批且 task_id 以 `|` 连接、token 缓存（两次远端调用仅一次取 token）、1301 限流提示。
  - 售后宝：verifyCode=md5(appSecret_timestamp) 32 位小写可复算、errorCode 包裹判定、分页归一（99→50）、50 次/天到顶返回失败结果（不抛异常）、2QPS 节流（连续读取 ≥600ms）、Webhook 原始报文落库 + sharedKey 校验 + 未启用忽略。
  - T+：Authorization 头 Base64 解码四字段齐全、authInfo JWT 三段且测试私钥对公钥验签通过（与 TplusJwtSigner 互证）、RealName 端点 IsFree=1 + _args 三字段、请求体无 type=Asyn（13.0 红线）、EXSM0001 清 token 重试成功、「服务名称不正确」自动附 13.0 提示、实例探测 404/200 均判 UP、私钥缺失显式 NO_PRIVATE_KEY 不降级。
- 结果：**module-integration 21/21 全绿**（8+7+6）。

### 4.2 自检要点

- 降级铁律全链路：所有客户端不向外抛网络异常（返回结果对象）；审计落库失败仅 warn；适配器全失败时返回 false 由调用方降级站内。
- 密钥零落码：所有凭据走环境变量（.gitignore 的 .env 注入），int_adapter.auth_config 不存密钥。
- 健康检查纪律：SHB 禁用 token 端点探测（50 次/天）；T+ 探测仿 AI 网关（有 HTTP 响应即 UP）。
- 交叉检查项：ShbClient.throttle 的 sleep 在 synchronized 块内（节流期间不并发读）；TplusJwtSigner 手拼 JSON 键值均为程序字面量（无注入面）。

### 4.3 与需求比对（调研报告逐条对照）

| 调研要求 | 落实 |
|---|---|
| ①Token 缓存+提前刷新（三家） | 三家 TokenManager 均 synchronized 双检 + 提前 10min（T+ 按官方建议 20h 一日一次） |
| ①工作通知假成功须回查 | task_id 持久化 + 发送即回查 + OPS 二次回查端点（getsendprogress/getsendresult） |
| ①机器人加签 | HmacSHA256 → Base64 → urlEncode，桩端重算验签通过 |
| ①task_id/instanceId 持久化 | int_sync_log.remote_id（SPI IntegrationAuditRecorder 落库） |
| ②token 50 次/天 + 2h | 当日计数（45 告警/50 拒绝）+ 2h 缓存；健康检查不消耗 |
| ②分页仅 5/10/20/50 | normalizePageSize 就近归一（桩断言 99→50） |
| ②读接口 2 QPS | 600ms 节流（桩断言实际间隔） |
| ②Webhook+定时对账双通道 | Webhook 接收器 + OPS sync-events 手动对账（按 updateTime 窗口分页拉全） |
| ②字段映射先行 | get_event_types/get_event_type_fields 已封装；写入类留待租户元数据确认（§2.5 排除） |
| ③Authorization 三件套结构 | Base64(JSON{appKey,authInfo,orgId,access_token}) 桩端解码断言 |
| ③Token 一天一次 | 20h 缓存 |
| ③13.0 版本红线 | 无 Asyn + 只做查询类 + 缺补丁提示内置异常消息 |
| ③订阅消息+对账双通道 | 本批次出站查询 + 对账；订阅消息（Webhook）需开放平台侧配置，列 A-38/后续批次 |

### 4.4 损益验证（无损验证）

- 全量回归：`mvn test` 全反应堆 25 模块 BUILD SUCCESS（103 E2E + 21 集成 + 既有全量）。
- 共享库无损：V21 仅新增列 + INSERT IGNORE 种子，int_adapter/int_sync_log 既有数据零影响（ImportSql 直导 OK:2 statements，回查 3 行+列存在）。
- 原项目仓库零改动：原仓库仍停在 protech-v2@080d75d 工作树干净；本批次全部提交在 projlife-dev@feature/dev。

### 4.5 交付物清单

- 代码：module-integration（notify/shb/tplus/ops 四包 12 类 + 3 测试类 21 用例）、module-cost（IntSyncRecorderImpl + IntSyncLog 扩展）、projlife-common（IntegrationAuditRecorder SPI）、projlife-security（PUBLIC_PATHS +1）。
- SQL：db/migration/V21__integ_targets.sql。
- 配置/文档：application.yml integ 三段、.env.example、deploy/.env.production.example、DEPLOYMENT.md（凭据 5/5b/5c + Runbook 2/2b/2c）、ASSUMPTIONS.md A-36~A-43、本 SPRINT 文档。

### 4.6 凭据到位后的启用步骤（Runbook 摘要）

1. 钉钉：注入 PROJLIFE_DINGTALK_* → 重启 → /api/v1/integ/ops/dingtalk/health → test-notify（AUTO 全链路含降级）。
2. 售后宝：PC 端建 API 应用+IP 白名单 → 注入 PROJLIFE_SHB_* → 推送地址配 /api/internal/shb/webhook?sharedKey= → /ops/shb/sync-events 对账冒烟。
3. T+：开放平台三件套 + 账套授权 → 注入 PROJLIFE_TPLUS_* → /ops/tplus/health probe=UP → /tplus/query Inventory/Query 冒烟。

