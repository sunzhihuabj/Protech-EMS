package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST int_sync_log（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("int_sync_log")
public class IntSyncLog extends BaseEntity {

    /** 适配器 id */
    private Long adapterId;

    /** PUSH/PULL */
    private String direction;

    /** 本地 id */
    private Long localId;

    /** 远端 id */
    private String remoteId;

    /** PENDING/SUCCESS/FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 重试次数 */
    private Integer retryCount;

    /** 推送时间 */
    private LocalDateTime pushedAt;

    public Long getAdapterId() {
        return adapterId;
    }

    public void setAdapterId(Long adapterId) {
        this.adapterId = adapterId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Long getLocalId() {
        return localId;
    }

    public void setLocalId(Long localId) {
        this.localId = localId;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getPushedAt() {
        return pushedAt;
    }

    public void setPushedAt(LocalDateTime pushedAt) {
        this.pushedAt = pushedAt;
    }

}