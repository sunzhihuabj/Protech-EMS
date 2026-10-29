package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_project（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_project")
public class PrjProject extends VersionedEntity {

    /** PRJ-{YYYY}-{4位} 执行号 */
    private String projectNo;

    /** 项目档案（1:1 或 1:N） */
    private Long mdProjectId;

    /** 关联合同 */
    private Long contractId;

    /** 项目名称 */
    private String projectName;

    /** 项目经理 */
    private Long managerId;

    /** 项目模板 */
    private Long templateId;

    /** 计划开始 */
    private LocalDate planStart;

    /** 计划结束 */
    private LocalDate planEnd;

    /** 实际开始 */
    private LocalDate actualStart;

    /** 实际结束 */
    private LocalDate actualEnd;

    /** E-14 项目状态 */
    private String status;

    /** 进度 0-100 */
    private BigDecimal progressPct;

    /** 预算总额（元） */
    private BigDecimal budgetTotal;

    /** 实际成本汇总（冗余） */
    private BigDecimal costActual;

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
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

    public LocalDate getActualStart() {
        return actualStart;
    }

    public void setActualStart(LocalDate actualStart) {
        this.actualStart = actualStart;
    }

    public LocalDate getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(LocalDate actualEnd) {
        this.actualEnd = actualEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(BigDecimal progressPct) {
        this.progressPct = progressPct;
    }

    public BigDecimal getBudgetTotal() {
        return budgetTotal;
    }

    public void setBudgetTotal(BigDecimal budgetTotal) {
        this.budgetTotal = budgetTotal;
    }

    public BigDecimal getCostActual() {
        return costActual;
    }

    public void setCostActual(BigDecimal costActual) {
        this.costActual = costActual;
    }

}