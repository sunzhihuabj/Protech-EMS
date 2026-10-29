package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM plm_survey（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("plm_survey")
public class PlmSurvey extends BaseEntity {

    /** 项目 id */
    private Long projectId;

    /** 勘测类型 */
    private String surveyType;

    /** 勘测日期 */
    private LocalDate surveyDate;

    /** 勘测人 */
    private Long surveyorId;

    /** 勘测内容（现场条件/关键尺寸/限制条件） */
    private String contentJson;

    /** 附件 */
    private String attachIds;

    /** E-02 */
    private String status;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getSurveyType() {
        return surveyType;
    }

    public void setSurveyType(String surveyType) {
        this.surveyType = surveyType;
    }

    public LocalDate getSurveyDate() {
        return surveyDate;
    }

    public void setSurveyDate(LocalDate surveyDate) {
        this.surveyDate = surveyDate;
    }

    public Long getSurveyorId() {
        return surveyorId;
    }

    public void setSurveyorId(Long surveyorId) {
        this.surveyorId = surveyorId;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getAttachIds() {
        return attachIds;
    }

    public void setAttachIds(String attachIds) {
        this.attachIds = attachIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}