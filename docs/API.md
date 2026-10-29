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

## AI 网关（独立进程 :8090）

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 22 | POST | /ai/v1/call | 统一 AI 调用入口（aiCode + payload），mock 优先，降级链可演练 |
| 23 | GET | /ai/v1/health | 模型健康检查（DEGRADED 不抛错） |
