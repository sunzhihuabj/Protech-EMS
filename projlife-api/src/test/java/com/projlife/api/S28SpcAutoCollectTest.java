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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPRINT-OPS1 E2E：F10 SPC 自动采集（检验创建→缺陷率点位→列表端 X 单值图控制限 mean±3σ）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S28SpcAutoCollectTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static final String CHAR = "IQC:DR:50000002";

    private static final class Resp {
        final Map<String, Object> body;

        Resp(Map<String, Object> body) { this.body = body; }

        int code() {
            Object code = body.get("code");
            if (code == null) {
                throw new AssertionError("非标准响应: " + body);
            }
            return ((Number) code).intValue();
        }

        @SuppressWarnings("unchecked")
        <T> T data() { return (T) body.get("data"); }

        Map<String, Object> body() { return body; }
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
    @DisplayName("F10：3 笔检验自动采集 3 个 SPC 缺陷率点位")
    void autoCollect() {
        loginAs("Administrator");
        int[][] samples = {{100, 5}, {100, 2}, {100, 8}};
        for (int[] s : samples) {
            Resp created = post("/api/v1/qms/inspections", Map.of(
                    "inspectionType", "IQC", "materialId", 50000002L,
                    "sampleSize", s[0], "defectCount", s[1], "sourceType", "MANUAL",
                    "sourceId", 1L, "result", "PASS"));
            assertEquals(0, created.code(), created.body().toString());
        }
        Resp list = get("/api/v1/qms/spc?characteristic=" + CHAR);
        assertEquals(0, list.code(), list.body().toString());
        List<?> rows = list.data();
        assertEquals(3, rows.size(), "3 笔检验应自动采集 3 个点位");
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertEquals(CHAR, first.get("characteristic"));
        // 5/100 → 0.05
        assertEquals(0.05, ((Number) first.get("valueX")).doubleValue(), 0.0001);
    }

    @Test
    @Order(2)
    @DisplayName("F10：≥3 点计算控制限（ucl≥cl≥lcl，出控标记）")
    void controlLimits() {
        loginAs("Administrator");
        Resp list = get("/api/v1/qms/spc?characteristic=" + CHAR);
        List<?> rows = list.data();
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        double cl = ((Number) first.get("cl")).doubleValue();
        double ucl = ((Number) first.get("ucl")).doubleValue();
        double lcl = ((Number) first.get("lcl")).doubleValue();
        // mean=(0.05+0.02+0.08)/3≈0.05，σ≈0.0245，ucl≈0.1235，lcl=max(0, -0.0235)=0
        assertEquals(0.05, cl, 0.001);
        assertTrue(ucl > cl, "ucl 应大于 cl");
        assertTrue(lcl >= 0, "缺陷率下限应截 0");
        // 0.08 < ucl≈0.1235 → 未出控
        assertEquals(0, ((Number) first.get("isOutOfControl")).intValue());
    }
}
