-- =====================================================================
-- ProjLife V8：S5 生产与排程（02 册 T-MES 8 + T-APS 4 = 12 张）
-- 依据：02 册 §4.10 全字段 + §4.20 + 08 册全册（C-11 工序级裁决）
-- =====================================================================

-- T-MES-01 生产订单（乐观锁）
CREATE TABLE mes_production_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    mo_no          VARCHAR(24)   NOT NULL COMMENT 'MO-{YYYYMM}-{4位}',
    project_id     BIGINT        NOT NULL,
    mo_type        VARCHAR(16)   NOT NULL COMMENT 'PROTOTYPE/BATCH/TEMP',
    bom_header_id  BIGINT        NOT NULL,
    plan_qty       DECIMAL(14,3) NOT NULL,
    finished_qty   DECIMAL(14,3) NOT NULL DEFAULT 0,
    plan_start     DATE          NULL,
    plan_end       DATE          NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/CLOSED',
    priority       INT           NOT NULL DEFAULT 5,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_mo_no UNIQUE (mo_no)
) COMMENT '生产订单';
CREATE INDEX idx_mo_project ON mes_production_order (project_id, status);

-- T-MES-02 工序工单（乐观锁，E-26 状态机）
CREATE TABLE mes_process_order (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_no          VARCHAR(32)   NOT NULL COMMENT 'WS-{MO后6位}-{2位}',
    production_order_id BIGINT        NOT NULL,
    process_seq         INT           NOT NULL,
    process_code        VARCHAR(32)   NOT NULL,
    process_name        VARCHAR(100)  NOT NULL,
    process_type        VARCHAR(12)   NOT NULL DEFAULT 'SERIAL' COMMENT 'E-25',
    work_center_id      BIGINT        NULL,
    standard_hours      DECIMAL(8,2)  NULL,
    planned_qty         DECIMAL(14,3) NOT NULL,
    good_qty            DECIMAL(14,3) NOT NULL DEFAULT 0,
    defect_qty          DECIMAL(14,3) NOT NULL DEFAULT 0,
    scrap_qty           DECIMAL(14,3) NOT NULL DEFAULT 0,
    status              VARCHAR(16)   NOT NULL DEFAULT 'TODO' COMMENT 'E-26',
    need_inspection     TINYINT       NOT NULL DEFAULT 0,
    start_time          DATETIME      NULL,
    end_time            DATETIME      NULL,
    operator_id         BIGINT        NULL,
    locked_reason       VARCHAR(300)  NULL,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)   NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)   NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_process_no UNIQUE (process_no)
) COMMENT '工序工单';
CREATE INDEX idx_process_po ON mes_process_order (production_order_id, process_seq);
CREATE INDEX idx_process_status ON mes_process_order (status);

-- T-MES-03 报工记录
CREATE TABLE mes_process_report (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_order_id BIGINT        NOT NULL,
    report_type      VARCHAR(12)   NOT NULL COMMENT 'START/FINISH',
    good_qty         DECIMAL(14,3) NULL,
    defect_qty       DECIMAL(14,3) NULL,
    scrap_qty        DECIMAL(14,3) NULL,
    work_hours       DECIMAL(8,2)  NULL,
    operator_id      BIGINT        NOT NULL,
    report_time      DATETIME      NOT NULL,
    is_backfill      TINYINT       NOT NULL DEFAULT 0,
    remark           VARCHAR(300)  NULL,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '报工记录';
CREATE INDEX idx_report_process ON mes_process_report (process_order_id, report_time);

-- T-MES-04 工艺参数
CREATE TABLE mes_process_param (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_order_id BIGINT        NOT NULL,
    param_name       VARCHAR(60)   NOT NULL,
    param_value      VARCHAR(100)  NOT NULL,
    standard_min     DECIMAL(18,4) NULL,
    standard_max     DECIMAL(18,4) NULL,
    is_qualified     TINYINT       NULL,
    record_time      DATETIME      NOT NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工艺参数';
CREATE INDEX idx_param_process ON mes_process_param (process_order_id);

-- T-MES-05 Andon（不软删）
CREATE TABLE mes_andon (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    andon_no            VARCHAR(24)  NOT NULL,
    production_order_id BIGINT       NULL,
    process_order_id    BIGINT       NULL,
    alert_type          VARCHAR(16)  NOT NULL COMMENT 'EQUIPMENT/MATERIAL/QUALITY/SAFETY',
    alert_level         INT          NOT NULL DEFAULT 1 COMMENT '1-3（FR-MES-30 分级升级）',
    content             VARCHAR(500) NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESPONDED/RESOLVED',
    response_at         DATETIME     NULL,
    resolve_at          DATETIME     NULL,
    escalate_at         DATETIME     NULL,
    handler_id          BIGINT       NULL,
    created_by          VARCHAR(64)  NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_andon_no UNIQUE (andon_no)
) COMMENT 'Andon';
CREATE INDEX idx_andon_status ON mes_andon (status, alert_level);

-- T-MES-06 领料
CREATE TABLE mes_material_issue (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue_no            VARCHAR(24)   NOT NULL,
    production_order_id BIGINT        NOT NULL,
    material_id         BIGINT        NOT NULL,
    req_qty             DECIMAL(14,3) NOT NULL,
    issued_qty          DECIMAL(14,3) NOT NULL DEFAULT 0,
    return_qty          DECIMAL(14,3) NOT NULL DEFAULT 0,
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    warehouse_id        BIGINT        NULL,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)   NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)   NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_issue_no UNIQUE (issue_no)
) COMMENT '领料';

