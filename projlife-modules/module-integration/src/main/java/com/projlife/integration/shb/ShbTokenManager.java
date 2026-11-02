package com.projlife.integration.shb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 售后宝 accessToken 中心（SPRINT-INT1，报告②§3.1/§3.3【实】）：
 * POST /service/auth/get_access_token {appKey, timestamp, verifyCode=md5(appSecret+"_"+timestamp) 32位小写}
 * → {errorCode:"0", data:{access_token, expire_time}}。
 *
 * 纪律（四条硬限制中最严一条）：
 *  - Token 每应用每 24h 仅 50 次获取（从第一次调用起算）→ 当日计数 + 45 次告警 + 到顶拒绝
 *  - Token 有效期 2h → 缓存 + 过期前 10 分钟刷新 + synchronized 双检
 */
@Component
public class ShbTokenManager {

    private static final Logger log = LoggerFactory.getLogger(ShbTokenManager.class);

    public static final int DAILY_LIMIT = 50;
    public static final int DAILY_WARN_AT = 45;

    static final long REFRESH_AHEAD_MS = 10 * 60 * 1000L;
    static final long DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L;

    private final ShbConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile long expireAt;
    private final AtomicReference<LocalDate> countDay = new AtomicReference<>(LocalDate.now());
    private final AtomicInteger dailyFetchCount = new AtomicInteger();

    public ShbTokenManager(ShbConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** verifyCode = md5(appSecret + "_" + timestamp) 32 位小写（官方口径） */
    static String verifyCode(String appSecret, String timestamp) {
        return md5Hex(appSecret + "_" + timestamp);
    }

    static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 不可用", ex);
        }
    }

    /** 获取有效 token；失败/超限抛 IllegalStateException 由调用方降级 */
    public String getToken() {
        rollDay();
        if (cachedToken != null && expireAt - REFRESH_AHEAD_MS > System.currentTimeMillis()) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && expireAt - REFRESH_AHEAD_MS > System.currentTimeMillis()) {
                return cachedToken;
            }
            if (dailyFetchCount.get() >= DAILY_LIMIT) {
                throw new IllegalStateException("售后宝 token 每日获取次数已达上限（50 次/24h），明日自动恢复");
            }
            return fetchToken();
        }
    }

    public synchronized void invalidate() {
        cachedToken = null;
        expireAt = 0;
    }

    private String fetchToken() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appKey", config.getAppKey());
            body.put("timestamp", timestamp);
            body.put("verifyCode", verifyCode(config.getAppSecret(), timestamp));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode resp = restTemplate.postForObject(
                    config.getBaseUrl() + "/service/auth/get_access_token",
                    new HttpEntity<>(body, headers), JsonNode.class);
            if (resp == null || !"0".equals(resp.path("errorCode").asText("-1"))) {
                throw new IllegalStateException("售后宝 token 接口返回异常："
                        + (resp == null ? "空响应" : resp.path("message").asText(resp.toString())));
            }
            String token = resp.path("data").path("access_token").asText("");
            if (token.isEmpty()) {
                throw new IllegalStateException("售后宝 token 响应缺少 data.access_token");
            }
            int used = dailyFetchCount.incrementAndGet();
            if (used >= DAILY_WARN_AT) {
                log.warn("售后宝 token 当日获取次数已达 {} 次（上限 {}）——检查是否存在缓存失效风暴", used, DAILY_LIMIT);
            }
            cachedToken = token;
            // expire_time 官方样例为毫秒时间戳；容错解析，异常时按 2h 默认
            long expireAtCandidate = resp.path("data").path("expire_time").asLong(0);
            long now = System.currentTimeMillis();
            expireAt = (expireAtCandidate > now) ? expireAtCandidate : now + DEFAULT_TTL_MS;
            log.info("售后宝 accessToken 刷新成功，当日第 {}/50 次", used);
            return token;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            cachedToken = null;
            throw new IllegalStateException("获取售后宝 accessToken 失败：" + ex.getMessage(), ex);
        }
    }

    private void rollDay() {
        LocalDate today = LocalDate.now();
        LocalDate day = countDay.get();
        if (!today.equals(day) && countDay.compareAndSet(day, today)) {
            dailyFetchCount.set(0);
        }
    }

    // ---- 测试/运维辅助 ----
    public int todayFetchCount() {
        rollDay();
        return dailyFetchCount.get();
    }

    void forceDailyCount(int n) {
        dailyFetchCount.set(n);
    }

    synchronized String cachedValue() { return cachedToken; }
}
