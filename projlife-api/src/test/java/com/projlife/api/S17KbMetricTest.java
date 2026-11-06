package com.projlife.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S17-A6 E2E（V4-05 M12 T-KB-01/02）：指标口径定义 → 快照灯色三态
 * （GREEN 达标 / YELLOW ≥0.9 / RED <0.8）→ 期间修正（UK 幂等）→ 趋势下钻。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S17KbMetricTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;

    private static final class Resp {
        final Map<String, Object> body;

        Resp(Map<String, Object> body) {
            this.body = body;
        }

        int code() {
            Object code = body.get("code");
            if (code == null) {
                throw new AssertionError("非标准响应: " + body);
            }
            return ((Number) code).intValue();
        }

        @SuppressWarnings("unchecked")
        <T> T data() {
            return (T) body.get("data");
        }

        Map<String, Object> body() {
            return body;
        }
    }

    private Resp post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody());
    }

    private Resp get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
    }

    private void loginAs(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "12345678", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("指标定义：编码唯一 + 重复冲突")
    void createDef() {
        loginAs("admin@demo");
        Resp created = post("/api/v1/kb/metrics",
                Map.of("metricCode", "S17.TEST.DELIVERY", "boardId", 2,
                        "metricName", "S17 demo 订单准时交付率", "target", 100));
        assertEquals(0, created.code(), created.body().toString());
        assertEquals("S17.TEST.DELIVERY", ((Map<?, ?>) created.data()).get("metricCode"));

        Resp again = post("/api/v1/kb/metrics",
                Map.of("metricCode", "S17.TEST.DELIVERY", "boardId", 2, "metricName", "重复"));
        assertEquals(4001, again.code(), "重复编码应 409");
    }

    @Test
    @Order(2)
    @DisplayName("快照灯色：GREEN(100) / YELLOW(95) / RED(70)")
    void snapshotLight() {
        loginAs("admin@demo");
        Resp green = post("/api/v1/kb/metrics/S17.TEST.DELIVERY/snapshots",
                Map.of("period", "2026-08", "value", "100"));
        assertEquals(0, green.code(), green.body().toString());
        assertEquals("GREEN", ((Map<?, ?>) green.data()).get("status"));

        Resp yellow = post("/api/v1/kb/metrics/S17.TEST.DELIVERY/snapshots",
                Map.of("period", "2026-09", "value", "95"));
        assertEquals("YELLOW", ((Map<?, ?>) yellow.data()).get("status"), "95/100 ≥0.9 应黄");

        Resp red = post("/api/v1/kb/metrics/S17.TEST.DELIVERY/snapshots",
                Map.of("period", "2026-10", "value", "70"));
        assertEquals("RED", ((Map<?, ?>) red.data()).get("status"), "70/100 <0.8 应红");

        // 同期修正幂等
        Resp fixed = post("/api/v1/kb/metrics/S17.TEST.DELIVERY/snapshots",
                Map.of("period", "2026-10", "value", "100"));
        assertEquals(0, fixed.code());
        assertEquals("GREEN", ((Map<?, ?>) fixed.data()).get("status"));
    }

    @Test
    @Order(3)
    @DisplayName("趋势下钻：三期快照 + 绿灯率")
    void trend() {
        loginAs("admin@demo");
        Resp trend = get("/api/v1/kb/metrics/S17.TEST.DELIVERY/trend?periods=12");
        assertEquals(0, trend.code(), trend.body().toString());
        Map<String, Object> data = trend.data();
        assertEquals(3, ((java.util.List<?>) data.get("periods")).size(), "应含 3 期快照");
        // 修正后 08=GREEN 09=YELLOW 10=GREEN → 绿灯率 66.67
        assertTrue(String.valueOf(data.get("greenRate")).startsWith("66.6"),
                "绿灯率应 66.67，实际 " + data.get("greenRate"));
    }
}
