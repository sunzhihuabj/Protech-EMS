package com.projlife.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CRM crm_opportunity（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("crm_opportunity")
public class CrmOpportunity extends VersionedEntity {

    /** OPP-{YYYYMM}-{4位} */
    private String oppNo;

    /** 商机名称 */
    private String oppName;

    /** 客户主体（md_customer） */
    private Long customerId;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 需求简述 */
    private String summary;

    /** 意向等级 H/M/L */
    private String intentionLevel;

    /** 预计签约日 */
    private LocalDate expectSignDate;

    /** 预计金额（元） */
    private BigDecimal expectAmount;

    /** 竞争对手 */
    private String competitor;

    /** 负责人（默认创建人） */
    private Long ownerId;

    /** TRACKING/WON/LOST */
    private String status;

    /** 丢单原因 */
    private String lostReason;

    /** 成交合同 id */
    private Long convertedContractId;

    /** 来源类型（BID/MANUAL/REFERRAL，FR-CRM-20/D-05） */
    private String sourceType;

    /** 来源 id（如标讯公告） */
    private Long sourceId;

    /** 来源说明 */
    private String sourceNote;

    public String getOppNo() {
        return oppNo;
    }

    public void setOppNo(String oppNo) {
        this.oppNo = oppNo;
    }

    public String getOppName() {
        return oppName;
    }

    public void setOppName(String oppName) {
        this.oppName = oppName;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getIntentionLevel() {
        return intentionLevel;
    }

    public void setIntentionLevel(String intentionLevel) {
        this.intentionLevel = intentionLevel;
    }

    public LocalDate getExpectSignDate() {
        return expectSignDate;
    }

    public void setExpectSignDate(LocalDate expectSignDate) {
        this.expectSignDate = expectSignDate;
    }

    public BigDecimal getExpectAmount() {
        return expectAmount;
    }

    public void setExpectAmount(BigDecimal expectAmount) {
        this.expectAmount = expectAmount;
    }

    public String getCompetitor() {
        return competitor;
    }

    public void setCompetitor(String competitor) {
        this.competitor = competitor;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLostReason() {
        return lostReason;
    }

    public void setLostReason(String lostReason) {
        this.lostReason = lostReason;
    }

    public Long getConvertedContractId() {
        return convertedContractId;
    }

    public void setConvertedContractId(Long convertedContractId) {
        this.convertedContractId = convertedContractId;
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

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }

}