package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_customer_alias（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_customer_alias")
public class MdCustomerAlias extends BaseEntity {

    /** 主体 id */
    private Long customerId;

    /** 别名（历史写法/简称/错别字） */
    private String aliasName;

    /** 别名类型 */
    private String aliasType;

    /** 来源 */
    private String source;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getAliasType() {
        return aliasType;
    }

    public void setAliasType(String aliasType) {
        this.aliasType = aliasType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

}