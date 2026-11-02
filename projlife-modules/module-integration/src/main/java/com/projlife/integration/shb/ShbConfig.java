package com.projlife.integration.shb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 售后宝接入配置（SPRINT-INT1 P1，报告②）：
 * 凭据经环境变量注入；网关按开通端选择（C2 裁决：钉钉端 oapi.shb.ltd 默认，企微端 oapi.linker.ltd 可切）。
 * 硬前置【实】：OpenAPI 需标准版+；Webhook 推送需企业版+；IP 白名单必须公网固定 IP。
 */
@Component
public class ShbConfig {

    @Value("${projlife.integ.shb.enabled:false}")
    private boolean enabled;

    @Value("${projlife.integ.shb.app-key:}")
    private String appKey;

    @Value("${projlife.integ.shb.app-secret:}")
    private String appSecret;

    @Value("${projlife.integ.shb.base-url:https://oapi.shb.ltd}")
    private String baseUrl;

    /** Webhook 回调可选共享密钥（推送报文签名未公开，A-40：先以共享密钥+原始落库兜底） */
    @Value("${projlife.integ.shb.webhook-shared-key:}")
    private String webhookSharedKey;

    /** 事件列表分页（官方仅允许 5/10/20/50） */
    @Value("${projlife.integ.shb.page-size:50}")
    private int pageSize;

    /** 增量对账默认窗口（小时） */
    @Value("${projlife.integ.shb.sync-window-hours:24}")
    private int syncWindowHours;

    public boolean isEnabled() { return enabled; }

    public boolean configured() {
        return enabled && notEmpty(appKey) && notEmpty(appSecret);
    }

    public String getAppKey() { return appKey; }

    public String getAppSecret() { return appSecret; }

    public String getBaseUrl() { return baseUrl; }

    public String getWebhookSharedKey() { return webhookSharedKey; }

    public int getPageSize() { return pageSize; }

    public int getSyncWindowHours() { return syncWindowHours; }

    static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    // ---- 测试注入口 ----
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setAppKey(String appKey) { this.appKey = appKey; }

    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public void setWebhookSharedKey(String webhookSharedKey) { this.webhookSharedKey = webhookSharedKey; }
}
