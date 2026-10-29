package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM bom_change（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bom_change")
public class BomChange extends VersionedEntity {

    /** ECN-{YYYY}-{4位}（与 ECO 统一编号） */
    private String changeNo;

    /** BOM 头 */
    private Long bomHeaderId;

    /** 设计/工艺/替换料/纠错 */
    private String changeType;

    /** 变更原因 */
    private String changeReason;

    /** 影响范围 */
    private String impactScope;

    /** 影响清单已确认 */
    private Integer impactConfirmed;

    /** 旧料处置方案（BR-09 必填，枚举+说明） */
    private String oldDisposalPlan;

    /** 发布后新版本 */
    private String newVersion;

    /** SUBMITTED/COUNTERSIGN/APPROVED/PUBLISHED/REJECTED */
    private String status;

    /** 批准人 */
    private Long approvedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    public String getChangeNo() {
        return changeNo;
    }

    public void setChangeNo(String changeNo) {
        this.changeNo = changeNo;
    }

    public Long getBomHeaderId() {
        return bomHeaderId;
    }

    public void setBomHeaderId(Long bomHeaderId) {
        this.bomHeaderId = bomHeaderId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getImpactScope() {
        return impactScope;
    }

    public void setImpactScope(String impactScope) {
        this.impactScope = impactScope;
    }

    public Integer getImpactConfirmed() {
        return impactConfirmed;
    }

    public void setImpactConfirmed(Integer impactConfirmed) {
        this.impactConfirmed = impactConfirmed;
    }

    public String getOldDisposalPlan() {
        return oldDisposalPlan;
    }

    public void setOldDisposalPlan(String oldDisposalPlan) {
        this.oldDisposalPlan = oldDisposalPlan;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

}