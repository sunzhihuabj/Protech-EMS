package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM plm_design_review（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("plm_design_review")
public class PlmDesignReview extends BaseEntity {

    /** 设计文档 id */
    private Long designDocId;

    /** 审核类型 */
    private String reviewType;

    /** 审核人 */
    private Long reviewerId;

    /** 结果 */
    private String result;

    /** 意见 */
    private String opinion;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** AI 预检结果 */
    private String aiCheckResult;

    public Long getDesignDocId() {
        return designDocId;
    }

    public void setDesignDocId(Long designDocId) {
        this.designDocId = designDocId;
    }

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getAiCheckResult() {
        return aiCheckResult;
    }

    public void setAiCheckResult(String aiCheckResult) {
        this.aiCheckResult = aiCheckResult;
    }

}