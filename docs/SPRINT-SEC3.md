# SPRINT-SEC3：安全专项（审核 P0/P1 最后一块收口）

> 日期：2026-11-05 ｜ 依据：2026-11-03 严格审核 P0/P1 清单 ｜ 目标：JWT typ 校验（refresh 令牌可当访问令牌用）、refresh rotation、SK- 手工取号收敛引擎、按 ID 直查数据范围校验

## 一、总述

审核四项安全收口 + 一个顺手修复的死代码缺陷：

1. **JWT typ 校验（审核 P0）**：`JwtAuthFilter` 只验签名与黑名单，不验令牌类型——**refresh 令牌（7 天有效）可以直接当 access 令牌调用全部业务端点**，登出/改密仅拉黑当场令牌后，攻击者仍可持 refresh 令牌长驱直入
2. **refresh rotation（审核 P1）**：`AuthService.refresh` 校验通过后原样返回旧 refresh 令牌——令牌 7 天内可无限复用，被盗后无"一次性"止损窗口
3. **SK- 手工取号（审核 P0 取号绕引擎）**：`ContractFinanceService.applyReceipt` 用 `selectCount(likeRight "SK-yyyyMMdd")+1` 推号——并发下两请求同号（唯一键兜底炸 500），且绕过取号引擎（02 册 §4.17 单一入口）
4. **按 ID 直查数据范围（审核 P0）**：列表端点有数据范围过滤，但 `GET /contract/{id}`、`GET /project/{id}` 直调 `mustGet(id)`——SELF 档商务看全公司合同（含金额）、DEPT 档跨部门看单据
5. **顺手修复（R18 引入的死逻辑）**：`remindOverduePlans` 把 `created_by`（工号 E0009）当 userId 转 `Long.valueOf` **恒抛 NumberFormatException 被 catch 吞掉**——逾期收款提醒 job 上线起从未发出过任何通知

## 二、修复明细

### 1. JwtAuthFilter：typ 校验 + refresh 端点豁免（projlife-security）

- 新增 `shouldNotFilter`：仅 `/api/v1/auth/refresh` 路径跳过本过滤器（该端点独占 refresh 令牌，登录无令牌、其余端点一律需 access）
- `parse` + 黑名单校验后追加：`!JwtUtil.TYPE_ACCESS.equals(claims.get("typ"))` → 401「令牌类型不正确（应为访问令牌）」
- 兼容性：前端 request.js 刷新链路只走 `/auth/refresh`（豁免路径），无破坏面

### 2. AuthService.refresh：rotation（module-org)

- 旧 refresh 令牌 `blacklist.revoke(claims)`（按 jti 入黑名单，TTL=剩余有效期）
- 签发全新 refresh 令牌返回（一次性语义）；运行态实测旧令牌重放 401「刷新令牌已失效」，新令牌可继续轮换
- 前端 `request.js:72-73` 已在刷新响应中滚动更新 `localStorage.pl_refresh_token`，天然兼容

### 3. 取号收敛 + 逾期提醒修复（module-contract / module-org）

- `CodeTypes.receipt(date)`：`SK-{yyyyMMdd}-{4位}`，scope=`SK:{yyyyMMdd}`（新方法不影响 verify-spec 第 3 项域名级门禁）
- `applyReceipt` 改 `codeSequenceService.next(CodeTypes.receipt(...))`（并发安全，`INSERT ... ON DUPLICATE KEY UPDATE`）
- `NotificationService.sendToUserCode(userCode, ...)`：按工号解析 userId 后发送（工号不存在/system 静默跳过）——created_by 存工号（审计填充器 `MybatisPlusConfig.currentUserCode`），提醒类 job 的正确入口
- `remindOverduePlans` 改走 `sendToUserCode`，死逻辑复活

### 4. V29 迁移：SK- 历史号回填序列（db/migration/V29__sk_receipt_sequence_align.sql）

