-- =====================================================================
-- ProjLife V4：S1 营销与合同（02 册 T-MDM 12 + T-CRM 4 + T-CT 7 + T-AI-03）
-- 依据：02 册 §4.2-4.4 全字段 + §4.20 简明字段 + 12 册 §2 建档规则
-- 附注：合同-项目多对多经 md_project_product 承载（FR-MDM-17/18，A-12）；
--       全文索引暂不建（H2 兼容，A-12），检索走 LIKE + 别名表
-- =====================================================================

-- T-MDM-01 法人主体
CREATE TABLE md_legal_entity (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_code    VARCHAR(8)   NOT NULL COMMENT '主体码 PRT/TLD',
    entity_name    VARCHAR(100) NOT NULL,
    tax_no         VARCHAR(32)  NULL,
    address        VARCHAR(300) NULL,
    bank_info      VARCHAR(300) NULL,
    invoice_header VARCHAR(300) NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_entity_code UNIQUE (entity_code)
) COMMENT '法人主体';

-- T-MDM-02 客户主体（三级树，02 册 §4.2）
CREATE TABLE md_customer (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_code  VARCHAR(12)   NOT NULL COMMENT 'C{4位}，不可改',
    customer_name  VARCHAR(200)  NOT NULL COMMENT '规范全称',
    short_name     VARCHAR(60)   NULL,
    parent_id      BIGINT        NULL COMMENT '上级（区域/项目公司→集团）',
    level          VARCHAR(16)   NOT NULL COMMENT 'E-03 GROUP/REGION/PROJECT',
    customer_type  VARCHAR(16)   NOT NULL COMMENT 'E-04',
    group_name     VARCHAR(100)  NULL COMMENT '集团归并名（142→14）',
    credit_code    VARCHAR(32)   NULL,
    contact_person VARCHAR(50)   NULL,
    contact_phone  VARCHAR(30)   NULL,
    address        VARCHAR(300)  NULL,
    level_abc      VARCHAR(4)    NULL COMMENT 'E-05 客户分级',
    credit_score   DECIMAL(5,2)  NULL,
    credit_limit   DECIMAL(18,2) NULL,
    is_internal    TINYINT       NOT NULL DEFAULT 0 COMMENT '内部关联方',
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'E-02',
    source         VARCHAR(24)   NOT NULL DEFAULT 'MANUAL' COMMENT 'E-06',
    remark         VARCHAR(300)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_customer_code UNIQUE (customer_code),
    CONSTRAINT uniq_customer_name UNIQUE (customer_name, deleted)
) COMMENT '客户主体';
CREATE INDEX idx_customer_group ON md_customer (group_name);
CREATE INDEX idx_customer_parent ON md_customer (parent_id);

-- T-MDM-03 客户别名
CREATE TABLE md_customer_alias (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id   BIGINT       NOT NULL,
    alias_name    VARCHAR(200) NOT NULL,
    alias_type    VARCHAR(24)  NULL COMMENT '历史写法/口语/错别字',
    source        VARCHAR(24)  NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '客户别名';
CREATE INDEX idx_calias_name ON md_customer_alias (alias_name);

-- T-MDM-04 风场
CREATE TABLE md_wind_farm (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    farm_code   VARCHAR(16)   NOT NULL COMMENT 'WF{YYYY}-{NNN}',
    farm_name   VARCHAR(200)  NOT NULL COMMENT '业主规范全称',
    province    VARCHAR(30)   NULL,
    city        VARCHAR(30)   NULL,
    county      VARCHAR(30)   NULL,
    owner_id    BIGINT        NULL COMMENT '业主（md_customer）',
    capacity_mw DECIMAL(10,2) NULL,
    phase_count INT           NOT NULL DEFAULT 1,
    status      VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted     TINYINT       NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)   NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_farm_code UNIQUE (farm_code)
) COMMENT '风场';

