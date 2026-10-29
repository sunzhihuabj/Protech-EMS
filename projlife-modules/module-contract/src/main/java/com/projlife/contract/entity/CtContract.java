package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_contract（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_contract")
public class CtContract extends VersionedEntity {

    /** {主体}-{YYYY}-{性质}-{业务}-{4位}，不可改 */
    private String contractNo;

    /** 存量 CON-YYYYMM-nnnn */
    private String legacyNo;

    /** 「对方主体－项目/事项」 */
    private String contractName;

    /** E-11 XS/CG/KJ */
    private String nature;

    /** 甲方 */
    private String partyA;

    /** 乙方 */
    private String partyB;

    /** 我方主体 */
    private Long legalEntityId;

    /** 对方主体（md_customer） */
    private Long counterpartyId;

    /** E-12 业务类型 */
    private String businessType;

    /** 合同金额（元） */
    private BigDecimal amount;

    /** 币种，默认 CNY */
    private String currency;

    /** 税率 0-1 */
    private BigDecimal taxRate;

    /** 付款条件原文 */
    private String paymentTerms;

    /** 交货地址 */
    private String deliveryAddr;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 交货期 */
    private LocalDate deliveryDate;

    /** 质保月数 */
    private Integer warrantyMonths;

    /** 验收条款 */
    private String acceptanceClause;

    /** 签订日期 */
    private LocalDate signDate;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 履约起 */
    private LocalDate performStart;

    /** 履约止 */
    private LocalDate performEnd;

    /** 正本份数 */
    private Integer copyCount;

    /** 经办人 */
    private Long handlerId;

    /** 评审人 */
    private Long reviewerId;

    /** E-13 合同状态 */
    private String status;

    /** 建档完成时间（BR-02 判定） */
    private LocalDateTime filedAt;

    /** sign_date + 3 工作日 */
    private LocalDate filingDeadline;

    /** 开票冻结 */
    private Integer invoiceFrozen;

    /** 是否经 AI 提取 */
    private Integer aiExtracted;

    /** 档号 */
    private String archiveNo;

    /** 签章 PDF（sys_attachment） */
    private Long originalFileId;

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public String getLegacyNo() {
        return legacyNo;
    }

    public void setLegacyNo(String legacyNo) {
        this.legacyNo = legacyNo;
    }

    public String getContractName() {
        return contractName;
    }

    public void setContractName(String contractName) {
        this.contractName = contractName;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public String getPartyA() {
        return partyA;
    }

    public void setPartyA(String partyA) {
        this.partyA = partyA;
    }

    public String getPartyB() {
        return partyB;
    }

    public void setPartyB(String partyB) {
        this.partyB = partyB;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(Long counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getDeliveryAddr() {
        return deliveryAddr;
    }

    public void setDeliveryAddr(String deliveryAddr) {
        this.deliveryAddr = deliveryAddr;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public String getAcceptanceClause() {
        return acceptanceClause;
    }

    public void setAcceptanceClause(String acceptanceClause) {
        this.acceptanceClause = acceptanceClause;
    }

    public LocalDate getSignDate() {
        return signDate;
    }

    public void setSignDate(LocalDate signDate) {
        this.signDate = signDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getPerformStart() {
        return performStart;
    }

    public void setPerformStart(LocalDate performStart) {
        this.performStart = performStart;
    }

    public LocalDate getPerformEnd() {
        return performEnd;
    }

    public void setPerformEnd(LocalDate performEnd) {
        this.performEnd = performEnd;
    }

    public Integer getCopyCount() {
        return copyCount;
    }

    public void setCopyCount(Integer copyCount) {
        this.copyCount = copyCount;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getFiledAt() {
        return filedAt;
    }

    public void setFiledAt(LocalDateTime filedAt) {
        this.filedAt = filedAt;
    }

    public LocalDate getFilingDeadline() {
        return filingDeadline;
    }

    public void setFilingDeadline(LocalDate filingDeadline) {
        this.filingDeadline = filingDeadline;
    }

    public Integer getInvoiceFrozen() {
        return invoiceFrozen;
    }

    public void setInvoiceFrozen(Integer invoiceFrozen) {
        this.invoiceFrozen = invoiceFrozen;
    }

    public Integer getAiExtracted() {
        return aiExtracted;
    }

    public void setAiExtracted(Integer aiExtracted) {
        this.aiExtracted = aiExtracted;
    }

    public String getArchiveNo() {
        return archiveNo;
    }

    public void setArchiveNo(String archiveNo) {
        this.archiveNo = archiveNo;
    }

    public Long getOriginalFileId() {
        return originalFileId;
    }

    public void setOriginalFileId(Long originalFileId) {
        this.originalFileId = originalFileId;
    }

}