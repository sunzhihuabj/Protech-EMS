package com.projlife.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-AGT agt_session（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("agt_session")
public class AgtSession extends BaseEntity {

    /** 会话标识 UK */
    private String sessionRef;

    /** 注册 id */
    private Long registrationId;

    /** EXPERT/WORKER/ASSISTANT */
    private String identityType;

    /** WORKER 强制代人 */
    private Long onBehalfOf;

    /** 授予角色 */
    private String grantedRoleCode;

    /** 数据范围 */
    private String dataScope;

    /** ACTIVE/CLOSED/REVOKED */
    private String status;

    /** 过期时间 2h */
    private LocalDateTime expiresAt;

    public String getSessionRef() {
        return sessionRef;
    }

    public void setSessionRef(String sessionRef) {
        this.sessionRef = sessionRef;
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public Long getOnBehalfOf() {
        return onBehalfOf;
    }

    public void setOnBehalfOf(Long onBehalfOf) {
        this.onBehalfOf = onBehalfOf;
    }

    public String getGrantedRoleCode() {
        return grantedRoleCode;
    }

    public void setGrantedRoleCode(String grantedRoleCode) {
        this.grantedRoleCode = grantedRoleCode;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

}