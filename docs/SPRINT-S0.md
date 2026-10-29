# SPRINT S0 · 平台底座（阶段记录）

> 日期：2026-10-29 ~ 10-30 ｜ 状态：✅ 完成并通过全部验证门
> 记录格式按用户开发流程要求：工作内容 / 技术选型依据 / 问题与解决 / 验证结论 / 需求比对。

## 一、具体工作内容（对照 16 册 §五 S0 任务表）

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S0-1 | 仓库与 Maven 多模块 | 7 个顶层模块（common/security/modules/api/job/ai-gateway/file-service），Git 管理，每阶段一 commit | ✅ |
| S0-2 | Flyway 建表 + 枚举字典 | V1（T-SYS 14 + kb_board + T-AI 2）/ V2（部门/角色/权限/用户/九板/参数）/ V3（E-01~E-42 全部枚举） | ✅ |
| S0-3 | 认证 | 登录/登出/刷新/改密/首登强改/失败 5 次锁 15 分钟/JWT 双令牌/黑名单 | ✅ |
| S0-4 | RBAC | 18 角色种子 + @RequirePermission AOP + 403 带 missingPerm | ✅ |
| S0-5 | 数据范围拦截器 | DataPermissionInterceptor 注解驱动，SELF/DEPT/DEPT_SUB/PROJECT/ALL 五档 SQL 改写，测试验证 DEPT/DEPT_SUB | ✅ |
| S0-6 | 审计切面 | @Audited 注解 → sys_operation_log（同步写，操作人=工号，含 IP/traceId） | ✅ |
| S0-7 | 编码引擎 | 18 类格式 CodeTypes + LAST_INSERT_ID 原子取号，50 并发无重复 | ✅ |
| S0-8 | 字典管理 | 类型/项 CRUD；系统内置枚举删除被拒（422 + SYS-DICT-FROZEN） | ✅ |
| S0-9 | 文件上传/下载/水印 | MIME 魔数白名单/SHA256；PDF→W2 水印（CJK 字体可配）；图片→W3 合成且原图删除；Office 拒绝直下 | ✅ |
| S0-10 | 导入框架 | 两段式（dryRun 干跑→错误报告→0 错误才事务提交），批次留痕 sys_import_batch | ✅ |
| S0-11 | 导出 | EasyExcel + 脱敏（手机号掩码）+ 审计留痕 | ✅ |
| S0-12 | 通知中心 | 站内必达 + 微信渠道适配器接口（stub 降级）+ job 提醒骨架（ShedLock） | ✅ |
| S0-13 | AI 网关 | 独立进程 :8090；mock 引擎（AI-001 三条红黄绿/AI-002 12 要素）+ 降级链 + ai_call_log | ✅ |
| S0-14 | 主布局 | 菜单按权限渲染（R04 登录仅见 2 菜单）、v-permission、空状态、W1 页面水印（10 秒刷新） | ✅ |
| S0-15 | 种子数据 | 14 部门 + 技术中心两组、18 角色、9 板、审批模板（sys_config）、42 组字典、8 个 demo 用户 | ✅ |
| S0-16 | 部署脚本 | docker-compose.yml（生产）、scripts/ImportSql.java（本地直导）、备份脚本随 S8 运维批次补全 | ✅（部分顺延） |
| S0-17 | 12 套主题 | 构建期预编译（tokens.json → sass@1.32.13 → 12 份 CSS）+ 运行时 link 切换 + sys_user.theme_code 持久化 + sys_config 系统默认 | ✅ |

## 二、技术选型依据（关键决策）

| 决策 | 依据 |
|---|---|
| 编译 `--release 11`，构建机 JDK 25 | 00 册 C-01 锁 Java 11（信创）；本机无 11。产物字节码 55 可部署 JDK 11（ADR-001） |
| 测试 H2(MySQL 模式)，本地 MySQL 5.7.19 直导验证 | 16 册允许 H2/Testcontainers；无 Docker；Flyway 8.5 社区版不支持 5.7 → 脚本改为直导（生产 GreatDB 8.0 兼容走 Flyway 门禁不变，ADR-002/A-10） |
| KeyValStore 接口双实现（Redis/内存） | Redis 做登录锁定/黑名单/权限缓存；CI 无 Redis 时内存兜底（ADR-005） |
| MQ 组件条件装配默认关 | 本机无 RabbitMQ；规格规定 MQ 不在业务主链路（ADR-003） |
| 主题=预编译 12 包，非 CSS 变量 hack | 03 册 §6A.3 明文规定；色源 tokens.reference.json（ADR-004） |
| created_by 存工号 | 03 册 §1.1 SELF 范围以工号匹配；姓名会重名且可改 |

