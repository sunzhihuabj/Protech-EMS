package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_contract_change（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_contract_change")
public class CtContractChange extends BaseEntity {

    /** {contract_no}-BC{n} */
    private String changeNo;

    /** 合同 id */
    private Long contractId;

    /** 变更类型 */
    private String changeType;

    /** 变更原因 */
    private String changeReason;

    /** 变更前 */
    private String beforeJson;

    /** 变更后 */
    private String afterJson;

    /** 金额增减 */
    private BigDecimal amountDelta;

    /** PENDING/APPROVED/REJECTED */
    private String approvalStatus;

    /** 审批人 */
    private Long approvedBy;

    /** 生效日期 */
    private LocalDate effectiveDate;

    public String getChangeNo() {
        return changeNo;
    }

    public void setChangeNo(String changeNo) {
        this.changeNo = changeNo;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public BigDecimal getAmountDelta() {
        return amountDelta;
    }

    public void setAmountDelta(BigDecimal amountDelta) {
        this.amountDelta = amountDelta;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

}