package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_production_order（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_production_order")
public class MesProductionOrder extends VersionedEntity {

    /** MO-{YYYYMM}-{4位} */
    private String moNo;

    /** 项目（BR-03） */
    private Long projectId;

    /** PROTOTYPE/BATCH/TEMP */
    private String moType;

    /** 关联 BOM */
    private Long bomHeaderId;

    /** 计划数量 */
    private BigDecimal planQty;

    /** 完工数量 */
    private BigDecimal finishedQty;

    /** 计划开始 */
    private LocalDate planStart;

    /** 计划结束 */
    private LocalDate planEnd;

    /** PENDING/RUNNING/COMPLETED/CLOSED */
    private String status;

    /** 优先级 */
    private Integer priority;

    public String getMoNo() {
        return moNo;
    }

    public void setMoNo(String moNo) {
        this.moNo = moNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getMoType() {
        return moType;
    }

    public void setMoType(String moType) {
        this.moType = moType;
    }

    public Long getBomHeaderId() {
        return bomHeaderId;
    }

    public void setBomHeaderId(Long bomHeaderId) {
        this.bomHeaderId = bomHeaderId;
    }

    public BigDecimal getPlanQty() {
        return planQty;
    }

    public void setPlanQty(BigDecimal planQty) {
        this.planQty = planQty;
    }

    public BigDecimal getFinishedQty() {
        return finishedQty;
    }

    public void setFinishedQty(BigDecimal finishedQty) {
        this.finishedQty = finishedQty;
    }

    public LocalDate getPlanStart() {
        return planStart;
    }

    public void setPlanStart(LocalDate planStart) {
        this.planStart = planStart;
    }

    public LocalDate getPlanEnd() {
        return planEnd;
    }

    public void setPlanEnd(LocalDate planEnd) {
        this.planEnd = planEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

}