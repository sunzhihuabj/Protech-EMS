-- V24: SPRINT-PRJ2 监控菜单补齐（Monitor.vue 页面已存在但缺菜单/路由登记）
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
 (152, 'menu:system:monitor', '集成监控', 'system', 'MENU', 3, 7, 'ACTIVE', 'V24');

-- 授权：R01 总经理 / R03 系统管理员 / R20 Administrator
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, 152, 'V24' FROM sys_role r WHERE r.id IN (1, 3, 20)
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.perm_id = 152);
