-- =====================================================================
-- ProjLife V7：S4 供应链与库存（02 册 T-SCM 9 + T-INV 11 = 20 张）
-- 依据：02 册 §4.9 全字段 + §4.20 简明字段 + 07 册全册 + 00 册 C-04（T+ 权威）
-- 铁律：BR-INV-01 不建权威库存数量表（inv_stock_cache 为只读缓存）；
--       BR-INV-03 幂等键 UNIQUE；inv_transaction 只增不改不删
-- =====================================================================

-- T-SCM-01 供应商准入
CREATE TABLE scm_supplier_onboarding (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id        BIGINT       NOT NULL,
    current_stage      VARCHAR(16)  NOT NULL DEFAULT 'REGISTERED' COMMENT '五阶状态机',
    stage_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    qualification_json JSON         NULL,
    audit_result       VARCHAR(500) NULL,
    final_level        VARCHAR(16)  NULL COMMENT 'E-36',
    apply_by           BIGINT       NULL,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)  NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)  NULL,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_onboarding_supplier UNIQUE (supplier_id)
) COMMENT '供应商准入';

-- T-SCM-02 供应商资质
CREATE TABLE scm_supplier_qualification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT      NOT NULL,
    cert_type   VARCHAR(24) NOT NULL,
    cert_no     VARCHAR(60) NULL,
    cert_name   VARCHAR(200) NOT NULL,
    issue_date  DATE        NULL,
    expire_date DATE        NULL,
    file_id     BIGINT      NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted     TINYINT     NOT NULL DEFAULT 0,
    created_by  VARCHAR(64) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64) NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '供应商资质';
CREATE INDEX idx_qual_supplier ON scm_supplier_qualification (supplier_id, expire_date);

-- T-SCM-03 供应商评价
CREATE TABLE scm_supplier_evaluation (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id    BIGINT        NOT NULL,
    eval_period    VARCHAR(16)   NOT NULL,
    quality_score  DECIMAL(5,2)  NULL,
    delivery_score DECIMAL(5,2)  NULL,
    service_score  DECIMAL(5,2)  NULL,
    total_score    DECIMAL(5,2)  NULL,
    grade          VARCHAR(8)    NULL,
    evaluator_id   BIGINT        NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_eval UNIQUE (supplier_id, eval_period)
) COMMENT '供应商评价';

-- T-SCM-04 价格库
CREATE TABLE scm_price_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id  BIGINT        NOT NULL,
    supplier_id  BIGINT        NOT NULL,
    unit_price   DECIMAL(18,4) NOT NULL,
    tax_rate     DECIMAL(6,4)  NULL,
    price_date   DATE          NOT NULL,
    source_po_id BIGINT        NULL,
    is_lowest    TINYINT       NOT NULL DEFAULT 0,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '价格库';
CREATE INDEX idx_price_material ON scm_price_history (material_id, supplier_id, price_date);

-- T-SCM-05 采购需求（乐观锁）
CREATE TABLE scm_purchase_requirement (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    req_no       VARCHAR(24)   NOT NULL,
    source_type  VARCHAR(16)   NOT NULL COMMENT 'BOM_SHORTAGE/SAFETY_STOCK/MANUAL/STOCK',
    project_id   BIGINT        NULL,
    material_id  BIGINT        NOT NULL,
    qty          DECIMAL(14,3) NOT NULL,
    expect_date  DATE          NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/MERGED/CLOSED',
    merge_po_id  BIGINT        NULL,
    requester_id BIGINT        NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version      INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_req_no UNIQUE (req_no)
) COMMENT '采购需求';
CREATE INDEX idx_req_status ON scm_purchase_requirement (status, source_type);

-- T-SCM-06 采购订单（乐观锁）
CREATE TABLE scm_purchase_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_no          VARCHAR(24)   NOT NULL,
    supplier_id    BIGINT        NOT NULL,
    legal_entity_id BIGINT       NOT NULL,
    project_id     BIGINT        NULL,
    total_amount   DECIMAL(18,2) NULL,
    tax_rate       DECIMAL(6,4)  NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'E-34',
    dispatched_at  DATETIME      NULL,
    buyer_id       BIGINT        NOT NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_po_no UNIQUE (po_no)
) COMMENT '采购订单';
CREATE INDEX idx_po_supplier ON scm_purchase_order (supplier_id, status);

