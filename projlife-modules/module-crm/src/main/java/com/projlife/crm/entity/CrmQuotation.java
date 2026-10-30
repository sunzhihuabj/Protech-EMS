package com.projlife.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CRM crm_quotation（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("crm_quotation")
public class CrmQuotation extends BaseEntity {

    /** QT-{YYYYMM}-{4位} UK */
    private String quotationNo;

    /** 商机（一商机多版报价 UK） */
    private Long opportunityId;

    /** 客户 */
    private Long customerId;

    /** 项目档案 */
    private Long projectId;

    /** E-08 */
    private String productLine;

    /** V1/V2…，旧版只读 */
    private String version;

    /** 数量 */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 税率 */
    private BigDecimal taxRate;

    /** 金额=数量×单价×(1±税率) 服务端算 */
    private BigDecimal amount;

    /** 交付条件 */
    private String deliveryTerms;

    /** 有效期至 */
    private LocalDate validUntil;

    /** DRAFT/SUBMITTED/WON/LOST/EXPIRED */
    private String status;

    /** 价格基准（成本提示，FR-CRM-18） */
    private String priceBasisJson;

    /** 转合同回写（FR-CRM-19） */
    private Long convertedContractId;

    public String getQuotationNo() {
        return quotationNo;
    }

    public void setQuotationNo(String quotationNo) {
        this.quotationNo = quotationNo;
    }

    public Long getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Long opportunityId) {
        this.opportunityId = opportunityId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProductLine() {
        return productLine;
    }

    public void setProductLine(String productLine) {
        this.productLine = productLine;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDeliveryTerms() {
        return deliveryTerms;
    }

    public void setDeliveryTerms(String deliveryTerms) {
        this.deliveryTerms = deliveryTerms;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriceBasisJson() {
        return priceBasisJson;
    }

    public void setPriceBasisJson(String priceBasisJson) {
        this.priceBasisJson = priceBasisJson;
    }

    public Long getConvertedContractId() {
        return convertedContractId;
    }

    public void setConvertedContractId(Long convertedContractId) {
        this.convertedContractId = convertedContractId;
    }

}