package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.AuditedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_raw（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_raw")
public class BidRaw extends AuditedEntity {

    /** 数据源 id */
    private Long sourceId;

    /** 批次号 */
    private String batchNo;

    /** 原文 URL */
    private String url;

    /** HTTP 状态 */
    private Integer httpStatus;

    /** SHA-256 UK */
    private String contentHash;

    /** 原文暂存路径 */
    private String rawPath;

    /** 抓取时间 */
    private LocalDateTime fetchedAt;

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

}