-- T-SCM-07 采购订单行
CREATE TABLE scm_purchase_order_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id         BIGINT        NOT NULL,
    line_no       INT           NOT NULL,
    project_id    BIGINT        NULL,
    material_id   BIGINT        NOT NULL,
    qty           DECIMAL(14,3) NOT NULL,
    received_qty  DECIMAL(14,3) NOT NULL DEFAULT 0,
    unit_price    DECIMAL(18,4) NULL COMMENT 'BR-06 脱敏',
    amount        DECIMAL(18,2) NULL,
    expected_date DATE          NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '采购订单行';
CREATE INDEX idx_poitem_po ON scm_purchase_order_item (po_id);

-- T-SCM-08 采购退货
CREATE TABLE scm_purchase_return (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_no   VARCHAR(24)   NOT NULL,
    po_id       BIGINT        NOT NULL,
    item_id     BIGINT        NOT NULL,
    qty         DECIMAL(14,3) NOT NULL,
    reason      VARCHAR(500)  NULL,
    return_date DATE          NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'APPLIED',
    t_plus_ref  VARCHAR(40)   NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)   NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_return_no UNIQUE (return_no)
) COMMENT '采购退货';

-- T-SCM-09 物流
CREATE TABLE scm_logistics (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id         BIGINT       NOT NULL,
    carrier       VARCHAR(60)  NULL,
    tracking_no   VARCHAR(60)  NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'IN_TRANSIT',
    last_update   DATETIME     NULL,
    timeline_json JSON         NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '物流';

-- T-INV-01 库存事务（只增不改不删，不软删；幂等键唯一）
CREATE TABLE inv_transaction (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_no            VARCHAR(40)   NOT NULL,
    txn_type          VARCHAR(20)   NOT NULL COMMENT 'E-23',
    legal_entity_id   BIGINT        NOT NULL,
    warehouse_code    VARCHAR(20)   NOT NULL,
    location_id       BIGINT        NULL,
    material_id       BIGINT        NOT NULL,
    batch_no          VARCHAR(40)   NULL,
    qty               DECIMAL(14,3) NOT NULL,
    unit              VARCHAR(10)   NOT NULL,
    cost_price        DECIMAL(18,4) NULL,
    amount            DECIMAL(18,2) NULL,
    before_qty        DECIMAL(14,3) NULL,
    after_qty         DECIMAL(14,3) NULL,
    project_id        BIGINT        NULL,
    source_type       VARCHAR(24)   NOT NULL,
    source_id         BIGINT        NULL,
    source_no         VARCHAR(40)   NULL,
    idempotency_key   VARCHAR(80)   NOT NULL,
    sync_status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING_SYNC' COMMENT 'E-24',
    t_plus_voucher_no VARCHAR(40)   NULL,
    sync_error        VARCHAR(300)  NULL,
    occurred_at       DATETIME      NOT NULL,
    operator_id       BIGINT        NOT NULL,
    scanned_code      VARCHAR(100)  NULL,
    remark            VARCHAR(300)  NULL,
    created_by        VARCHAR(64)   NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_txn_no UNIQUE (txn_no),
    CONSTRAINT uniq_idem UNIQUE (idempotency_key)
) COMMENT '库存事务（只增）';
CREATE INDEX idx_txn_material ON inv_transaction (material_id, occurred_at);
CREATE INDEX idx_txn_source ON inv_transaction (source_type, source_id);
CREATE INDEX idx_txn_sync ON inv_transaction (sync_status);

-- T-INV-02 库存缓存（只读副本，非权威账）
CREATE TABLE inv_stock_cache (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    legal_entity_id BIGINT        NOT NULL,
    warehouse_code  VARCHAR(20)   NOT NULL,
    location_id     BIGINT        NULL,
    material_id     BIGINT        NOT NULL,
    batch_no        VARCHAR(40)   NULL,
    qty_on_hand     DECIMAL(14,3) NOT NULL DEFAULT 0,
    qty_allocated   DECIMAL(14,3) NOT NULL DEFAULT 0,
    qty_available   DECIMAL(14,3) NOT NULL DEFAULT 0,
    cached_at       DATETIME      NOT NULL,
    ttl_seconds     INT           NOT NULL DEFAULT 300,
    source          VARCHAR(16)   NOT NULL DEFAULT 'T_PLUS',
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_stock_cache UNIQUE (legal_entity_id, warehouse_code, material_id, batch_no)
) COMMENT '库存缓存（只读）';

-- T-INV-03 批次
CREATE TABLE inv_batch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id     BIGINT        NOT NULL,
    batch_no        VARCHAR(40)   NOT NULL,
    location_id     BIGINT        NULL,
    qty             DECIMAL(14,3) NOT NULL DEFAULT 0,
    locked_qty      DECIMAL(14,3) NOT NULL DEFAULT 0,
    receipt_date    DATE          NULL,
    supplier_id     BIGINT        NULL,
    expire_date     DATE          NULL,
    legal_entity_id BIGINT        NOT NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_batch UNIQUE (material_id, batch_no, legal_entity_id)
) COMMENT '批次';

