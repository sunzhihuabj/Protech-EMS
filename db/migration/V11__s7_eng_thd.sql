-- =====================================================================
-- ProjLife V11：S7 工程交付与第三方（02 册 T-ENG 16 + T-THD 5 = 21 张）
-- 依据：V4-05 M10/M11（02 册 §4.12/§4.14/§4.20 字段）
-- 硬卡点：BR-03 派工必挂项目、BR-04 必选风场、BR-08 R17 仅小程序（S0 已生效）
-- =====================================================================

-- T-ENG-01 班组
CREATE TABLE eng_crew (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    crew_code           VARCHAR(24)   NOT NULL,
    crew_name           VARCHAR(100)  NOT NULL,
    supplier_id         BIGINT        NULL,
    leader_name         VARCHAR(50)   NULL,
    leader_phone        VARCHAR(30)   NULL,
    qualification_level VARCHAR(24)   NULL,
    score               DECIMAL(5,2)  NULL,
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    blacklist_flag      TINYINT       NOT NULL DEFAULT 0,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)   NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)   NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_crew_code UNIQUE (crew_code)
) COMMENT '施工班组';

-- T-ENG-02 施工人员（身份证密文 FR-ENG-01）
CREATE TABLE eng_worker (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    worker_code           VARCHAR(24)  NOT NULL,
    crew_id               BIGINT       NOT NULL,
    name                  VARCHAR(50)  NOT NULL,
    id_card_cipher        VARCHAR(200) NULL COMMENT 'AES 密文',
    phone                 VARCHAR(30)  NULL,
    certificates_json     JSON         NULL,
    insurance_expire      DATE         NULL COMMENT '过期拒绝授权（UC-ENG-10）',
    training_records_json JSON         NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/FROZEN/EXPIRED',
    deleted               TINYINT      NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)  NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)  NULL,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_worker_code UNIQUE (worker_code)
) COMMENT '施工人员';
CREATE INDEX idx_worker_crew ON eng_worker (crew_id, status);

-- T-ENG-03 项目工程扩展
CREATE TABLE eng_project_ext (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    md_project_id BIGINT      NOT NULL,
    plan_start   DATE         NULL,
    plan_end     DATE         NULL,
    actual_start DATE         NULL,
    actual_end   DATE         NULL,
    manager_id   BIGINT       NULL,
    health       VARCHAR(8)   NULL,
    progress_pct DECIMAL(5,2) NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_ext_project UNIQUE (md_project_id)
) COMMENT '项目工程扩展';

-- T-ENG-04 安装计划
CREATE TABLE eng_install_plan (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id     BIGINT      NOT NULL,
    unit_id        BIGINT      NOT NULL,
    plan_date      DATE        NULL,
    actual_date    DATE        NULL,
    responsible_id BIGINT      NULL,
    content        VARCHAR(500) NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/INSTALLING/TO_DEBUG/DEBUGGING/TO_ACCEPT/ACCEPTED',
    deleted        TINYINT     NOT NULL DEFAULT 0,
    created_by     VARCHAR(64) NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64) NULL,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '安装计划';
CREATE INDEX idx_iplan_project ON eng_install_plan (project_id, status);

-- T-ENG-05 机组（一机一档，乐观锁）
CREATE TABLE eng_unit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_no         VARCHAR(40)  NOT NULL COMMENT 'U-{project_code}-{3位}',
    md_project_id   BIGINT       NOT NULL,
    unit_name       VARCHAR(100) NULL,
    turbine_model   VARCHAR(100) NULL,
    turbine_sn      VARCHAR(80)  NULL,
    product_lines   JSON         NULL,
    install_date    DATE         NULL,
    debug_date      DATE         NULL,
    accept_date     DATE         NULL,
    warranty_start  DATE         NULL,
    warranty_end    DATE         NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'TO_INSTALL' COMMENT 'E-29',
    sbom_header_id  BIGINT       NULL,
    location_desc   VARCHAR(200) NULL,
    qr_code         VARCHAR(64)  NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 0,
    CONSTRAINT uniq_unit_no UNIQUE (unit_no)
) COMMENT '机组';
CREATE INDEX idx_unit_project ON eng_unit (md_project_id, status);

