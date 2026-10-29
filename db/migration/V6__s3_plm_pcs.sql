-- =====================================================================
-- ProjLife V6：S3 研发与工艺（02 册 T-PLM 10 + T-PCS 5 = 15 张）
-- 依据：02 册 §4.7-4.8 全字段 + §4.20 简明字段 + 06 册全册
-- 附注：bom_qc_result 未在 02 册登记（A-16：13 条校验实时计算不落表）
-- =====================================================================

-- T-PLM-01 勘测
CREATE TABLE plm_survey (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    survey_type  VARCHAR(24)  NOT NULL,
    survey_date  DATE         NULL,
    surveyor_id  BIGINT       NULL,
    content_json JSON         NULL,
    attach_ids   VARCHAR(200) NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '勘测报告';
CREATE INDEX idx_survey_project ON plm_survey (project_id);

-- T-PLM-02 方案
CREATE TABLE plm_scheme (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    scheme_no         VARCHAR(32)  NOT NULL,
    scheme_name       VARCHAR(200) NOT NULL,
    scheme_type       VARCHAR(24)  NULL,
    version           VARCHAR(16)  NOT NULL DEFAULT 'V1.0',
    review_status     VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    review_opinion    VARCHAR(1000) NULL,
    review_meeting_id BIGINT       NULL,
    attach_ids        VARCHAR(200) NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NULL,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '技术方案';

-- T-PLM-03 设计文档
CREATE TABLE plm_design_doc (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    design_type   VARCHAR(16)  NOT NULL COMMENT 'ELECTRIC/MECHANICAL',
    doc_type      VARCHAR(24)  NOT NULL,
    doc_name      VARCHAR(200) NOT NULL,
    version       VARCHAR(16)  NOT NULL DEFAULT 'V1.0',
    file_id       BIGINT       NULL,
    reviewer_id   BIGINT       NULL,
    review_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    is_locked     TINYINT      NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '设计文档';
CREATE INDEX idx_ddoc_project ON plm_design_doc (project_id, design_type);

-- T-PLM-04 图纸审核记录
CREATE TABLE plm_design_review (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_doc_id   BIGINT      NOT NULL,
    review_type     VARCHAR(24) NULL,
    reviewer_id     BIGINT      NOT NULL,
    result          VARCHAR(16) NOT NULL COMMENT 'PASS/REJECT',
    opinion         VARCHAR(1000) NULL,
    reviewed_at     DATETIME    NULL,
    ai_check_result JSON        NULL,
    deleted         TINYINT     NOT NULL DEFAULT 0,
    created_by      VARCHAR(64) NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) NULL,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '图纸审核';

-- T-PLM-05 BOM 头（二维矩阵核心；乐观锁与业务 version 同名冲突，用 BaseEntity，A-19）
CREATE TABLE bom_header (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_no               VARCHAR(48)  NOT NULL,
    bom_form             VARCHAR(4)   NOT NULL COMMENT 'E-17 A/B/C',
    bom_view             VARCHAR(8)   NOT NULL COMMENT 'E-18 EBOM/MBOM/SBOM',
    product_line         VARCHAR(16)  NOT NULL COMMENT 'E-08 单值',
    product_model        VARCHAR(100) NOT NULL,
    md_project_id        BIGINT       NULL,
    base_bom_id          BIGINT       NULL,
    source_bom_id        BIGINT       NULL,
    derived_from         BIGINT       NULL COMMENT '项目派生源 Golden BOM',
    version              VARCHAR(16)  NOT NULL DEFAULT 'V1.0',
    is_current           TINYINT      NOT NULL DEFAULT 0,
    status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'E-19',
    mbom_not_applicable  TINYINT      NOT NULL DEFAULT 0,
    param_json           JSON         NULL,
    point_json           JSON         NULL,
    released_at          DATETIME     NULL,
    released_by          VARCHAR(64)  NULL,
    remark               VARCHAR(500) NULL,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)  NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)  NULL,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bom_no UNIQUE (bom_no)
) COMMENT 'BOM 头';
CREATE INDEX idx_bom_key ON bom_header (bom_form, bom_view, product_line, product_model, is_current);
CREATE INDEX idx_bom_project ON bom_header (md_project_id);

-- T-PLM-06 BOM 行
CREATE TABLE bom_line (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_header_id         BIGINT        NOT NULL,
    line_no               VARCHAR(20)   NOT NULL,
    material_id           BIGINT        NOT NULL,
    material_code         VARCHAR(16)   NULL COMMENT '编码快照',
    qty_per_unit          DECIMAL(14,3) NOT NULL,
    unit                  VARCHAR(10)   NOT NULL,
    loss_rate             DECIMAL(6,4)  NULL,
    material_category     VARCHAR(16)   NULL,
    point_code            VARCHAR(40)   NULL,
    process_doc_id        BIGINT        NULL,
    is_project_special    TINYINT       NOT NULL DEFAULT 0,
    substitute_material_id BIGINT       NULL,
    source_type           VARCHAR(16)   NULL,
    remark                VARCHAR(300)  NULL,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)   NULL,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_bom_line UNIQUE (bom_header_id, line_no, material_id, deleted)
) COMMENT 'BOM 行';
CREATE INDEX idx_bomline_material ON bom_line (material_id);

-- T-PLM-07 ECN 变更（乐观锁，与 ECO 统一编号）
CREATE TABLE bom_change (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_no         VARCHAR(24)   NOT NULL,
    bom_header_id     BIGINT        NOT NULL,
    change_type       VARCHAR(16)   NOT NULL COMMENT '设计/工艺/替换料/纠错',
    change_reason     VARCHAR(1000) NOT NULL,
    impact_scope      VARCHAR(1000) NULL,
    impact_confirmed  TINYINT       NOT NULL DEFAULT 0,
    old_disposal_plan VARCHAR(1000) NULL COMMENT 'BR-09 必填',
    new_version       VARCHAR(16)   NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/COUNTERSIGN/APPROVED/PUBLISHED/REJECTED',
    approved_by       BIGINT        NULL,
    published_at      DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)   NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_bchange_no UNIQUE (change_no)
) COMMENT 'BOM 变更';

