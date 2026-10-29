package com.projlife.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SCM scm_supplier_onboarding（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("scm_supplier_onboarding")
public class ScmSupplierOnboarding extends BaseEntity {

    /** 供应商（md_supplier） */
    private Long supplierId;

    /** REGISTERED/QUALIFIED/VISITED/SAMPLED/APPROVED/REJECTED */
    private String currentStage;

    /** PENDING/PASSED/FAILED */
    private String stageStatus;

    /** 各阶段材料 */
    private String qualificationJson;

    /** 审核结论 */
    private String auditResult;

    /** E-36 五档 */
    private String finalLevel;

    /** 申请人 */
    private Long applyBy;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public String getStageStatus() {
        return stageStatus;
    }

    public void setStageStatus(String stageStatus) {
        this.stageStatus = stageStatus;
    }

    public String getQualificationJson() {
        return qualificationJson;
    }

    public void setQualificationJson(String qualificationJson) {
        this.qualificationJson = qualificationJson;
    }

    public String getAuditResult() {
        return auditResult;
    }

    public void setAuditResult(String auditResult) {
        this.auditResult = auditResult;
    }

    public String getFinalLevel() {
        return finalLevel;
    }

    public void setFinalLevel(String finalLevel) {
        this.finalLevel = finalLevel;
    }

    public Long getApplyBy() {
        return applyBy;
    }

    public void setApplyBy(Long applyBy) {
        this.applyBy = applyBy;
    }

}