## 三、遇到的问题与解决（8 项）

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | Mapper 泛型编译错误 | 批量生成脚本把接口名当实体名 | 重新生成 12 个 Mapper |
| P2 | shedlock LockProvider 找不到 | 4.x 包路径是 `.core` 非 `.support` | 改 import |
| P3 | 正则 `\\{` 编译错 | heredoc 转义被吞 | Edit 工具修复，沉淀经验：含转义的代码一律用 Write 而非 bash heredoc |
| P4 | springdoc 1.7.1 镜像缺失 | aliyun 镜像无此版本 | 降 1.7.0（同 1.7 线） |
| P5 | SecurityProperties 无 Bean | @ConfigurationProperties 未注册 | 加 @Component |
| P6 | Mapper 未扫描 | 启动类包根不覆盖模块包 | @MapperScan(annotationClass=Mapper.class) |
| P7 | **循环依赖** sqlSessionFactory→拦截器→DeptTreeProvider→Mapper | 拦截器构造期注入 Mapper | 改 ObjectProvider 惰性解析（请求期取 Bean） |
| P8 | **登出黑名单失效**（最隐蔽） | JwtUtil `.setId()` 先于 `.setClaims()` 被整体覆盖，JWT 无 jti | jti 放入 claims map；测试内解码 jti 断言 KV 落键定位 |
| P9 | 中文水印 .notdef 异常 | PDFBox Helvetica 无 CJK | PDType0Font 按配置→simhei→msyh→Noto 链加载，ASCII 降级不阻断 |
| P10 | 种子漏 42 组字典 | V2 编写遗漏 | 补 V3__seed_dicts.sql（只增不改），verify-spec 增加枚举一致性检查防回归 |

## 四、验证结论（验证门全部通过）

| 验证项 | 结果 |
|---|---|
| `mvn test` 全模块 | **13/13 通过**（S0 冒烟 9：迁移种子/登录锁定/登出黑名单/RBAC 越权/数据范围 DEPT+DEPT_SUB/编码并发 50 无重/字典保护/审计留痕/BR-08；AI 网关 4：AI-001 三色风险/AI-002 十二要素/未知编号回显/健康检查） |
| `verify-spec.py` | **7 项全 PASS**（表登记/枚举一致/编码 18 类/硬卡点/禁公网 AI/编码规范/测试存在性） |
| 前端 `vite build` | 成功；`build:themes` 产出 12 份主题 CSS（色值与 tokens.json 逐一核对一致） |
| 真实环境 E2E（MySQL 5.7.19 + memurai） | 登录→me（21 权限/3 菜单）→42 组字典→编码 `TLD-2026-CG-AZFWXF-0001`→主题持久化→worker PC 登录 403（BR-08）→PDF 上传 336B 下载 9.6KB 含嵌入 CJK 字体水印（BR-07） |
| 浏览器 GUI 验证 | 登录页渲染 ✓；R04 登录仅见「个人工作台/消息中心」2 菜单（S0-14 换角色导航变化）✓；外观面板 12 卡片（档位标注无禁用）✓；点击藏紫 t6：data-theme/link/localStorage 三处即时生效 ✓；刷新后仍 t6（服务端持久化恢复）✓ |

## 五、需求比对（防偏离勾稽）

- 技术栈 16 册 §二：逐项吻合（无 Spring Boot 3/JPA/Vue3/公网 AI）。
- 数据模型 02 册：T-SYS 14 张字段按 §4.1/§4.17/§4.20 逐字段建列；KB/AI 表登记在册。
- 九板（00 册 C-10）：①总板周 + 四链日板 + 四职能周板 + 链主部门标记。
- 主题（03 册 §6A）：个人覆盖系统默认、空=跟随默认、无自定义色值、切换无刷新——均已实现。
- 审计（03 册 §七）：只插不改删（应用层无更新方法）；敏感字段脱敏。
- 偏离记录：见 `docs/ASSUMPTIONS.md` A-01~A-10（全部为环境适配类，无业务语义偏离）。

## 六、汇报四件事（16 册 §一 交付方式）

1. **建表清单**：T-SYS 14 + kb_board + ai_call_log + ai_model_config，共 17 张（V1）；种子 V2/V3（14 部门/18 角色/62 权限项/9 板/5 参数/42 组 131 项枚举/8 用户）。
2. **接口清单**：见 `docs/API.md` S0 23 个接口（主应用 21 + AI 网关 2）。
3. **自测结果**：13/13 单测 + verify-spec 7/7 + 前端构建 + 真实环境 E2E + 浏览器 GUI 验证，全部通过。
4. **下一片确认**：S1 营销与合同（T-MDM 12 表建档/三重查重/商机/合同/AI 提取/BR-01/BR-02/E2E s1a.spec）。开工前先读 02 §4.2-4.4、04 册、12 册 §2。

## 七、遗留与顺延

- W1 页面水印的防篡改加固（MutationObserver 重挂载）→ S8 安全批次。
- 备份/恢复脚本（backup.sh/restore.sh）→ 与运维批次（S8 后）合并交付。
- Knife4j 文档界面默认关闭（springdoc 依赖已入包，按需开 profile）。
- 演示账号 must_change_pwd=0（豁免首登强改，A-05），admin@demo 保留强改以演示该流程。