-- T-PLM-08 变更影响清单
CREATE TABLE bom_change_impact (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_id   BIGINT        NOT NULL,
    impact_type VARCHAR(16)   NOT NULL COMMENT 'STOCK/WIP/ON_WAY/PROCESS/PROJECT',
    material_id BIGINT        NULL,
    qty         DECIMAL(14,3) NULL,
    amount      DECIMAL(18,2) NULL,
    action      VARCHAR(300)  NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)   NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '变更影响';
CREATE INDEX idx_impact_change ON bom_change_impact (change_id);

-- T-PLM-09 标准件库
CREATE TABLE plm_std_part (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    part_code    VARCHAR(16)  NOT NULL,
    part_name    VARCHAR(200) NOT NULL,
    spec         VARCHAR(200) NULL,
    category     VARCHAR(24)  NULL,
    prefer_level VARCHAR(8)   NULL,
    usage_count  INT          NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_std_part_code UNIQUE (part_code)
) COMMENT '标准件库';

-- T-PLM-10 需求追溯
CREATE TABLE plm_requirement_trace (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    requirement_desc VARCHAR(1000) NOT NULL,
    source           VARCHAR(200) NULL,
    design_param     VARCHAR(500) NULL,
    verify_method    VARCHAR(500) NULL,
    verify_result    VARCHAR(500) NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    deleted          TINYINT      NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NULL,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '需求追溯';

-- T-PCS-01 工艺文件
CREATE TABLE pcs_process_doc (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_no         VARCHAR(32)  NOT NULL,
    product_line   VARCHAR(16)  NOT NULL,
    doc_type       VARCHAR(24)  NOT NULL,
    doc_name       VARCHAR(200) NOT NULL,
    version        VARCHAR(16)  NOT NULL DEFAULT 'V1.0',
    is_temp        TINYINT      NOT NULL DEFAULT 0,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/PAUSED/ARCHIVED',
    bom_header_id  BIGINT       NULL,
    file_id        BIGINT       NULL,
    approved_by    BIGINT       NULL,
    published_at   DATETIME     NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_pdoc_no UNIQUE (doc_no)
) COMMENT '工艺文件';
CREATE INDEX idx_pdoc_line ON pcs_process_doc (product_line, status);

-- T-PCS-02 工艺路线
CREATE TABLE pcs_route (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_header_id  BIGINT       NOT NULL,
    route_no       VARCHAR(32)  NOT NULL,
    route_name     VARCHAR(200) NOT NULL,
    version        VARCHAR(16)  NOT NULL DEFAULT 'V1.0',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工艺路线';

-- T-PCS-03 工序步骤
CREATE TABLE pcs_route_step (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id        BIGINT        NOT NULL,
    step_seq        INT           NOT NULL,
    step_name       VARCHAR(100)  NOT NULL,
    work_center_id  BIGINT        NULL,
    standard_hours  DECIMAL(8,2)  NULL,
    is_key          TINYINT       NOT NULL DEFAULT 0,
    need_inspection TINYINT       NOT NULL DEFAULT 0,
    description     VARCHAR(500)  NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '工序步骤';

-- T-PCS-04 标准工时
CREATE TABLE pcs_worktime (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_model  VARCHAR(100) NOT NULL,
    step_name      VARCHAR(100) NOT NULL,
    standard_hours DECIMAL(8,2) NOT NULL,
    labor_rate_id  BIGINT       NULL,
    effective_date DATE         NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '标准工时';

-- T-PCS-05 线缆清册
CREATE TABLE pcs_cable_list (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT        NOT NULL,
    cable_no   VARCHAR(32)   NOT NULL,
    from_point VARCHAR(100)  NOT NULL,
    to_point   VARCHAR(100)  NOT NULL,
    model      VARCHAR(100)  NULL,
    length     DECIMAL(10,2) NULL,
    remark     VARCHAR(300)  NULL,
    deleted    TINYINT       NOT NULL DEFAULT 0,
    created_by VARCHAR(64)   NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)   NULL,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '线缆清册';
CREATE INDEX idx_cable_project ON pcs_cable_list (project_id);

-- ---------- 权限与菜单（S3） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(75, 'menu:plm',        '研发与BOM', 'plm', 'MENU', NULL, 7, 'ACTIVE', 'seed'),
(76, 'plm:doc:manage',  '设计文档',  'plm', 'API', NULL, 1, 'ACTIVE', 'seed'),
(77, 'bom:create',      'BOM 新建',  'bom', 'API', NULL, 2, 'ACTIVE', 'seed'),
(78, 'bom:edit',        'BOM 编辑',  'bom', 'API', NULL, 3, 'ACTIVE', 'seed'),
(79, 'bom:release',     'BOM 发布',  'bom', 'API', NULL, 4, 'ACTIVE', 'seed'),
(80, 'bom:ecn:create',  'ECN 发起',  'bom', 'API', NULL, 5, 'ACTIVE', 'seed'),
(81, 'bom:ecn:approve', 'ECN 审批',  'bom', 'API', NULL, 6, 'ACTIVE', 'seed'),
(82, 'pcs:manage',      '工艺管理',  'pcs', 'API', NULL, 7, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 75 AND 82
WHERE r.id IN (6, 8);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (75, 78, 80, 81)
WHERE r.id = 14;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id = 75
WHERE r.id IN (1, 2, 18);
