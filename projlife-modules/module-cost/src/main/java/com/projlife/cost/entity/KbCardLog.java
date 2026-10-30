package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_card_log（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_card_log")
public class KbCardLog extends BaseEntity {

    /** 卡片 */
    private Long cardId;

    /** 原状态 */
    private String fromStatus;

    /** 新状态 */
    private String toStatus;

    /** 动作 */
    private String action;

    /** 备注 */
    private String remark;

    /** 操作人 */
    private Long operatorId;

    /** 补录 */
    private Integer isBackfill;

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getIsBackfill() {
        return isBackfill;
    }

    public void setIsBackfill(Integer isBackfill) {
        this.isBackfill = isBackfill;
    }

}