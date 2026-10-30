package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_subscribe（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_subscribe")
public class BidSubscribe extends VersionedEntity {

    /** 用户 UK */
    private Long userId;

    /** 区域 */
    private String regions;

    /** 业主 */
    private String owners;

    /** 关键词 */
    private String keywords;

    /** 金额下限 */
    private BigDecimal amountMin;

    /** 金额上限 */
    private BigDecimal amountMax;

    /** 源类别 */
    private String categories;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRegions() {
        return regions;
    }

    public void setRegions(String regions) {
        this.regions = regions;
    }

    public String getOwners() {
        return owners;
    }

    public void setOwners(String owners) {
        this.owners = owners;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public BigDecimal getAmountMin() {
        return amountMin;
    }

    public void setAmountMin(BigDecimal amountMin) {
        this.amountMin = amountMin;
    }

    public BigDecimal getAmountMax() {
        return amountMax;
    }

    public void setAmountMax(BigDecimal amountMax) {
        this.amountMax = amountMax;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

}