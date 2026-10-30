-- =====================================================================
-- ProjLife V13：S10 招标情报 + Agent 接入（V4-05 M18 T-BID 8 + V4-06 M19 T-AGT 3 = 11 张）
-- 依据：V4-05 M18 §3（T-BID 字段）+ V4-06 M19 §5（T-AGT 字段）
-- 合规：robots 遵守/content_hash UK/原文 90 天物理清理/同源 ≥60s/日 ≤500 次
-- =====================================================================

-- T-BID-01 数据源
CREATE TABLE bid_source (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_code      VARCHAR(16)   NOT NULL COMMENT 'SRC-{A|B|C|D}-{3位}',
    category         VARCHAR(16)   NOT NULL COMMENT 'A_OWNER/B_OEM/C_PORTAL/D_MANUAL',
    entry_url        VARCHAR(500)  NOT NULL,
    fetch_template   JSON          NULL,
    frequency        VARCHAR(16)   NOT NULL DEFAULT 'DAILY_2',
    robots_checked_at DATETIME     NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING_REVIEW',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    health_score     DECIMAL(5,2)  NULL,
    last_watermark   DATETIME      NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_source_code UNIQUE (source_code)
) COMMENT '标讯数据源';

-- T-BID-02 原文暂存（豁免软删，90 天物理清理）
CREATE TABLE bid_raw (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id    BIGINT       NOT NULL,
    batch_no     VARCHAR(40)  NOT NULL,
    url          VARCHAR(1000) NOT NULL,
    http_status  INT          NULL,
    content_hash VARCHAR(64)  NOT NULL COMMENT 'SHA-256 UK',
    raw_path     VARCHAR(500) NULL,
    fetched_at   DATETIME     NOT NULL,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_raw_hash UNIQUE (content_hash)
) COMMENT '标讯原文（豁免软删）';
CREATE INDEX idx_bid_raw_source ON bid_raw (source_id, fetched_at);

-- T-BID-03 标讯公告
CREATE TABLE bid_notice (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_code      VARCHAR(24)   NOT NULL COMMENT 'BID-{YYYYMM}-{4位}',
    source_id        BIGINT        NOT NULL,
    url              VARCHAR(1000) NOT NULL,
    content_hash     VARCHAR(64)   NOT NULL,
    title            VARCHAR(500)  NOT NULL,
    owner_name_raw   VARCHAR(300)  NULL,
    owner_id         BIGINT        NULL,
    project_name     VARCHAR(300)  NULL,
    lot_no           VARCHAR(60)   NULL,
    budget_amount    DECIMAL(18,2) NULL,
    deadline_at      DATETIME      NULL,
    open_bid_at      DATETIME      NULL,
    qualification    JSON          NULL,
    region           VARCHAR(100)  NULL,
    turbine_requirements JSON     NULL,
    summary          VARCHAR(2000) NULL,
    parse_status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSED/MANUAL/INCOMPLETE',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    parse_by         VARCHAR(16)   NULL COMMENT 'TEMPLATE/AI-015',
    confidence       JSON          NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUPERSEDED/CONVERTED/CLOSED/EXCLUDED',
    parent_id        BIGINT        NULL COMMENT '变更/补遗关联原公告',
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_notice_code UNIQUE (notice_code),
    CONSTRAINT uniq_bid_notice_hash UNIQUE (content_hash)
) COMMENT '标讯公告';
CREATE INDEX idx_bid_notice_status ON bid_notice (status, parse_status);
CREATE INDEX idx_bid_notice_deadline ON bid_notice (deadline_at);

-- T-BID-04 匹配打分
CREATE TABLE bid_match (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id    BIGINT        NOT NULL,
    rule_version VARCHAR(16)   NOT NULL,
    score        DECIMAL(5,2)  NOT NULL,
    level        VARCHAR(16)   NOT NULL COMMENT 'HIGH/MEDIUM/ARCHIVED',
    breakdown    VARCHAR(2000) NULL COMMENT '各维度得分明细 JSON 文本（A-30）',
    is_correct   TINYINT       NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_match UNIQUE (notice_id, rule_version)
) COMMENT '匹配打分';
CREATE INDEX idx_bid_match_level ON bid_match (level);

