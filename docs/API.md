# 接口清单（每片更新）

> 前缀 `/api/v1`；统一响应包 `ApiResponse<T>`；401=未登录，403=无权限(data.missingPerm)，422=硬卡点(data.blockCode)。

## S0 已交付接口

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 1 | POST | /api/v1/auth/login | 公开 | 登录（5 次失败锁 15 分钟） |
| 2 | POST | /api/v1/auth/refresh | 公开 | 刷新 access token |
| 3 | POST | /api/v1/auth/logout | 登录 | 登出（token 入黑名单） |
| 4 | GET | /api/v1/auth/me | 登录 | 当前用户信息+权限码+菜单 |
| 5 | PUT | /api/v1/auth/me/password | 登录 | 修改密码（首登强改） |
| 6 | PUT | /api/v1/auth/me/theme | profile:theme:switch | 保存个人主题偏好 |
| 7 | GET/POST/PUT | /api/v1/org/users | org:user:manage | 用户分页/新增/编辑/冻结/重置密码 |
| 8 | POST | /api/v1/org/users/import | org:user:manage | 用户导入（dryRun 干跑→确认提交） |
| 9 | GET | /api/v1/org/users/export | org:user:manage | 用户导出（脱敏） |
| 10 | GET | /api/v1/org/depts/tree | 登录 | 部门树 |
| 11 | POST/PUT | /api/v1/org/depts | org:dept:manage | 部门维护 |
| 12 | GET/POST/PUT | /api/v1/org/roles | org:role:manage | 角色与权限码分配 |
| 13 | GET | /api/v1/org/permissions | 登录 | 权限码树（分配界面用） |
| 14 | GET/POST/PUT/DELETE | /api/v1/system/dicts | org:dict:manage | 字典类型/项维护（系统枚举删除被拒） |
| 15 | GET/PUT | /api/v1/system/configs | system:config:manage | 系统参数（含 app.theme.default） |
| 16 | GET | /api/v1/system/logs | system:log:view | 操作日志（before/after） |
| 17 | GET | /api/v1/notifications | 登录 | 我的通知；PUT /read 标记已读 |
| 18 | POST | /api/v1/files/upload | 登录 | 上传（MIME 嗅探/白名单/SHA256） |
| 19 | GET | /api/v1/files/{id}/download | file:download | 下载（PDF 强制 W2 水印；图片 W3 合成且原图不落盘） |
| 20 | GET | /api/v1/system/codes/preview | system:config:manage | 编码引擎预览取号（18 类） |
| 21 | GET | /actuator/health | 内部 | db/redis/disk 健康检查 |

## S1 已交付接口（主数据 / 商机 / 合同）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 24 | GET/POST | /api/v1/mdm/customers(+ /groups /aliases /resolve /{id}/deactivate) | mdm:customer:* | 客户主体（R18 唯一入口） |
| 25 | GET/POST | /api/v1/mdm/farms | mdm:project:create | 风场（一地一档） |
| 26 | GET/POST | /api/v1/mdm/projects(+ /{id} /name-rule /triple-check /{id}/products /{id}/aliases) | mdm:project:* | 项目档案（三重查重/名称生成器） |
| 27 | GET/POST | /api/v1/crm/opportunities(+ /{id} /{id}/followups /{id}/convert /{id}/lose /{id}/reopen) | opportunity:* | 商机全流程 |
| 28 | GET | /api/v1/crm/funnel；/api/v1/crm/customers/{id}/profile | opportunity:* | 漏斗/客户360 |
| 29 | GET/POST | /api/v1/contract(+ /{id} /{id}/number-preview) | contract:* | 台账/起草 |
| 30 | POST | /api/v1/contract/{id}/review/submit | contract:review:submit | BR-01 硬卡点 |
| 31 | POST | /api/v1/contract/{id}/seal；/{id}/filing；/{id}/completeness | contract:seal/confirm | 用印取号/建档 26 要素 |
| 32 | POST | /api/v1/contract/{id}/ai/risk · /ai/extract · /ai/confirm；/ai/risks/{id}/adopt | contract:edit/confirm | AI-001/002（网关 mock 可跑通） |
| 33 | POST/GET | /api/v1/contract/{id}/changes · /changes/{id}/approve | contract:change:* | 变更 -BCn |
| 34 | POST/GET | /api/v1/contract/{id}/dispatch · /dispatches/{id}/read | contract:dispatch | 交底/已阅 |
| 35 | POST | /api/v1/contract/receipts(+ confirm)；/invoices(+ confirm)；/{id}/unfreeze | contract:payment:* | 收款/开票/解冻 |

