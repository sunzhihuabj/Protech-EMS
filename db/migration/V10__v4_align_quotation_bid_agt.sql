-- =====================================================================
-- ProjLife V10：V4.0 RPD 对齐（D-04 报价单 / D-05 商机来源 / F-09 枚举 E-52 / F-05 权限登记）
-- 依据：V4-03 M01 §2/§3、V4-01 §5.4/§6.4、V4-00 §九
-- =====================================================================

-- D-05：商机来源三字段（F-17）
ALTER TABLE crm_opportunity ADD COLUMN source_type VARCHAR(16) NULL COMMENT '来源类型 BID/MANUAL/REFERRAL（FR-CRM-20）';
ALTER TABLE crm_opportunity ADD COLUMN source_id BIGINT NULL COMMENT '来源 id（如标讯公告）';
ALTER TABLE crm_opportunity ADD COLUMN source_note VARCHAR(300) NULL COMMENT '来源说明';
CREATE INDEX idx_opp_source ON crm_opportunity (source_type, source_id);

-- V4-03 M01 §3：合同表补报价单来源
ALTER TABLE ct_contract ADD COLUMN quotation_id BIGINT NULL COMMENT '报价单来源（FR-CRM-19）';

-- D-04：报价单主表（FR-CRM-17）
CREATE TABLE crm_quotation (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_no          VARCHAR(24)   NOT NULL COMMENT 'QT-{YYYYMM}-{4位}',
    opportunity_id        BIGINT        NOT NULL COMMENT '商机（一商机多版报价）',
    customer_id           BIGINT        NOT NULL,
    project_id            BIGINT        NULL,
    product_line          VARCHAR(16)   NOT NULL COMMENT 'E-08',
    version               VARCHAR(8)    NOT NULL DEFAULT 'V1' COMMENT 'V1/V2…，旧版只读',
    qty                   DECIMAL(14,3) NOT NULL,
    unit_price            DECIMAL(18,4) NOT NULL,
    tax_rate              DECIMAL(6,4)  NOT NULL DEFAULT 0.13,
    amount                DECIMAL(18,2) NOT NULL COMMENT '数量×单价×(1+税率) 服务端算',
    delivery_terms        VARCHAR(500)  NULL COMMENT '交付条件',
    valid_until           DATE          NULL COMMENT '有效期至',
    status                VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/WON/LOST/EXPIRED',
    price_basis_json      JSON          NULL COMMENT '价格基准（FR-CRM-18）',
    converted_contract_id BIGINT        NULL COMMENT '转合同回写（FR-CRM-19）',
    deleted               TINYINT       NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)   NULL,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_quotation_no UNIQUE (quotation_no),
    CONSTRAINT uniq_quotation_opp_ver UNIQUE (opportunity_id, version)
) COMMENT '报价单';
CREATE INDEX idx_quote_opp ON crm_quotation (opportunity_id, status);

-- 报价单行表（行金额校验）
CREATE TABLE crm_quotation_line (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id  BIGINT        NOT NULL,
    material_id   BIGINT        NOT NULL,
    qty           DECIMAL(14,3) NOT NULL,
    price         DECIMAL(18,4) NOT NULL,
    amount        DECIMAL(18,2) NOT NULL COMMENT '行金额=qty×price',
    remark        VARCHAR(300)  NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)   NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '报价单行';
CREATE INDEX idx_qline_quote ON crm_quotation_line (quotation_id);

-- ---------- 枚举 E-43—E-52（F-09，V4-01 §5.4） ----------
INSERT INTO sys_dict_type (id, type_code, type_name, is_system, status, created_by) VALUES
(43,'bid_source_category','标讯源类别',1,'ACTIVE','seed'),
(44,'bid_source_status','标讯源状态',1,'ACTIVE','seed'),
(45,'bid_parse_status','标讯解析状态',1,'ACTIVE','seed'),
(46,'bid_notice_status','标讯公告状态',1,'ACTIVE','seed'),
(47,'bid_match_level','标讯匹配级别',1,'ACTIVE','seed'),
(48,'bid_push_channel','标讯推送渠道',1,'ACTIVE','seed'),
(49,'agt_identity_type','Agent 身份类型',1,'ACTIVE','seed'),
(50,'agt_task_status','Agent 任务状态',1,'ACTIVE','seed'),
(51,'agt_call_result','Agent 调用结果',1,'ACTIVE','seed'),
(52,'kb_card_light','看板卡片灯色',1,'ACTIVE','seed');

