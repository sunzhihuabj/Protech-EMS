package com.projlife.integration.tplus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 捷信通T+ 接入配置（SPRINT-INT1 P2，报告③）：
 * 通道 B 直连实例（C4 裁决：实例 v2 网关实测在线）；凭据三件套 appKey/appSecret/私钥 由畅捷通发放。
 *
 * 13.0 红线（C5）：禁用 type=Asyn（18.0+）、报表查询辅助（16.0+）等高版本特性。
 * 私钥格式：PKCS#8 的 Base64（PEM 文件去掉头尾后内容）经环境变量注入。
 */
@Component
public class TplusConfig {

    @Value("${projlife.integ.tplus.enabled:false}")
    private boolean enabled;

    /** T+ 实例域名（专属云直连） */
    @Value("${projlife.integ.tplus.base-url:https://hbtgl.a4.cjtmsp.com}")
    private String baseUrl;

    @Value("${projlife.integ.tplus.app-key:}")
    private String appKey;

    @Value("${projlife.integ.tplus.app-secret:}")
    private String appSecret;

    /** 云企业 ID（测试与正式账套必须不同） */
    @Value("${projlife.integ.tplus.org-id:}")
    private String orgId;

    /** REALNAME（用户名密码+账套号）/ ANONYMOUS（orgId） */
    @Value("${projlife.integ.tplus.token-mode:REALNAME}")
    private String tokenMode;

    @Value("${projlife.integ.tplus.user-name:}")
    private String userName;

    @Value("${projlife.integ.tplus.password:}")
    private String password;

    @Value("${projlife.integ.tplus.acc-num:}")
    private String accNum;

    /** ISV 私钥（PKCS#8 Base64 或 PEM 全文），用于 authInfo JWT 签名 */
    @Value("${projlife.integ.tplus.private-key:}")
    private String privateKey;

    public boolean isEnabled() { return enabled; }

    /** 直连模式凭据是否齐备（私钥可选——缺失时 authInfo 降级为空并由签名器显式报错） */
    public boolean configured() {
        boolean base = enabled && notEmpty(appKey) && notEmpty(appSecret) && notEmpty(orgId);
        if ("ANONYMOUS".equalsIgnoreCase(tokenMode)) {
            return base;
        }
        return base && notEmpty(userName) && notEmpty(accNum);
    }

    public boolean jwtConfigured() {
        return notEmpty(privateKey);
    }

    public String getBaseUrl() { return baseUrl; }

    public String getAppKey() { return appKey; }

    public String getAppSecret() { return appSecret; }

    public String getOrgId() { return orgId; }

    public String getTokenMode() { return tokenMode; }

    public String getUserName() { return userName; }

    public String getPassword() { return password; }

    public String getAccNum() { return accNum; }

    public String getPrivateKey() { return privateKey; }

    static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    // ---- 测试注入口 ----
    public void setEnabled(boolean v) { this.enabled = v; }

    public void setBaseUrl(String v) { this.baseUrl = v; }

    public void setAppKey(String v) { this.appKey = v; }

    public void setAppSecret(String v) { this.appSecret = v; }

    public void setOrgId(String v) { this.orgId = v; }

    public void setTokenMode(String v) { this.tokenMode = v; }

    public void setUserName(String v) { this.userName = v; }

    public void setPassword(String v) { this.password = v; }

    public void setAccNum(String v) { this.accNum = v; }

    public void setPrivateKey(String v) { this.privateKey = v; }
}