## S3 已交付接口（研发与工艺）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 36 | GET/POST | /api/v1/mdm/materials | mdm:material:* | 物料建档（双码检测 FR-MDM-26） |
| 37 | GET/POST | /api/v1/plm/bom(+ /{id} /{id}/lines /lines/{id} DELETE) | bom:create/edit | BOM 二维矩阵（C+MBOM 422） |
| 38 | GET | /api/v1/plm/bom/{id}/validate | bom:edit | 13 条质量校验 |
| 39 | POST | /api/v1/plm/bom/{id}/release；/bom/{goldenId}/derive；/bom/{src}/convert；/bom/{tpl}/expand | bom:release/create/edit | 发布/派生/转换/参数化展开 |
| 40 | GET | /api/v1/plm/bom/diff?bomAId&bomBId | bom:edit | 四类差异 |
| 41 | POST | /api/v1/plm/bom/{id}/ecn；/ecn/{id}/publish | bom:ecn:* | ECN（BR-09）/发布新版本 |
| 42 | GET/POST | /api/v1/plm/pcs/docs(+ /{id}/publish /{id}/pause)；/pcs/routes(+ steps) | pcs:manage | 工艺文件（BR-PCS-01/02）/路线 |
| 43 | GET | /api/v1/plm/pcs/coverage | 登录 | 覆盖度矩阵 |

> JSON 列入参约定：传 JSON 字符串（如 affectedDepts、param_json）。

## S4 已交付接口（供应链 / 库房）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 44 | POST | /api/v1/mdm/suppliers-register | mdm:supplier:manage | 供应商建档（S{4位}） |
| 45 | POST/GET | /api/v1/scm/suppliers/{id}/register · /advance · /onboarding | scm:supplier:manage | 五阶准入 |
| 46 | GET/POST | /api/v1/scm/requirements · /orders(+ /{id} /{id}/dispatch) | scm:req/order:* | 采购链（BR-SCM-01/03） |
| 47 | POST | /api/v1/inv/transactions(+ /by-key /retry-exceptions) | inv:scan | 库存事务（幂等 BR-INV-03） |
| 48 | GET | /api/v1/inv/stock/available | inv:scan | 可用量（缓存口径） |
| 49 | POST | /api/v1/inv/reconcile | inv:count:manage | 每日对账 |
| 50 | GET/POST | /api/v1/inv/replenish(+ /{id}/approve /issue /backfill /lock-overdue) | inv:replenish:* | 补货（BR-05） |

## S5 已交付接口（生产）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 51 | GET/POST | /api/v1/mes/orders(+ /{id} /{id}/routes /{id}/processes /{id}/material-check) | mes:order:create | 生产订单/工序/齐套 |
| 52 | POST | /api/v1/mes/processes/{id}/start · /finish · /qc | mes:process:work / mes:qc:release | 开工/报工/检验放行（E-26） |
| 53 | POST | /api/v1/mes/andon(+ /escalate) | mes:andon:raise | Andon 四类报警/分级升级 |

## S6 已交付接口（质量）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 54 | GET/POST | /api/v1/qms/inspections(+ /{id}/correct) | qms:inspect | 检验三关口/AI 改判留痕 |
| 55 | POST | /api/v1/qms/8ds(+ /{id}/close) | qms:8d:manage | 8D（D6 未填拒关） |
| 56 | GET/POST | /api/v1/qms/gauges/usable · /gauges/{id}/calibrate | qms:gauge:manage | 量具超期锁定/校准 |
| 57 | GET | /api/v1/qms/assets/{id}/mtbf | 登录 | MTBF |

## V4 对齐切片接口（报价单，FR-CRM-17~20）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 58 | GET/POST | /api/v1/crm/quotations(+ /{id}/lines) | opportunity:* | 报价单建档（QT 编号/版本递增/金额服务端算） |
| 59 | POST | /api/v1/crm/quotations/{id}/submit · /decide | opportunity:edit | 提交/定标（WON 转合同草稿+回写） |
| 60 | GET | /api/v1/crm/quotations/{id}/price-basis · /conversion-by-source | opportunity:* | 价格基准（FR-CRM-18）/来源转化统计（FR-CRM-20） |

> FR 回填（V4-00 §8.2）：S0=FR-SYS-01~24 部分；S1=FR-CRM-01~20+FR-CT-01~38；S2=FR-PRJ-01~39；S3=FR-PLM-11~33+FR-PCS-01~13；S4=FR-SCM-01~17+FR-INV-07~14/33~41；S5=FR-MES-01~16/26/28/30；S6=质量 09 册 FR。未实现区间见 PROGRESS 台账 BACKLOG 列。

## S7 已交付接口（工程交付）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 61 | POST | /api/v1/eng/workers(+ /{id}/activate) | eng:crew:manage | 人员建档/激活（UC-ENG-10） |
| 62 | GET/POST | /api/v1/eng/dispatch(+ /{id}/send /start /accept /close) | eng:dispatch | 派工（BR-03/04，E-32） |
| 63 | GET/POST | /api/v1/eng/units(+ /{id}/advance) | eng:install | 机组（E-29 一机一档） |
| 64 | POST/GET | /api/v1/eng/rectifications(+ /{id}/advance) · /projects/{id}/rect-open-check | eng:rect:manage | 整改（FR-ENG-08） |

## AI 网关（独立进程 :8090）

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 22 | POST | /ai/v1/call | 统一 AI 调用入口（aiCode + payload），mock 优先，降级链可演练 |
| 23 | GET | /ai/v1/health | 模型健康检查（DEGRADED 不抛错） |
