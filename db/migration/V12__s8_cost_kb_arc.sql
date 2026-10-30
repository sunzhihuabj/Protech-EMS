-- =====================================================================
-- ProjLife V12：S8 成本与看板 + 档案（02 册 T-COST 5 + T-KB 7 + T-ARC 4 = 16 张）
-- 依据：V4-05 M12（成本七项/三算/EVM/九板四区/红灯升级/会议）+ V4-02 M13 档案
-- =====================================================================

-- T-COST-01 成本明细
CREATE TABLE cost_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    cost_no       VARCHAR(32)   NOT NULL COMMENT 'CI-{YYYYMM}-{6位}',
    project_id    BIGINT        NOT NULL,
    cost_type     VARCHAR(16)   NOT NULL COMMENT 'E-33 七项',
    cost_category VARCHAR(32)   NULL,
    cost_center   VARCHAR(50)   NULL,
    amount        DECIMAL(18,2) NOT NULL,
    qty           DECIMAL(14,3) NULL,
    unit_price    DECIMAL(18,4) NULL,
    source_type   VARCHAR(24)   NOT NULL,
    source_id     BIGINT        NULL,
    occurred_date DATE          NOT NULL,
    is_backfill   TINYINT       NOT NULL DEFAULT 0,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_cost_no UNIQUE (cost_no)
) COMMENT '成本明细';
CREATE INDEX idx_cost_item_project ON cost_item (project_id, cost_type, occurred_date);

