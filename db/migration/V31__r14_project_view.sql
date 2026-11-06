-- =====================================================================
-- V31: SPRINT-A 派工联动选择器配套授权
-- 背景：派工弹窗项目搜索调 GET /mdm/projects（@RequirePermission mdm:project:view=148），
--       而 R14（工程，派工操作角色 eng:dispatch=125 持有）未持 148 → 项目下拉恒 403；
--       MainLayout 全局搜索亦调用同端点（此前静默 catch）。
-- 授权规则：BR-03 派工必须关联项目 → R14 需要"看"项目主数据（仅 view，无建档/编辑权）。
-- 幂等：NOT EXISTS 防重（V23/V30 范式）。
-- =====================================================================

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 14, p.id, 'V31' FROM sys_permission p
WHERE p.perm_code = 'mdm:project:view'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 14 AND x.perm_id = p.id);

-- 注意：本迁移不放任何业务表种子（eng_crew/eng_worker/md_wind_farm/md_project）。
-- 曾试验补演示种子，但 S1FlowTest 等全链流测假设业务表从空开始（种子项目触发 FR-MDM
-- 一地一档查重 5001、占用自增 id 致后续用例 9001）——运行态演示数据改由 API 造。