-- T-ENG-06 验收单
CREATE TABLE eng_acceptance (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    accept_no    VARCHAR(24)  NOT NULL,
    unit_id      BIGINT       NOT NULL,
    project_id   BIGINT       NOT NULL,
    accept_type  VARCHAR(8)   NOT NULL COMMENT 'FIRST/PARTIAL/FINAL',
    result       VARCHAR(8)   NOT NULL COMMENT 'PASS/REJECT',
    accept_date  DATE         NULL,
    sign_file_id BIGINT       NULL,
    opinion      VARCHAR(500) NULL,
    acceptor_id  BIGINT       NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_accept_no UNIQUE (accept_no)
) COMMENT '验收单';
CREATE INDEX idx_accept_unit ON eng_acceptance (unit_id, accept_type);

-- T-ENG-07 施工日志
CREATE TABLE eng_daily_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispatch_id     BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    worker_id       BIGINT       NOT NULL,
    log_date        DATE         NOT NULL,
    weather         VARCHAR(20)  NULL,
    content         VARCHAR(2000) NULL,
    photo_ids       JSON         NULL,
    ai_check_result JSON         NULL,
    offline_flag    TINYINT      NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '施工日志';
CREATE INDEX idx_log_dispatch ON eng_daily_log (dispatch_id, log_date);

