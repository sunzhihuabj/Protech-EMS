package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_match（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_match")
public class BidMatch extends BaseEntity {

    /** 公告 id */
    private Long noticeId;

    /** 规则版本 */
    private String ruleVersion;

    /** 总分 100 制 */
    private BigDecimal score;

    /** HIGH/MEDIUM/ARCHIVED */
    private String level;

    /** 各维度得分明细 JSON 文本（A-30） */
    private String breakdown;

    /** 回训校准 */
    private Integer isCorrect;

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(String breakdown) {
        this.breakdown = breakdown;
    }

    public Integer getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Integer isCorrect) {
        this.isCorrect = isCorrect;
    }

}