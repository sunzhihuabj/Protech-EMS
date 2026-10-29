package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_contract_risk（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_contract_risk")
public class CtContractRisk extends BaseEntity {

    /** 合同 id */
    private Long contractId;

    /** 风险类型 */
    private String riskType;

    /** H/M/L */
    private String riskLevel;

    /** 风险描述 */
    private String riskDesc;

    /** 建议 */
    private String suggestion;

    /** 是否采纳 */
    private Integer isAdopted;

    /** AI 调用 id（ai_call_log） */
    private Long aiCallId;

    /** 复核人 */
    private Long reviewerId;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getRiskType() {
        return riskType;
    }

    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRiskDesc() {
        return riskDesc;
    }

    public void setRiskDesc(String riskDesc) {
        this.riskDesc = riskDesc;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public Integer getIsAdopted() {
        return isAdopted;
    }

    public void setIsAdopted(Integer isAdopted) {
        this.isAdopted = isAdopted;
    }

    public Long getAiCallId() {
        return aiCallId;
    }

    public void setAiCallId(Long aiCallId) {
        this.aiCallId = aiCallId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

}