-- T-BID-05 匹配规则
CREATE TABLE bid_rule (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_version     VARCHAR(16)  NOT NULL,
    keyword_positive JSON         NULL,
    keyword_negative JSON         NULL,
    region_list      JSON         NULL,
    amount_min       DECIMAL(18,2) NULL,
    amount_max       DECIMAL(18,2) NULL,
    weights          JSON         NULL,
    hard_qualification JSON       NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted          TINYINT      NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NULL,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_rule_ver UNIQUE (rule_version)
) COMMENT '匹配规则';

-- T-BID-06 推送
CREATE TABLE bid_push (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id   BIGINT      NOT NULL,
    receiver_id BIGINT      NOT NULL,
    channel     VARCHAR(16) NOT NULL COMMENT 'INNER/DINGTALK',
    send_status VARCHAR(16) NOT NULL DEFAULT 'SENT',
    is_effective TINYINT    NULL,
    sent_at     DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    created_by  VARCHAR(64) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64) NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_push UNIQUE (notice_id, receiver_id, channel)
) COMMENT '标讯推送';

-- T-BID-07 订阅
CREATE TABLE bid_subscribe (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    regions     JSON        NULL,
    owners      JSON        NULL,
    keywords    JSON        NULL,
    amount_min  DECIMAL(18,2) NULL,
    amount_max  DECIMAL(18,2) NULL,
    categories  VARCHAR(200) NULL,
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    deleted     TINYINT     NOT NULL DEFAULT 0,
    created_by  VARCHAR(64) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64) NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bid_sub_user UNIQUE (user_id)
) COMMENT '标讯订阅';

-- T-BID-08 反馈回训
CREATE TABLE bid_feedback (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id    BIGINT      NOT NULL,
    feedback_type VARCHAR(16) NOT NULL COMMENT 'EFFECTIVE/IRRELEVANT/BID_RESULT',
    bid_result   VARCHAR(100) NULL,
    note         VARCHAR(500) NULL,
    operator_id  BIGINT      NOT NULL,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '反馈回训';

-- T-AGT-01 Agent 应用注册
CREATE TABLE agt_registration (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id           VARCHAR(64)  NOT NULL,
    platform            VARCHAR(100) NOT NULL,
    public_key          VARCHAR(2000) NULL,
    ip_whitelist        JSON         NULL,
    allowed_identities  VARCHAR(200) NOT NULL COMMENT 'JSON 数组文本（A-30）',
    quota_daily         INT          NOT NULL DEFAULT 10000,
    quota_monthly_token BIGINT       NULL,
    secret_cipher       VARCHAR(500) NULL COMMENT 'AES 密文',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted             TINYINT      NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)  NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_agt_client_id UNIQUE (client_id)
) COMMENT 'Agent 注册';

-- T-AGT-02 Agent 会话
CREATE TABLE agt_session (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_ref       VARCHAR(64)  NOT NULL,
    registration_id   BIGINT       NOT NULL,
    identity_type     VARCHAR(16)  NOT NULL COMMENT 'EXPERT/WORKER/ASSISTANT',
    on_behalf_of      BIGINT       NULL COMMENT 'WORKER 强制',
    granted_role_code VARCHAR(32)  NULL,
    data_scope        VARCHAR(16)  NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED/REVOKED',
    expires_at        DATETIME     NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NULL,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_agt_session UNIQUE (session_ref)
) COMMENT 'Agent 会话';

-- T-AGT-03 Agent 调用日志
CREATE TABLE agt_call_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT       NOT NULL,
    session_id      BIGINT       NULL,
    capability      VARCHAR(60)  NOT NULL,
    task_id         VARCHAR(64)  NULL,
    request_digest  VARCHAR(500) NULL,
    response_status VARCHAR(16)  NOT NULL,
    block_code      VARCHAR(24)  NULL,
    latency_ms      INT          NULL,
    trace_id        VARCHAR(40)  NULL,
    tokens_used     BIGINT       NULL,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'Agent 调用日志';
CREATE INDEX idx_agt_log_reg ON agt_call_log (registration_id, created_at);
CREATE INDEX idx_agt_log_cap ON agt_call_log (capability, response_status);

-- ---------- 权限授予：R03 系统管理员承接标讯源/规则/入库（数据治理职责，FR-BID-01~06 管理端） ----------
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (112, 113, 114, 115, 117, 118, 120)
WHERE r.id = 3;

-- R04 商务补齐标讯查看（转商机/订阅/推送已有 116/117）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (112, 115)
WHERE r.id = 4;
