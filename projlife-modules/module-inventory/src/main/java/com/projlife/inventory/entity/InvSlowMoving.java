package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_slow_moving（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_slow_moving")
public class InvSlowMoving extends BaseEntity {

    /** 物料 */
    private Long materialId;

    /** 货位 */
    private Long locationId;

    /** 数量 */
    private BigDecimal qty;

    /** 金额 */
    private BigDecimal amount;

    /** 账龄 */
    private Integer ageDays;

    /** CANDIDATE/CONFIRMED/DISPOSING/DISPOSED */
    private String status;

    /** E-38 责任归属 */
    private String responsibility;

    /** 来源 ECN */
    private Long sourceEcnId;

    /** 首次识别日期 */
    private LocalDate firstSeenDate;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getAgeDays() {
        return ageDays;
    }

    public void setAgeDays(Integer ageDays) {
        this.ageDays = ageDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponsibility() {
        return responsibility;
    }

    public void setResponsibility(String responsibility) {
        this.responsibility = responsibility;
    }

    public Long getSourceEcnId() {
        return sourceEcnId;
    }

    public void setSourceEcnId(Long sourceEcnId) {
        this.sourceEcnId = sourceEcnId;
    }

    public LocalDate getFirstSeenDate() {
        return firstSeenDate;
    }

    public void setFirstSeenDate(LocalDate firstSeenDate) {
        this.firstSeenDate = firstSeenDate;
    }

}