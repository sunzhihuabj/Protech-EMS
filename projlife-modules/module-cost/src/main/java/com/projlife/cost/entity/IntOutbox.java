package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST int_outbox（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("int_outbox")
public class IntOutbox extends BaseEntity {

    /** 适配器 id */
    private Long adapterId;

    /** 实体类型 */
    private String entityType;

    /** 实体 id */
    private Long entityId;

    /** 载荷 */
    private String payload;

    /** PENDING/SENT/EXPIRED */
    private String status;

    /** 幂等键 */
    private String idempotencyKey;

    public Long getAdapterId() {
        return adapterId;
    }

    public void setAdapterId(Long adapterId) {
        this.adapterId = adapterId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

}