-- T-MDM-05 项目档案（核心锚点，02 册 §4.3，乐观锁）
CREATE TABLE md_project (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_code    VARCHAR(16)   NOT NULL COMMENT 'PJ{YYYY}-{NNNN} 永不复用',
    std_name        VARCHAR(200)  NOT NULL COMMENT '标准名',
    short_name      VARCHAR(60)   NOT NULL COMMENT '常用简称',
    project_name    VARCHAR(200)  NULL COMMENT '项目业务名',
    customer_id     BIGINT        NULL COMMENT '签约主体',
    owner_id        BIGINT        NULL COMMENT '业主',
    oem_id          BIGINT        NULL COMMENT '整机厂',
    legal_entity_id BIGINT        NOT NULL COMMENT '我方主体',
    order_type      VARCHAR(16)   NOT NULL COMMENT 'E-07',
    farm_id         BIGINT        NULL,
    farm_name_raw   VARCHAR(200)  NULL,
    phase           VARCHAR(20)   NULL,
    product_lines   JSON          NOT NULL COMMENT 'E-08 数组',
    turbine_model   VARCHAR(100)  NULL,
    turbine_qty     INT           NULL,
    contract_amount DECIMAL(18,2) NULL COMMENT '合同额（元）',
    set_qty         DECIMAL(14,3) NOT NULL COMMENT '台数/套数 >0',
    warranty_start  DATE          NULL,
    warranty_end    DATE          NULL,
    warranty_months INT           NULL,
    eng_owner_id    BIGINT        NOT NULL COMMENT '工程责任人',
    biz_owner_id    BIGINT        NOT NULL COMMENT '商务责任人',
    svc_owner_id    BIGINT        NULL COMMENT '售后责任人',
    status          VARCHAR(16)   NOT NULL DEFAULT 'SIGNED_PENDING' COMMENT 'E-09',
    health          VARCHAR(8)    NULL COMMENT 'E-10',
    owner_pending   TINYINT       NOT NULL DEFAULT 0,
    farm_pending    TINYINT       NOT NULL DEFAULT 0,
    remark          VARCHAR(500)  NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_project_code UNIQUE (project_code)
) COMMENT '项目档案';
CREATE INDEX idx_project_status ON md_project (legal_entity_id, status);
CREATE INDEX idx_project_farm ON md_project (farm_id, phase);
CREATE INDEX idx_project_std ON md_project (std_name);

