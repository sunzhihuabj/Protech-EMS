# 部署准备清单与运行手册（Runbook）

> 版本基线：V4.0 RPD（8 卷）；代码主分支 main；本文对应 20+ commit 批次（S0-S17 + 前端 W1-W6 + 本批）。
> 适用：V4-01 §9 里程碑的生产化阶段。轨道三剩余项（真实模型/钉钉/小程序）= 下表凭据就绪后按 §五 步骤接入，代码侧均已预留配置点，**无需再改代码**。

## 一、组件与端口

| 组件 | 端口 | 说明 |
|---|---|---|
| projlife-api（主应用） | 8080 | 模块化单体，全部业务接口 |
| projlife-ai-gateway | 8090 | AI 统一入口（mock/真实模型切换） |
| projlife-file-service | 8091 | 文件/水印（BR-07） |
| projlife-job | 8081 | 定时任务（ShedLock JDBC 行锁） |
| projlife-web | Nginx 443 | Vue 2.7 构建产物 dist/，`/api`→8080、`/fs`→8091 反代 |
| MySQL/MySQL 8.0 | 3306 | 生产走 Flyway 门禁（ADR-002） |
| Redis/memurai | 6379 | 会话/权限缓存（30min TTL）/令牌黑名单 |

## 二、凭据与资源清单（待用户提供）

| # | 项 | 用途 | 就绪判据 |
|---|---|---|---|
| 1 | DB 连接（MySQL 8.0 8.0 兼容） | 主数据 | 连通 + `flyway info` 正常 |
| 2 | Redis 地址+密码 | 缓存 | `PING` 通 |
| 3 | JWT_SECRET（≥32 随机） | 令牌签名 | 已生成并注入 |
| 4 | 模型端点 + Key + 模型名（OpenAI 兼容） | AI-001~016 真实推理 | `POST {endpoint}/chat/completions` 200；三者注入 `PROJLIFE_AI_MODEL_ENDPOINT/API_KEY/MODEL_NAME`（测试阶段已接入 NVIDIA integrate API + z-ai/glm-5.3-flash，实测 AI-002 抽取 6.4s、AI-001 风险分析 16~81s；生产轮换 Key 并切回内网端点对齐 BR-AI-02） |
| 5 | 钉钉企业内部应用 AppKey/AppSecret/agentId（+可选群机器人 webhook/secret） | 通知主通道 D-08（SPRINT-INT1 已实现：工作通知+机器人+待办，L1→L2 降级链） | 注入 `PROJLIFE_DINGTALK_*` → `GET /api/v1/integ/ops/dingtalk/health` available=true → `/dingtalk/test-notify` 测试送达 |
| 5b | 售后宝 appKey/appSecret + 固定公网 IP 白名单（Webhook 推送需企业版+） | 事件/工单同步（INT-SHB） | 注入 `PROJLIFE_SHB_*` → `/api/v1/integ/ops/shb/health` configured=true → `/shb/sync-events` 拉取成功落 int_sync_log |
| 5c | T+ 三件套 appKey/appSecret/私钥 + orgId + 账套号 | 存货/基础档案同步（INT-TPLUS，13.0 红线见 SPRINT-INT1） | 注入 `PROJLIFE_TPLUS_*` → `/api/v1/integ/ops/tplus/health` probe=UP → `/tplus/query` Inventory/Query 成功 |
| 6 | 企微凭据（备用） | 通知备通道 | 同上 |
| 7 | 微信小程序 AppID/Secret + 服务器域名备案 | 工人端 BR-08 | 小程序 code2session 通 |
| 8 | DMZ 抓取机 + 标讯域名清单合规审查 | FR-BID 抓取调度 | robots 审查记录归档 |
| 9 | TLS 证书 + 域名 | HTTPS | 证书链完整 |

## 三、构建与产物

```bash
# 后端（JDK 25 构建，字节码 11，信创兼容 ADR-001）
mvn clean package -DskipTests
# 产物：projlife-api/target/projlife-api-1.0.0-SNAPSHOT.jar（同 file-service/ai-gateway/job）

# 前端
cd projlife-web && npm ci && npm run build   # 产物 dist/
```

## 四、数据库迁移

- 生产（MySQL 8.0/MySQL 8）：Flyway 门禁自动执行 `db/migration/V1..V15`；首次对存量库先 `baseline`。
- 本机 MySQL 5.7 开发环境：Flyway 不支持 5.7，用 `scripts/ImportSql.java` 直导（A-10）。
- 迁移回滚：按 V 版本逆序执行回滚脚本（当前迁移均为幂等 DDL，备份先行）。

## 五、接入步骤（凭据就绪后）

