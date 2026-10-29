package com.projlife.common.entity;

import com.baomidou.mybatisplus.annotation.Version;

/**
 * 带乐观锁的基础实体（单据主表用，02 册 §一 规范 11）。
 */
public abstract class VersionedEntity extends BaseEntity {

    @Version
    private Integer version;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
