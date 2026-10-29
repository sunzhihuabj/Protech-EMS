-- =====================================================================
-- ProjLife V1：平台底座（S0）
-- 范围：02 册 T-SYS 14 张 + kb_board（S0-15 种子九板需要）+ T-AI 2 张（AI 网关进程需要）
-- 规范：02 册 §一（主键自增/软删/审计字段/无外键/枚举 VARCHAR(24)/JSON 不建索引）
-- 兼容：仅 MySQL 8.0 标准语法（GreatDB 兼容，禁用 8.0.30+ 特性）；UNSIGNED 记 ASSUMPTIONS A-08
-- =====================================================================

-- T-SYS-01 部门（14 部门树）
CREATE TABLE sys_department (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_code       VARCHAR(32)  NOT NULL COMMENT '部门编码',
    dept_name       VARCHAR(64)  NOT NULL COMMENT '部门名称',
    parent_id       BIGINT       NULL COMMENT '上级部门',
    dept_type       VARCHAR(24)  NOT NULL DEFAULT 'DEPT' COMMENT 'COMPANY/DEPT/GROUP',
    leader_id       BIGINT       NULL COMMENT '负责人',
    is_chain_master TINYINT      NOT NULL DEFAULT 0 COMMENT '是否业务链主（九板四链）',
    legal_entity_id BIGINT       NULL COMMENT '所属法人',
    sort_no         INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'E-02',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_dept_code UNIQUE (dept_code)
) COMMENT '部门';

-- T-SYS-02 用户（02 册 §4.1 全字段）
CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_code       VARCHAR(32)  NOT NULL COMMENT '工号',
    username        VARCHAR(64)  NOT NULL COMMENT '登录名（手机号/工号）',
    real_name       VARCHAR(50)  NOT NULL COMMENT '真实姓名（水印用）',
    password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt(10)',
    phone           VARCHAR(20)  NULL COMMENT '水印用；脱敏展示',
    dept_id         BIGINT       NOT NULL COMMENT '部门',
    position        VARCHAR(50)  NULL COMMENT '岗位',
    user_type       VARCHAR(16)  NOT NULL COMMENT 'E-01 INTERNAL/SUPPLIER/WORKER',
    legal_entity_id BIGINT       NOT NULL COMMENT '所属法人',
    status          VARCHAR(16)  NOT NULL COMMENT 'E-02 ACTIVE/FROZEN/PENDING',
    must_change_pwd TINYINT      NOT NULL DEFAULT 1 COMMENT '首登强制改密',
    pwd_updated_at  DATETIME     NULL COMMENT '密码有效期判定',
    last_login_at   DATETIME     NULL,
    last_login_ip   VARCHAR(64)  NULL,
    valid_from      DATE         NULL COMMENT '第三方账号有效期起',
    valid_to        DATE         NULL COMMENT '第三方账号有效期止',
    wx_openid       VARCHAR(64)  NULL COMMENT '小程序绑定',
    theme_code      VARCHAR(24)  NULL COMMENT '个人主题偏好 t1-t12（03 册 §6A）',
    remark          VARCHAR(300) NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_user_code UNIQUE (user_code),
    CONSTRAINT uniq_user_name UNIQUE (username)
) COMMENT '用户';
CREATE INDEX idx_user_dept ON sys_user (dept_id);
CREATE INDEX idx_user_type_status ON sys_user (user_type, status);

-- T-SYS-03 角色（18 角色）
CREATE TABLE sys_role (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code  VARCHAR(24)  NOT NULL COMMENT 'R01-R18',
    role_name  VARCHAR(64)  NOT NULL,
    data_scope VARCHAR(16)  NOT NULL COMMENT 'E-35 SELF/DEPT/DEPT_SUB/PROJECT/ALL',
    remark     VARCHAR(200) NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted    TINYINT      NOT NULL DEFAULT 0,
    created_by VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)  NULL,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_role_code UNIQUE (role_code)
) COMMENT '角色';

