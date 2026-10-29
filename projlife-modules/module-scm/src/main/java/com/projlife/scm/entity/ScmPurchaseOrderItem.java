package com.projlife.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SCM scm_purchase_order_item（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("scm_purchase_order_item")
public class ScmPurchaseOrderItem extends BaseEntity {

    /** PO id */
    private Long poId;

    /** 行号 */
    private Integer lineNo;

    /** 项目（分行保留） */
    private Long projectId;

    /** 物料 */
    private Long materialId;

    /** 数量 */
    private BigDecimal qty;

    /** 已收数量 */
    private BigDecimal receivedQty;

    /** 单价（BR-06 脱敏） */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 期望到货 */
    private LocalDate expectedDate;

    /** E-02 */
    private String status;

    public Long getPoId() {
        return poId;
    }

    public void setPoId(Long poId) {
        this.poId = poId;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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

    public BigDecimal getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(BigDecimal receivedQty) {
        this.receivedQty = receivedQty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}