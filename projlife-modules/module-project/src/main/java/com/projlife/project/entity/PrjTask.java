package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_task（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_task")
public class PrjTask extends VersionedEntity {

    /** T-{project_no}-{3位} */
    private String taskNo;

    /** 执行项目 id */
    private Long projectId;

    /** 父任务（树形） */
    private Long parentId;

    /** 形如 1.2.3 */
    private String wbsCode;

    /** 任务名称 */
    private String taskName;

    /** E-15 NORMAL/APPROVAL/DELIVERABLE */
    private String taskType;

    /** 承担部门 */
    private Long deptId;

    /** 责任人 */
    private Long assigneeId;

    /** 默认角色 */
    private String roleCode;

    /** 可选任务 */
    private Integer isOptional;

    /** 计划外临时任务（FR-PRJ-12，A-14） */
    private Integer isTemp;

    /** 里程碑（FR-PRJ-27，A-14） */
    private Integer isMilestone;

    /** 计划开始 */
    private LocalDate planStart;

    /** 计划结束 */
    private LocalDate planEnd;

    /** 实际开始 */
    private LocalDateTime actualStart;

    /** 实际完成 */
    private LocalDateTime actualEnd;

    /** 进度 */
    private BigDecimal progressPct;

    /** E-16 任务状态 */
    private String status;

    /** 必须上传产出物 */
    private Integer outputRequired;

    /** 产出物附件 */
    private String outputFileIds;

    /** 完成说明（FR-PRJ-16） */
    private String finishNote;

    /** 需审批 */
    private Integer approvalRequired;

    /** 审批人 */
    private Long approveBy;

    /** 逾期天数（计算） */
    private Integer delayDays;

    /** 暂停原因 */
    private String pauseReason;

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getWbsCode() {
        return wbsCode;
    }

    public void setWbsCode(String wbsCode) {
        this.wbsCode = wbsCode;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Integer getIsOptional() {
        return isOptional;
    }

    public void setIsOptional(Integer isOptional) {
        this.isOptional = isOptional;
    }

    public Integer getIsTemp() {
        return isTemp;
    }

    public void setIsTemp(Integer isTemp) {
        this.isTemp = isTemp;
    }

    public Integer getIsMilestone() {
        return isMilestone;
    }

    public void setIsMilestone(Integer isMilestone) {
        this.isMilestone = isMilestone;
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

    public LocalDateTime getActualStart() {
        return actualStart;
    }

    public void setActualStart(LocalDateTime actualStart) {
        this.actualStart = actualStart;
    }

    public LocalDateTime getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(LocalDateTime actualEnd) {
        this.actualEnd = actualEnd;
    }

    public BigDecimal getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(BigDecimal progressPct) {
        this.progressPct = progressPct;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getOutputRequired() {
        return outputRequired;
    }

    public void setOutputRequired(Integer outputRequired) {
        this.outputRequired = outputRequired;
    }

    public String getOutputFileIds() {
        return outputFileIds;
    }

    public void setOutputFileIds(String outputFileIds) {
        this.outputFileIds = outputFileIds;
    }

    public String getFinishNote() {
        return finishNote;
    }

    public void setFinishNote(String finishNote) {
        this.finishNote = finishNote;
    }

    public Integer getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(Integer approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public Long getApproveBy() {
        return approveBy;
    }

    public void setApproveBy(Long approveBy) {
        this.approveBy = approveBy;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public void setDelayDays(Integer delayDays) {
        this.delayDays = delayDays;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public void setPauseReason(String pauseReason) {
        this.pauseReason = pauseReason;
    }

}