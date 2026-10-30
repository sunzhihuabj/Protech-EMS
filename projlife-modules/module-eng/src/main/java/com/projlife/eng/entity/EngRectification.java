package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_rectification（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_rectification")
public class EngRectification extends VersionedEntity {

    /** 整改单号 */
    private String rectNo;

    /** AI/MANUAL/SUPERVISOR */
    private String sourceType;

    /** 来源 id */
    private Long sourceId;

    /** 项目 */
    private Long projectId;

    /** 机组 */
    private Long unitId;

    /** 问题描述 */
    private String problemDesc;

    /** 整改前照片 */
    private String photoIds;

    /** 整改要求 */
    private String requirement;

    /** 期限（超期升级 UC-ENG-08） */
    private LocalDate deadline;

    /** 整改后照片 */
    private String afterPhotoIds;

    /** PENDING/DOING/TO_REVIEW/PASSED/CLOSED */
    private String status;

    /** 复核人 */
    private Long confirmBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    public String getRectNo() {
        return rectNo;
    }

    public void setRectNo(String rectNo) {
        this.rectNo = rectNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

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

    public String getProblemDesc() {
        return problemDesc;
    }

    public void setProblemDesc(String problemDesc) {
        this.problemDesc = problemDesc;
    }

    public String getPhotoIds() {
        return photoIds;
    }

    public void setPhotoIds(String photoIds) {
        this.photoIds = photoIds;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public String getAfterPhotoIds() {
        return afterPhotoIds;
    }

    public void setAfterPhotoIds(String afterPhotoIds) {
        this.afterPhotoIds = afterPhotoIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConfirmBy() {
        return confirmBy;
    }

    public void setConfirmBy(Long confirmBy) {
        this.confirmBy = confirmBy;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

}