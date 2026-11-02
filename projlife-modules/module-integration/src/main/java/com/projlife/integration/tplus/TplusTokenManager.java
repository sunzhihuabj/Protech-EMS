package com.projlife.integration.tplus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T+ accessToken 管理器（SPRINT-INT1，报告③§三 通道 B）：
 *  REALNAME：POST /tplus/api/v2/collaborationapp/GetRealNameTPlusToken?IsFree=1 {"_args":{userName,password,accNum}}
 *  ANONYMOUS：POST /tplus/api/v2/collaborationapp/GetAnonymousTPlusToken?IsFree=1 {"_args":{orgId}}
 * 官方建议一天获取一次 → 缓存 20h。响应字段作容错解析（access_token/accessToken/data.access_token，A-43）。
 */
@Component
public class TplusTokenManager {

    private static final Logger log = LoggerFactory.getLogger(TplusTokenManager.class);

    /** 一天一次纪律：20h 缓存 */
    static final long TTL_MS = 20 * 60 * 60 * 1000L;

    private final TplusConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile long expireAt;

    public TplusTokenManager(TplusConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String getToken() {
        if (cachedToken != null && expireAt > System.currentTimeMillis()) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && expireAt > System.currentTimeMillis()) {
                return cachedToken;
            }
            return fetchToken();
        }
    }

    public synchronized void invalidate() {
        cachedToken = null;
        expireAt = 0;
    }

    private String fetchToken() {
        boolean anonymous = "ANONYMOUS".equalsIgnoreCase(config.getTokenMode());
        String path = anonymous
                ? "/tplus/api/v2/collaborationapp/GetAnonymousTPlusToken?IsFree=1"
                : "/tplus/api/v2/collaborationapp/GetRealNameTPlusToken?IsFree=1";
        Map<String, Object> args = new LinkedHashMap<>();
        if (anonymous) {
            args.put("orgId", config.getOrgId());
        } else {
            args.put("userName", config.getUserName());
            // A-42：密码加密方式未在公开资料中披露，HTTPS 链路明文承载；实施侧若有加密要求再补
            args.put("password", config.getPassword());
            args.put("accNum", config.getAccNum());
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("_args", args);
            JsonNode resp = restTemplate.postForObject(config.getBaseUrl() + path,
                    new HttpEntity<>(body, headers), JsonNode.class);
            String token = extractToken(resp);
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("T+ token 响应未含 access_token："
                        + (resp == null ? "空响应" : resp.toString()));
            }
            cachedToken = token;
            expireAt = System.currentTimeMillis() + TTL_MS;
            log.info("T+ accessToken 获取成功（{} 模式），缓存 20h", anonymous ? "ANONYMOUS" : "REALNAME");
            return token;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            cachedToken = null;
            throw new IllegalStateException("获取 T+ accessToken 失败：" + ex.getMessage(), ex);
        }
    }

    /** 容错解析 token 字段（A-43：响应包裹结构未完全公开） */
    private String extractToken(JsonNode resp) {
        if (resp == null) {
            return null;
        }
        String[] candidates = {resp.path("access_token").asText(""),
                resp.path("accessToken").asText(""),
                resp.path("data").path("access_token").asText(""),
                resp.path("data").path("accessToken").asText("")};
        for (String c : candidates) {
            if (!c.isEmpty()) {
                return c;
            }
        }
        return null;
    }

    synchronized String cachedValue() { return cachedToken; }
}
