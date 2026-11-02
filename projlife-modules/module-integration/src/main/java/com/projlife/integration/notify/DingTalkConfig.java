package com.projlife.integration.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 钉钉接入配置（SPRINT-INT1，DEPLOYMENT 凭据清单第 5 项）：
 * 凭据全部经环境变量注入（禁入库入码）；未配置时 enabled=false，调用侧自动降级站内。
 *
 * 通道分级（报告①§二.2/§4.5）：
 *  L1 工作通知（需 AppKey/AppSecret/agentId，应用→个人，官方推荐任务类用待办）
 *  L2 群机器人 webhook（仅需机器人 token + 加签 secret，最小凭据即可端到端）
 */
@Component
public class DingTalkConfig {

    @Value("${projlife.integ.dingtalk.enabled:false}")
    private boolean enabled;

    @Value("${projlife.integ.dingtalk.app-key:}")
    private String appKey;

    @Value("${projlife.integ.dingtalk.app-secret:}")
    private String appSecret;

    /** 工作通知必填（asyncsend_v2 agent_id） */
    @Value("${projlife.integ.dingtalk.agent-id:}")
    private String agentId;

    /** L2 群机器人 webhook（备选/兜底通道） */
    @Value("${projlife.integ.dingtalk.webhook:}")
    private String webhook;

    /** 机器人加签密钥（安全设置选"加签"时必填） */
    @Value("${projlife.integ.dingtalk.webhook-secret:}")
    private String webhookSecret;

    /** 新版 REST 域（Header 鉴权） */
    @Value("${projlife.integ.dingtalk.api-base:https://api.dingtalk.com}")
    private String apiBase;

    /** 旧版 topapi 域（Query 鉴权） */
    @Value("${projlife.integ.dingtalk.oapi-base:https://oapi.dingtalk.com}")
    private String oapiBase;

    /** sys_user.id→钉钉 userid 静态映射（测试期口径 A-39："30:dtu001,31:dtu002"；生产批次上通讯录同步） */
    @Value("${projlife.integ.dingtalk.user-mapping:}")
    private String userMapping;

    public boolean isEnabled() { return enabled; }

    /** L1 工作通知凭据是否齐备 */
    public boolean workNotifyConfigured() {
        return enabled && notEmpty(appKey) && notEmpty(appSecret) && notEmpty(agentId);
    }

    /** L2 机器人通道是否可用 */
    public boolean robotConfigured() {
        return enabled && notEmpty(webhook);
    }

    public boolean tokenConfigured() {
        return enabled && notEmpty(appKey) && notEmpty(appSecret);
    }

    public String getAppKey() { return appKey; }

    public String getAppSecret() { return appSecret; }

    public String getAgentId() { return agentId; }

    public String getWebhook() { return webhook; }

    public String getWebhookSecret() { return webhookSecret; }

    public String getApiBase() { return apiBase; }

    public String getOapiBase() { return oapiBase; }

    /** 解析静态映射 "30:dtu001,31:dtu002" → Map */
    public java.util.Map<Long, String> parseUserMapping() {
        java.util.Map<Long, String> map = new java.util.LinkedHashMap<>();
        if (userMapping == null || userMapping.isEmpty()) {
            return map;
        }
        for (String pair : userMapping.split(",")) {
            String[] kv = pair.trim().split(":");
            if (kv.length == 2 && kv[0].matches("\\d+")) {
                map.put(Long.valueOf(kv[0].trim()), kv[1].trim());
            }
        }
        return map;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    // ---- 测试注入口（生产由 Spring 注入） ----
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setAppKey(String appKey) { this.appKey = appKey; }

    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public void setAgentId(String agentId) { this.agentId = agentId; }

    public void setWebhook(String webhook) { this.webhook = webhook; }

    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public void setApiBase(String apiBase) { this.apiBase = apiBase; }

    public void setOapiBase(String oapiBase) { this.oapiBase = oapiBase; }

    public void setUserMapping(String userMapping) { this.userMapping = userMapping; }
}