- 历史手工号（SK-yyyyMMdd-nnnn）按日期分组预占 `sys_code_sequence`（同 V28 教训：**占号必须同步序列行**，否则引擎从 1 起发号撞已发号）
- 两条语句均幂等：INSERT 走 `NOT EXISTS`；UPDATE 兜底校正 `current_value < 历史最大` 的行
- 不用 `CAST AS INT`（MySQL 8 不支持该目标类型）——`%04d` 定长流水字符串字典序=数值序，`MAX(字符串)` 直接赋 BIGINT 列，H2 MODE=MySQL 与 MySQL 8 均兼容

### 5. DataScopeGuard：按 ID 直查数据范围（projlife-security 新类）

- `checkByOwner(ownerUserCode)`：按单据创建人（created_by 工号）校验当前登录者数据范围档位（E-35）：
  - **ALL**：放行
  - **SELF**：owner == 本人工号
  - **DEPT / DEPT_SUB**：owner 部门 == 本人部门（经 `UserDetailsProvider.loadByUserCode` 查创建人部门；DEPT_SUB 暂按本部门，部门树联动待部门查询能力开放后补）
  - **PROJECT**：暂放行（prj_member 暂无数据，按 owner 部门比对反而破坏 R14 工程跨项目协作，备案）
  - owner 为 `system` / 工号查无此人：放行（系统动作数据，备案）
- 越界抛 `BizException.forbidden`（403/2002）
- 落地：`ContractService.detail` / `ProjectService.detail`（`GET /contract/{id}` 的 previewNo/completeness 等经 detail 的端点一并收口）
- `UserDetailsProvider` 接口新增 `loadByUserCode`，`OrgUserDetailsService` 实现（按 userCode 查 sys_user 装配 LoginUser）
- 设计约束：不控制字段脱敏（BR-06 由各端点独立判断）；种子演示链路验证 boss@demo（R05 DEPT 部门 4）审 biz@demo（R04 SELF 部门 4）合同不受影响

## 三、测试（S31Security3Test，4 用例）

| 用例 | 验证 |
|---|---|
| refreshTokenCannotBeUsedAsAccess | refresh 令牌调 /auth/me → 2001；对照 access 令牌 → 0 |
| refreshRotation | 首刷 0 + 新旧 refresh 不同 + 新 access 可用；旧 refresh 重放 → 2001；新 refresh 续轮 → 0 |
| contractDetailDataScopePositive | biz@demo 建合同：owner（SELF）查 0；同部门 boss@demo（DEPT）查 0 |
| dataScopeGuardUnit | 进程内直测 Guard：SELF 跨 owner 拒/本人过；DEPT 跨部门拒/同部门过；PROJECT/ALL/system 过 |

- 测试基建注意：TestRestTemplate（JDK HttpURLConnection）对 **4xx + POST** 有 `HttpRetryException`（streaming mode 认证重试）怪癖拿不到真实响应体——`post()` 统一改 JDK HttpClient 直发；实测服务端对合法 refresh 返回 200/code=0，干扰纯属客户端
- 种子账号权限事实记录：R14 无 contract:view、R03/R15 也未授权 46——PROJECT/ALL 档 HTTP 级用例会被 RequirePermission 先拦（2002），与本守卫无关，故进程内直测覆盖

## 四、验证结果

- 全量回归：150 用例（146 存量 + S31 新增 4）0 失败
- verify-spec：7/7 PASS（CodeTypes 新增方法不破坏第 3 项域名级门禁）
- 运行态抽查（h2 jar，dev+CONSOLE）：登录 0 → refresh 当 access 用 401 → 首刷 0（新 refresh≠旧）→ 新 access 可用 → 旧 refresh 重放 401「刷新令牌已失效」→ 新 refresh 续轮 0
- 排障记录：PowerShell 直发 curl JSON 双引号被剥离导致 `{username:...}` 解析 9001，系脚本转义问题非服务缺陷（改用 ps1 脚本文件验证）

## 五、备案（后续批次）

1. PROJECT 档放行：prj_member 成员表有数据后改为"项目成员可见"
2. DEPT_SUB 档：接入部门树查询后扩展为"本部门及下级"
3. 其他按 ID 直查端点（opportunity/farm/mdm project 等 10 处）：本批先收口审核点名的 contract/project 重灾区，其余按同样模式分批套用 DataScopeGuard
