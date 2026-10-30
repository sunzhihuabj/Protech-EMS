package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_progress（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_progress")
public class EngProgress extends BaseEntity {

    /** 项目 */
    private Long projectId;

    /** WBS */
    private String wbsCode;

    /** 里程碑 */
    private String milestoneName;

    /** 计划进度 */
    private BigDecimal planPct;

    /** 实际进度 */
    private BigDecimal actualPct;

    /** 计划开始 */
    private LocalDate planStart;

    /** 计划结束 */
    private LocalDate planEnd;

    /** E-02 */
    private String status;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getWbsCode() {
        return wbsCode;
    }

    public void setWbsCode(String wbsCode) {
        this.wbsCode = wbsCode;
    }

    public String getMilestoneName() {
        return milestoneName;
    }

    public void setMilestoneName(String milestoneName) {
        this.milestoneName = milestoneName;
    }

    public BigDecimal getPlanPct() {
        return planPct;
    }

    public void setPlanPct(BigDecimal planPct) {
        this.planPct = planPct;
    }

    public BigDecimal getActualPct() {
        return actualPct;
    }

    public void setActualPct(BigDecimal actualPct) {
        this.actualPct = actualPct;
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

}