-- =====================================================================
-- V19：全系统权限测试账户 Administrator（用户指令）
-- 超级管理员角色：GRANT ALL sys_permission + data_scope=ALL
-- =====================================================================

-- 1. 超级管理员角色（data_scope=ALL）
INSERT INTO sys_role (id, role_code, role_name, data_scope, remark, status, created_by)
SELECT 20, 'R20', '超级管理员', 'ALL', '全系统权限测试账户专用（拥有全部权限码+全部数据范围）', 'ACTIVE', 'seed'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'R20');

-- 2. 授予全部权限码（SELECT 动态插入，覆盖现有+未来新增）
INSERT IGNORE INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 20, p.id, 'seed' FROM sys_permission p
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 20 AND rp.perm_id = p.id);

-- 3. 创建 Administrator 用户（初始密码同种子 ProjLife@2026——V32 已重置为 12345678，首登不强制改密——测试账户）
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by)
SELECT 30, 'E0030', 'Administrator', '超级管理员',
       (SELECT password_hash FROM sys_user WHERE username = 'biz@demo' LIMIT 1),
       '13800000030', 2, '超级管理员', 'INTERNAL', 1, 'ACTIVE', 0,
       '全系统权限测试账户（拥有全部权限码+ALL 数据范围）', 'seed'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'Administrator');

-- 4. 绑定角色
INSERT IGNORE INTO sys_user_role (user_id, role_id, created_by)
SELECT u.id, 20, 'seed' FROM sys_user u
WHERE u.username = 'Administrator'
AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = 20);
