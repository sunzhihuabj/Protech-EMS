package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_mrb（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_mrb")
public class QmsMrb extends BaseEntity {

    /** 检验单 */
    private Long inspectionId;

    /** RETURN/CONCESSION/REWORK/SCRAP */
    private String disposition;

    /** 评审意见 */
    private String reviewOpinion;

    /** 评审人 */
    private String reviewerIds;

    /** OPEN/CLOSED */
    private String status;

    /** 成本影响 */
    private BigDecimal costImpact;

    public Long getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId) {
        this.inspectionId = inspectionId;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getReviewOpinion() {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion) {
        this.reviewOpinion = reviewOpinion;
    }

    public String getReviewerIds() {
        return reviewerIds;
    }

    public void setReviewerIds(String reviewerIds) {
        this.reviewerIds = reviewerIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getCostImpact() {
        return costImpact;
    }

    public void setCostImpact(BigDecimal costImpact) {
        this.costImpact = costImpact;
    }

}