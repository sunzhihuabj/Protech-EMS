package com.projlife.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全配置项（16 册 §7.3：JWT access 2h + refresh 7d，Redis 黑名单；03 册：5 次锁定 15 分钟）。
 */
@Component
@ConfigurationProperties(prefix = "projlife.security")
public class SecurityProperties {

    /** HS256 密钥，生产从环境变量注入，禁止入库入码 */
    private String jwtSecret = "change-me-in-production-projlife-jwt-secret-0001";

    /** 内部任务端点共享密钥（X-Internal-Key），生产经 PROJLIFE_INTERNAL_KEY 注入（SPRINT-UID-R17） */
    private String internalKey = "dev-only-internal-key";

    /** access token 有效期（秒），默认 2 小时 */
    private long accessTtlSeconds = 7200;

    /** refresh token 有效期（秒），默认 7 天 */
    private long refreshTtlSeconds = 604800;

    /** 连续失败锁定阈值 */
    private int maxLoginFail = 5;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getInternalKey() {
        return internalKey;
    }

    public void setInternalKey(String internalKey) {
        this.internalKey = internalKey;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(long accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public void setRefreshTtlSeconds(long refreshTtlSeconds) {
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public int getMaxLoginFail() {
        return maxLoginFail;
    }

    public void setMaxLoginFail(int maxLoginFail) {
        this.maxLoginFail = maxLoginFail;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }
}
