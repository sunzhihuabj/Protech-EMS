-- V21: SPRINT-INT1 外部系统接入（钉钉/售后宝/T+）
-- 1) int_sync_log 增加载荷快照列（webhook 原始报文必存 A-40；出站调试）
-- 2) 三目标适配器种子行（enabled=0，凭据经环境变量注入后置 1；对应 int_adapter.auth_config 不落密钥）

ALTER TABLE int_sync_log ADD COLUMN payload_json TEXT NULL COMMENT '请求/响应载荷快照（webhook 原始报文必存 A-40）';

INSERT IGNORE INTO int_adapter (adapter_code, adapter_name, base_url, auth_type, enabled, health_status, created_by)
VALUES
 ('DINGTALK', '钉钉开放平台（工作通知/机器人/待办）', 'https://api.dingtalk.com', 'TOKEN', 0, 'UNKNOWN', 'V21'),
 ('SHB',      '售后宝 OpenAPI（事件/工单同步+Webhook）', 'https://oapi.shb.ltd', 'TOKEN', 0, 'UNKNOWN', 'V21'),
 ('TPLUS',    '捷信通T+ 13.0（通道B直连：存货/基础档案）', 'https://hbtgl.a4.cjtmsp.com', 'TOKEN', 0, 'UNKNOWN', 'V21');
