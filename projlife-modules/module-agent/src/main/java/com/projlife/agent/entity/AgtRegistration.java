package com.projlife.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-AGT agt_registration（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("agt_registration")
public class AgtRegistration extends VersionedEntity {

    /** 客户端标识 UK */
    private String clientId;

    /** 平台名 */
    private String platform;

    /** 公钥（HMAC 验签基础） */
    private String publicKey;

    /** IP 白名单 */
    private String ipWhitelist;

    /** 允许身份 JSON 文本（A-30） */
    private String allowedIdentities;

    /** 日配额 */
    private Integer quotaDaily;

    /** 月 token 配额 */
    private Long quotaMonthlyToken;

    /** AES 密文 */
    private String secretCipher;

    /** ACTIVE/REVOKED */
    private String status;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(String ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public String getAllowedIdentities() {
        return allowedIdentities;
    }

    public void setAllowedIdentities(String allowedIdentities) {
        this.allowedIdentities = allowedIdentities;
    }

    public Integer getQuotaDaily() {
        return quotaDaily;
    }

    public void setQuotaDaily(Integer quotaDaily) {
        this.quotaDaily = quotaDaily;
    }

    public Long getQuotaMonthlyToken() {
        return quotaMonthlyToken;
    }

    public void setQuotaMonthlyToken(Long quotaMonthlyToken) {
        this.quotaMonthlyToken = quotaMonthlyToken;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher) {
        this.secretCipher = secretCipher;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}