1. **真实模型**：注入 `PROJLIFE_AI_MODEL_ENDPOINT + PROJLIFE_AI_API_KEY + PROJLIFE_AI_MODEL_NAME`（三者齐备即启用 OpenAI 兼容真实推理；`PROJLIFE_AI_DISABLE_THINKING=true` 默认关闭思维链）→ 重启 ai-gateway → `GET /ai/v1/health` 应报 `mode=LLM, status=UP` → 冒烟 `/ai/v1/call`（AI-001 风险分析）确认响应含 `isMock=false` + `modelVersion`；降级链保持不动（模型失联自动 DEGRADED 回落 mock，业务不阻塞，`ai_call_log.status=DEGRADED` 可查证）。可选 `PROJLIFE_AI_TIMEOUT_MS`（默认 90000）。
2. **钉钉（SPRINT-INT1 已实现）**：注入 `PROJLIFE_DINGTALK_ENABLED=true + APP_KEY/APP_SECRET/AGENT_ID`（最小凭据路径：仅配 `WEBHOOK + WEBHOOK_SECRET` 即可用群机器人通道）→ 重启主应用 → `GET /api/v1/integ/ops/dingtalk/health` 验证 available=true → `POST /api/v1/integ/ops/dingtalk/test-notify {"channel":"AUTO","receiverId":30}` 全链路测试（工作通知失败自动降级机器人）；工作通知 task_id 已落 int_sync_log.remote_id，假成功用 `GET /dingtalk/notify-result?taskId=` 二次回查。企微备用同法。
2b. **售后宝（SPRINT-INT1 已实现）**：售后宝PC端→系统设置→应用管理 创建 API 应用（IP 白名单填业务总台固定出口 IP）→ 注入 `PROJLIFE_SHB_ENABLED=true + APP_KEY/APP_SECRET + WEBHOOK_KEY` → 推送地址配 `https://<总台域名>/api/internal/shb/webhook?sharedKey=<KEY>` → `POST /api/v1/integ/ops/shb/sync-events {"hours":24}` 验证对账拉取。**纪律**：token 50 次/天（客户端已缓存+计数），健康检查勿用 token 端点探测。
2c. **捷信通T+（SPRINT-INT1 已实现，通道B直连）**：畅捷通开放平台入驻拿三件套 → T+ 系统内账套主管授权 → 注入 `PROJLIFE_TPLUS_ENABLED=true + APP_KEY/APP_SECRET/ORG_ID/USER_NAME/PASSWORD/ACC_NUM/PRIVATE_KEY` → `GET /api/v1/integ/ops/tplus/health` 验证 probe=UP → `POST /tplus/query {"service":"Inventory","method":"Query","args":{}}` 冒烟。**13.0 红线**：仅用官方文档标注 13.0 可用的接口；「服务名称不正确/令牌信息不正确」优先查补丁。
3. **定时任务**：注入 `JOB_ENABLED=true + JOB_GATEWAY + 系统账号` → 启动 projlife-job → 验证 ShedLock 行锁表与 02:00 红灯升级日志。
4. **小程序（BR-08 工人端）**：凭据就绪后按 03 册小程序切片开发（后端 `eng_worker`/`thd_worker_project`/水印接口已就绪）。
5. **DMZ 抓取**：部署机安装 crawler（调用 `/api/internal/bid/tasks` 拉模式，30s 轮询，mTLS 占位待证书）。

## 六、启动顺序与健康检查

```bash
1. MySQL/Redis 就绪 → 2. ai-gateway(8090) → 3. file-service(8091)
→ 4. projlife-api(8080) → 5. projlife-job(8081) → 6. Nginx(443, dist)
健康检查：/actuator/health 各组件 UP；登录 admin → 工作台渲染
```

## 七、回滚

- 应用：保留上一版本 jar/dist，回滚即替换重启（Flyway 版本表不回退，仅前滚）。
- 配置：全部经环境变量注入，回滚=还原 .env 重启。
- 数据：DDL 均为加列/建表，回滚不删列；重大变更前整库备份。

## 七.5 功能开关

| 开关 | 默认 | 说明 |
|---|---|---|
| `projlife.approval.enabled` | `false` | 合同评审走审批流引擎（两级链：工程主管→总经理）；`true` 时 submitReview 创建审批实例而非直达 REVIEWED |
| `projlife.notify.dingtalk.enabled` | `false` | 钉钉工作通知主通道（D-08） |
| `projlife.notify.wechat.enabled` | `false` | 企微备用通道 |
| `projlife.job.enabled` | `false` | job 进程定时任务总开关 |

> 生产建议：`projlife.approval.enabled=true` 启用审批流（验收 FR-SYS-14 P8 待办中心必须有数据）

## 八、安全基线（对齐 RPD 硬约束）

- [ ] 全链路 HTTPS；JWT_SECRET 轮换策略（季度）
- [ ] 禁公网 AI（BR-AI-02）：模型端点仅内网可达，verify-spec 第 5 点随 CI 执行
- [ ] 敏感数据脱敏展示（客户手机号/联系人）；水印下载（BR-07）已内建
- [ ] 账号：首登强制改密；连续失败锁定（LoginProtection）；施工账号仅小程序（BR-08）
- [ ] 审计日志落 sys_audit_log 并归档（menu:system:log 可查）