-- T-ENG-08 质量检查
CREATE TABLE eng_quality_check (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id        BIGINT       NOT NULL,
    dispatch_id    BIGINT       NULL,
    check_type     VARCHAR(24)  NOT NULL,
    check_item     VARCHAR(200) NOT NULL,
    result         VARCHAR(16)  NOT NULL,
    defect_desc    VARCHAR(500) NULL,
    ai_result_json JSON         NULL,
    checker_id     BIGINT       NOT NULL,
    check_time     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '质量检查';

-- T-ENG-09 整改单（乐观锁）
CREATE TABLE eng_rectification (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rect_no         VARCHAR(24)   NOT NULL,
    source_type     VARCHAR(16)   NOT NULL COMMENT 'AI/MANUAL/SUPERVISOR',
    source_id       BIGINT        NULL,
    project_id      BIGINT        NOT NULL,
    unit_id         BIGINT        NULL,
    problem_desc    VARCHAR(1000) NOT NULL,
    photo_ids       JSON          NULL,
    requirement     VARCHAR(1000) NULL,
    deadline        DATE          NULL,
    after_photo_ids JSON          NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DOING/TO_REVIEW/PASSED/CLOSED',
    confirm_by      BIGINT        NULL,
    closed_at       DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_rect_no UNIQUE (rect_no)
) COMMENT '整改单';
CREATE INDEX idx_rect_project ON eng_rectification (project_id, status);

-- T-ENG-10 工程进度
CREATE TABLE eng_progress (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id     BIGINT        NOT NULL,
    wbs_code       VARCHAR(40)   NULL,
    milestone_name VARCHAR(100)  NULL,
    plan_pct       DECIMAL(5,2)  NULL,
    actual_pct     DECIMAL(5,2)  NULL,
    plan_start     DATE          NULL,
    plan_end       DATE          NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工程进度';

-- T-ENG-11 工程预算
CREATE TABLE eng_cost_budget (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT        NOT NULL,
    cost_category VARCHAR(32)   NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL,
    target_amount DECIMAL(18,2) NULL,
    version       VARCHAR(16)   NOT NULL DEFAULT 'V1',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工程预算';

-- T-ENG-12 工程实际成本
CREATE TABLE eng_cost_actual (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT        NOT NULL,
    cost_category VARCHAR(32)  NOT NULL,
    amount       DECIMAL(18,2) NOT NULL,
    voucher_no   VARCHAR(40)   NULL,
    occurred_date DATE         NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工程实际成本';

-- T-ENG-13 安全检查
CREATE TABLE eng_safety_check (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    check_type  VARCHAR(24)  NOT NULL,
    check_date  DATE         NOT NULL,
    hazard_desc VARCHAR(500) NULL,
    photo_ids   JSON         NULL,
    rect_status VARCHAR(16)  NULL,
    checker_id  BIGINT       NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '安全检查';

-- T-ENG-14 现场物料
CREATE TABLE eng_material_issue (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT        NOT NULL,
    dispatch_id BIGINT        NULL,
    material_id BIGINT        NOT NULL,
    issue_qty   DECIMAL(14,3) NOT NULL,
    return_qty  DECIMAL(14,3) NOT NULL DEFAULT 0,
    status      VARCHAR(16)   NOT NULL DEFAULT 'ISSUED',
    deleted     TINYINT       NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)   NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '现场物料';

-- T-ENG-15 派工单（乐观锁，BR-03/04）
CREATE TABLE eng_dispatch (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispatch_no    VARCHAR(32)   NOT NULL COMMENT 'AS-{YYYYMMDD}-{4位}',
    md_project_id  BIGINT        NOT NULL COMMENT 'BR-03',
    farm_id        BIGINT        NOT NULL COMMENT 'BR-04',
    contract_id    BIGINT        NULL,
    ship_order_id  BIGINT        NULL,
    crew_id        BIGINT        NULL,
    leader_id      BIGINT        NOT NULL,
    member_ids     JSON          NULL,
    plan_start     DATE          NOT NULL,
    plan_end       DATE          NOT NULL,
    actual_start   DATETIME      NULL,
    actual_end     DATETIME      NULL,
    unit_ids       JSON          NULL,
    briefing_done  TINYINT       NOT NULL DEFAULT 0,
    safety_done    TINYINT       NOT NULL DEFAULT 0,
    status         VARCHAR(16)   NOT NULL DEFAULT 'TO_DISPATCH' COMMENT 'E-32',
    progress_pct   DECIMAL(5,2)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_dispatch_no UNIQUE (dispatch_no)
) COMMENT '派工单';
CREATE INDEX idx_dispatch_project ON eng_dispatch (md_project_id, status);
CREATE INDEX idx_dispatch_crew ON eng_dispatch (crew_id);

-- T-ENG-16 售后工单（乐观锁，BR-ENG-08）
CREATE TABLE eng_after_sales (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no       VARCHAR(24)   NOT NULL COMMENT 'WO-{YYYYMMDD}-{3位}',
    md_project_id  BIGINT        NOT NULL,
    unit_id        BIGINT        NULL,
    fault_type     VARCHAR(24)   NULL,
    urgency        VARCHAR(8)    NOT NULL DEFAULT 'NORMAL',
    warranty_flag  TINYINT       NOT NULL DEFAULT 0,
    reported_at    DATETIME      NOT NULL,
    responded_at   DATETIME      NULL,
    closed_at      DATETIME      NULL,
    cost_amount    DECIMAL(18,2) NULL,
    handler_id     BIGINT        NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'TO_DISPATCH',
    solution       VARCHAR(1000) NULL,
    customer_confirm TINYINT     NOT NULL DEFAULT 0,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_wo_no UNIQUE (order_no)
) COMMENT '售后工单';

-- T-THD-01 供应商门户账号
CREATE TABLE thd_supplier_account (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id  BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    account_type VARCHAR(16) NOT NULL DEFAULT 'BUSINESS',
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/FROZEN/EXPIRED',
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64) NULL,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_thd_account UNIQUE (supplier_id, user_id)
) COMMENT '供应商门户账号';

-- T-THD-02 施工人员项目授权
CREATE TABLE thd_worker_project (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    worker_id     BIGINT      NOT NULL,
    md_project_id BIGINT      NOT NULL,
    auth_start    DATE        NOT NULL,
    auth_end      DATE        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'GRANTED' COMMENT 'GRANTED/EXPIRED/REVOKED',
    frozen_reason VARCHAR(300) NULL,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    created_by    VARCHAR(64) NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64) NULL,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '人员项目授权';
CREATE INDEX idx_wproj_worker ON thd_worker_project (worker_id, status);

-- T-THD-03 考核评价
CREATE TABLE thd_evaluation (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type    VARCHAR(16)  NOT NULL COMMENT 'SUPPLIER/CREW',
    target_id      BIGINT       NOT NULL,
    period         VARCHAR(16)  NOT NULL,
    quality_score  DECIMAL(5,2) NULL,
    delivery_score DECIMAL(5,2) NULL,
    safety_score   DECIMAL(5,2) NULL,
    service_score  DECIMAL(5,2) NULL,
    total_score    DECIMAL(5,2) NULL,
    grade          VARCHAR(8)   NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_thd_eval UNIQUE (target_type, target_id, period)
) COMMENT '考核评价';

-- T-THD-04 黑名单
CREATE TABLE thd_blacklist (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type    VARCHAR(16)  NOT NULL COMMENT 'SUPPLIER/CREW/WORKER',
    target_id      BIGINT       NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    evidence       VARCHAR(500) NULL,
    approved_by    BIGINT       NOT NULL COMMENT 'R02 审批',
    effective_date DATE         NOT NULL,
    release_date   DATE         NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '黑名单';

-- T-THD-05 送货单
CREATE TABLE thd_delivery_note (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id         BIGINT      NOT NULL,
    note_no       VARCHAR(24) NOT NULL,
    delivery_date DATE        NULL,
    items_json    JSON        NULL,
    printed_at    DATETIME    NULL,
    printed_by    BIGINT      NULL,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    created_by    VARCHAR(64) NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64) NULL,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_note_no UNIQUE (note_no)
) COMMENT '送货单';

-- ---------- 权限与菜单（S7） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(123, 'menu:eng',          '工程交付',   'eng', 'MENU', NULL, 13, 'ACTIVE', 'seed'),
(124, 'eng:crew:manage',   '班组管理',   'eng', 'API', NULL, 1, 'ACTIVE', 'seed'),
(125, 'eng:dispatch',      '派工',       'eng', 'API', NULL, 2, 'ACTIVE', 'seed'),
(126, 'eng:install',       '安装验收',   'eng', 'API', NULL, 3, 'ACTIVE', 'seed'),
(127, 'eng:log',           '施工日志',   'eng', 'API', NULL, 4, 'ACTIVE', 'seed'),
(128, 'eng:rect:manage',   '整改管理',   'eng', 'API', NULL, 5, 'ACTIVE', 'seed'),
(129, 'eng:expense',       '现场费用',   'eng', 'API', NULL, 6, 'ACTIVE', 'seed'),
(130, 'eng:after:manage',  '售后管理',   'eng', 'API', NULL, 7, 'ACTIVE', 'seed'),
(131, 'thd:portal',        '第三方门户', 'thd', 'API', NULL, 8, 'ACTIVE', 'seed'),
(132, 'thd:account',       '门户账号',   'thd', 'API', NULL, 9, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 123 AND 130
WHERE r.id = 14;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (123, 131, 132)
WHERE r.id IN (16, 18);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (123, 130)
WHERE r.id IN (1, 2);

-- ---------- 演示用户：第三方门户（R16） ----------
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by) VALUES
(13, 'E0013', 'portal@demo', '供应商门户', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000013', 10, '门户', 'SUPPLIER', 1, 'ACTIVE', 0, '第三方门户演示（R16 SELF）', 'seed');
INSERT INTO sys_user_role (user_id, role_id, created_by) VALUES (13, 16, 'seed');
