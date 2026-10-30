package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_escalation（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_escalation")
public class KbEscalation extends BaseEntity {

    /** 卡片 */
    private Long cardId;

    /** DAY3/CROSS24/RESP48 */
    private String level;

    /** 升级时间 */
    private LocalDateTime escalatedAt;

    /** 通知对象 */
    private String notifiedIds;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理人 */
    private Long handledBy;

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(LocalDateTime escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public String getNotifiedIds() {
        return notifiedIds;
    }

    public void setNotifiedIds(String notifiedIds) {
        this.notifiedIds = notifiedIds;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

}