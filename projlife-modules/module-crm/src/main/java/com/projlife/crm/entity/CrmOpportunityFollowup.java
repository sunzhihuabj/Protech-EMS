package com.projlife.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CRM crm_opportunity_followup（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("crm_opportunity_followup")
public class CrmOpportunityFollowup extends BaseEntity {

    /** 商机 id */
    private Long opportunityId;

    /** 跟进类型（拜访/电话/邮件/报价） */
    private String followType;

    /** 跟进内容 */
    private String content;

    /** 附件 id 集合 */
    private String attachIds;

    /** 下一步动作 */
    private String nextAction;

    /** 下一步日期 */
    private LocalDate nextDate;

    /** 跟进人 */
    private Long operatorId;

    public Long getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Long opportunityId) {
        this.opportunityId = opportunityId;
    }

    public String getFollowType() {
        return followType;
    }

    public void setFollowType(String followType) {
        this.followType = followType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachIds() {
        return attachIds;
    }

    public void setAttachIds(String attachIds) {
        this.attachIds = attachIds;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public LocalDate getNextDate() {
        return nextDate;
    }

    public void setNextDate(LocalDate nextDate) {
        this.nextDate = nextDate;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

}