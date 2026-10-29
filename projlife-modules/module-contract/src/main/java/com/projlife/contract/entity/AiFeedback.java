package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ai_feedback（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ai_feedback")
public class AiFeedback extends BaseEntity {

    /** AI 调用 id */
    private Long callId;

    /** 业务对象类型 */
    private String bizType;

    /** 业务对象 id */
    private Long bizId;

    /** 字段名 */
    private String aiField;

    /** AI 原值 */
    private String aiValue;

    /** 人工修正值 */
    private String humanValue;

    /** AI 是否正确 */
    private Integer isCorrect;

    /** 备注 */
    private String feedbackNote;

    /** 操作人 */
    private Long operatorId;

    public Long getCallId() {
        return callId;
    }

    public void setCallId(Long callId) {
        this.callId = callId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getAiField() {
        return aiField;
    }

    public void setAiField(String aiField) {
        this.aiField = aiField;
    }

    public String getAiValue() {
        return aiValue;
    }

    public void setAiValue(String aiValue) {
        this.aiValue = aiValue;
    }

    public String getHumanValue() {
        return humanValue;
    }

    public void setHumanValue(String humanValue) {
        this.humanValue = humanValue;
    }

    public Integer getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Integer isCorrect) {
        this.isCorrect = isCorrect;
    }

    public String getFeedbackNote() {
        return feedbackNote;
    }

    public void setFeedbackNote(String feedbackNote) {
        this.feedbackNote = feedbackNote;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

}