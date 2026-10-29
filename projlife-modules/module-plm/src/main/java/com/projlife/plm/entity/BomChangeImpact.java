package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM bom_change_impact（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bom_change_impact")
public class BomChangeImpact extends BaseEntity {

    /** 变更单 id */
    private Long changeId;

    /** STOCK/WIP/ON_WAY/PROCESS/PROJECT */
    private String impactType;

    /** 物料 */
    private Long materialId;

    /** 数量 */
    private BigDecimal qty;

    /** 金额 */
    private BigDecimal amount;

    /** 处置动作 */
    private String action;

    public Long getChangeId() {
        return changeId;
    }

    public void setChangeId(Long changeId) {
        this.changeId = changeId;
    }

    public String getImpactType() {
        return impactType;
    }

    public void setImpactType(String impactType) {
        this.impactType = impactType;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

}