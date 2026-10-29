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

## AI 网关（独立进程 :8090）

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 22 | POST | /ai/v1/call | 统一 AI 调用入口（aiCode + payload），mock 优先，降级链可演练 |
| 23 | GET | /ai/v1/health | 模型健康检查（DEGRADED 不抛错） |
