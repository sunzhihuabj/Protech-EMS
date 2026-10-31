# SPRINT S7 · 部署工件 + 迁移 + 验收（收尾切片）

> 切片 S7（06 册 §4.3）｜分支 protech-v2｜2026-11-01
> DoD：一键部署工件齐备 + PG 迁移脚本 + UAT 路径文档；Vitest 94 全绿。

## 交付内容

### 部署工件（deploy/，09/11 册）
- docker-compose.yml：postgres16 / redis7 / minio / api / worker / web(nginx) 六服务
- .env.example：密钥占位（JWT/INTERNAL_TOKEN/PG/MINIO/AI/钉钉 Webhook）
- nginx.conf：静态资源 + /api 反代 + 远程白名单路径收敛（09 册 §五安全边界）
- 三个 Dockerfile：api（Node20-alpine，prisma generate→tsc）、worker、web（vite build→nginx）
- scripts：backup.sh（pg_dump+sha256+30 天保留）/ restore.sh（半年演练）/ upgrade.sh（先 migrate 后滚动）

### PostgreSQL 迁移（BR-09 同源原则：只增不改）
- prisma/migrations/0001_init/migration.sql：63 表 / BIGSERIAL / TIMESTAMPTZ / 无库级外键（05 册规范）
- 生产路径：migrate deploy → seed（空库一次）→ 12 册 UAT 十二步

### 可追溯性（NFR-12）
- scripts/check-fr-coverage.mjs：FR-M##-nnn 代码标注扫描 → **117/230（50.9%）显式标注**
- 缺口与分期规划一致：M09 成本/M11 售后/M12 档案（R2/R3）、M16 AI-04~12（R2/R3）、盘点/呆滞/货位（R2）——详见各 SPRINT-* 遗留清单

### UAT
- docs/UAT.md：12 步演示路径（11 册 §7.2 十步扩展），每步含 12 册用例编号与 BR 拦截演示点

## R1 验收口径对照（06 册 §4.2 上线标志）
① 新签合同 100% 带码 → BR-01 硬卡点 ✅（UC-BR01 测试）
② 扫码过账跑通且 T+ 推送队列运转 → 幂等过账+队列状态机 ✅（UC-M06 测试）
③ 九板+三级视图上线且指标自动取数 → 九板预置+BV-01/02/03+快照引擎（3 真实计算器+10 占位）✅（占比口径待 R2 业务指标接入后达 90%）
④ BR-01/02/03/04/08 生效 → 全部有服务端硬校验+测试断言 ✅
