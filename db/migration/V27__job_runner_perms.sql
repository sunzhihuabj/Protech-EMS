-- V27: SPRINT-UID-R18 job 链路授权补齐
-- 1) V16 声称为 R03 补授 137/141/103 三个任务码，但 SQL 仅落地了 137——141 arc:manage（档案逾期扫描）
--    与 103 mes:andon:raise（Andon 升级）实际漏授，对应定时任务默认部署下 403 静默失败（本迁移修正）
-- 2) R18 新挂的 job 入口所需码：91 inv:count:manage（T+ 每日对账）、96 inv:replenish:approve（BR-05 补货逾期锁定）
-- 3) job-runner(E0020) 绑定 R03（V16 已建），以上码补授后其全部定时任务可达
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 3, p.id, 'V27' FROM sys_permission p
WHERE p.id IN (91, 96, 103, 141)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 3 AND x.perm_id = p.id);
