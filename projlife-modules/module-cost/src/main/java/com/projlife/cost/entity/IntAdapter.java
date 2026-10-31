package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST int_adapter（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("int_adapter")
public class IntAdapter extends VersionedEntity {

    /** TPLUS/ONLYOFFICE/DINGTALK/WECHAT UK */
    private String adapterCode;

    /** 适配器名称 */
    private String adapterName;

    /** 基础地址 */
    private String baseUrl;

    /** NONE/BASIC/TOKEN/OAUTH2 */
    private String authType;

    /** 凭据配置 */
    private String authConfig;

    /** 启用 */
    private Integer enabled;

    /** UP/DOWN/UNKNOWN */
    private String healthStatus;

    /** 最近检查 */
    private LocalDateTime lastCheckAt;

    public String getAdapterCode() {
        return adapterCode;
    }

    public void setAdapterCode(String adapterCode) {
        this.adapterCode = adapterCode;
    }

    public String getAdapterName() {
        return adapterName;
    }

    public void setAdapterName(String adapterName) {
        this.adapterName = adapterName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(String authConfig) {
        this.authConfig = authConfig;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public LocalDateTime getLastCheckAt() {
        return lastCheckAt;
    }

    public void setLastCheckAt(LocalDateTime lastCheckAt) {
        this.lastCheckAt = lastCheckAt;
    }

}