-- =====================================================================
-- V16：F6 job 专用账号（TEST-REPORT F6）+ F2 采购价格权限核对（scm:order:view_price=89 已有）
-- job-runner：定时任务专用系统账号，授 137 kb:card:manage（红灯升级）与
-- 141 arc:manage（档案逾期扫描）+ mes:andon:raise(103)（Andon 升级），
-- 避免借用 admin@demo（R03 无 137 → 任务 403 静默失败）
-- =====================================================================
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by)
SELECT 20, 'E0020', 'job-runner', '定时任务系统账号',
       (SELECT password_hash FROM sys_user WHERE username = 'biz@demo'),
       '13800000020', 2, '系统任务', 'INTERNAL', 1, 'ACTIVE', 0, 'job 进程专用（JOB_SYSTEM_USERNAME）', 'seed'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'job-runner');

INSERT INTO sys_user_role (user_id, role_id, created_by)
SELECT u.id, 3, 'seed' FROM sys_user u WHERE u.username = 'job-runner'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id);

-- 补授 R03 缺的任务权限：137 kb:card:manage（job 入口经 HTTP 调用需登录账号持有）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 3, 137, 'seed' FROM DUAL WHERE NOT EXISTS
  (SELECT 1 FROM sys_role_permission WHERE role_id = 3 AND perm_id = 137);
