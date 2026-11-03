-- V25: SPRINT-UID-R16 菜单补齐
-- 1) QMS 菜单从单叶升级为子菜单（检验台账/8D 报告/SPC 控制图/量具台账——8D 与 SPC 此前为无菜单入口的孤儿页）
-- 2) 文件中心挂系统管理（module-file FileController 已有端点，此前前端零调用）
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
 (153, 'menu:qms:inspection', '检验台账',   'qms',    'MENU', 105, 1, 'ACTIVE', 'V25'),
 (154, 'menu:qms:8d',         '8D 报告',     'qms',    'MENU', 105, 2, 'ACTIVE', 'V25'),
 (155, 'menu:qms:spc',        'SPC 控制图',  'qms',    'MENU', 105, 3, 'ACTIVE', 'V25'),
 (156, 'menu:qms:gauges',     '量具台账',   'qms',    'MENU', 105, 4, 'ACTIVE', 'V25'),
 (157, 'menu:system:file',    '文件中心',   'system', 'MENU', 3,   8, 'ACTIVE', 'V25');

-- 授权：QMS 四个子菜单授予所有已持有 menu:qms(105) 的角色（跟随既有授权面，含 R13 及其余全员角色）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT rp.role_id, p.id, 'V25' FROM sys_role_permission rp
JOIN sys_permission p ON p.id BETWEEN 153 AND 156
WHERE rp.perm_id = 105
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rp.role_id AND x.perm_id = p.id);

-- 授权：文件中心对齐 V24 集成监控口径（R01 总经理 / R03 系统管理员 / R20 Administrator）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, 157, 'V25' FROM sys_role r WHERE r.id IN (1, 3, 20)
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.perm_id = 157);

-- 授权修正：file:upload(20) 自 V2 种子以来从未授予任何角色，导致 FileController @RequirePermission("file:upload")
-- 对全员 403（与控制器注释"上传登录即可"意图相悖）。对齐已持有 file:download(19) 的角色面（基础包全员）。
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT rp.role_id, 20, 'V25' FROM sys_role_permission rp
WHERE rp.perm_id = 19
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rp.role_id AND x.perm_id = 20);
