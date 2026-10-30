package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ARC arc_destroy（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("arc_destroy")
public class ArcDestroy extends BaseEntity {

    /** 销毁单号 */
    private String destroyNo;

    /** 档案清单 */
    private String archiveIds;

    /** 销毁原因 */
    private String reason;

    /** 鉴定意见 */
    private String appraisalOpinion;

    /** 签字人 */
    private String signersJson;

    /** 审批人 */
    private Long approvedBy;

    /** 销毁时间 */
    private LocalDateTime destroyedAt;

    /** 监销人 */
    private String supervisorIds;

    public String getDestroyNo() {
        return destroyNo;
    }

    public void setDestroyNo(String destroyNo) {
        this.destroyNo = destroyNo;
    }

    public String getArchiveIds() {
        return archiveIds;
    }

    public void setArchiveIds(String archiveIds) {
        this.archiveIds = archiveIds;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAppraisalOpinion() {
        return appraisalOpinion;
    }

    public void setAppraisalOpinion(String appraisalOpinion) {
        this.appraisalOpinion = appraisalOpinion;
    }

    public String getSignersJson() {
        return signersJson;
    }

    public void setSignersJson(String signersJson) {
        this.signersJson = signersJson;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getDestroyedAt() {
        return destroyedAt;
    }

    public void setDestroyedAt(LocalDateTime destroyedAt) {
        this.destroyedAt = destroyedAt;
    }

    public String getSupervisorIds() {
        return supervisorIds;
    }

    public void setSupervisorIds(String supervisorIds) {
        this.supervisorIds = supervisorIds;
    }

}