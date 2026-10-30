package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_acceptance（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_acceptance")
public class EngAcceptance extends BaseEntity {

    /** 验收单号 */
    private String acceptNo;

    /** 机组 */
    private Long unitId;

    /** 项目 */
    private Long projectId;

    /** FIRST/PARTIAL/FINAL */
    private String acceptType;

    /** PASS/REJECT */
    private String result;

    /** 验收日期 */
    private LocalDate acceptDate;

    /** 签字件 */
    private Long signFileId;

    /** 意见 */
    private String opinion;

    /** 验收人 */
    private Long acceptorId;

    public String getAcceptNo() {
        return acceptNo;
    }

    public void setAcceptNo(String acceptNo) {
        this.acceptNo = acceptNo;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getAcceptType() {
        return acceptType;
    }

    public void setAcceptType(String acceptType) {
        this.acceptType = acceptType;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDate getAcceptDate() {
        return acceptDate;
    }

    public void setAcceptDate(LocalDate acceptDate) {
        this.acceptDate = acceptDate;
    }

    public Long getSignFileId() {
        return signFileId;
    }

    public void setSignFileId(Long signFileId) {
        this.signFileId = signFileId;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Long getAcceptorId() {
        return acceptorId;
    }

    public void setAcceptorId(Long acceptorId) {
        this.acceptorId = acceptorId;
    }

}