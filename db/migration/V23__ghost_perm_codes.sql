-- V23: SPRINT-OPS1 幽灵权限码补种（注解存在但 sys_permission 无此码 → 恒 2002 死端点修复）
-- 授权规则：读码授予"持同级写码的角色"，R03/R20 全量。

INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, sort_no, status, created_by) VALUES
 (146, 'mdm:customer:view',  '客户查看',       'mdm',    'ACTION', 200, 'ACTIVE', 'V23'),
 (147, 'mdm:material:view',  '物料查看',       'mdm',    'ACTION', 201, 'ACTIVE', 'V23'),
 (148, 'mdm:project:view',   '项目主数据查看', 'mdm',    'ACTION', 202, 'ACTIVE', 'V23'),
 (149, 'mes:order:view',     '生产订单查看',   'mes',    'ACTION', 203, 'ACTIVE', 'V23'),
 (150, 'qms:inspection',     '检验查看/SPC',   'qms',    'ACTION', 204, 'ACTIVE', 'V23'),
 (151, 'sys:config',         '系统配置操作',   'system', 'ACTION', 205, 'ACTIVE', 'V23');

-- 同伴写码持有者自动获得对应读码
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 146, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mdm:customer:edit'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 146);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 147, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mdm:material:edit'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 147);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 148, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mdm:project:edit'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 148);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 149, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mes:order:create'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 149);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 150, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'qms:inspect'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 150);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 151, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'system:config:manage'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 151);

-- R03（管理员演示角色）与 R20（Administrator 全权限）补全 6 码
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 3, p.id, 'V23' FROM sys_permission p WHERE p.id BETWEEN 146 AND 151
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 3 AND x.perm_id = p.id);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 20, p.id, 'V23' FROM sys_permission p WHERE p.id BETWEEN 146 AND 151
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 20 AND x.perm_id = p.id);