-- T-COST-02 预算
CREATE TABLE cost_budget (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT        NOT NULL,
    version       VARCHAR(16)   NOT NULL DEFAULT 'V1',
    cost_category VARCHAR(32)   NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL,
    target_amount DECIMAL(18,2) NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED',
    approved_by   BIGINT        NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '预算';
CREATE INDEX idx_cbudget_project ON cost_budget (project_id, version);

-- T-COST-03 工时费率
CREATE TABLE cost_labor_rate (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code      VARCHAR(32)   NOT NULL,
    work_center_id BIGINT        NULL,
    hourly_rate    DECIMAL(18,4) NOT NULL,
    effective_from DATE          NOT NULL,
    effective_to   DATE          NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工时费率';

-- T-COST-04 成本快照（唯一：项目+日）
CREATE TABLE cost_snapshot (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id            BIGINT        NOT NULL,
    snapshot_date         DATE          NOT NULL,
    material_cost         DECIMAL(18,2) NOT NULL DEFAULT 0,
    labor_cost            DECIMAL(18,2) NOT NULL DEFAULT 0,
    outsource_cost        DECIMAL(18,2) NOT NULL DEFAULT 0,
    travel_cost           DECIMAL(18,2) NOT NULL DEFAULT 0,
    site_cost             DECIMAL(18,2) NOT NULL DEFAULT 0,
    quality_cost          DECIMAL(18,2) NOT NULL DEFAULT 0,
    mgmt_cost             DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_cost            DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_amount       DECIMAL(18,2) NULL,
    gross_margin_contract DECIMAL(6,4)  NULL,
    gross_margin_stage    DECIMAL(6,4)  NULL,
    gross_margin_cash     DECIMAL(6,4)  NULL,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)   NULL,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_snapshot UNIQUE (project_id, snapshot_date)
) COMMENT '成本快照';

-- T-COST-05 EVM
CREATE TABLE cost_evm (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT        NOT NULL,
    snapshot_date DATE          NOT NULL,
    pv            DECIMAL(18,2) NOT NULL DEFAULT 0,
    ev            DECIMAL(18,2) NOT NULL DEFAULT 0,
    ac            DECIMAL(18,2) NOT NULL DEFAULT 0,
    spi           DECIMAL(6,4)  NULL,
    cpi           DECIMAL(6,4)  NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_evm UNIQUE (project_id, snapshot_date)
) COMMENT 'EVM';

-- T-KB-01 看板（V2 已建表并种子九板，V12 不重复建）

-- T-KB-02 指标定义
CREATE TABLE kb_metric_def (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_code    VARCHAR(32)  NOT NULL,
    board_id       BIGINT       NOT NULL,
    metric_name    VARCHAR(100) NOT NULL,
    formula        VARCHAR(500) NULL,
    source         VARCHAR(200) NULL,
    target         DECIMAL(18,2) NULL,
    threshold_json JSON         NULL,
    is_manual      TINYINT      NOT NULL DEFAULT 0,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_metric_code UNIQUE (metric_code)
) COMMENT '指标定义';

-- T-KB-03 指标快照
CREATE TABLE kb_metric_snapshot (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_code  VARCHAR(32)   NOT NULL,
    period       VARCHAR(16)   NOT NULL,
    metric_value DECIMAL(18,2) NULL,
    target_value DECIMAL(18,2) NULL,
    status       VARCHAR(8)    NOT NULL DEFAULT 'GREEN' COMMENT 'GREEN/YELLOW/RED',
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_metric_snap UNIQUE (metric_code, period)
) COMMENT '指标快照';

-- T-KB-04 卡片（乐观锁，五要素）
CREATE TABLE kb_card (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_no      VARCHAR(24)   NOT NULL COMMENT 'CARD-{YYYYMMDD}-{3位}',
    board_id     BIGINT        NOT NULL,
    title        VARCHAR(300)  NOT NULL COMMENT '五要素 1：事项',
    owner_id     BIGINT        NOT NULL COMMENT '五要素 2：责任人',
    promise_date DATE          NOT NULL COMMENT '五要素 3：承诺日',
    status       VARCHAR(16)   NOT NULL DEFAULT 'TODO' COMMENT '五要素 4：TODO/DOING/DONE/BLOCKED/ESCALATED',
    block_reason VARCHAR(500)  NULL COMMENT '五要素 5：卡点说明（红灯必填）',
    is_backfill  TINYINT       NOT NULL DEFAULT 0,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version      INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_card_no UNIQUE (card_no)
) COMMENT '看板卡片';
CREATE INDEX idx_card_board ON kb_card (board_id, status);

-- T-KB-05 卡片流转日志
CREATE TABLE kb_card_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id     BIGINT       NOT NULL,
    from_status VARCHAR(16)  NOT NULL,
    to_status   VARCHAR(16)  NOT NULL,
    action      VARCHAR(100) NOT NULL,
    remark      VARCHAR(500) NULL,
    operator_id BIGINT       NOT NULL,
    is_backfill TINYINT      NOT NULL DEFAULT 0,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '卡片流转';
CREATE INDEX idx_clog_card ON kb_card_log (card_id);

-- T-KB-06 升级记录
CREATE TABLE kb_escalation (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id      BIGINT      NOT NULL,
    level        VARCHAR(16) NOT NULL COMMENT 'DAY3/CROSS24/RESP48',
    escalated_at DATETIME    NOT NULL,
    notified_ids VARCHAR(200) NULL,
    handled_at   DATETIME    NULL,
    handled_by   BIGINT      NULL,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '升级记录';
CREATE INDEX idx_esc_card ON kb_escalation (card_id, level);

-- T-KB-07 会议
CREATE TABLE kb_meeting (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id         BIGINT       NOT NULL,
    meeting_date     DATE         NOT NULL,
    duration_minutes INT          NOT NULL DEFAULT 30,
    attendees        VARCHAR(500) NULL,
    red_count        INT          NOT NULL DEFAULT 0,
    reviewed_count   INT          NOT NULL DEFAULT 0,
    closed_count     INT          NOT NULL DEFAULT 0,
    promises_json    JSON         NULL,
    minutes          VARCHAR(2000) NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NULL,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '会议';

-- T-ARC-01 档案
CREATE TABLE arc_archive (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_no    VARCHAR(40)  NOT NULL COMMENT 'PRT-{YYYY}-{门类}-{期限}-{4位}',
    category      VARCHAR(8)   NOT NULL COMMENT 'E-41',
    title         VARCHAR(300) NOT NULL,
    retention     VARCHAR(8)   NOT NULL COMMENT 'E-42',
    archive_year  INT          NOT NULL COMMENT '年度（V4-02 M13，H2 保留字改名 A-26）',
    box_no        VARCHAR(24)  NULL,
    location      VARCHAR(100) NULL,
    dept_id       BIGINT       NULL,
    handler_id    BIGINT       NULL,
    handover_date DATE         NULL,
    contract_id   BIGINT       NULL,
    project_id    BIGINT       NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'IN' COMMENT 'IN/OUT/DESTROYED',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_archive_no UNIQUE (archive_no)
) COMMENT '档案';

-- T-ARC-02 借阅
CREATE TABLE arc_borrow (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    borrow_no   VARCHAR(24) NOT NULL,
    archive_id  BIGINT      NOT NULL,
    borrower_id BIGINT      NOT NULL,
    purpose     VARCHAR(500) NULL,
    approver_id BIGINT      NOT NULL,
    borrow_date DATE        NOT NULL,
    due_date    DATE        NOT NULL,
    return_date DATE        NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'OUT' COMMENT 'OUT/RETURNED/OVERDUE',
    deleted     TINYINT     NOT NULL DEFAULT 0,
    created_by  VARCHAR(64) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64) NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_borrow_no UNIQUE (borrow_no)
) COMMENT '借阅';

-- T-ARC-03 销毁
CREATE TABLE arc_destroy (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    destroy_no        VARCHAR(24) NOT NULL,
    archive_ids       VARCHAR(500) NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    appraisal_opinion VARCHAR(500) NULL,
    signers_json      JSON        NULL,
    approved_by       BIGINT      NOT NULL,
    destroyed_at      DATETIME    NULL,
    supervisor_ids    VARCHAR(200) NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    created_by        VARCHAR(64) NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64) NULL,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_destroy_no UNIQUE (destroy_no)
) COMMENT '销毁';

-- T-ARC-04 移交
CREATE TABLE arc_transfer (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_no  VARCHAR(24) NOT NULL,
    from_dept    BIGINT      NOT NULL,
    to_dept      BIGINT      NOT NULL,
    archive_ids  VARCHAR(500) NOT NULL,
    transfer_date DATE       NOT NULL,
    transferor   BIGINT      NOT NULL,
    receiver     BIGINT      NOT NULL,
    supervisor   BIGINT      NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_transfer_no UNIQUE (transfer_no)
) COMMENT '移交';

-- ---------- 权限与菜单（S8） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(133, 'menu:cost',       '成本管理',   'cost', 'MENU', NULL, 14, 'ACTIVE', 'seed'),
(134, 'cost:view',       '成本查看',   'cost', 'API', NULL, 1, 'ACTIVE', 'seed'),
(135, 'cost:budget',     '预算管理',   'cost', 'API', NULL, 2, 'ACTIVE', 'seed'),
(136, 'menu:kb',         '经营看板',   'kb',  'MENU', NULL, 15, 'ACTIVE', 'seed'),
(137, 'kb:card:manage',  '卡片管理',   'kb',  'API', NULL, 3, 'ACTIVE', 'seed'),
(138, 'kb:board:config', '看板配置',   'kb',  'API', NULL, 4, 'ACTIVE', 'seed'),
(139, 'kb:metric:def',   '指标口径',   'kb',  'API', NULL, 5, 'ACTIVE', 'seed'),
(140, 'menu:arc',        '档案管理',   'arc', 'MENU', NULL, 16, 'ACTIVE', 'seed'),
(141, 'arc:manage',      '档案管理',   'arc', 'API', NULL, 6, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (133, 134, 135)
WHERE r.id IN (1, 2, 15);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (136, 137)
WHERE r.id IN (1, 2, 4, 5, 12, 14);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (136, 138, 139)
WHERE r.id IN (3, 14);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (140, 141)
WHERE r.id IN (3, 18);
