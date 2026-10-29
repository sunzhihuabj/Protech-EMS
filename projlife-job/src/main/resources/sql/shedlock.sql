-- ShedLock 基础设施表（16 册 §2.1 要求 ShedLock；表属调度基础设施，不在 02 册 150 张业务表清单内，记 ASSUMPTIONS A-09）
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until DATETIME(3)  NOT NULL,
    locked_at  DATETIME(3)  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
