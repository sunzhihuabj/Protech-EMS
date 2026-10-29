package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_invoice（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_invoice")
public class CtInvoice extends BaseEntity {

    /** 发票号 */
    private String invoiceNo;

    /** 合同 id */
    private Long contractId;

    /** 项目 id */
    private Long projectId;

    /** 发票类型 */
    private String invoiceType;

    /** 开票金额 */
    private BigDecimal amount;

    /** 税率 */
    private BigDecimal taxRate;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** APPLIED/CONFIRMED/REJECTED */
    private String status;

    /** T+ 凭证号 */
    private String tPlusRef;

    /** 申请人 */
    private Long applyBy;

    /** 确认人 */
    private Long confirmBy;

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
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

    public String getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(String invoiceType) {
        this.invoiceType = invoiceType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTPlusRef() {
        return tPlusRef;
    }

    public void setTPlusRef(String tPlusRef) {
        this.tPlusRef = tPlusRef;
    }

    public Long getApplyBy() {
        return applyBy;
    }

    public void setApplyBy(Long applyBy) {
        this.applyBy = applyBy;
    }

    public Long getConfirmBy() {
        return confirmBy;
    }

    public void setConfirmBy(Long confirmBy) {
        this.confirmBy = confirmBy;
    }

}