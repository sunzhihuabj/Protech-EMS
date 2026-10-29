-- 测试环境最小表结构：T-AI-01 调用日志（生产由主应用 Flyway 建）
CREATE TABLE IF NOT EXISTS ai_call_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_no        VARCHAR(40)  NOT NULL,
    ai_code        VARCHAR(16)  NOT NULL,
    model_type     VARCHAR(8)   NOT NULL,
    biz_type       VARCHAR(32)  NOT NULL,
    biz_id         BIGINT       NULL,
    request_json   JSON         NULL,
    response_json  JSON         NULL,
    status         VARCHAR(16)  NOT NULL,
    confidence     DECIMAL(5,2) NULL,
    elapsed_ms     INT          NULL,
    model_version  VARCHAR(40)  NULL,
    prompt_version VARCHAR(20)  NULL,
    is_mock        TINYINT      NOT NULL,
    operator_id    BIGINT       NOT NULL,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_ai_call_no UNIQUE (call_no)
);