-- T-SYS-04 用户角色
CREATE TABLE sys_user_role (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '用户角色关系';
CREATE INDEX idx_ur_user ON sys_user_role (user_id);
CREATE INDEX idx_ur_role ON sys_user_role (role_id);

-- T-SYS-05 权限项
CREATE TABLE sys_permission (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_code  VARCHAR(64) NOT NULL COMMENT '模块:操作[:对象]',
    perm_name  VARCHAR(64) NOT NULL,
    module     VARCHAR(32) NOT NULL,
    perm_type  VARCHAR(16) NOT NULL COMMENT 'MENU/BUTTON/API',
    parent_id  BIGINT      NULL,
    sort_no    INT         NOT NULL DEFAULT 0,
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted    TINYINT     NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_perm_code UNIQUE (perm_code)
) COMMENT '权限项';

-- T-SYS-06 角色权限
CREATE TABLE sys_role_permission (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id    BIGINT      NOT NULL,
    perm_id    BIGINT      NOT NULL,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '角色权限关系';
CREATE INDEX idx_rp_role ON sys_role_permission (role_id);
CREATE INDEX idx_rp_perm ON sys_role_permission (perm_id);

-- T-SYS-07 字典类型（E-01~E-42 全部 is_system=1）
CREATE TABLE sys_dict_type (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_code  VARCHAR(32)  NOT NULL,
    type_name  VARCHAR(64)  NOT NULL,
    is_system  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=规格内置枚举，禁删禁改 item_code',
    remark     VARCHAR(200) NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted    TINYINT      NOT NULL DEFAULT 0,
    created_by VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)  NULL,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_dict_type_code UNIQUE (type_code)
) COMMENT '字典类型';

-- T-SYS-08 字典项
CREATE TABLE sys_dict_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_id    BIGINT      NOT NULL,
    item_code  VARCHAR(24) NOT NULL COMMENT '英文大写 code（02 册 §一 规范 9）',
    item_label VARCHAR(64) NOT NULL COMMENT '中文 label',
    item_value VARCHAR(100) NULL,
    sort_no    INT         NOT NULL DEFAULT 0,
    is_default TINYINT     NOT NULL DEFAULT 0,
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted    TINYINT     NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_dict_item UNIQUE (type_id, item_code)
) COMMENT '字典项';

-- T-SYS-09 操作日志（不软删；应用层无更新方法，DB 账号另限权，03 册 §七）
CREATE TABLE sys_operation_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NULL,
    user_name   VARCHAR(64)  NULL,
    module      VARCHAR(32)  NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    target_type VARCHAR(32)  NULL,
    target_id   VARCHAR(64)  NULL,
    before_json JSON         NULL,
    after_json  JSON         NULL,
    ip          VARCHAR(64)  NULL,
    trace_id    VARCHAR(40)  NULL,
    cost_ms     INT          NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '操作日志';
CREATE INDEX idx_oplog_user ON sys_operation_log (user_id, created_at);
CREATE INDEX idx_oplog_module ON sys_operation_log (module, action);

-- T-SYS-11 编码序列（02 册 §4.17）
CREATE TABLE sys_code_sequence (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope_key     VARCHAR(120) NOT NULL COMMENT '唯一，如 CT:TLD:CG:AZFWXF:2026',
    period        VARCHAR(16)  NOT NULL COMMENT '年度/月/日',
    current_value BIGINT       NOT NULL DEFAULT 0,
    step          INT          NOT NULL DEFAULT 1,
    prefix        VARCHAR(40)  NULL COMMENT '展示前缀',
    remark        VARCHAR(200) NULL,
    created_by    VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_code_scope UNIQUE (scope_key)
) COMMENT '编码序列';

-- T-SYS-12 附件
CREATE TABLE sys_attachment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type       VARCHAR(32)  NOT NULL,
    biz_id         VARCHAR(64)  NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_path      VARCHAR(500) NOT NULL,
    file_size      BIGINT       NOT NULL,
    file_ext       VARCHAR(16)  NOT NULL,
    mime_type      VARCHAR(100) NOT NULL COMMENT '真实 MIME（魔数嗅探）',
    sha256         VARCHAR(64)  NOT NULL,
    version        INT          NOT NULL DEFAULT 1,
    is_watermarked TINYINT      NOT NULL DEFAULT 0,
    watermark_type VARCHAR(8)   NULL COMMENT 'W1/W2/W3/W4',
    uploader_id    BIGINT       NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '附件';
CREATE INDEX idx_att_biz ON sys_attachment (biz_type, biz_id);

