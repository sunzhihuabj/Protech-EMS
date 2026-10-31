-- =====================================================================
-- V17：审批流引擎（R8 · P0 核心缺口）
-- 依据：V4-02 M00 FR-SYS-14（待办中心）+ 03 册 §4 审批链
-- 两表：sys_approval（实例）+ sys_approval_node（节点链）
-- =====================================================================

CREATE TABLE sys_approval (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_no    VARCHAR(32)  NOT NULL COMMENT 'AP-{YYYYMM}-{4位}',
    approval_type  VARCHAR(32)  NOT NULL COMMENT 'CONTRACT_REVIEW/ECO/ARC_BORROW/BUDGET...',
    target_id      BIGINT       NOT NULL COMMENT '目标单据 id',
    target_desc    VARCHAR(300) NULL,
    submitter_id   BIGINT       NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN',
    current_node   INT          NOT NULL DEFAULT 1 COMMENT '当前节点序（1 起）',
    total_nodes    INT          NOT NULL DEFAULT 1,
    version        INT          NOT NULL DEFAULT 0,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_approval_no UNIQUE (approval_no)
) COMMENT '审批实例';

CREATE TABLE sys_approval_node (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id     BIGINT       NOT NULL,
    node_seq        INT          NOT NULL,
    node_name       VARCHAR(60)  NOT NULL,
    approver_id     BIGINT       NOT NULL COMMENT '审批人',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/PENDING_ADDSIGN',
    comment         VARCHAR(500) NULL,
    acted_at        DATETIME     NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_node UNIQUE (approval_id, node_seq)
) COMMENT '审批节点';

CREATE INDEX idx_approval_type ON sys_approval (approval_type, target_id);
CREATE INDEX idx_node_approver ON sys_approval_node (approver_id, status);

-- ---------- 审批待办列表所需的权限码 ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(144, 'menu:approval',      '审批待办',     'sys', 'MENU', NULL, 17, 'ACTIVE', 'seed'),
(145, 'sys:approval:act',   '审批操作',     'sys', 'API',  NULL, 18, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (144, 145)
WHERE r.id IN (1, 2, 3, 4, 5, 14, 15);