-- T-INV-04 盘点单（乐观锁）
CREATE TABLE inv_count (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    count_no    VARCHAR(24)  NOT NULL,
    count_type  VARCHAR(8)   NOT NULL COMMENT 'A/B/C/FULL',
    scope_json  JSON         NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/COUNTING/REVIEW/DIFF_PROCESS/ADJUSTED/CLOSED',
    started_at  DATETIME     NULL,
    finished_at DATETIME     NULL,
    creator_id  BIGINT       NOT NULL,
    reviewer_id BIGINT       NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT uniq_count_no UNIQUE (count_no)
) COMMENT '盘点单';

-- T-INV-05 盘点行
CREATE TABLE inv_count_line (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    count_id    BIGINT        NOT NULL,
    location_id BIGINT        NULL,
    material_id BIGINT        NOT NULL,
    batch_no    VARCHAR(40)   NULL,
    book_qty    DECIMAL(14,3) NULL,
    actual_qty  DECIMAL(14,3) NULL,
    diff_qty    DECIMAL(14,3) NULL,
    diff_amount DECIMAL(18,2) NULL,
    cause       VARCHAR(24)   NULL COMMENT 'E-37 六类归因',
    remark      VARCHAR(300)  NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)   NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '盘点行';
CREATE INDEX idx_countline_count ON inv_count_line (count_id);

-- T-INV-06 差异单（乐观锁）
CREATE TABLE inv_diff (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    diff_no        VARCHAR(24)   NOT NULL,
    count_id       BIGINT        NOT NULL,
    material_id    BIGINT        NOT NULL,
    location_id    BIGINT        NULL,
    book_qty       DECIMAL(14,3) NULL,
    actual_qty     DECIMAL(14,3) NULL,
    diff_qty       DECIMAL(14,3) NULL,
    diff_amount    DECIMAL(18,2) NOT NULL,
    cause          VARCHAR(24)   NULL COMMENT 'E-37 无归因不调账',
    approval_level VARCHAR(8)    NULL COMMENT 'L1/L2/L3',
    approved_by    BIGINT        NULL,
    approved_at    DATETIME      NULL,
    closed_at      DATETIME      NULL,
    close_days     INT           NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_diff_no UNIQUE (diff_no)
) COMMENT '差异单';

-- T-INV-07 呆滞台账
CREATE TABLE inv_slow_moving (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id      BIGINT        NOT NULL,
    location_id      BIGINT        NULL,
    qty              DECIMAL(14,3) NOT NULL,
    amount           DECIMAL(18,2) NULL,
    age_days         INT           NOT NULL DEFAULT 0,
    status           VARCHAR(16)   NOT NULL DEFAULT 'CANDIDATE' COMMENT 'CANDIDATE/CONFIRMED/DISPOSING/DISPOSED',
    responsibility   VARCHAR(24)   NULL COMMENT 'E-38',
    source_ecn_id    BIGINT        NULL,
    first_seen_date  DATE          NOT NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '呆滞台账';
CREATE INDEX idx_slow_material ON inv_slow_moving (material_id, status);

-- T-INV-08 处置单
CREATE TABLE inv_disposal (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    disposal_no    VARCHAR(24)   NOT NULL,
    slow_moving_id BIGINT        NOT NULL,
    method         VARCHAR(16)   NOT NULL COMMENT 'E-39',
    qty            DECIMAL(14,3) NOT NULL,
    expect_amount  DECIMAL(18,2) NULL,
    actual_amount  DECIMAL(18,2) NULL,
    approval_level VARCHAR(8)    NULL,
    approved_by    BIGINT        NULL,
    approved_at    DATETIME      NULL,
    disposed_at    DATETIME      NULL,
    remark         VARCHAR(300)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_disposal_no UNIQUE (disposal_no)
) COMMENT '处置单';

-- T-INV-09 补货单（乐观锁，BR-05）
CREATE TABLE inv_replenish (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    replenish_no      VARCHAR(24)   NOT NULL,
    project_id        BIGINT        NULL,
    md_project_id     BIGINT        NULL,
    reason            VARCHAR(24)   NOT NULL COMMENT 'E-40 八类',
    urgency           VARCHAR(8)    NOT NULL DEFAULT 'NORMAL',
    applicant_id      BIGINT        NOT NULL,
    amount            DECIMAL(18,2) NULL,
    approval_path     VARCHAR(100)  NULL,
    need_backfill     TINYINT       NOT NULL DEFAULT 0,
    backfill_deadline DATETIME      NULL,
    backfilled_at     DATETIME      NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/ISSUED/BACKFILLED/CLOSED/LOCKED',
    applicant_locked  TINYINT       NOT NULL DEFAULT 0,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)   NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_replenish_no UNIQUE (replenish_no)
) COMMENT '补货单';

