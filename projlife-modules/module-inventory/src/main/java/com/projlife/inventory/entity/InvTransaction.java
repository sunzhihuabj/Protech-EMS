package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.AuditedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_transaction（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_transaction")
public class InvTransaction extends AuditedEntity {

    /** TX-{YYYYMMDD}-{6位} */
    private String txnNo;

    /** E-23 RECEIPT/ISSUE/TRANSFER/ADJUST/COUNT_ADJ/RETURN */
    private String txnType;

    /** 两司隔离 */
    private Long legalEntityId;

    /** 仓库 YL/CP/QT/GC */
    private String warehouseCode;

    /** 货位 */
    private Long locationId;

    /** 物料 */
    private Long materialId;

    /** 批次（安全件必填） */
    private String batchNo;

    /** 正入负出 ≠0 */
    private BigDecimal qty;

    /** 单位 */
    private String unit;

    /** 成本单价（R11 脱敏） */
    private BigDecimal costPrice;

    /** qty×cost_price */
    private BigDecimal amount;

    /** T+ 回读快照 */
    private BigDecimal beforeQty;

    /** T+ 回读快照 */
    private BigDecimal afterQty;

    /** 成本归集维度 */
    private Long projectId;

    /** PO/RECEIPT/SHIP/DISPATCH/REPLENISH/COUNT */
    private String sourceType;

    /** 来源单据 id */
    private Long sourceId;

    /** 来源单号 */
    private String sourceNo;

    /** 来源单号+行号+动作，UNIQUE */
    private String idempotencyKey;

    /** E-24 PENDING_SYNC/SYNCED/EXCEPTION */
    private String syncStatus;

    /** T+ 单据号 */
    private String tPlusVoucherNo;

    /** 失败原因 */
    private String syncError;

    /** 业务发生时间 */
    private LocalDateTime occurredAt;

    /** 操作人 */
    private Long operatorId;

    /** 扫码原文 */
    private String scannedCode;

    /** 备注 */
    private String remark;

    public String getTxnNo() {
        return txnNo;
    }

    public void setTxnNo(String txnNo) {
        this.txnNo = txnNo;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBeforeQty() {
        return beforeQty;
    }

    public void setBeforeQty(BigDecimal beforeQty) {
        this.beforeQty = beforeQty;
    }

    public BigDecimal getAfterQty() {
        return afterQty;
    }

    public void setAfterQty(BigDecimal afterQty) {
        this.afterQty = afterQty;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getTPlusVoucherNo() {
        return tPlusVoucherNo;
    }

    public void setTPlusVoucherNo(String tPlusVoucherNo) {
        this.tPlusVoucherNo = tPlusVoucherNo;
    }

    public String getSyncError() {
        return syncError;
    }

    public void setSyncError(String syncError) {
        this.syncError = syncError;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}