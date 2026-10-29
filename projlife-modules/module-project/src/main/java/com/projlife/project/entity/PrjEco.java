package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_eco（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_eco")
public class PrjEco extends VersionedEntity {

    /** ECN-{YYYY}-{4位}（与 BOM 变更统一编号） */
    private String ecoNo;

    /** 执行项目 id */
    private Long projectId;

    /** 变更原因 */
    private String changeReason;

    /** 变更内容 */
    private String changeContent;

    /** 影响分析（采购/生产/进度） */
    private String impactAnalysis;

    /** 受影响部门数组 */
    private String affectedDepts;

    /** 新版本文件 */
    private String fileIds;

    /** 发起/会签中/已批准/已生效/已驳回 */
    private String status;

    /** 批准人 */
    private Long approvedBy;

    /** 生效时间 */
    private LocalDateTime effectiveAt;

    /** 旧料处置方案（BR-09 必填） */
    private String oldDisposalPlan;

    /** 联动 bom_change（FR-PRJ-36） */
    private Long bomChangeId;

    public String getEcoNo() {
        return ecoNo;
    }

    public void setEcoNo(String ecoNo) {
        this.ecoNo = ecoNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getChangeContent() {
        return changeContent;
    }

    public void setChangeContent(String changeContent) {
        this.changeContent = changeContent;
    }

    public String getImpactAnalysis() {
        return impactAnalysis;
    }

    public void setImpactAnalysis(String impactAnalysis) {
        this.impactAnalysis = impactAnalysis;
    }

    public String getAffectedDepts() {
        return affectedDepts;
    }

    public void setAffectedDepts(String affectedDepts) {
        this.affectedDepts = affectedDepts;
    }

    public String getFileIds() {
        return fileIds;
    }

    public void setFileIds(String fileIds) {
        this.fileIds = fileIds;
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

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(LocalDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getOldDisposalPlan() {
        return oldDisposalPlan;
    }

    public void setOldDisposalPlan(String oldDisposalPlan) {
        this.oldDisposalPlan = oldDisposalPlan;
    }

    public Long getBomChangeId() {
        return bomChangeId;
    }

    public void setBomChangeId(Long bomChangeId) {
        this.bomChangeId = bomChangeId;
    }

}