-- T-MES-07 物料防错
CREATE TABLE mes_material_check (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_order_id      BIGINT       NOT NULL,
    material_id           BIGINT       NULL,
    scanned_code          VARCHAR(100) NOT NULL,
    expected_material_id  BIGINT       NULL,
    is_match              TINYINT      NOT NULL,
    check_time            DATETIME     NOT NULL,
    operator_id           BIGINT       NOT NULL,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)  NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)  NULL,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '物料防错';
CREATE INDEX idx_check_process ON mes_material_check (process_order_id, check_time);

-- T-MES-08 工作中心
CREATE TABLE mes_work_center (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    wc_code          VARCHAR(24)   NOT NULL,
    wc_name          VARCHAR(100)  NOT NULL,
    wc_type          VARCHAR(24)   NULL,
    capacity_per_day DECIMAL(14,3) NULL,
    dept_id          BIGINT        NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_wc_code UNIQUE (wc_code)
) COMMENT '工作中心';

-- T-APS-01 排产单
CREATE TABLE aps_schedule (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_no      VARCHAR(32)  NOT NULL,
    schedule_version INT          NOT NULL DEFAULT 1,
    schedule_type    VARCHAR(16)  NOT NULL DEFAULT 'MASTER',
    start_date       DATE         NULL,
    end_date         DATE         NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'CALCULATING' COMMENT 'CALCULATING/PUBLISHED/ADJUSTED',
    algorithm        VARCHAR(8)   NOT NULL DEFAULT 'RULE' COMMENT 'RULE/GA（GA 二期 A-21）',
    created_by       VARCHAR(64)  NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NULL,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_schedule_no UNIQUE (schedule_no)
) COMMENT '排产单';

-- T-APS-02 排产明细
CREATE TABLE aps_schedule_detail (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id         BIGINT      NOT NULL,
    production_order_id BIGINT      NOT NULL,
    process_order_id    BIGINT      NOT NULL,
    resource_id         BIGINT      NULL,
    planned_start       DATETIME    NOT NULL,
    planned_end         DATETIME    NOT NULL,
    setup_minutes       INT         NOT NULL DEFAULT 0,
    run_minutes         INT         NOT NULL DEFAULT 0,
    status              VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    deleted             TINYINT     NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NULL,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '排产明细';
CREATE INDEX idx_sdetail_schedule ON aps_schedule_detail (schedule_id);
CREATE INDEX idx_sdetail_resource ON aps_schedule_detail (resource_id, planned_start);

-- T-APS-03 齐套检查
CREATE TABLE aps_material_check (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id             BIGINT        NOT NULL,
    production_order_id     BIGINT        NOT NULL,
    material_id             BIGINT        NOT NULL,
    required_qty            DECIMAL(14,3) NOT NULL,
    available_qty           DECIMAL(14,3) NOT NULL DEFAULT 0,
    shortage_qty            DECIMAL(14,3) NOT NULL DEFAULT 0,
    earliest_available_date DATE          NULL,
    is_ready                TINYINT       NOT NULL DEFAULT 0,
    deleted                 TINYINT       NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)   NULL,
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64)   NULL,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '齐套检查';
CREATE INDEX idx_mcheck_schedule ON aps_material_check (schedule_id);

-- T-APS-04 产能
CREATE TABLE aps_capacity (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id      BIGINT        NOT NULL,
    resource_type    VARCHAR(16)   NOT NULL COMMENT 'EQUIPMENT/PERSON/MOLD',
    capacity_per_day DECIMAL(14,3) NOT NULL,
    calendar_json    JSON          NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '产能';

-- ---------- 演示用户：生产操作工（R12） ----------
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by) VALUES
(11, 'E0011', 'prod@demo', '生产操作工', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000011', 8, '装配工', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed');
INSERT INTO sys_user_role (user_id, role_id, created_by) VALUES (11, 12, 'seed');

-- ---------- 权限与菜单（S5） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(99,  'menu:mes',        '生产管理',   'mes', 'MENU', NULL, 10, 'ACTIVE', 'seed'),
(100, 'mes:order:create','生产订单',   'mes', 'API', NULL, 1, 'ACTIVE', 'seed'),
(101, 'mes:process:work','工序开工报工','mes', 'API', NULL, 2, 'ACTIVE', 'seed'),
(102, 'mes:qc:release',  '检验放行',   'mes', 'API', NULL, 3, 'ACTIVE', 'seed'),
(103, 'mes:andon:raise', 'Andon 报警', 'mes', 'API', NULL, 4, 'ACTIVE', 'seed'),
(104, 'aps:schedule',    '排产',       'aps', 'API', NULL, 5, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 99 AND 104
WHERE r.id = 12;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (99, 102)
WHERE r.id = 13;
