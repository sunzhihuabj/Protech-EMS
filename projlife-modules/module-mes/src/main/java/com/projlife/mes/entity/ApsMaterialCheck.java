package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES aps_material_check（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("aps_material_check")
public class ApsMaterialCheck extends BaseEntity {

    /** 排产单 */
    private Long scheduleId;

    /** 生产订单 */
    private Long productionOrderId;

    /** 物料 */
    private Long materialId;

    /** 需求数量 */
    private BigDecimal requiredQty;

    /** 可用数量 */
    private BigDecimal availableQty;

    /** 缺料数量 */
    private BigDecimal shortageQty;

    /** 最早可开工日 */
    private LocalDate earliestAvailableDate;

    /** 齐套标记 */
    private Integer isReady;

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public void setProductionOrderId(Long productionOrderId) {
        this.productionOrderId = productionOrderId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getRequiredQty() {
        return requiredQty;
    }

    public void setRequiredQty(BigDecimal requiredQty) {
        this.requiredQty = requiredQty;
    }

    public BigDecimal getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(BigDecimal availableQty) {
        this.availableQty = availableQty;
    }

    public BigDecimal getShortageQty() {
        return shortageQty;
    }

    public void setShortageQty(BigDecimal shortageQty) {
        this.shortageQty = shortageQty;
    }

    public LocalDate getEarliestAvailableDate() {
        return earliestAvailableDate;
    }

    public void setEarliestAvailableDate(LocalDate earliestAvailableDate) {
        this.earliestAvailableDate = earliestAvailableDate;
    }

    public Integer getIsReady() {
        return isReady;
    }

    public void setIsReady(Integer isReady) {
        this.isReady = isReady;
    }

}