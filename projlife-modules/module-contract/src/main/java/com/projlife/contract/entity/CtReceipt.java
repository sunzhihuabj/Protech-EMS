package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_receipt（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_receipt")
public class CtReceipt extends BaseEntity {

    /** 回款单号 */
    private String receiptNo;

    /** 合同 id */
    private Long contractId;

    /** 项目 id */
    private Long projectId;

    /** 收款计划行 */
    private Long paymentPlanId;

    /** 回款金额 */
    private BigDecimal amount;

    /** 到账日期 */
    private LocalDate receiptDate;

    /** 收款方式 */
    private String method;

    /** 银行流水 */
    private String bankRef;

    /** 回单附件 */
    private Long voucherFileId;

    /** PENDING/CONFIRMED */
    private String status;

    /** 财务确认人 */
    private Long confirmBy;

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getPaymentPlanId() {
        return paymentPlanId;
    }

    public void setPaymentPlanId(Long paymentPlanId) {
        this.paymentPlanId = paymentPlanId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getBankRef() {
        return bankRef;
    }

    public void setBankRef(String bankRef) {
        this.bankRef = bankRef;
    }

    public Long getVoucherFileId() {
        return voucherFileId;
    }

    public void setVoucherFileId(Long voucherFileId) {
        this.voucherFileId = voucherFileId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConfirmBy() {
        return confirmBy;
    }

    public void setConfirmBy(Long confirmBy) {
        this.confirmBy = confirmBy;
    }

}