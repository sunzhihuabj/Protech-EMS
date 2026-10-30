package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_feedback（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_feedback")
public class BidFeedback extends BaseEntity {

    /** 公告 id */
    private Long noticeId;

    /** EFFECTIVE/IRRELEVANT/BID_RESULT */
    private String feedbackType;

    /** 中标结果 */
    private String bidResult;

    /** 备注 */
    private String note;

    /** 操作人 */
    private Long operatorId;

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getBidResult() {
        return bidResult;
    }

    public void setBidResult(String bidResult) {
        this.bidResult = bidResult;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

}