-- T-MDM-06 项目别名
CREATE TABLE md_project_alias (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    md_project_id BIGINT       NOT NULL,
    alias_name    VARCHAR(200) NOT NULL,
    alias_type    VARCHAR(24)  NULL,
    source        VARCHAR(24)  NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '项目别名';
CREATE INDEX idx_palias_name ON md_project_alias (alias_name);

-- T-MDM-07 项目产品子项（兼合同-项目关联，FR-MDM-17/18）
CREATE TABLE md_project_product (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    md_project_id  BIGINT        NOT NULL,
    product_line   VARCHAR(16)   NOT NULL COMMENT 'E-08',
    contract_id    BIGINT        NULL COMMENT '关联合同（多对多承载）',
    set_qty        DECIMAL(14,3) NULL,
    warranty_start DATE          NULL,
    warranty_end   DATE          NULL,
    remark         VARCHAR(300)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '项目产品子项';
CREATE INDEX idx_pproduct_project ON md_project_product (md_project_id);
CREATE INDEX idx_pproduct_contract ON md_project_product (contract_id);

-- T-MDM-08 项目变更单
CREATE TABLE md_project_change (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_no     VARCHAR(40)  NOT NULL,
    md_project_id BIGINT       NOT NULL,
    change_type   VARCHAR(24)  NOT NULL COMMENT 'FARM_FILL/ENTITY_CHANGE/PHASE_SPLIT',
    before_json   JSON         NULL,
    after_json    JSON         NULL,
    approved_by   BIGINT       NULL,
    approved_at   DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '项目变更单';

-- T-MDM-09 物料主数据
CREATE TABLE md_material (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_code    VARCHAR(16)   NOT NULL COMMENT '8 位数字主干',
    material_name    VARCHAR(200)  NOT NULL,
    spec             VARCHAR(200)  NULL,
    unit             VARCHAR(8)    NOT NULL COMMENT 'E-20 八种受控',
    category         VARCHAR(16)   NOT NULL COMMENT 'E-21',
    material_type    VARCHAR(16)   NOT NULL DEFAULT 'PURCHASE' COMMENT 'STD/SELF/PURCHASE/ELECTRIC/SHEET/VIRTUAL',
    brand            VARCHAR(60)   NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    substitute_code  VARCHAR(16)   NULL COMMENT '替代料',
    cost_price       DECIMAL(18,2) NULL,
    safety_stock_min DECIMAL(14,3) NULL,
    safety_stock_max DECIMAL(14,3) NULL,
    abc_class        VARCHAR(4)    NULL COMMENT 'E-05',
    is_controlled    TINYINT       NOT NULL DEFAULT 0,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_material_code UNIQUE (material_code)
) COMMENT '物料主数据';
CREATE INDEX idx_material_name ON md_material (material_name);

-- T-MDM-10 编码映射（存量/双码治理）
CREATE TABLE md_code_map (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    old_code   VARCHAR(32)  NOT NULL,
    new_code   VARCHAR(32)  NOT NULL,
    map_type   VARCHAR(16)  NOT NULL COMMENT 'MERGE/REPLACE/ALIAS',
    source     VARCHAR(24)  NULL,
    remark     VARCHAR(200) NULL,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    created_by VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)  NULL,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '编码映射';

-- T-MDM-11 供应商（准入明细在 T-SCM，S4 建）
CREATE TABLE md_supplier (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_code VARCHAR(16)  NOT NULL COMMENT 'S{4位}',
    supplier_name VARCHAR(200) NOT NULL,
    credit_code   VARCHAR(32)  NULL,
    level         VARCHAR(16)  NULL COMMENT 'E-36',
    category      VARCHAR(200) NULL COMMENT '主营品类（多选逗号分隔）',
    contact       VARCHAR(50)  NULL,
    phone         VARCHAR(30)  NULL,
    payment_terms VARCHAR(300) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/QUALIFIED/PAUSED/BLACKLIST',
    blacklist_flag TINYINT     NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_supplier_code UNIQUE (supplier_code)
) COMMENT '供应商';

-- T-MDM-12 货位
CREATE TABLE md_location (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_code VARCHAR(60)  NOT NULL COMMENT '{仓库}-{库区}-{排}-{列}-{层}',
    warehouse_code VARCHAR(8)  NOT NULL COMMENT 'YL/CP/QT/GC',
    zone_code     VARCHAR(8)   NOT NULL,
    row_no        VARCHAR(8)   NOT NULL,
    col_no        VARCHAR(8)   NOT NULL,
    layer_no      VARCHAR(8)   NOT NULL,
    location_type VARCHAR(16)  NULL,
    capacity      DECIMAL(14,3) NULL,
    abc_class     VARCHAR(4)   NULL,
    color_flag    VARCHAR(8)   NULL COMMENT 'RED/YELLOW/BLUE',
    qr_code       VARCHAR(200) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_location_code UNIQUE (location_code)
) COMMENT '货位';

-- T-CRM-01 商机
CREATE TABLE crm_opportunity (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    opp_no               VARCHAR(24)   NOT NULL COMMENT 'OPP-{YYYYMM}-{4位}',
    opp_name             VARCHAR(200)  NOT NULL,
    customer_id          BIGINT        NOT NULL,
    contact              VARCHAR(50)   NULL,
    phone                VARCHAR(30)   NULL,
    summary              VARCHAR(1000) NULL,
    intention_level      VARCHAR(4)    NOT NULL DEFAULT 'M' COMMENT 'H/M/L',
    expect_sign_date     DATE          NULL,
    expect_amount        DECIMAL(18,2) NULL,
    competitor           VARCHAR(200)  NULL,
    owner_id             BIGINT        NOT NULL COMMENT '负责人（默认创建人）',
    status               VARCHAR(16)   NOT NULL DEFAULT 'TRACKING' COMMENT 'TRACKING/WON/LOST',
    lost_reason          VARCHAR(300)  NULL,
    converted_contract_id BIGINT       NULL,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)   NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)   NULL,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_opp_no UNIQUE (opp_no)
) COMMENT '商机';
CREATE INDEX idx_opp_customer ON crm_opportunity (customer_id, status);

-- T-CRM-02 跟进记录
CREATE TABLE crm_opportunity_followup (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    opportunity_id BIGINT       NOT NULL,
    follow_type    VARCHAR(16)  NOT NULL COMMENT '拜访/电话/邮件/报价',
    content        VARCHAR(1000) NOT NULL,
    attach_ids     VARCHAR(200) NULL,
    next_action    VARCHAR(200) NULL,
    next_date      DATE         NULL,
    operator_id    BIGINT       NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '商机跟进';
CREATE INDEX idx_follow_opp ON crm_opportunity_followup (opportunity_id);

-- T-CRM-03 客户画像
CREATE TABLE crm_customer_profile (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id           BIGINT        NOT NULL,
    total_contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_receivable      DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_received        DECIMAL(18,2) NOT NULL DEFAULT 0,
    complaint_count       INT           NOT NULL DEFAULT 0,
    satisfaction_score    DECIMAL(5,2)  NULL,
    last_txn_date         DATE          NULL,
    rfm_r                 INT           NULL,
    rfm_f                 INT           NULL,
    rfm_m                 INT           NULL,
    profile_json          JSON          NULL,
    snapshot_date         DATE          NULL,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)   NULL,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_profile_customer UNIQUE (customer_id)
) COMMENT '客户画像';

-- T-CRM-04 销售漏斗
CREATE TABLE crm_sales_funnel (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    opportunity_id BIGINT        NOT NULL,
    stage          VARCHAR(16)   NOT NULL COMMENT 'OPPORTUNITY/QUOTE/CONTRACT/DELIVERY',
    enter_time     DATETIME      NOT NULL,
    exit_time      DATETIME      NULL,
    stay_days      INT           NULL,
    amount         DECIMAL(18,2) NULL,
    probability    DECIMAL(6,4)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '销售漏斗';
CREATE INDEX idx_funnel_opp ON crm_sales_funnel (opportunity_id);

-- T-CT-01 合同（02 册 §4.4，乐观锁）
CREATE TABLE ct_contract (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_no     VARCHAR(40)   NULL COMMENT '{主体}-{YYYY}-{性质}-{业务}-{4位}；用印时取号（FR-CT-17），起草阶段为空',
    legacy_no       VARCHAR(40)   NULL,
    contract_name   VARCHAR(300)  NOT NULL,
    nature          VARCHAR(8)    NOT NULL COMMENT 'E-11',
    party_a         VARCHAR(200)  NOT NULL,
    party_b         VARCHAR(200)  NOT NULL,
    legal_entity_id BIGINT        NOT NULL,
    counterparty_id BIGINT        NULL,
    business_type   VARCHAR(16)   NOT NULL COMMENT 'E-12',
    amount          DECIMAL(18,2) NOT NULL COMMENT '合同金额（元）',
    currency        VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    tax_rate        DECIMAL(6,4)  NOT NULL,
    payment_terms   VARCHAR(1000) NOT NULL,
    delivery_addr   VARCHAR(300)  NULL,
    contact         VARCHAR(50)   NULL,
    phone           VARCHAR(30)   NULL,
    delivery_date   DATE          NULL,
    warranty_months INT           NULL,
    acceptance_clause VARCHAR(1000) NULL,
    sign_date       DATE          NOT NULL,
    effective_date  DATE          NOT NULL,
    perform_start   DATE          NULL,
    perform_end     DATE          NULL,
    copy_count      INT           NULL,
    handler_id      BIGINT        NOT NULL,
    reviewer_id     BIGINT        NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'E-13',
    filed_at        DATETIME      NULL COMMENT '建档完成时间（BR-02）',
    filing_deadline DATE          NULL COMMENT 'sign_date+3 工作日',
    invoice_frozen  TINYINT       NOT NULL DEFAULT 0,
    ai_extracted    TINYINT       NOT NULL DEFAULT 0,
    archive_no      VARCHAR(40)   NULL,
    original_file_id BIGINT       NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_contract_no UNIQUE (contract_no)
) COMMENT '合同';
CREATE INDEX idx_contract_status ON ct_contract (legal_entity_id, status, sign_date);
CREATE INDEX idx_contract_filing ON ct_contract (status, filing_deadline);

-- T-CT-02 收款计划
CREATE TABLE ct_contract_payment_plan (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id   BIGINT        NOT NULL,
    phase_name    VARCHAR(50)   NOT NULL,
    percentage    DECIMAL(6,4)  NULL,
    amount        DECIMAL(18,2) NOT NULL,
    plan_date     DATE          NULL,
    actual_amount DECIMAL(18,2) NULL,
    actual_date   DATE          NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARTIAL/RECEIVED',
    invoice_id    BIGINT        NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '收款计划';
CREATE INDEX idx_pp_contract ON ct_contract_payment_plan (contract_id, status);

-- T-CT-03 合同风险
CREATE TABLE ct_contract_risk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT       NOT NULL,
    risk_type   VARCHAR(50)  NOT NULL,
    risk_level  VARCHAR(4)   NOT NULL COMMENT 'H/M/L',
    risk_desc   VARCHAR(1000) NULL,
    suggestion  VARCHAR(1000) NULL,
    is_adopted  TINYINT      NULL,
    ai_call_id  BIGINT       NULL,
    reviewer_id BIGINT       NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '合同风险';
CREATE INDEX idx_risk_contract ON ct_contract_risk (contract_id);

-- T-CT-04 合同变更
CREATE TABLE ct_contract_change (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_no       VARCHAR(60)   NOT NULL COMMENT '{contract_no}-BC{n}',
    contract_id     BIGINT        NOT NULL,
    change_type     VARCHAR(24)   NOT NULL,
    change_reason   VARCHAR(1000) NOT NULL,
    before_json     JSON          NULL,
    after_json      JSON          NULL,
    amount_delta    DECIMAL(18,2) NULL,
    approval_status VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    approved_by     BIGINT        NULL,
    effective_date  DATE          NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '合同变更';
CREATE INDEX idx_change_contract ON ct_contract_change (contract_id);

-- T-CT-05 合同交底
CREATE TABLE ct_contract_dispatch (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id      BIGINT        NOT NULL,
    receiver_id      BIGINT        NOT NULL,
    dispatch_content VARCHAR(2000) NOT NULL,
    sent_at          DATETIME      NULL,
    read_at          DATETIME      NULL,
    remind_count     INT           NOT NULL DEFAULT 0,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '合同交底';
CREATE INDEX idx_dispatch_contract ON ct_contract_dispatch (contract_id, receiver_id);

-- T-CT-06 发票
CREATE TABLE ct_invoice (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_no   VARCHAR(60)   NOT NULL,
    contract_id  BIGINT        NOT NULL,
    project_id   BIGINT        NULL,
    invoice_type VARCHAR(16)   NOT NULL DEFAULT 'SPECIAL' COMMENT 'SPECIAL/GENERAL',
    amount       DECIMAL(18,2) NOT NULL,
    tax_rate     DECIMAL(6,4)  NULL,
    tax_amount   DECIMAL(18,2) NULL,
    invoice_date DATE          NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED/CONFIRMED/REJECTED',
    t_plus_ref   VARCHAR(60)   NULL,
    apply_by     BIGINT        NOT NULL,
    confirm_by   BIGINT        NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '发票';
CREATE INDEX idx_invoice_contract ON ct_invoice (contract_id, status);

-- T-CT-07 回款
CREATE TABLE ct_receipt (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_no      VARCHAR(40)   NOT NULL,
    contract_id     BIGINT        NOT NULL,
    project_id      BIGINT        NULL,
    payment_plan_id BIGINT        NULL,
    amount          DECIMAL(18,2) NOT NULL,
    receipt_date    DATE          NOT NULL,
    method          VARCHAR(16)   NULL,
    bank_ref        VARCHAR(60)   NULL,
    voucher_file_id BIGINT        NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED',
    confirm_by      BIGINT        NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '回款';
CREATE INDEX idx_receipt_contract ON ct_receipt (contract_id, status);

-- T-AI-03 AI 复核反馈
CREATE TABLE ai_feedback (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_id       BIGINT       NOT NULL,
    biz_type      VARCHAR(32)  NOT NULL,
    biz_id        BIGINT       NOT NULL,
    ai_field      VARCHAR(50)  NOT NULL,
    ai_value      VARCHAR(1000) NULL,
    human_value   VARCHAR(1000) NULL,
    is_correct    TINYINT      NOT NULL DEFAULT 0,
    feedback_note VARCHAR(300) NULL,
    operator_id   BIGINT       NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'AI 复核反馈';
CREATE INDEX idx_feedback_biz ON ai_feedback (biz_type, biz_id);

-- ---------- 种子：法人/演示主数据/菜单/授权/建档专员 ----------
INSERT INTO md_legal_entity (id, entity_code, entity_name, tax_no, address, bank_info, invoice_header, created_by) VALUES
(1, 'PRT', '特固兰（demo）', '91110000DEMO00001X', '北京市朝阳区（demo）', '工行北京分行 demo 账号', '特固兰有限公司（demo）', 'seed'),
(2, 'TLD', '天立达（demo）',   '91110000DEMO00002X', '北京市海淀区（demo）', '建行北京分行 demo 账号', '天立达有限公司（demo）', 'seed');

INSERT INTO md_customer (id, customer_code, customer_name, short_name, parent_id, level, customer_type, group_name, is_internal, status, source, created_by) VALUES
(1,  'C0001', '远景能源集团有限公司（demo）',   '远景',   NULL, 'GROUP',   'OEM',   '远景能源', 0, 'ACTIVE', 'MANUAL', 'seed'),
(2,  'C0002', '金风科技股份有限公司（demo）',   '金风',   NULL, 'GROUP',   'OEM',   '金风科技', 0, 'ACTIVE', 'MANUAL', 'seed'),
(3,  'C0003', '特固兰集团（demo）',             '特固兰', NULL, 'GROUP',   'RELATED','特固兰',   1, 'ACTIVE', 'MANUAL', 'seed'),
(4,  'C0004', '远景能源江苏有限公司（demo）',   '远景江苏', 1,  'REGION',  'OEM',   '远景能源', 0, 'ACTIVE', 'MANUAL', 'seed'),
(5,  'C0005', '金风科技甘肃有限公司（demo）',   '金风甘肃', 2,  'REGION',  'OEM',   '金风科技', 0, 'ACTIVE', 'MANUAL', 'seed'),
(6,  'C0006', '竹子岭风场项目公司（demo）',     '竹子岭', 4,    'PROJECT', 'OWNER', '远景能源', 0, 'ACTIVE', 'MANUAL', 'seed'),
(7,  'C0007', '酒泉风场项目公司（demo）',       '酒泉',   5,    'PROJECT', 'OWNER', '金风科技', 0, 'ACTIVE', 'MANUAL', 'seed');

INSERT INTO md_wind_farm (id, farm_code, farm_name, province, city, county, owner_id, capacity_mw, phase_count, status, created_by) VALUES
(1, 'WF2026-001', '竹子岭风电场（demo）', '江苏', '盐城', '射阳', 6, 200.00, 2, 'ACTIVE', 'seed');

-- R18 建档专员（FR-MDM 唯一写入入口）
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by) VALUES
(9, 'E0009', 'mdm@demo', '建档专员', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000009', 4, '商务建档', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号（R18 主数据唯一入口）', 'seed');
INSERT INTO sys_user_role (user_id, role_id, created_by) VALUES (9, 18, 'seed');

-- 菜单（S1：商机/合同/主数据）
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(63, 'menu:crm',      '商机管理', 'crm', 'MENU', NULL, 3, 'ACTIVE', 'seed'),
(64, 'menu:contract', '合同管理', 'contract', 'MENU', NULL, 4, 'ACTIVE', 'seed'),
(65, 'menu:mdm',      '主数据',   'mdm', 'MENU', NULL, 5, 'ACTIVE', 'seed');

-- 授权：R04 全量；R05 查看+审批；R15 合同+开票确认；R18 主数据；R01/R02 查看类
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (63, 65) OR (p.id = 64)
WHERE r.id IN (4, 5, 15, 18)
  AND (r.id IN (4, 5) OR p.id IN (63, 64, 65));

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (63, 64, 65)
WHERE r.id IN (1, 2);
