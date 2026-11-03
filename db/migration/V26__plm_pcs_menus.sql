-- V26: SPRINT-UID-R17 菜单与授权补齐
-- 1) PLM 菜单从单叶（75 menu:plm → /plm/bom）升级为子菜单（BOM 台账 / PCS 工艺——PCS 后端端点已齐，此前前端无页面）
-- 2) 授权对齐：量具台账 API（qms:gauge:manage(108)）补授给所有已持有 menu:qms:gauges(156) 的角色，
--    修复 R16 引入的"菜单可见但接口 403"权限面错位（与 V25 file:upload 修正同类）
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
 (158, 'menu:plm:bom', 'BOM 台账', 'plm', 'MENU', 75, 1, 'ACTIVE', 'V26'),
 (159, 'menu:plm:pcs', 'PCS 工艺', 'plm', 'MENU', 75, 2, 'ACTIVE', 'V26');

-- 授权：PLM 两个子菜单授予所有已持有 menu:plm(75) 的角色（跟随既有授权面）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT rp.role_id, p.id, 'V26' FROM sys_role_permission rp
JOIN sys_permission p ON p.id BETWEEN 158 AND 159
WHERE rp.perm_id = 75
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rp.role_id AND x.perm_id = p.id);

-- 授权对齐：量具 API 权限跟随量具菜单持有面（R16 错位修复）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT rp.role_id, 108, 'V26' FROM sys_role_permission rp
WHERE rp.perm_id = 156
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rp.role_id AND x.perm_id = 108);

-- 授权对齐：PCS API（pcs:manage(82)）跟随 PCS 菜单持有面——V6 仅授研发角色(6,8)，
-- 持有 menu:plm 的管理/工艺角色打开页面按钮可见但接口 403（与 V25 file:upload 同类错位）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT rp.role_id, 82, 'V26' FROM sys_role_permission rp
WHERE rp.perm_id = 159
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rp.role_id AND x.perm_id = 82);
