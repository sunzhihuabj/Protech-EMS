package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_stock_cache（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_stock_cache")
public class InvStockCache extends BaseEntity {

    /** 法人 */
    private Long legalEntityId;

    /** 仓库 */
    private String warehouseCode;

    /** 货位 */
    private Long locationId;

    /** 物料 */
    private Long materialId;

    /** 批次 */
    private String batchNo;

    /** 现存量 */
    private BigDecimal qtyOnHand;

    /** 已分配 */
    private BigDecimal qtyAllocated;

    /** 可用量 */
    private BigDecimal qtyAvailable;

    /** 缓存时间 */
    private LocalDateTime cachedAt;

    /** TTL 秒 */
    private Integer ttlSeconds;

    /** 来源（T_PLUS） */
    private String source;

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

    public BigDecimal getQtyOnHand() {
        return qtyOnHand;
    }

    public void setQtyOnHand(BigDecimal qtyOnHand) {
        this.qtyOnHand = qtyOnHand;
    }

    public BigDecimal getQtyAllocated() {
        return qtyAllocated;
    }

    public void setQtyAllocated(BigDecimal qtyAllocated) {
        this.qtyAllocated = qtyAllocated;
    }

    public BigDecimal getQtyAvailable() {
        return qtyAvailable;
    }

    public void setQtyAvailable(BigDecimal qtyAvailable) {
        this.qtyAvailable = qtyAvailable;
    }

    public LocalDateTime getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(LocalDateTime cachedAt) {
        this.cachedAt = cachedAt;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Integer ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

}