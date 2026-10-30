package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_install_plan（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_install_plan")
public class EngInstallPlan extends BaseEntity {

    /** 项目 */
    private Long projectId;

    /** 机组 */
    private Long unitId;

    /** 计划日期 */
    private LocalDate planDate;

    /** 实际日期 */
    private LocalDate actualDate;

    /** 负责人 */
    private Long responsibleId;

    /** 安装内容 */
    private String content;

    /** PLANNED/INSTALLING/TO_DEBUG/DEBUGGING/TO_ACCEPT/ACCEPTED */
    private String status;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public LocalDate getActualDate() {
        return actualDate;
    }

    public void setActualDate(LocalDate actualDate) {
        this.actualDate = actualDate;
    }

    public Long getResponsibleId() {
        return responsibleId;
    }

    public void setResponsibleId(Long responsibleId) {
        this.responsibleId = responsibleId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}