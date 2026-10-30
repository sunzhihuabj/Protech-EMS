package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_safety_check（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_safety_check")
public class EngSafetyCheck extends BaseEntity {

    /** 项目 */
    private Long projectId;

    /** 检查类型 */
    private String checkType;

    /** 检查日期 */
    private LocalDate checkDate;

    /** 隐患 */
    private String hazardDesc;

    /** 照片 */
    private String photoIds;

    /** 整改状态 */
    private String rectStatus;

    /** 检查人 */
    private Long checkerId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public LocalDate getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(LocalDate checkDate) {
        this.checkDate = checkDate;
    }

    public String getHazardDesc() {
        return hazardDesc;
    }

    public void setHazardDesc(String hazardDesc) {
        this.hazardDesc = hazardDesc;
    }

    public String getPhotoIds() {
        return photoIds;
    }

    public void setPhotoIds(String photoIds) {
        this.photoIds = photoIds;
    }

    public String getRectStatus() {
        return rectStatus;
    }

    public void setRectStatus(String rectStatus) {
        this.rectStatus = rectStatus;
    }

    public Long getCheckerId() {
        return checkerId;
    }

    public void setCheckerId(Long checkerId) {
        this.checkerId = checkerId;
    }

}