-- =====================================================================
-- V15：招标情报 / Agent 台 菜单登记（S18 前端切片）
-- =====================================================================
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(142, 'menu:bid', '招标情报', 'bid', 'MENU', NULL, 14, 'ACTIVE', 'seed'),
(143, 'menu:agt', 'Agent 接入', 'agt', 'MENU', NULL, 15, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (142, 143)
WHERE r.id = 3;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id = 142
WHERE r.id IN (4, 5);
