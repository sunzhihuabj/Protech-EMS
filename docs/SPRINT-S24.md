# SPRINT S24 · 轨道三（生产集成装配与部署准备）

> 日期：2026-10-31 ｜ 状态：✅ 完成（凭据类接入点全部就绪，配置即生效）
> 依据：V4-01 §9 里程碑 + 16 册集成视图

## 一、交付

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S24-1 | 定时任务装配 | projlife-job：InternalJobInvoker（系统账号登录→调用 job 入口）+ 4 任务（02:00 红灯升级 UC-KB-03 / 02:30 档案逾期 / 03:00 授权到期 / 10min Andon 升级），ShedLock 多副本安全，默认 projlife.job.enabled=false | ✅ |
| S24-2 | 内部任务端点 | InternalJobsController POST /api/internal/jobs/thd/expire-scan + 白名单 /api/internal/jobs/**（mTLS 占位 A-36） | ✅ |
| S24-3 | 钉钉主通道 | DingtalkStubNotifyAdapter（独立组件，D-08 主/企微备）：projlife.notify.dingtalk.enabled/app-key/webhook 配置点，未启用自动降级站内（A-37） | ✅ |
| S24-4 | 生产配置模板 | application-prod.yml.example + deploy/.env.production.example（DB/Redis/JWT/模型端点/钉钉/job 全变量化，敏感项不入仓） | ✅ |
| S24-5 | 部署清单 | docs/DEPLOYMENT.md：组件端口/凭据清单（9 项）/构建/迁移/接入五步/启动顺序/回滚/安全基线 | ✅ |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | 上下文 103 用例全挂 NoUniqueBeanDefinitionException | NotifyChannelAdapter 接口要求单通道 Bean，钉钉桩实现该接口产生第二 Bean | 钉钉桩改独立组件（不占接口口径），回归恢复 103+4 全绿 |

## 三、验证

| 验证项 | 结果 |
|---|---|
| mvn test（全仓） | **103/103 + 网关 4/4** 全绿 |
| verify-spec / gen --check | PASS / UP-TO-DATE |
| job 进程编译 | mvn compile 通过（调度+锁骨架复用 S0 机制） |

## 四、凭据接入点（全部配置即生效，无需改代码）

1. 真实模型：PROJLIFE_AI_MODEL_ENDPOINT/KEY → ai-gateway 重启（降级链不动）
2. 钉钉：DINGTALK_ENABLED=true + APP_KEY/SECRET → 主应用重启
3. 定时任务：JOB_ENABLED=true + JOB_GATEWAY/系统账号 → 启动 job
4. 小程序 / DMZ 抓取：凭据+部署机就绪后按 DEPLOYMENT §五执行

## 五、汇报四件事

1. 建表：无新增。
2. 接口：新增 1 个内部任务端点 + 4 个调度任务装配。
3. 自测：103+4 全绿 + verify-spec 7/7。
4. 后续：等用户凭据（DEPLOYMENT §二）按 §五 逐步接入；开发态待办仅剩轨道二页面的深度联调打磨。
