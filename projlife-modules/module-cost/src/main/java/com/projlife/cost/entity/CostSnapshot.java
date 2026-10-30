package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST cost_snapshot（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("cost_snapshot")
public class CostSnapshot extends BaseEntity {

    /** 项目 */
    private Long projectId;

    /** 快照日（唯一：项目+日） */
    private LocalDate snapshotDate;

    /** 材料 */
    private BigDecimal materialCost;

    /** 人工 */
    private BigDecimal laborCost;

    /** 外协 */
    private BigDecimal outsourceCost;

    /** 差旅 */
    private BigDecimal travelCost;

    /** 现场 */
    private BigDecimal siteCost;

    /** 质量 */
    private BigDecimal qualityCost;

    /** 管理 */
    private BigDecimal mgmtCost;

    /** 合计 */
    private BigDecimal totalCost;

    /** 合同额 */
    private BigDecimal contractAmount;

    /** 合同口径毛利 */
    private BigDecimal grossMarginContract;

    /** 阶段口径毛利 */
    private BigDecimal grossMarginStage;

    /** 现金口径毛利 */
    private BigDecimal grossMarginCash;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public BigDecimal getMaterialCost() {
        return materialCost;
    }

    public void setMaterialCost(BigDecimal materialCost) {
        this.materialCost = materialCost;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public void setLaborCost(BigDecimal laborCost) {
        this.laborCost = laborCost;
    }

    public BigDecimal getOutsourceCost() {
        return outsourceCost;
    }

    public void setOutsourceCost(BigDecimal outsourceCost) {
        this.outsourceCost = outsourceCost;
    }

    public BigDecimal getTravelCost() {
        return travelCost;
    }

    public void setTravelCost(BigDecimal travelCost) {
        this.travelCost = travelCost;
    }

    public BigDecimal getSiteCost() {
        return siteCost;
    }

    public void setSiteCost(BigDecimal siteCost) {
        this.siteCost = siteCost;
    }

    public BigDecimal getQualityCost() {
        return qualityCost;
    }

    public void setQualityCost(BigDecimal qualityCost) {
        this.qualityCost = qualityCost;
    }

    public BigDecimal getMgmtCost() {
        return mgmtCost;
    }

    public void setMgmtCost(BigDecimal mgmtCost) {
        this.mgmtCost = mgmtCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getContractAmount() {
        return contractAmount;
    }

    public void setContractAmount(BigDecimal contractAmount) {
        this.contractAmount = contractAmount;
    }

    public BigDecimal getGrossMarginContract() {
        return grossMarginContract;
    }

    public void setGrossMarginContract(BigDecimal grossMarginContract) {
        this.grossMarginContract = grossMarginContract;
    }

    public BigDecimal getGrossMarginStage() {
        return grossMarginStage;
    }

    public void setGrossMarginStage(BigDecimal grossMarginStage) {
        this.grossMarginStage = grossMarginStage;
    }

    public BigDecimal getGrossMarginCash() {
        return grossMarginCash;
    }

    public void setGrossMarginCash(BigDecimal grossMarginCash) {
        this.grossMarginCash = grossMarginCash;
    }

}