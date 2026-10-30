package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_source（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_source")
public class BidSource extends VersionedEntity {

    /** SRC-{A|B|C|D}-{3位} UK */
    private String sourceCode;

    /** A_OWNER/B_OEM/C_PORTAL/D_MANUAL */
    private String category;

    /** 入口 URL */
    private String entryUrl;

    /** 抓取模板 */
    private String fetchTemplate;

    /** 频次 DAILY_2 等 */
    private String frequency;

    /** robots 检查留痕 */
    private LocalDateTime robotsCheckedAt;

    /** PENDING_REVIEW/ACTIVE/DISABLED */
    private String status;

    /** 健康度 */
    private BigDecimal healthScore;

    /** 上次水位 */
    private LocalDateTime lastWatermark;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEntryUrl() {
        return entryUrl;
    }

    public void setEntryUrl(String entryUrl) {
        this.entryUrl = entryUrl;
    }

    public String getFetchTemplate() {
        return fetchTemplate;
    }

    public void setFetchTemplate(String fetchTemplate) {
        this.fetchTemplate = fetchTemplate;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public LocalDateTime getRobotsCheckedAt() {
        return robotsCheckedAt;
    }

    public void setRobotsCheckedAt(LocalDateTime robotsCheckedAt) {
        this.robotsCheckedAt = robotsCheckedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(BigDecimal healthScore) {
        this.healthScore = healthScore;
    }

    public LocalDateTime getLastWatermark() {
        return lastWatermark;
    }

    public void setLastWatermark(LocalDateTime lastWatermark) {
        this.lastWatermark = lastWatermark;
    }

}