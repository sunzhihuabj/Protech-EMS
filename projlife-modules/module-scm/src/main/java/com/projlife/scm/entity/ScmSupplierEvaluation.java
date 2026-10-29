package com.projlife.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SCM scm_supplier_evaluation（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("scm_supplier_evaluation")
public class ScmSupplierEvaluation extends BaseEntity {

    /** 供应商 */
    private Long supplierId;

    /** 评价期（如 2026Q4） */
    private String evalPeriod;

    /** 质量分 */
    private BigDecimal qualityScore;

    /** 交期分 */
    private BigDecimal deliveryScore;

    /** 服务分 */
    private BigDecimal serviceScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 等级 */
    private String grade;

    /** 评价人 */
    private Long evaluatorId;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getEvalPeriod() {
        return evalPeriod;
    }

    public void setEvalPeriod(String evalPeriod) {
        this.evalPeriod = evalPeriod;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public BigDecimal getDeliveryScore() {
        return deliveryScore;
    }

    public void setDeliveryScore(BigDecimal deliveryScore) {
        this.deliveryScore = deliveryScore;
    }

    public BigDecimal getServiceScore() {
        return serviceScore;
    }

    public void setServiceScore(BigDecimal serviceScore) {
        this.serviceScore = serviceScore;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Long getEvaluatorId() {
        return evaluatorId;
    }

    public void setEvaluatorId(Long evaluatorId) {
        this.evaluatorId = evaluatorId;
    }

}