-- =====================================================================
-- V14：THD 门户权限补授（S14-A3）
-- R03 系统管理员承接门户账号/黑名单/评价管理端（FR-THD 管理侧）
-- =====================================================================
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (131, 132)
WHERE r.id = 3;
