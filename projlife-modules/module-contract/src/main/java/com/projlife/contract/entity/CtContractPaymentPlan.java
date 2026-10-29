package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_contract_payment_plan（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_contract_payment_plan")
public class CtContractPaymentPlan extends BaseEntity {

    /** 合同 id */
    private Long contractId;

    /** 阶段名（预付款/到货款/验收款/质保金） */
    private String phaseName;

    /** 比例 0-1 */
    private BigDecimal percentage;

    /** 金额（元） */
    private BigDecimal amount;

    /** 计划收款日 */
    private LocalDate planDate;

    /** 实际收款 */
    private BigDecimal actualAmount;

    /** 实际到账日 */
    private LocalDate actualDate;

    /** PENDING/PARTIAL/RECEIVED */
    private String status;

    /** 关联发票 */
    private Long invoiceId;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public LocalDate getActualDate() {
        return actualDate;
    }

    public void setActualDate(LocalDate actualDate) {
        this.actualDate = actualDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

}