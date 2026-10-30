package com.projlife.bid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-BID bid_rule（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bid_rule")
public class BidRule extends BaseEntity {

    /** 版本 UK */
    private String ruleVersion;

    /** 正向关键词 */
    private String keywordPositive;

    /** 负向关键词 */
    private String keywordNegative;

    /** 区域白名单 */
    private String regionList;

    /** 金额下限 */
    private BigDecimal amountMin;

    /** 金额上限 */
    private BigDecimal amountMax;

    /** 维度权重 25/25/15/15/10/10 */
    private String weights;

    /** 硬性资质 */
    private String hardQualification;

    /** ACTIVE/ARCHIVED */
    private String status;

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getKeywordPositive() {
        return keywordPositive;
    }

    public void setKeywordPositive(String keywordPositive) {
        this.keywordPositive = keywordPositive;
    }

    public String getKeywordNegative() {
        return keywordNegative;
    }

    public void setKeywordNegative(String keywordNegative) {
        this.keywordNegative = keywordNegative;
    }

    public String getRegionList() {
        return regionList;
    }

    public void setRegionList(String regionList) {
        this.regionList = regionList;
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

    public String getWeights() {
        return weights;
    }

    public void setWeights(String weights) {
        this.weights = weights;
    }

    public String getHardQualification() {
        return hardQualification;
    }

    public void setHardQualification(String hardQualification) {
        this.hardQualification = hardQualification;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}