# SPRINT-INT2：钉钉通讯录同步（消灭 A-39 静态映射）

> 日期：2026-11-03 · 分支：`feature/dev`（projlife-dev）· 前置：SPRINT-INT1（钉钉通道已真实接入）
> 依据：调研①钉钉开放平台 §2.1 通讯录接口（新版 subDepts + 旧版 topapi v2/user/list）

## 一、目标与方案

| 项 | 方案 |
|---|---|
| 目标 | 钉钉 userid/unionId ↔ sys_user 自动绑定，替代 INT1 的 yml 静态映射（A-39），打通工作通知按接收人直发 + 待办 unionId 预留 |
| 数据 | 新表 `sys_dingtalk_binding`（V22）：dingtalk_userid 唯一 / union_id / name / dept_ids / sys_user_id / match_type(MANUAL/MOBILE/NAME/NONE) |
| 同步 | BFS 部门树（根=1，新版 REST GET /v1.0/departments/{id}/subDepts，Header 鉴权）→ 逐部门分页拉用户（旧版 topapi /topapi/v2/user/list，Query 鉴权，cursor 分页）→ 绑定 upsert |
| 匹配 | A-44：MOBILE（sys_user.phone 精确）→ NAME（realName 唯一才匹配）→ NONE；**MANUAL 人工绑定永不被同步覆盖** |
| 护栏 | 部门 ≤500 / 用户 ≤5000 / 树深 ≤10 / 每部门 ≤100 页，防异常数据拖垮限流 |
| 解析 | SPI `DingtalkUserResolver`（common）→ module-org 实现（查绑定表）；适配器解析顺序：yml 静态映射 → 绑定表 → 机器人兜底 |
| 端点 | POST /api/v1/integ/ops/dingtalk/sync-directory（手动同步）；GET/POST /bindings（查询/人工绑定）；全部 sys:config |
| 审计 | 按运行汇总落 int_sync_log（PULL，remoteId=dirsync），逐调用不落 |

## 二、实施记录

| 文件 | 内容 |
|---|---|
| db/migration/V22__dingtalk_binding.sql | 绑定表（已直导共享 MySQL 验证） |
| common/spi/DingtalkUserResolver.java | userid/unionId 解析 SPI |
| module-org entity/SysDingtalkBinding + mapper | 绑定表实体/Mapper |
| module-org sync/DingtalkUserResolverImpl | SPI 实现 |
| module-org sync/DingTalkDirectorySyncService | 同步服务（BFS+分页+匹配+护栏+审计） |
| module-org controller/DingtalkSyncController | 同步/绑定端点（与 IntegrationOpsController 共用前缀，路径不重叠） |
| module-integration DingTalkClient | +listSubDepartments（exchange+Header）/ +listDeptUsers（cursor 分页） |
| module-integration DingTalkNotifyAdapter | 解析顺序接入 resolver（静态映射→绑定表→机器人） |
| module-org pom | +module-integration 依赖（方向 org→integration，无环） |

## 三、验证结果

- **协议桩测试 3/3**（module-org，JDK HttpServer 仿真 + Mockito 内存 Mapper）：
  - fullSync：4 部门 BFS（含二级）/ 两页 cursor 分页（has_more 驱动）/ 跨部门去重 / MOBILE、NAME、NONE 三种匹配落库字段逐一断言 / 新版 Header + 旧版 Query 鉴权口径
  - manualKept：MANUAL 绑定在同步后保留 sys_user_id 与标记（updateById 而非 insert）
  - matchRules：手机号优先于姓名；重名不匹配
- **适配器优先级测试**（DingTalkClientTest 9/9）：静态映射 > 绑定表 > 机器人兜底三跳逐级断言
- **全量回归**：全反应堆 BUILD SUCCESS（103 E2E + 24 集成 + 3 同步 + 既有全量）
- **测试抓出真 Bug 1 个**：listSubDepartments 初版构造了 Header 却未随 GET 发出（getForObject 不带 HttpEntity）→ 改 exchange；**测试先行价值实证**

## 四、假设与遗留

- A-44：匹配顺序 MOBILE→NAME(唯一)→NONE，MANUAL 最高；重名不匹配记 NONE 供人工绑定
- A-45：subDepts 响应字段 dept_id/deptId 容错解析；user/list 响应结构以联调实测收紧
- 遗留：job 定时调度同步（每日一次）随部署批次接入 InternalJobInvoker；前端绑定管理页随运维页批次