-- T-INV-10 补货行
CREATE TABLE inv_replenish_line (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    replenish_id BIGINT        NOT NULL,
    material_id  BIGINT        NOT NULL,
    qty          DECIMAL(14,3) NOT NULL,
    unit         VARCHAR(10)   NOT NULL,
    use_location VARCHAR(200)  NULL,
    remark       VARCHAR(300)  NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)   NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '补货行';
CREATE INDEX idx_rline_replenish ON inv_replenish_line (replenish_id);

-- T-INV-11 每日对账
CREATE TABLE inv_reconcile (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    reconcile_date   DATE          NOT NULL,
    legal_entity_id  BIGINT        NOT NULL,
    local_txn_count  INT           NOT NULL DEFAULT 0,
    t_plus_txn_count INT           NOT NULL DEFAULT 0,
    diff_count       INT           NOT NULL DEFAULT 0,
    diff_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    status           VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_reconcile UNIQUE (reconcile_date, legal_entity_id)
) COMMENT '每日对账';

-- ---------- 演示用户：采购员（R09） ----------
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by) VALUES
(10, 'E0010', 'purchase@demo', '采购专员', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000010', 10, '采购', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed');
INSERT INTO sys_user_role (user_id, role_id, created_by) VALUES (10, 9, 'seed');

-- ---------- 权限与菜单（S4） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(83, 'menu:scm',           '采购与供应商', 'scm', 'MENU', NULL, 8, 'ACTIVE', 'seed'),
(84, 'menu:inv',           '库房',        'inv', 'MENU', NULL, 9, 'ACTIVE', 'seed'),
(85, 'scm:supplier:manage','供应商管理',  'scm', 'API', NULL, 1, 'ACTIVE', 'seed'),
(86, 'scm:req:create',     '采购需求',    'scm', 'API', NULL, 2, 'ACTIVE', 'seed'),
(87, 'scm:order:create',   '采购下单',    'scm', 'API', NULL, 3, 'ACTIVE', 'seed'),
(88, 'scm:order:dispatch', '订单下发',    'scm', 'API', NULL, 4, 'ACTIVE', 'seed'),
(89, 'scm:order:view_price','查看采购价格','scm', 'API', NULL, 5, 'ACTIVE', 'seed'),
(90, 'inv:scan',           '扫码出入库',  'inv', 'API', NULL, 6, 'ACTIVE', 'seed'),
(91, 'inv:count:manage',   '盘点管理',    'inv', 'API', NULL, 7, 'ACTIVE', 'seed'),
(92, 'inv:diff:approve:L1','差异一级审批','inv', 'API', NULL, 8, 'ACTIVE', 'seed'),
(93, 'inv:diff:approve:L2','差异二级审批','inv', 'API', NULL, 9, 'ACTIVE', 'seed'),
(94, 'inv:diff:approve:L3','差异三级审批','inv', 'API', NULL, 10, 'ACTIVE', 'seed'),
(95, 'inv:replenish:create','补货申请',   'inv', 'API', NULL, 11, 'ACTIVE', 'seed'),
(96, 'inv:replenish:approve','补货审批',  'inv', 'API', NULL, 12, 'ACTIVE', 'seed'),
(97, 'inv:slow:manage',    '呆滞管理',    'inv', 'API', NULL, 13, 'ACTIVE', 'seed'),
(98, 'scm:order:view',     '采购订单查看','scm', 'API', NULL, 14, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (83, 85, 86, 87, 88, 89, 98)
WHERE r.id IN (9, 18);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (83, 98)
WHERE r.id IN (11, 12, 14);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (84, 90, 91, 92, 95, 96, 97)
WHERE r.id = 11;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (84, 93, 94, 96)
WHERE r.id IN (10, 15);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (84, 95)
WHERE r.id = 14;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (83, 84)
WHERE r.id IN (1, 2, 12);
