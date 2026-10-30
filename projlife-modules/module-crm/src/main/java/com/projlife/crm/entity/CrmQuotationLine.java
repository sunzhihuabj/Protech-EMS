package com.projlife.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CRM crm_quotation_line（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("crm_quotation_line")
public class CrmQuotationLine extends BaseEntity {

    /** 报价单 */
    private Long quotationId;

    /** 物料 */
    private Long materialId;

    /** 数量 */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal price;

    /** 行金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    public Long getQuotationId() {
        return quotationId;
    }

    public void setQuotationId(Long quotationId) {
        this.quotationId = quotationId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}