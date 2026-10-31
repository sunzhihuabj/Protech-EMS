package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST int_mapping（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("int_mapping")
public class IntMapping extends BaseEntity {

    /** 适配器 id */
    private Long adapterId;

    /** 本地实体 */
    private String localEntity;

    /** 远端实体 */
    private String remoteEntity;

    /** 字段映射 */
    private String fieldMappings;

    public Long getAdapterId() {
        return adapterId;
    }

    public void setAdapterId(Long adapterId) {
        this.adapterId = adapterId;
    }

    public String getLocalEntity() {
        return localEntity;
    }

    public void setLocalEntity(String localEntity) {
        this.localEntity = localEntity;
    }

    public String getRemoteEntity() {
        return remoteEntity;
    }

    public void setRemoteEntity(String remoteEntity) {
        this.remoteEntity = remoteEntity;
    }

    public String getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(String fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

}