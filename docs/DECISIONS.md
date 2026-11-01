# 架构决策记录（ADR）

> 格式：决策 / 背景 / 备选 / 后果 / 回滚。追加式，只增不改。

## ADR-001 编译目标 Java 11，构建与运行在 JDK 25 上验证
- **决策**：`maven.compiler.release=11`，所有模块按 Java 11 语法（不用 records/text blocks/sealed）。
- **背景**：规格锁定 OpenJDK 11（信创兼容，16 册 §2.1）；本机仅有 Temurin 25.0.3。
- **后果**：产物字节码 55，可部署到生产 JDK 11；本地验证 JVM 为 25。Spring Boot 2.7.18 官方支持至 JDK 21，JDK 25 属超规运行，仅用于开发验证。若出现反射/字节码增强类不兼容，优先缩小 Spring 上下文测试范围或换用纯 JUnit 测试，不升级 Spring 版本。
- **回滚**：无需回滚——release=11 不受验证 JDK 影响。

## ADR-002 集成测试使用 H2（MySQL 模式），生产仍为 GreatDB
- **背景**：16 册允许"H2 或 Testcontainers"；本机无 Docker；本机 MySQL 服务端实测为 **5.7.19**（Flyway 8.5 社区版不支持，CLI 9.6 亦缺 native_password 插件）。
- **决策**：单测/仓储层测试用 H2 `MODE=MySQL` 走 Flyway 门禁；本机 MySQL 5.7 用 `scripts/ImportSql.java` 直导三个迁移脚本做真实库验证（已通过，证明 DDL 为 5.7/8.0 兼容子集）；生产 GreatDB(8.0 兼容) 由 Flyway 正常迁移。禁止使用 MySQL 8.0.30+ 专属语法，保证 GreatDB 兼容。
- **回滚**：有 Docker 环境后切 Testcontainers MySQL 8。

## ADR-003 RabbitMQ 组件条件化，S0 默认关闭
- **背景**：本机未安装 RabbitMQ；规格规定 MQ 只用于异步任务与事件，不在业务主链路（16 册 §2.1）。
- **决策**：与 MQ 交互的组件（事件发布/消费）统一放 `mq` 包并 `@ConditionalOnProperty("projlife.mq.enabled")`，默认 false。生产配置开启。
- **回滚**：安装 RabbitMQ 后打开开关即可，代码不变。

## ADR-004 12 套主题色值以 tokens.reference.json 为唯一色源，采用预编译主题包
- **背景**：03 册 §6A.3 明确 Element UI 2.15 必须"构建期预编译 12 套主题包、运行时按 theme_code 换 link"，禁止运行时 CSS 变量全量替换 hack；仓库另有 `项目主题风格版本/theme-engine`（面向 Element Plus/Vue3 的运行时方案，与本栈不兼容）。
- **决策**：Vue2 前端遵循 03 册 §6A 路线：构建脚本从 `docs/tokens.reference.json`（复制自 `项目主题风格版本/tokens.json`）读取 12 套 `p500/p600/p800` 主色阶，生成 12 份 `element-variables.tX.scss`（`$--color-primary` 等 SCSS 变量 + 项目 override），用 sass@1.32.13 编译出 12 份完整 CSS；运行时按用户 `sys_user.theme_code` 与系统默认 `sys_config:app.theme.default` 动态切换 `<link>`。theme-engine 目录仅作为设计规范与色源参考，不直接引入。
- **回滚**：如未来迁 Vue3 + Element Plus，可直接复用 theme-engine 运行时方案，色源不变。

## ADR-005 本地缓存/防锁定抽象 KeyValStore（Redis + 内存双实现）
- **背景**：CI 环境无 Redis；登录失败锁定、权限码缓存需要 KV 存储。
- **决策**：定义 `KeyValStore` 接口；`RedisKeyValStore`（Lettuce，默认）与 `InMemoryKeyValStore`（`projlife.redis.enabled=false` 时兜底）。业务代码只依赖接口。
- **后果**：功能等价，测试零外部依赖；生产 Redis 的 TTL/原子性由 Redis 实现保证。

## ADR-006 仓库位置：工作区 `projlife/` 子目录
- **背景**：需求文档目录 `ProjLife-企业级管理平台/` 含中文路径。
- **决策**：代码仓库建于 `ProjLife-企业级管理平台/projlife/`，规格包复制为 `docs/SPEC/`（只读，21 册）。Windows/中文路径下 Maven 与 Vite 构建已验证可行；若后续工具链出现路径编码问题，评估迁至 ASCII 路径并记录。
| ADR-007 | 2026-11-01 | 数据库选型：**MySQL 8.0**（非 GreatDB/非 PostgreSQL）。用户裁决：本项目无国产化要求，追求高效/稳定/性能。MySQL 8.0 与现有 MyBatis-Plus/Flyway/HikariCP 全兼容，代码零迁移；InnoDB 在 165 表 + 292 端点规模下性能充分；8.0 相比 5.7 新增 CTE/窗口函数/JSON 改进/InnoDB 元数据锁优化 | V4-01 §2.3 修订 |
