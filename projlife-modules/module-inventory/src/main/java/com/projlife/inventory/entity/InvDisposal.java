package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_disposal（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_disposal")
public class InvDisposal extends BaseEntity {

    /** DP-{YYYYMM}-{4位} */
    private String disposalNo;

    /** 呆滞 id */
    private Long slowMovingId;

    /** E-39 处置方式 */
    private String method;

    /** 数量 */
    private BigDecimal qty;

    /** 预计金额 */
    private BigDecimal expectAmount;

    /** 实际金额 */
    private BigDecimal actualAmount;

    /** 分级审批 */
    private String approvalLevel;

    /** 审批人 */
    private Long approvedBy;

    /** 处置时间 */
    private LocalDateTime disposedAt;

    /** 备注 */
    private String remark;

    public String getDisposalNo() {
        return disposalNo;
    }

    public void setDisposalNo(String disposalNo) {
        this.disposalNo = disposalNo;
    }

    public Long getSlowMovingId() {
        return slowMovingId;
    }

    public void setSlowMovingId(Long slowMovingId) {
        this.slowMovingId = slowMovingId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getExpectAmount() {
        return expectAmount;
    }

    public void setExpectAmount(BigDecimal expectAmount) {
        this.expectAmount = expectAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public String getApprovalLevel() {
        return approvalLevel;
    }

    public void setApprovalLevel(String approvalLevel) {
        this.approvalLevel = approvalLevel;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getDisposedAt() {
        return disposedAt;
    }

    public void setDisposedAt(LocalDateTime disposedAt) {
        this.disposedAt = disposedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}