INSERT INTO sys_dict_item (type_id, item_code, item_label, sort_no, status, created_by) VALUES
(43,'A_OWNER','业主官网',1,'ACTIVE','seed'),
(43,'B_OEM','整机厂门户',2,'ACTIVE','seed'),
(43,'C_PORTAL','公共招投标平台',3,'ACTIVE','seed'),
(43,'D_MANUAL','人工导入',4,'ACTIVE','seed'),
(44,'PENDING_REVIEW','待审核',1,'ACTIVE','seed'),
(44,'ENABLED','已启用',2,'ACTIVE','seed'),
(44,'SUSPENDED','已暂停',3,'ACTIVE','seed'),
(44,'DISABLED','已禁用',4,'ACTIVE','seed'),
(45,'PENDING','待解析',1,'ACTIVE','seed'),
(45,'PARSED','已解析',2,'ACTIVE','seed'),
(45,'MANUAL','待人工',3,'ACTIVE','seed'),
(45,'INCOMPLETE','关键字段缺失',4,'ACTIVE','seed'),
(46,'ACTIVE','有效',1,'ACTIVE','seed'),
(46,'SUPERSEDED','被变更',2,'ACTIVE','seed'),
(46,'CONVERTED','已转商机',3,'ACTIVE','seed'),
(46,'CLOSED','已关闭',4,'ACTIVE','seed'),
(46,'EXCLUDED','已排除',5,'ACTIVE','seed'),
(47,'HIGH','高匹配',1,'ACTIVE','seed'),
(47,'MEDIUM','中匹配',2,'ACTIVE','seed'),
(47,'ARCHIVED','归档',3,'ACTIVE','seed'),
(48,'INNER','站内',1,'ACTIVE','seed'),
(48,'DINGTALK','钉钉',2,'ACTIVE','seed'),
(49,'EXPERT','专家',1,'ACTIVE','seed'),
(49,'WORKER','员工',2,'ACTIVE','seed'),
(49,'ASSISTANT','助理',3,'ACTIVE','seed'),
(50,'CREATED','已创建',1,'ACTIVE','seed'),
(50,'ACCEPTED','已受理',2,'ACTIVE','seed'),
(50,'RUNNING','执行中',3,'ACTIVE','seed'),
(50,'SUCCEEDED','已成功',4,'ACTIVE','seed'),
(50,'FAILED','已失败',5,'ACTIVE','seed'),
(50,'TIMEOUT','已超时',6,'ACTIVE','seed'),
(50,'CONFIRMED','已确认',7,'ACTIVE','seed'),
(50,'REJECTED','已拒绝',8,'ACTIVE','seed'),
(50,'CANCELLED','已取消',9,'ACTIVE','seed'),
(51,'SUCCESS','成功',1,'ACTIVE','seed'),
(51,'DEGRADED','降级',2,'ACTIVE','seed'),
(51,'FAILED','失败',3,'ACTIVE','seed'),
(52,'GREEN','绿灯',1,'ACTIVE','seed'),
(52,'YELLOW','黄灯',2,'ACTIVE','seed'),
(52,'RED','红灯',3,'ACTIVE','seed'),
(52,'BLOCKED','受阻',4,'ACTIVE','seed');

-- ---------- 权限登记（F-05：bid:* 7 项、agt:* 4 项、crm:cost:view） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(111, 'crm:cost:view',       '报价成本基准', 'crm', 'API', NULL, 13, 'ACTIVE', 'seed'),
(112, 'bid:source:manage',   '标讯源管理',   'bid', 'API', NULL, 1, 'ACTIVE', 'seed'),
(113, 'bid:rule:manage',     '匹配规则管理', 'bid', 'API', NULL, 2, 'ACTIVE', 'seed'),
(114, 'bid:notice:view',     '标讯查看',     'bid', 'API', NULL, 3, 'ACTIVE', 'seed'),
(115, 'bid:notice:manual',   '标讯人工导入', 'bid', 'API', NULL, 4, 'ACTIVE', 'seed'),
(116, 'bid:notice:convert',  '标讯转商机',   'bid', 'API', NULL, 5, 'ACTIVE', 'seed'),
(117, 'bid:push:subscribe',  '标讯订阅',     'bid', 'API', NULL, 6, 'ACTIVE', 'seed'),
(118, 'bid:board:view',      '标讯看板',     'bid', 'API', NULL, 7, 'ACTIVE', 'seed'),
(119, 'agt:register:manage', 'Agent 注册管理','agt', 'API', NULL, 8, 'ACTIVE', 'seed'),
(120, 'agt:call',            'Agent 调用',   'agt', 'API', NULL, 9, 'ACTIVE', 'seed'),
(121, 'agt:board:view',      'Agent 看板',   'agt', 'API', NULL, 10, 'ACTIVE', 'seed'),
(122, 'agt:feedback:view',   'Agent 反馈查看','agt', 'API', NULL, 11, 'ACTIVE', 'seed');

-- 授权：报价（R04/R05）；标讯查看（R04/R05/R01/R02）；标讯转商机（R04）；成本基准（R04/R05/R02/R01）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (111, 114, 117)
WHERE r.id IN (4, 5);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (114, 118)
WHERE r.id IN (1, 2);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (112, 113, 115)
WHERE r.id = 18;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id = 116
WHERE r.id = 4;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (119, 121, 122)
WHERE r.id = 3;
