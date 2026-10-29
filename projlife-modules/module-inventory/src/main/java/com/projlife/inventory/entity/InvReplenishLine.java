package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_replenish_line（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_replenish_line")
public class InvReplenishLine extends BaseEntity {

    /** 补货单 */
    private Long replenishId;

    /** 物料 */
    private Long materialId;

    /** 数量 */
    private BigDecimal qty;

    /** 单位 */
    private String unit;

    /** 使用位置 */
    private String useLocation;

    /** 备注 */
    private String remark;

    public Long getReplenishId() {
        return replenishId;
    }

    public void setReplenishId(Long replenishId) {
        this.replenishId = replenishId;
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUseLocation() {
        return useLocation;
    }

    public void setUseLocation(String useLocation) {
        this.useLocation = useLocation;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}