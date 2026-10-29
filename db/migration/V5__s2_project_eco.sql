-- =====================================================================
-- ProjLife V5：S2 项目与工程变更（02 册 T-PRJ 7 张）
-- 依据：02 册 §4.5-4.6 + 05 册全册；模板种子 = 05 册 §2.1 六阶段标准集成项目
-- 附注：prj_task 增加 is_temp/is_milestone/finish_note（FR-PRJ-12/16/27 承载列，A-14）
-- =====================================================================

-- T-PRJ-02 项目成员
CREATE TABLE prj_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role_code  VARCHAR(32) NULL COMMENT '项目内角色',
    join_date  DATE        NULL,
    leave_date DATE        NULL,
    is_active  TINYINT     NOT NULL DEFAULT 1,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '项目成员';
CREATE INDEX idx_member_project ON prj_member (project_id, is_active);
CREATE INDEX idx_member_user ON prj_member (user_id, is_active);

-- T-PRJ-05 项目模板
CREATE TABLE prj_template (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(24)  NOT NULL,
    template_name VARCHAR(64)  NOT NULL,
    project_type VARCHAR(24)  NULL,
    is_default   TINYINT      NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_template_code UNIQUE (template_code)
) COMMENT '项目模板';

-- T-PRJ-06 模板任务（树形：一级=阶段，二级=任务）
CREATE TABLE prj_template_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id      BIGINT      NOT NULL,
    parent_id        BIGINT      NULL,
    task_name        VARCHAR(200) NOT NULL,
    dept_id          BIGINT      NULL,
    role_code        VARCHAR(32) NULL,
    duration_days    INT         NOT NULL DEFAULT 1,
    is_optional      TINYINT     NOT NULL DEFAULT 0,
    output_required  TINYINT     NOT NULL DEFAULT 0,
    output_name      VARCHAR(100) NULL,
    approval_required TINYINT    NOT NULL DEFAULT 0,
    seq              INT         NOT NULL DEFAULT 0,
    deleted          TINYINT     NOT NULL DEFAULT 0,
    created_by       VARCHAR(64) NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64) NULL,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '模板任务';
CREATE INDEX idx_ttask_template ON prj_template_task (template_id, seq);

-- T-PRJ-01 执行项目（乐观锁）
CREATE TABLE prj_project (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_no     VARCHAR(32)   NOT NULL COMMENT 'PRJ-{YYYY}-{4位} 执行号',
    md_project_id  BIGINT        NOT NULL,
    contract_id    BIGINT        NULL,
    project_name   VARCHAR(200)  NOT NULL,
    manager_id     BIGINT        NOT NULL,
    template_id    BIGINT        NULL,
    plan_start     DATE          NOT NULL,
    plan_end       DATE          NOT NULL,
    actual_start   DATE          NULL,
    actual_end     DATE          NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'E-14',
    progress_pct   DECIMAL(5,2)  NULL,
    budget_total   DECIMAL(18,2) NULL,
    cost_actual    DECIMAL(18,2) NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)   NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_prj_no UNIQUE (project_no)
) COMMENT '执行项目';
CREATE INDEX idx_prj_md ON prj_project (md_project_id);
CREATE INDEX idx_prj_manager ON prj_project (manager_id, status);

-- T-PRJ-03 任务（WBS，乐观锁）
CREATE TABLE prj_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no          VARCHAR(32)   NOT NULL,
    project_id       BIGINT        NOT NULL,
    parent_id        BIGINT        NULL,
    wbs_code         VARCHAR(40)   NOT NULL COMMENT '形如 1.2.3',
    task_name        VARCHAR(200)  NOT NULL,
    task_type        VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'E-15',
    dept_id          BIGINT        NULL,
    assignee_id      BIGINT        NULL,
    role_code        VARCHAR(32)   NULL,
    is_optional      TINYINT       NOT NULL DEFAULT 0,
    is_temp          TINYINT       NOT NULL DEFAULT 0,
    is_milestone     TINYINT       NOT NULL DEFAULT 0,
    plan_start       DATE          NOT NULL,
    plan_end         DATE          NOT NULL,
    actual_start     DATETIME      NULL,
    actual_end       DATETIME      NULL,
    progress_pct     DECIMAL(5,2)  NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'TODO' COMMENT 'E-16',
    output_required  TINYINT       NOT NULL DEFAULT 0,
    output_file_ids  JSON          NULL,
    finish_note      VARCHAR(500)  NULL,
    approval_required TINYINT      NOT NULL DEFAULT 0,
    approve_by       BIGINT        NULL,
    delay_days       INT           NULL,
    pause_reason     VARCHAR(300)  NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NULL,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_task_no UNIQUE (task_no)
) COMMENT '任务';
CREATE INDEX idx_task_project ON prj_task (project_id, status);
CREATE INDEX idx_task_assignee ON prj_task (assignee_id, status);
CREATE INDEX idx_task_plan ON prj_task (plan_end);

