package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-EAM eam_spare_part（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eam_spare_part")
public class EamSparePart extends BaseEntity {

    /** 设备 */
    private Long assetId;

    /** 备件物料 */
    private Long materialId;

    /** 数量 */
    private BigDecimal qty;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 货位 */
    private Long locationId;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
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

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

}