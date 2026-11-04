-- V28: SPRINT-UID-R19 种子已占号而取号序列缺行/低值校正（幂等）
-- 根因：V4 种子直接插业务行（md_customer C0001~C0007、md_wind_farm WF2026-001），
--       但 sys_code_sequence 无对应 scope 行——CodeSequenceService.next() 的 upsert
--       首次插入 current_value=1 即生成 C0001/WF2026-001，撞唯一键 → 建档 500。
--       （物料 MAT/项目 PJ 无种子行，不冲突，无需校正）
-- 语义：序列行 current_value = 已发出的最大号；种子占用的号必须预占进序列。

-- 1) 客户 CUST:GLOBAL：种子已用 C0001~C0007 → 起点应为 7
INSERT INTO sys_code_sequence (scope_key, period, current_value, step, prefix, created_by, created_at, updated_by, updated_at)
SELECT 'CUST:GLOBAL', 'GLOBAL', 7, 1, 'C', 'V28', NOW(), 'V28', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_code_sequence WHERE scope_key = 'CUST:GLOBAL');

UPDATE sys_code_sequence SET current_value = 7, updated_by = 'V28', updated_at = NOW()
WHERE scope_key = 'CUST:GLOBAL' AND current_value < 7;

-- 2) 风场 WF:<当前年>：种子已用 WF2026-001 → 当前年 scope 起点应为 1
INSERT INTO sys_code_sequence (scope_key, period, current_value, step, prefix, created_by, created_at, updated_by, updated_at)
SELECT CONCAT('WF:', YEAR(NOW())), CAST(YEAR(NOW()) AS CHAR), 1, 1, CONCAT('WF', YEAR(NOW()), '-'), 'V28', NOW(), 'V28', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_code_sequence WHERE scope_key = CONCAT('WF:', YEAR(NOW())));

UPDATE sys_code_sequence SET current_value = 1, updated_by = 'V28', updated_at = NOW()
WHERE scope_key = CONCAT('WF:', YEAR(NOW())) AND current_value < 1;
