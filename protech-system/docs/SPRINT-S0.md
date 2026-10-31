# SPRINT S0 · 平台底座 + 主数据（M00 + M01）

> 切片 S0（06 册 §4.3）｜分支 `protech-v2`｜日期 2026-11-01
> DoD 口径：建档取码演示可跑（API 层）；Vitest 全绿；tsc 零错误。

## 交付内容

### Monorepo 脚手架（11 册 §三）
- npm workspaces：`apps/api`（NestJS 10）、`packages/shared-types`
- TypeScript 5.6 / SWC（emitDecoratorMetadata）/ Vitest 2
- 交付型 schema：`prisma/schema.prisma`（PostgreSQL 16）；本机开发/测试镜像：`schema.mysql.prisma`（sync 脚本生成，见环境适配）

### shared-types（前后端同源）
- E-01~E-20 枚举止典 + 角色码 + 状态机常量
- BR-10 金额引擎：wanToCents/yuanToCents/assertCents（BigInt 全程，浮点入参直接拒绝）
- 工作日计算（BR-02 建档时限用）、Jaro-Winkler 相似度（阈值 0.85）、名称归一化
- BR-01~BR-12 文案常量（blockCode 前后端同源）、编码规则总表（05 册 §四 13 条）、项目名称生成器

### M00 平台底座（14 条 FR 中的 R1 12 条）
| FR | 实现 | 测试 |
|---|---|---|
| FR-M00-001 | 部门树（16 行实测结构）+ 121 人种子 + 兼任多角色 + 停用吊销会话 | 34# |
| FR-M00-002 | 九角色 + 权限矩阵（菜单/按钮/数据三级码）+ 默认闭口守卫 | 4 条 |
| FR-M00-003 | R-PM 项目级授权 grant/revoke + 关闭 7 天自动回收（expiredAt） | 1 条 |
| FR-M00-004 | 登录（失败 5 次锁 15 分钟）+ JWT 30m + refresh 轮换会话 + 密码强度 | 6 条 |
| FR-M00-005 | 编码引擎（条件 UPDATE 递增并发安全、年度周期、模板变量、审计） | 2 条 |
| FR-M00-006 | 审计：Prisma middleware 自动记录 + 显式 AuditService + 查询端点 | 2 条 |
| FR-M00-007 | 站内通知中心（未读数/已读/全读） | 1 条 |
| FR-M00-009 | 配置中心（10 项制度默认值；改动留痕） | 1 条 |
| FR-M00-010 | 附件（磁盘驱动；50MB 上限；S3 驱动接口预留） | 手工 |
| FR-M00-011 | 数据字典（97 枚举项种子；停用不删） | 手工 |
| FR-M00-013 | /monitor/health + stats | 1 条 |

### M01 主数据（R1 15 条）
- 客户集团/主体（三级+类型多选+别名只增+三重查重 FR-M01-003）、风场库（一地一档）、
  物料（归一前置校验 FR-M01-006 + DQ-01 拦截 + 旧码映射解析）、供应商、
  项目档案 27 字段（名称生成器 4 进 3 出、三重查重、关键 12 项完整度、E-04 状态机守卫、
  pending_farm 两段式、产品子项、别名沉淀）、主数据变更审批（提交→R-CHAIN 审批→应用，FR-M01-011/012）、
  全局别名搜索（FR-M01-016）

### 硬闸门（Prisma middleware，绕不过去）
- **BR-09**：*Code 字段出现在 update → 拒绝
- **BR-11**：物理 delete/deleteMany → 拒绝（仅会话表白名单）
- **BR-10**：金额接口层 assertCents（浮点即 422 blockCode=BR-10）
- **DQ-04/BR-08**：引用字段传自由文本 → 403 blockCode=BR-08

## 环境适配（登记于 V2-ALIGNMENT §四，偏离说明）
1. 本机无 Docker/PG → **dev/test 用本机 MySQL 5.7**（root:root@3306，utf8mb4 库 protech_v2[_test]）；
   Prisma SQLite 镜像不可行的原因：BIGINT 主键非 rowid 别名，autoincrement 失效。生产仍为 PostgreSQL 16（schema 不变，仅 provider 与 Timestamptz 标注差异，由 `scripts/sync-dev-schema.mjs` 生成）。
2. SWC 替代 esbuild 做测试转换（NestJS 需要 emitDecoratorMetadata）。
3. 无库级外键（05 册规范）→ 所有关联查询应用层装配；**禁止** Prisma 嵌套 relation select。
4. axios 侧约定：所有 BigInt id 以字符串返回（TransformInterceptor 全局序列化）。

## 测试与质量
- shared-types：12 单元（金额/相似度/生成器/尾缀剥离）
- api：34 契约（认证锁登/RBAC/编码/查重/DQ-01/BR-08/09/10/11/状态机/R-PM 回收/审计/通知/监控）
- `tsc --noEmit` 两个包零错误；种子：16 部门/133 用户/13 编码规则/10 配置/97 枚举/14 集团

## 遗留与后续
- [ ] web 外壳（S0-9）：React18+Vite+antd5 登录/布局/主数据页
- [ ] FR-M00-008 钉钉 Webhook 推送（M15 事件总线就绪后接）
- [ ] FR-M00-012 操作手册（R2）、FR-M00-014 主题（R2）
- [ ] Playwright E2E（S7）

## S0-9 · Web 外壳（React 18 + Vite 5 + antd 5）

- 登录页（错误含 blockCode → BlockCodeAlert 统一红线展示，07 册 I-05）
- BasicLayout：8 项一级导航（07 册 §1.1）+ 通知铃 + 用户菜单；刷新后 /auth/me 恢复上下文
- /home 工作台：角色化引导（03 册三角色三套文案）+ 待办/通知 + 我的项目
- /project 项目档案 P1 列表族（搜索/状态筛选/分页/空态引导）+ 建档抽屉（名称生成器 4 进 3 出
  实时预览、编码建议、BR-08 下拉、BR-10 整数分提示、三重查重 force 二次确认）
- /project/:id P2 详情族：状态时间线 + 关键 12 项完整度 + 27 字段分组卡 + 产品子项
- /supply/material 物料 P1；未到切片路由用 Stub 页标注交付切片
- API client：响应包解包 + 401 refresh 轮换重试一次（旧审计 F5 项修复）
- 共享包 @protech/shared-types 以 ESM 构建输出（Node24 require(esm) 兼容 API CJS 消费）
- 端口：api 9090（8080 被旧 Java 服务占用）/ web dev 5173（proxy /api→9090）
- 浏览器冒烟：登录 → 项目建档 → 生成器出「三峡新能源-姚安大龙口-二期」（S-02 口径）→
  编码引擎发 PJ2026-0001 → 列表可见，全链路 PASS
