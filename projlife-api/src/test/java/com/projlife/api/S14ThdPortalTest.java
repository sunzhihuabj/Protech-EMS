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

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S14-A3 E2E（V4-05 M11 T-THD）：送货单 DN 打印留痕/人员授权黑名单拦截/四维评价定级/
 * 黑名单自动冻结授权/解除恢复。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S14ThdPortalTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long noteId;
    private static Long blacklistId;

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

    private void loginAs(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "ProjLife@2026", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    private Long dataId(Resp resp) {
        return Long.valueOf(String.valueOf(((Map<?, ?>) resp.data()).get("id")));
    }

    @Test
    @Order(1)
    @DisplayName("送货单：DN 编号 + 打印留痕")
    void deliveryNote() {
        loginAs("admin@demo");
        Resp created = post("/api/v1/thd/delivery-notes",
                Map.of("poId", 1, "deliveryDate", LocalDate.now().toString(),
                        "itemsJson", "[{\"material\":\"M-1\",\"qty\":5}]"));
        assertEquals(0, created.code(), created.body().toString());
        noteId = dataId(created);
        assertTrue(String.valueOf(((Map<?, ?>) created.data()).get("noteNo")).startsWith("DN-"), "DN 编号");

        Resp printed = post("/api/v1/thd/delivery-notes/" + noteId + "/print", null);
        assertEquals(0, printed.code());
        assertTrue(((Map<?, ?>) printed.data()).get("printedAt") != null, "打印应留痕");
    }

    @Test
    @Order(2)
    @DisplayName("人员授权：GRANTED；四维评价 C 级（均分 66.67）")
    void grantAndEvaluate() {
        loginAs("admin@demo");
        Resp granted = post("/api/v1/thd/worker-projects",
                Map.of("workerId", 990, "mdProjectId", 1,
                        "authStart", LocalDate.now().toString(),
                        "authEnd", LocalDate.now().plusDays(30).toString()));
        assertEquals(0, granted.code(), granted.body().toString());
        Long grantId = dataId(granted);
        assertEquals("GRANTED", ((Map<?, ?>) granted.data()).get("status"));

        Resp revoked = post("/api/v1/thd/worker-projects/" + grantId + "/revoke",
                Map.of("reason", "S14 demo 调离"));
        assertEquals(0, revoked.code());
        assertEquals("REVOKED", ((Map<?, ?>) revoked.data()).get("status"));

        Resp evaluated = post("/api/v1/thd/evaluations",
                Map.of("targetType", "CREW", "targetId", 1, "period", "2026-10",
                        "qualityScore", "70", "deliveryScore", "65",
                        "safetyScore", "60", "serviceScore", "72"));
        assertEquals(0, evaluated.code(), evaluated.body().toString());
        assertEquals("C", ((Map<?, ?>) evaluated.data()).get("grade"), "均分 66.75 应 C 级");
    }

    @Test
    @Order(3)
    @DisplayName("黑名单：无原因拦截（THD-BL-REASON）→ 生效自动冻结授权 → 再授权拦截（THD-BLACKLISTED）→ 解除恢复")
    void blacklistLoop() {
        loginAs("admin@demo");
        Resp noReason = post("/api/v1/thd/blacklist",
                Map.of("targetType", "WORKER", "targetId", 990));
        assertEquals(5001, noReason.code());
        assertEquals("THD-BL-REASON", ((Map<?, ?>) noReason.body().get("data")).get("blockCode"));

        // 先给一份有效授权
        post("/api/v1/thd/worker-projects",
                Map.of("workerId", 990, "mdProjectId", 1,
                        "authStart", LocalDate.now().toString(),
                        "authEnd", LocalDate.now().plusDays(30).toString()));

        Resp added = post("/api/v1/thd/blacklist",
                Map.of("targetType", "WORKER", "targetId", 990,
                        "reason", "S14 demo 连续两期考核 D 级", "evidence", "EVAL-2026-10"));
        assertEquals(0, added.code(), added.body().toString());
        blacklistId = dataId(added);
        assertEquals("ACTIVE", ((Map<?, ?>) added.data()).get("status"));

        Resp blocked = post("/api/v1/thd/worker-projects",
                Map.of("workerId", 990, "mdProjectId", 1,
                        "authStart", LocalDate.now().toString(),
                        "authEnd", LocalDate.now().plusDays(30).toString()));
        assertEquals(5001, blocked.code(), "黑名单人员授权应 422");
        assertEquals("THD-BLACKLISTED", ((Map<?, ?>) blocked.body().get("data")).get("blockCode"));

        Resp released = post("/api/v1/thd/blacklist/" + blacklistId + "/release", null);
        assertEquals(0, released.code());
        assertEquals("RELEASED", ((Map<?, ?>) released.data()).get("status"));

        Resp reGrant = post("/api/v1/thd/worker-projects",
                Map.of("workerId", 990, "mdProjectId", 1,
                        "authStart", LocalDate.now().toString(),
                        "authEnd", LocalDate.now().plusDays(30).toString()));
        assertEquals(0, reGrant.code(), "解除后应可重新授权");
    }
}
