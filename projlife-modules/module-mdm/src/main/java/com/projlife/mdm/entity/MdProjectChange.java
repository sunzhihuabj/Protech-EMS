package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_project_change（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_project_change")
public class MdProjectChange extends BaseEntity {

    /** 变更单号 */
    private String changeNo;

    /** 项目档案 id */
    private Long mdProjectId;

    /** FARM_FILL/ENTITY_CHANGE/PHASE_SPLIT */
    private String changeType;

    /** 变更前快照 */
    private String beforeJson;

    /** 变更后快照 */
    private String afterJson;

    /** 审批人 */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    public String getChangeNo() {
        return changeNo;
    }

    public void setChangeNo(String changeNo) {
        this.changeNo = changeNo;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

}