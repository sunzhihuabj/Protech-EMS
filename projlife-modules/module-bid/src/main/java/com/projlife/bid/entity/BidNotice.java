package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_notice（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_notice")
public class BidNotice extends VersionedEntity {

    /** BID-{YYYYMM}-{4位} UK */
    private String noticeCode;

    /** 数据源 id */
    private Long sourceId;

    /** URL */
    private String url;

    /** SHA-256 UK */
    private String contentHash;

    /** 标题 */
    private String title;

    /** 业主原始名 */
    private String ownerNameRaw;

    /** 归并后客户 id */
    private Long ownerId;

    /** 项目名 */
    private String projectName;

    /** 标段号 */
    private String lotNo;

    /** 预算金额 */
    private BigDecimal budgetAmount;

    /** 投标截止 */
    private LocalDateTime deadlineAt;

    /** 开标时间 */
    private LocalDateTime openBidAt;

    /** 资质要求 */
    private String qualification;

    /** 区域 */
    private String region;

    /** 机型要求 */
    private String turbineRequirements;

    /** 摘要 */
    private String summary;

    /** PENDING/PARSED/MANUAL/INCOMPLETE */
    private String parseStatus;

    /** TEMPLATE/AI-015 */
    private String parseBy;

    /** 置信度四元组 */
    private String confidence;

    /** ACTIVE/SUPERSEDED/CONVERTED/CLOSED/EXCLUDED */
    private String status;

    /** 变更/补遗关联原公告 */
    private Long parentId;

    public String getNoticeCode() {
        return noticeCode;
    }

    public void setNoticeCode(String noticeCode) {
        this.noticeCode = noticeCode;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOwnerNameRaw() {
        return ownerNameRaw;
    }

    public void setOwnerNameRaw(String ownerNameRaw) {
        this.ownerNameRaw = ownerNameRaw;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public LocalDateTime getOpenBidAt() {
        return openBidAt;
    }

    public void setOpenBidAt(LocalDateTime openBidAt) {
        this.openBidAt = openBidAt;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getTurbineRequirements() {
        return turbineRequirements;
    }

    public void setTurbineRequirements(String turbineRequirements) {
        this.turbineRequirements = turbineRequirements;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParseBy() {
        return parseBy;
    }

    public void setParseBy(String parseBy) {
        this.parseBy = parseBy;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

}