-- T-PRJ-04 任务依赖
CREATE TABLE prj_task_dependency (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT      NOT NULL,
    pre_task_id BIGINT      NOT NULL,
    dep_type    VARCHAR(4)  NOT NULL DEFAULT 'FS' COMMENT 'FS/SS/FF/SF',
    lag_days    INT         NOT NULL DEFAULT 0,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    created_by  VARCHAR(64) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64) NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_task_dep UNIQUE (task_id, pre_task_id)
) COMMENT '任务依赖';

-- T-PRJ-07 ECO（乐观锁；编号与 BOM 变更统一 ECN）
CREATE TABLE prj_eco (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    eco_no            VARCHAR(24)   NOT NULL,
    project_id        BIGINT        NOT NULL,
    change_reason     VARCHAR(1000) NOT NULL,
    change_content    VARCHAR(2000) NOT NULL,
    impact_analysis   VARCHAR(2000) NULL,
    affected_depts    JSON          NULL,
    file_ids          JSON          NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/COUNTERSIGN/APPROVED/EFFECTED/REJECTED',
    approved_by       BIGINT        NULL,
    effective_at      DATETIME      NULL,
    old_disposal_plan VARCHAR(1000) NULL COMMENT '旧料处置方案（BR-09 必填）',
    bom_change_id     BIGINT        NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)   NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NULL,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT uniq_eco_no UNIQUE (eco_no)
) COMMENT '工程变更';
CREATE INDEX idx_eco_project ON prj_eco (project_id, status);

-- ---------- 种子：标准系统集成模板（05 册 §2.1 六阶段 22 任务） ----------
INSERT INTO prj_template (id, template_code, template_name, project_type, is_default, status, created_by) VALUES
(1, 'TPL-STD', '标准系统集成项目', 'SYSTEM_INTEGRATION', 1, 'ACTIVE', 'seed'),
(2, 'TPL-SB',   '纯设备供货项目',   'EQUIPMENT', 0, 'ACTIVE', 'seed'),
(3, 'TPL-RD',   '内部研发项目',     'INTERNAL_RD', 0, 'ACTIVE', 'seed');

