package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_template_task（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_template_task")
public class PrjTemplateTask extends BaseEntity {

    /** 模板 id */
    private Long templateId;

    /** 父节点（阶段=一级） */
    private Long parentId;

    /** 任务名 */
    private String taskName;

    /** 承担部门 */
    private Long deptId;

    /** 默认角色 */
    private String roleCode;

    /** 工期（天） */
    private Integer durationDays;

    /** 可选 */
    private Integer isOptional;

    /** 产出物要求 */
    private Integer outputRequired;

    /** 产出物名称 */
    private String outputName;

    /** 需审批 */
    private Integer approvalRequired;

    /** 同层顺序 */
    private Integer seq;

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public Integer getIsOptional() {
        return isOptional;
    }

    public void setIsOptional(Integer isOptional) {
        this.isOptional = isOptional;
    }

    public Integer getOutputRequired() {
        return outputRequired;
    }

    public void setOutputRequired(Integer outputRequired) {
        this.outputRequired = outputRequired;
    }

    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public Integer getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(Integer approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

}