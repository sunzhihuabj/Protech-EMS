package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST cost_item（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("cost_item")
public class CostItem extends BaseEntity {

    /** CI-{YYYYMM}-{6位} */
    private String costNo;

    /** 项目 */
    private Long projectId;

    /** E-33 七项 */
    private String costType;

    /** 二级科目 */
    private String costCategory;

    /** 成本中心 */
    private String costCenter;

    /** 金额 */
    private BigDecimal amount;

    /** 数量 */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 来源单据 */
    private String sourceType;

    /** 来源 id */
    private Long sourceId;

    /** 归属期间 */
    private LocalDate occurredDate;

    /** 补录标记 */
    private Integer isBackfill;

    public String getCostNo() {
        return costNo;
    }

    public void setCostNo(String costNo) {
        this.costNo = costNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getCostType() {
        return costType;
    }

    public void setCostType(String costType) {
        this.costType = costType;
    }

    public String getCostCategory() {
        return costCategory;
    }

    public void setCostCategory(String costCategory) {
        this.costCategory = costCategory;
    }

    public String getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public LocalDate getOccurredDate() {
        return occurredDate;
    }

    public void setOccurredDate(LocalDate occurredDate) {
        this.occurredDate = occurredDate;
    }

    public Integer getIsBackfill() {
        return isBackfill;
    }

    public void setIsBackfill(Integer isBackfill) {
        this.isBackfill = isBackfill;
    }

}