INSERT INTO prj_template_task (id, template_id, parent_id, task_name, dept_id, role_code, duration_days, is_optional, output_required, output_name, approval_required, seq, created_by) VALUES
-- 阶段 1 项目启动
(101, 1, NULL, '项目启动', 1, NULL, 7, 0, 0, NULL, 0, 1, 'seed'),
(111, 1, 101, '项目立项审批', 1, 'PM', 2, 0, 0, NULL, 1, 1, 'seed'),
(112, 1, 101, '项目组组建/开工会', 1, 'PM', 3, 0, 1, '会议纪要', 0, 2, 'seed'),
-- 阶段 2 技术准备
(102, 1, NULL, '技术准备', 6, NULL, 15, 0, 0, NULL, 0, 2, 'seed'),
(121, 1, 102, '技术条件获取（勘测报告/甲方技术条件）', 16, 'TECH', 7, 0, 1, '勘测报告或技术条件文件', 0, 1, 'seed'),
(122, 1, 102, '初步技术方案编制', 16, 'TECH', 5, 0, 1, '方案文档', 0, 2, 'seed'),
(123, 1, 102, '方案会审', 16, 'TECH', 1, 0, 1, '会议记录', 1, 3, 'seed'),
-- 阶段 3 设计开发
(103, 1, NULL, '设计开发', 6, NULL, 25, 0, 0, NULL, 0, 3, 'seed'),
(131, 1, 103, '电气设计（接线图/原理图/地址表/IP 规划）', 16, 'TECH', 10, 0, 1, '图纸集', 1, 1, 'seed'),
(132, 1, 103, '机械设计（3D 模型/组装图/钣金图）', 16, 'TECH', 10, 0, 1, '图纸集', 1, 2, 'seed'),
(133, 1, 103, '深化方案会审', 16, 'TECH', 1, 0, 1, '会议记录', 1, 3, 'seed'),
(134, 1, 103, 'EBOM 输出', 16, 'TECH', 4, 0, 1, 'BOM 已发布', 0, 4, 'seed'),
-- 阶段 4 采购生产
(104, 1, NULL, '采购生产', 10, NULL, 40, 0, 0, NULL, 0, 4, 'seed'),
(141, 1, 104, '采购需求生成与下单', 10, 'PUR', 5, 0, 1, 'PO', 0, 1, 'seed'),
(142, 1, 104, '采购到货跟踪', 10, 'PUR', 20, 0, 1, '到货记录', 0, 2, 'seed'),
(143, 1, 104, '工艺文件编制（临时）', 7, 'PCS', 5, 0, 1, '工艺文件', 1, 3, 'seed'),
(144, 1, 104, 'MBOM 生成', 7, 'PCS', 3, 0, 1, 'MBOM 已发布', 1, 4, 'seed'),
(145, 1, 104, '生产订单与执行', 8, 'PROD', 15, 0, 1, '完工入库', 0, 5, 'seed'),
-- 阶段 5 现场实施
(105, 1, NULL, '现场实施', 15, NULL, 30, 0, 0, NULL, 0, 5, 'seed'),
(151, 1, 105, '发货与到场确认', 12, 'WH', 3, 0, 1, '签收单', 0, 1, 'seed'),
(152, 1, 105, '首台安装调试', 15, 'ENG', 7, 0, 1, '安装/调试记录+业主验收单', 1, 2, 'seed'),
(153, 1, 105, '批量安装与施工监管', 15, 'ENG', 15, 0, 1, '日志/照片/销项表', 0, 3, 'seed'),
(154, 1, 105, '整改闭环', 15, 'ENG', 5, 0, 1, '整改前后照片', 1, 4, 'seed'),
-- 阶段 6 收尾
(106, 1, NULL, '收尾', 15, NULL, 15, 0, 0, NULL, 0, 6, 'seed'),
(161, 1, 106, '竣工验收', 15, 'ENG', 5, 0, 1, '验收报告', 1, 1, 'seed'),
(162, 1, 106, '结算与回款', 4, 'BIZ', 5, 0, 1, '发票/回款记录', 0, 2, 'seed'),
(163, 1, 106, '项目总结', 1, 'PM', 2, 0, 1, '总结报告', 0, 3, 'seed'),
(164, 1, 106, '质保启动', 15, 'ENG', 1, 0, 1, '质保跟踪', 0, 4, 'seed');

-- ---------- 权限与菜单（S2：project 组 + 菜单） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(66, 'menu:project',        '项目管理', 'project', 'MENU', NULL, 6, 'ACTIVE', 'seed'),
(67, 'project:create',      '立项',     'project', 'API', NULL, 1, 'ACTIVE', 'seed'),
(68, 'project:view',        '项目查看', 'project', 'API', NULL, 2, 'ACTIVE', 'seed'),
(69, 'project:task:assign', '任务指派', 'project', 'API', NULL, 3, 'ACTIVE', 'seed'),
(70, 'project:task:adjust_date', '调整日期', 'project', 'API', NULL, 4, 'ACTIVE', 'seed'),
(71, 'project:eco:create',  '发起 ECO', 'project', 'API', NULL, 5, 'ACTIVE', 'seed'),
(72, 'project:eco:approve', 'ECO 审批', 'project', 'API', NULL, 6, 'ACTIVE', 'seed'),
(73, 'project:gate:review', '门控评审', 'project', 'API', NULL, 7, 'ACTIVE', 'seed'),
(74, 'project:member:manage', '成员管理', 'project', 'API', NULL, 8, 'ACTIVE', 'seed');

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 67 AND 74
WHERE r.id IN (6, 7, 14);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (66, 67, 68, 69, 70, 71, 73, 74)
WHERE r.id = 4;

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 66 AND 74
WHERE r.id IN (1, 2);
