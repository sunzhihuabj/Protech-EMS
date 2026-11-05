-- V29: SPRINT-SEC3 SK- 回款单取号收敛走 CodeSequenceService——历史号回填补齐序列行（幂等）
-- 背景：applyReceipt 原手工 selectCount+1 推号（SK-yyyyMMdd-nnnn），并发下重号风险；
--       改走 CodeTypes.receipt（scope=SK:{yyyyMMdd}，4 位流水）后，已发出的历史号
--       必须预占进 sys_code_sequence（同 V28 教训：种子/历史占号必须同步序列行）。
-- 兼容：不使用 CAST AS INT（MySQL 8 不支持 INT 目标类型）；%04d 定长流水字符串
--       字典序 = 数值序，MAX(字符串) 直接赋 BIGINT 列（H2 MODE=MySQL / MySQL 8 均隐式转换）。

-- 1) 按历史回款单的日期分组补插序列行（每组仅在该 scope 缺行时插入）
INSERT INTO sys_code_sequence (scope_key, period, current_value, step, prefix, created_by, created_at, updated_by, updated_at)
SELECT CONCAT('SK:', SUBSTRING(r.receipt_no, 4, 8)),
       SUBSTRING(r.receipt_no, 4, 8),
       MAX(SUBSTRING(r.receipt_no, 13, 4)),
       1,
       CONCAT('SK-', SUBSTRING(r.receipt_no, 4, 8), '-'),
       'V29', NOW(), 'V29', NOW()
FROM ct_receipt r
WHERE r.receipt_no LIKE 'SK-%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_code_sequence s
      WHERE s.scope_key = CONCAT('SK:', SUBSTRING(r.receipt_no, 4, 8)))
GROUP BY SUBSTRING(r.receipt_no, 4, 8);

-- 2) 兜底校正：已存在但 current_value 低于历史最大号的 scope 行提到最大号
UPDATE sys_code_sequence s
SET s.current_value = (
        SELECT MAX(SUBSTRING(r.receipt_no, 13, 4))
        FROM ct_receipt r
        WHERE r.receipt_no LIKE 'SK-%'
          AND SUBSTRING(r.receipt_no, 4, 8) = SUBSTRING(s.scope_key, 4)),
    s.updated_by = 'V29',
    s.updated_at = NOW()
WHERE s.scope_key LIKE 'SK:%'
  AND s.current_value < (
        SELECT MAX(SUBSTRING(r.receipt_no, 13, 4))
        FROM ct_receipt r
        WHERE r.receipt_no LIKE 'SK-%'
          AND SUBSTRING(r.receipt_no, 4, 8) = SUBSTRING(s.scope_key, 4));
