# ProjLife 企业级管理平台

系统集成工程公司（ETO 模式）订单全生命周期管理平台：报价 → 合同 → 项目 → 研发 → 采购 → 生产 → 质量 → 交付 → 回款 → 看板。

> 需求唯一事实源：`docs/SPEC/`（RPD 开发规格包 21 册，只读）。开发计划：`docs/DEVELOPMENT_PLAN.md`。

## 架构（00 册 C-02 裁决：模块化单体 + 3 旁路进程）

```
projlife-api            主应用 :8080（Maven 多模块，14 业务模块逐步交付）
projlife-ai-gateway     AI 网关 :8090（唯一可访问内网模型的组件；mock 模式可跑通全流程）
projlife-file-service   文件服务 :8091（水印合成/照片处理旁路）
projlife-job            定时任务 :8081（ShedLock 防重复）
```

## 技术栈（16 册 §二 锁定）

Java 11（字节码 target）· Spring Boot 2.7.18 · MyBatis-Plus 3.5.5 · MySQL 8/GreatDB · Redis · RabbitMQ(条件装配) · Vue 2.7 + Element UI 2.15 · Flyway · JWT · EasyExcel · PDFBox

## 本地运行

```bash
# 1. 数据库与缓存（本机已具备 MySQL 9.6 + memurai；或用 docker-compose.yml）
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS projlife DEFAULT CHARSET utf8mb4"

# 2. 启动主应用（Flyway 自动迁移 + 种子）
cd projlife-api && mvn spring-boot:run

# 3. 启动 AI 网关（可选，mock 模式）
cd projlife-ai-gateway && mvn spring-boot:run

# 4. 前端（主题为运行时令牌切换，无需预编译步骤）
cd projlife-web && npm install && npm run dev
```

默认账号：`admin@demo / ProjLife@2026`（首登强制改密）；演示账号见 `db/migration/V2__seed_base.sql`。

## 演示路径（S0，约 5 分钟）

1. `admin@demo` 登录 → 首登强制改密 → 进入个人工作台
2. 系统管理 → 用户管理 → 新建用户（分配 R04 商务）→ 保存
3. 用新用户登录 → 菜单只剩工作台/消息中心（按权限渲染）
4. 系统管理员改一条数据（冻结用户）→ 审计日志可查到 before/after
5. `store@demo`（R11）登录 → 用户管理 → 403 无权限
6. 个人设置 → 外观 → 切换 24 组主题令牌之一 → 刷新后保持（sys_user.theme_code 持久化）
7. 文件上传 PDF → 下载得到带水印版本（BR-07）
8. `worker@demo`（R17）用 PC 端登录 → 403（BR-08）；`X-Client: mp` 小程序端放行

## 验证

```bash
mvn test                      # 后端全量测试（28 类 140+ 用例：认证/RBAC/数据范围/审计/编码并发/字典保护/BR 硬卡点/AI 网关/内部密钥门）
python scripts/verify-spec.py # 规格一致性自检 7 项（表/字典/编码/硬卡点/禁公网 AI/编码规范/测试存在性；3/4/6/7 项域名级匹配）
cd projlife-web && npm run build   # 前端构建（24 组主题令牌运行时切换，无预编译步骤）
```

## 文档

| 文档 | 说明 |
|---|---|
| docs/DEVELOPMENT_PLAN.md | 可执行开发计划（S0-S9 切片与验证门） |
| docs/PROGRESS.md | 阶段进度台账 |
| docs/SPRINT-S0.md | S0 阶段记录（工作内容/选型依据/问题与解决） |
| docs/DECISIONS.md · ASSUMPTIONS.md | 架构决策与默认假设 |
| docs/API.md | 接口清单（每片更新） |
