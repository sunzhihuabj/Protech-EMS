-- =====================================================================
-- V32: 种子用户密码统一重置为 12345678（用户指令 2026-11-06）
-- 覆盖范围（created_by='seed' 的全部账号）：
--   V2 八个演示账号（E0001-E0008）+ V4 mdm@demo（E0009）
--   + V8 prod@demo（E0011）+ V9 qc@demo（E0012）+ V11 portal@demo（E0013）
--   + V16 job-runner（E0020，job 进程登录账号，InternalJobInvoker 默认密码已同步改）
--   + V19 Administrator（E0030）
-- 幂等：UPDATE 常量哈希，重复执行结果一致；真实业务用户（created_by≠'seed'）不受影响。
-- 配套改动：V2/V19 注释、README、全部测试登录密码、InternalJobInvoker 默认值。
-- =====================================================================

UPDATE sys_user
SET password_hash = '$2a$10$gb3RxdVf7v1FM9k35laJlu7YvChiTfaq6FIJg/QezKK/M45TQaQnS',
    pwd_updated_at = CURRENT_TIMESTAMP,
    updated_by = 'V32',
    updated_at = CURRENT_TIMESTAMP
WHERE created_by = 'seed';
