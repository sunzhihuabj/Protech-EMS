package com.projlife.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SCM scm_price_history（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("scm_price_history")
public class ScmPriceHistory extends BaseEntity {

    /** 物料 */
    private Long materialId;

    /** 供应商 */
    private Long supplierId;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 税率 */
    private BigDecimal taxRate;

    /** 价格日期 */
    private LocalDate priceDate;

    /** 来源 PO */
    private Long sourcePoId;

    /** 历史最低 */
    private Integer isLowest;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = priceDate;
    }

    public Long getSourcePoId() {
        return sourcePoId;
    }

    public void setSourcePoId(Long sourcePoId) {
        this.sourcePoId = sourcePoId;
    }

    public Integer getIsLowest() {
        return isLowest;
    }

    public void setIsLowest(Integer isLowest) {
        this.isLowest = isLowest;
    }

}