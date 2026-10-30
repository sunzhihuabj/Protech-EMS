-- =====================================================================
-- ProjLife V9：S6 质量与设备（02 册 T-QMS 9 + T-EAM 5 = 14 张）
-- 依据：02 册 §4.11 全字段 + §4.20 + 09 册全册
-- =====================================================================

-- T-QMS-01 检验标准库
CREATE TABLE qms_inspection_standard (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_category VARCHAR(16) NOT NULL,
    inspection_type  VARCHAR(8)  NOT NULL COMMENT 'E-27',
    aql_level        VARCHAR(10) NULL,
    sample_plan_json JSON        NULL,
    items_json       JSON        NULL,
    version          VARCHAR(16) NOT NULL DEFAULT 'V1.0',
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted          TINYINT     NOT NULL DEFAULT 0,
    created_by       VARCHAR(64) NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64) NULL,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '检验标准库';

-- T-QMS-02 检验单（乐观锁）
CREATE TABLE qms_inspection (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_no  VARCHAR(32)   NOT NULL,
    inspection_type VARCHAR(8)   NOT NULL COMMENT 'E-27 IQC/IPQC/OQC',
    source_type    VARCHAR(24)   NOT NULL,
    source_id      BIGINT        NOT NULL,
    material_id    BIGINT        NULL,
    batch_no       VARCHAR(40)   NULL,
    sample_size    INT           NOT NULL,
    defect_count   INT           NOT NULL,
    aql_level      VARCHAR(10)   NULL,
    result         VARCHAR(16)   NOT NULL COMMENT 'E-28',
    inspector_id   BIGINT        NOT NULL,
    inspection_time DATETIME     NOT NULL,
    ai_result_json JSON          NULL,
    ai_confidence  DECIMAL(5,2)  NULL,
    human_corrected TINYINT      NOT NULL DEFAULT 0,
    photo_file_ids JSON          NULL,
    remark         VARCHAR(500)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_inspection_no UNIQUE (inspection_no)
) COMMENT '检验单';
CREATE INDEX idx_qc_source ON qms_inspection (source_type, source_id);
CREATE INDEX idx_qc_type_time ON qms_inspection (inspection_type, inspection_time);

-- T-QMS-03 检验项
CREATE TABLE qms_inspection_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT       NOT NULL,
    item_name     VARCHAR(100) NOT NULL,
    standard_value VARCHAR(100) NULL,
    actual_value  VARCHAR(100) NULL,
    is_qualified  TINYINT      NULL,
    defect_desc   VARCHAR(300) NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '检验项';
CREATE INDEX idx_qitem_inspection ON qms_inspection_item (inspection_id);

-- T-QMS-04 SPC 数据
CREATE TABLE qms_spc_data (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    characteristic     VARCHAR(60)   NOT NULL,
    sample_time        DATETIME      NOT NULL,
    subgroup_no        INT           NULL,
    value_x            DECIMAL(18,4) NULL,
    x_bar              DECIMAL(18,4) NULL,
    r_value            DECIMAL(18,4) NULL,
    usl                DECIMAL(18,4) NULL,
    lsl                DECIMAL(18,4) NULL,
    cl                 DECIMAL(18,4) NULL,
    ucl                DECIMAL(18,4) NULL,
    lcl                DECIMAL(18,4) NULL,
    is_out_of_control  TINYINT       NOT NULL DEFAULT 0,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)   NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)   NULL,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'SPC 数据';
CREATE INDEX idx_spc_char ON qms_spc_data (characteristic, sample_time);

-- T-QMS-05 FMEA
CREATE TABLE qms_fmea (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id         BIGINT        NULL,
    process_name       VARCHAR(100)  NOT NULL,
    failure_mode       VARCHAR(300)  NOT NULL,
    failure_effect     VARCHAR(300)  NULL,
    severity           INT           NOT NULL DEFAULT 1,
    failure_cause      VARCHAR(300)  NULL,
    occurrence         INT           NOT NULL DEFAULT 1,
    current_control    VARCHAR(300)  NULL,
    detection          INT           NOT NULL DEFAULT 1,
    rpn                INT           NOT NULL DEFAULT 0,
    recommended_action VARCHAR(500)  NULL,
    embedding          TEXT          NULL COMMENT '向量预留（相似检索）',
    deleted            TINYINT       NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)   NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)   NULL,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'FMEA';

-- T-QMS-06 8D 报告（乐观锁）
CREATE TABLE qms_8d (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_no      VARCHAR(32)   NOT NULL,
    problem_desc   VARCHAR(1000) NOT NULL,
    d1_team        VARCHAR(300)  NULL,
    d2_problem     VARCHAR(1000) NULL,
    d3_containment VARCHAR(1000) NULL,
    d4_root_cause  VARCHAR(1000) NULL,
    d5_corrective  VARCHAR(1000) NULL,
    d6_verify      VARCHAR(1000) NULL,
    d7_preventive  VARCHAR(1000) NULL,
    d8_congratulate VARCHAR(500) NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
    owner_id       BIGINT        NOT NULL,
    closed_at      DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_8d_no UNIQUE (report_no)
) COMMENT '8D 报告';

