package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_count（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_count")
public class InvCount extends VersionedEntity {

    /** CT-{YYYYMMDD}-{3位} */
    private String countNo;

    /** A/B/C/FULL */
    private String countType;

    /** 范围（仓库/库区/货位/三色） */
    private String scopeJson;

    /** 已创建/盘点中/待复核/差异处理/已调账/已关闭 */
    private String status;

    /** 开始 */
    private LocalDateTime startedAt;

    /** 结束 */
    private LocalDateTime finishedAt;

    /** 创建人 */
    private Long creatorId;

    /** 复核人 */
    private Long reviewerId;

    public String getCountNo() {
        return countNo;
    }

    public void setCountNo(String countNo) {
        this.countNo = countNo;
    }

    public String getCountType() {
        return countType;
    }

    public void setCountType(String countType) {
        this.countType = countType;
    }

    public String getScopeJson() {
        return scopeJson;
    }

    public void setScopeJson(String scopeJson) {
        this.scopeJson = scopeJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

}