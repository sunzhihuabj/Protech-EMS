package com.projlife.integration.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉 accessToken 中心（SPRINT-INT1，报告①§四.1【官方】）：
 * POST {apiBase}/v1.0/oauth2/accessToken → {accessToken, expireIn=7200}。
 * 纪律：有效期内重复获取返回相同值并自动续期，但频繁调用会被限流 → 必须缓存；
 * 过期前 10 分钟刷新；synchronized 双检防并发重复获取。
 *
 * Token 通用口径（SPRINT-INT1 §1.3 C1 裁决）：新版 accessToken 同时用于新版 REST（Header
 * x-acs-dingtalk-access-token）与旧版 topapi（Query access_token）；若旧通道返回认证类 errcode
 * 由调用方 invalidate 后重试一次（回退旧端点逻辑在 DingTalkClient 内）。
 */
@Component
public class DingTalkTokenManager {

    private static final Logger log = LoggerFactory.getLogger(DingTalkTokenManager.class);

    /** 过期前提前量（官方建议 10 分钟） */
    static final long REFRESH_AHEAD_MS = 10 * 60 * 1000L;

    private final DingTalkConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile long expireAt;

    public DingTalkTokenManager(DingTalkConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 获取有效 token（缓存命中直接返回）；失败抛 IllegalStateException 由调用方降级 */
    public String getToken() {
        long now = System.currentTimeMillis();
        String token = cachedToken;
        if (token != null && expireAt - REFRESH_AHEAD_MS > now) {
            return token;
        }
        synchronized (this) {
            // 双检：等待期间其他线程已刷新
            if (cachedToken != null && expireAt - REFRESH_AHEAD_MS > System.currentTimeMillis()) {
                return cachedToken;
            }
            return fetchToken();
        }
    }

    /** 认证类失败后清除缓存，强制下次重取 */
    public synchronized void invalidate() {
        cachedToken = null;
        expireAt = 0;
    }

    private String fetchToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("appKey", config.getAppKey());
            body.put("appSecret", config.getAppSecret());
            JsonNode resp = restTemplate.postForObject(
                    config.getApiBase() + "/v1.0/oauth2/accessToken",
                    new HttpEntity<>(body, headers), JsonNode.class);
            if (resp == null || resp.path("accessToken").asText("").isEmpty()) {
                throw new IllegalStateException("钉钉 accessToken 响应为空或缺少 accessToken 字段");
            }
            cachedToken = resp.path("accessToken").asText();
            long expireIn = resp.path("expireIn").asLong(7200);
            expireAt = System.currentTimeMillis() + expireIn * 1000;
            log.info("钉钉 accessToken 刷新成功，有效期 {}s", expireIn);
            return cachedToken;
        } catch (Exception ex) {
            cachedToken = null;
            throw new IllegalStateException("获取钉钉 accessToken 失败：" + ex.getMessage(), ex);
        }
    }

    // ---- 测试辅助 ----
    synchronized String cachedValue() { return cachedToken; }
}
