-- =====================================================================
-- V30: SPRINT-SEC2 僵尸权限码授予（孤儿盘点 2026-11-05 结论落地）
-- 背景：contract:unfreeze（V2 定义 id=62）零角色持有 → FR-CT-26 紧急解冻全链路死锁
--       （ContractController @RequirePermission + ContractDetail.vue v-permission 均引用，
--        无任何角色持有，按钮永不可见/接口恒 2002）。
-- 授权规则：Controller 注释明确「FR-CT-26：紧急解冻（R02）」→ 授 R02（业务板块负责人，
--           ALL 范围，板块内审批+资源调配，与紧急解冻的业务归属一致）。
-- 幂等：NOT EXISTS 防重（V23/V19 范式）；R20 超管由 V19 全量授予已覆盖。
-- =====================================================================

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 2, p.id, 'V30' FROM sys_permission p
WHERE p.perm_code = 'contract:unfreeze'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 2 AND x.perm_id = p.id);
