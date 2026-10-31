-- =====================================================================
-- V18：M15 集成层 5 表（163 清单最后 5 张，R9 落地）
-- 依据：V4-06 M15 FR-INT-01~08
-- =====================================================================

CREATE TABLE IF NOT EXISTS int_adapter (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    adapter_code   VARCHAR(32)  NOT NULL COMMENT 'TPLUS/ONLYOFFICE/DINGTALK/WECHAT',
    adapter_name   VARCHAR(100) NOT NULL,
    base_url       VARCHAR(300) NULL,
    auth_type      VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/BASIC/TOKEN/OAUTH2',
    auth_config    JSON         NULL COMMENT '凭据（生产加密）',
    enabled        TINYINT      NOT NULL DEFAULT 0,
    health_status  VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/UNKNOWN',
    last_check_at  DATETIME     NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT          NOT NULL DEFAULT 0,
    CONSTRAINT uniq_adapter_code UNIQUE (adapter_code)
) COMMENT '适配器';

CREATE TABLE IF NOT EXISTS int_mapping (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    adapter_id     BIGINT       NOT NULL,
    local_entity   VARCHAR(60)  NOT NULL COMMENT '本地实体（ct_contract/inv_transaction...）',
    remote_entity  VARCHAR(60)  NOT NULL COMMENT '远端实体（T+ 凭证/...）',
    field_mappings JSON         NULL COMMENT '字段映射 [{"local":"amount","remote":"AMT"}]',
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '字段映射';

CREATE TABLE IF NOT EXISTS int_sync_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    adapter_id     BIGINT       NOT NULL,
    direction      VARCHAR(8)   NOT NULL COMMENT 'PUSH/PULL',
    local_id       BIGINT       NULL,
    remote_id      VARCHAR(60)  NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    error_message  VARCHAR(500) NULL,
    retry_count    INT          NOT NULL DEFAULT 0,
    pushed_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sync_adapter (adapter_id, status, pushed_at)
) COMMENT '同步日志';

CREATE TABLE IF NOT EXISTS int_outbox (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    adapter_id     BIGINT       NOT NULL,
    entity_type    VARCHAR(60)  NOT NULL,
    entity_id      BIGINT       NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/EXPIRED',
    idempotency_key VARCHAR(80) NULL,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_outbox_status (status, created_at)
) COMMENT '待发队列（Outbox 模式）';

CREATE TABLE IF NOT EXISTS int_reconcile_diff (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    adapter_id     BIGINT       NOT NULL,
    local_amount   DECIMAL(18,2) NULL,
    remote_amount  DECIMAL(18,2) NULL,
    diff_amount    DECIMAL(18,2) NULL,
    biz_date       DATE         NOT NULL,
    resolved       TINYINT      NOT NULL DEFAULT 0,
    resolved_by    BIGINT       NULL,
    resolved_at    DATETIME     NULL,
    remark         VARCHAR(300) NULL,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reconcile (adapter_id, biz_date)
) COMMENT '对账差异';