-- T-QMS-07 MRB 评审
CREATE TABLE qms_mrb (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id  BIGINT        NOT NULL,
    disposition    VARCHAR(16)   NOT NULL COMMENT 'RETURN/CONCESSION/REWORK/SCRAP',
    review_opinion VARCHAR(1000) NULL,
    reviewer_ids   VARCHAR(200)  NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    cost_impact    DECIMAL(18,2) NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'MRB 评审';

-- T-QMS-08 量具
CREATE TABLE qms_gauge (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    gauge_code          VARCHAR(24)  NOT NULL,
    gauge_name          VARCHAR(100) NOT NULL,
    model               VARCHAR(60)  NULL,
    precision_class     VARCHAR(60)  NULL,
    calibrate_cycle_days INT         NOT NULL DEFAULT 365,
    last_calibrate_date DATE         NULL,
    next_calibrate_date DATE         NULL,
    location            VARCHAR(100) NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted             TINYINT      NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)  NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_gauge_code UNIQUE (gauge_code)
) COMMENT '量具';

-- T-QMS-09 校准记录
CREATE TABLE qms_calibration (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    gauge_id       BIGINT      NOT NULL,
    calibrate_date DATE        NOT NULL,
    result         VARCHAR(16) NOT NULL,
    certificate_no VARCHAR(60) NULL,
    agency         VARCHAR(100) NULL,
    next_date      DATE        NULL,
    file_id        BIGINT      NULL,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    created_by     VARCHAR(64) NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64) NULL,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '校准记录';

-- T-EAM-01 设备台账
CREATE TABLE eam_asset (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_code      VARCHAR(24)  NOT NULL,
    asset_name      VARCHAR(100) NOT NULL,
    asset_type      VARCHAR(24)  NULL,
    model           VARCHAR(60)  NULL,
    serial_no       VARCHAR(80)  NULL,
    manufacturer    VARCHAR(100) NULL,
    purchase_date   DATE         NULL,
    warranty_expire DATE         NULL,
    location        VARCHAR(100) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    dept_id         BIGINT       NULL,
    keeper_id       BIGINT       NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_asset_code UNIQUE (asset_code)
) COMMENT '设备台账';

-- T-EAM-02 维护计划
CREATE TABLE eam_maintenance_plan (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id       BIGINT      NOT NULL,
    plan_type      VARCHAR(16) NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
    content        VARCHAR(500) NULL,
    next_date      DATE        NULL,
    cycle_days     INT         NOT NULL DEFAULT 30,
    responsible_id BIGINT      NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted        TINYINT     NOT NULL DEFAULT 0,
    created_by     VARCHAR(64) NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64) NULL,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '维护计划';

-- T-EAM-03 维护工单
CREATE TABLE eam_maintenance_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no       VARCHAR(24)   NOT NULL,
    asset_id       BIGINT        NOT NULL,
    order_type     VARCHAR(16)   NOT NULL COMMENT 'PREVENTIVE/REPAIR/INSPECTION',
    content        VARCHAR(500)  NULL,
    planned_date   DATE          NULL,
    actual_date    DATE          NULL,
    labor_hours    DECIMAL(8,2)  NULL,
    cost           DECIMAL(18,2) NULL,
    spare_parts_json JSON        NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/CLOSED',
    technician_id  BIGINT        NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_eam_order_no UNIQUE (order_no)
) COMMENT '维护工单';

-- T-EAM-04 备件
CREATE TABLE eam_spare_part (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id     BIGINT        NOT NULL,
    material_id  BIGINT        NOT NULL,
    qty          DECIMAL(14,3) NOT NULL DEFAULT 0,
    safety_stock DECIMAL(14,3) NULL,
    location_id  BIGINT        NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '备件';

-- T-EAM-05 故障记录
CREATE TABLE eam_failure (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id         BIGINT        NOT NULL,
    failure_time     DATETIME      NOT NULL,
    repair_time      DATETIME      NULL,
    fault_desc       VARCHAR(500)  NULL,
    cause            VARCHAR(300)  NULL,
    downtime_minutes INT           NULL,
    repair_cost      DECIMAL(18,2) NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '故障记录';
CREATE INDEX idx_failure_asset ON eam_failure (asset_id, failure_time);

-- ---------- 演示用户：质量检验员（R13） ----------
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by) VALUES
(12, 'E0012', 'qc@demo', '质量检验员', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000012', 9, '检验员', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed');
INSERT INTO sys_user_role (user_id, role_id, created_by) VALUES (12, 13, 'seed');

-- ---------- 权限与菜单（S6） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(105, 'menu:qms',           '质量管理',   'qms', 'MENU', NULL, 11, 'ACTIVE', 'seed'),
(106, 'qms:inspect',        '检验执行',   'qms', 'API', NULL, 1, 'ACTIVE', 'seed'),
(107, 'qms:8d:manage',      '8D 管理',    'qms', 'API', NULL, 2, 'ACTIVE', 'seed'),
(108, 'qms:gauge:manage',   '量具管理',   'qms', 'API', NULL, 3, 'ACTIVE', 'seed'),
(109, 'menu:eam',           '设备管理',   'eam', 'MENU', NULL, 12, 'ACTIVE', 'seed'),
(110, 'eam:manage',         '设备维护',   'eam', 'API', NULL, 4, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 105 AND 110
WHERE r.id = 13;
