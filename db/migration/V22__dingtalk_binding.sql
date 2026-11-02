-- V22: SPRINT-INT2 钉钉通讯录同步（A-39 静态映射的替代数据源）
-- 绑定表：钉钉 userid/unionId ↔ sys_user 匹配结果（同步按 MOBILE→NAME(唯一)→NONE 顺序匹配，A-44）

CREATE TABLE IF NOT EXISTS sys_dingtalk_binding (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dingtalk_userid VARCHAR(64)  NOT NULL COMMENT '钉钉 userid',
    union_id        VARCHAR(64)  NULL COMMENT '钉钉 unionId（待办 API 用）',
    name            VARCHAR(100) NULL COMMENT '钉钉姓名快照',
    dept_ids        VARCHAR(500) NULL COMMENT '所属部门 id CSV',
    sys_user_id     BIGINT       NULL COMMENT '匹配到的 sys_user.id（MANUAL 优先级最高）',
    match_type      VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'MANUAL/MOBILE/NAME/NONE',
    synced_at       DATETIME     NULL COMMENT '最近同步时间',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 0,
    CONSTRAINT uniq_dingtalk_userid UNIQUE (dingtalk_userid),
    KEY idx_binding_sys_user (sys_user_id)
) COMMENT '钉钉用户绑定';