-- T-SYS-13 导入批次
CREATE TABLE sys_import_batch (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no          VARCHAR(40)  NOT NULL,
    module            VARCHAR(32)  NOT NULL,
    file_name         VARCHAR(255) NULL,
    total_rows        INT          NOT NULL DEFAULT 0,
    success_rows      INT          NOT NULL DEFAULT 0,
    fail_rows         INT          NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL COMMENT 'DRY_RUN/COMMITTED/FAILED/ROLLED_BACK',
    error_report_path VARCHAR(500) NULL,
    rolled_back       TINYINT      NOT NULL DEFAULT 0,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NULL,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_import_batch UNIQUE (batch_no)
) COMMENT '导入批次';

-- T-SYS-14 通知
CREATE TABLE sys_notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_id BIGINT       NOT NULL,
    msg_type    VARCHAR(24)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     VARCHAR(1000) NULL,
    biz_type    VARCHAR(32)  NULL,
    biz_id      VARCHAR(64)  NULL,
    channel     VARCHAR(16)  NOT NULL DEFAULT 'INNER' COMMENT 'INNER/WECHAT/BOTH',
    is_read     TINYINT      NOT NULL DEFAULT 0,
    read_at     DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '通知';
CREATE INDEX idx_notif_receiver ON sys_notification (receiver_id, is_read);

-- T-SYS-10 系统参数
CREATE TABLE sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL,
    config_value VARCHAR(1000) NULL,
    config_group VARCHAR(32)  NOT NULL DEFAULT 'app',
    is_encrypted TINYINT      NOT NULL DEFAULT 0,
    remark       VARCHAR(200) NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_config_key UNIQUE (config_key)
) COMMENT '系统参数';

-- T-KB-01 看板（S0-15 种子九板）
CREATE TABLE kb_board (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_code      VARCHAR(24) NOT NULL,
    board_name      VARCHAR(64) NOT NULL,
    board_level     VARCHAR(16) NOT NULL COMMENT 'TOTAL/CHAIN/FUNC',
    dept_id         BIGINT      NULL,
    chain_master_id BIGINT      NULL,
    rhythm          VARCHAR(8)  NOT NULL COMMENT 'DAILY/WEEKLY',
    sort_no         INT         NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted         TINYINT     NOT NULL DEFAULT 0,
    created_by      VARCHAR(64) NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) NULL,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_board_code UNIQUE (board_code)
) COMMENT '看板';

-- T-AI-01 AI 调用日志（不软删）
CREATE TABLE ai_call_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_no        VARCHAR(40)  NOT NULL,
    ai_code        VARCHAR(16)  NOT NULL COMMENT 'AI-001…AI-104',
    model_type     VARCHAR(8)   NOT NULL COMMENT 'DATA/IMAGE',
    biz_type       VARCHAR(32)  NOT NULL,
    biz_id         BIGINT       NULL,
    request_json   JSON         NOT NULL COMMENT '脱敏后',
    response_json  JSON         NULL,
    status         VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILED/TIMEOUT/DEGRADED',
    confidence     DECIMAL(5,2) NULL,
    elapsed_ms     INT          NULL,
    model_version  VARCHAR(40)  NULL COMMENT '可复现',
    prompt_version VARCHAR(20)  NULL,
    is_mock        TINYINT      NOT NULL,
    operator_id    BIGINT       NOT NULL,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_ai_call_no UNIQUE (call_no)
) COMMENT 'AI 调用日志';
CREATE INDEX idx_ai_biz ON ai_call_log (biz_type, biz_id);
CREATE INDEX idx_ai_code_time ON ai_call_log (ai_code, created_at);

-- T-AI-02 模型配置
CREATE TABLE ai_model_config (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_type     VARCHAR(8)   NOT NULL COMMENT 'DATA/IMAGE',
    model_name     VARCHAR(64)  NOT NULL,
    endpoint_url   VARCHAR(255) NULL COMMENT '仅内网地址（BR-AI-02）',
    api_key        VARCHAR(255) NULL COMMENT '加密存储',
    timeout_ms     INT          NOT NULL DEFAULT 8000,
    retry          INT          NOT NULL DEFAULT 1,
    is_mock        TINYINT      NOT NULL DEFAULT 1,
    health_status  VARCHAR(16)  NULL COMMENT 'UP/DOWN/MOCK',
    last_check_at  DATETIME     NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '模型配置';
