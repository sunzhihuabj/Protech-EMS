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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S12-A1 E2E（V4-04 M07 T-INV-07/08）：
 * 呆滞登记 → 确认（E-38 归因必填）→ 处置（E-39 方式/数量守卫/DP 编号/A-31 分级）→ 审批 → 完成回填。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S12SlowDisposalTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long slowId;
    private static Long disposalId;

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

        Long longData() {
            Object d = body.get("data");
            return d == null ? null : Long.valueOf(String.valueOf(d));
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

    @Test
    @Order(1)
    @DisplayName("呆滞登记 → 无归因确认拦截（INV-NO-RESPONSIBILITY）→ E-38 确认")
    void confirmSlowMoving() {
        loginAs("store@demo");
        Map<String, Object> slow = new HashMap<>();
        slow.put("materialId", 1);
        slow.put("qty", "12");
        slow.put("ageDays", 200);
        slow.put("firstSeenDate", "2026-04-01");
        Resp created = post("/api/v1/inv/slow-moving", slow);
        assertEquals(0, created.code(), created.body().toString());
        slowId = Long.valueOf(String.valueOf(((Map<?, ?>) created.data()).get("id")));
        assertEquals("CANDIDATE", ((Map<?, ?>) created.data()).get("status"));

        Resp noResp = post("/api/v1/inv/slow-moving/" + slowId + "/confirm", null);
        assertEquals(5001, noResp.code(), "无归因应 422");
        assertEquals("INV-NO-RESPONSIBILITY", ((Map<?, ?>) noResp.body().get("data")).get("blockCode"));

        // E-38 归因缺失场景：台账上无 responsibility 字段来源 → 走更新通道（此处直接重登一条带归因的）
        Map<String, Object> slow2 = new HashMap<>(slow);
        slow2.put("responsibility", "DESIGN");
        Resp created2 = post("/api/v1/inv/slow-moving", slow2);
        assertEquals(0, created2.code());
        Long slow2Id = Long.valueOf(String.valueOf(((Map<?, ?>) created2.data()).get("id")));
        Resp confirmed = post("/api/v1/inv/slow-moving/" + slow2Id + "/confirm", null);
        assertEquals(0, confirmed.code(), confirmed.body().toString());
        assertEquals("CONFIRMED", ((Map<?, ?>) confirmed.data()).get("status"));
    }

    @Test
    @Order(2)
    @DisplayName("处置：未确认拦截 → E-39 方式/数量守卫 → DP 编号 + L1 分级")
    void createDisposal() {
        loginAs("store@demo");
        // CANDIDATE 直接处置 → 拦截
        Resp notConfirmed = post("/api/v1/inv/slow-moving/" + slowId + "/disposals",
                Map.of("method", "SELL", "qty", "5"));
        assertEquals(5001, notConfirmed.code());
        assertEquals("INV-SLOW-NOT-CONFIRMED", ((Map<?, ?>) notConfirmed.body().get("data")).get("blockCode"));

        // 超量守卫：12 呆滞处置 15 → 拒绝
        Map<String, Object> over = new HashMap<>();
        over.put("method", "SELL");
        over.put("qty", "15");
        over.put("expectAmount", "8000");
        Resp overQty = post("/api/v1/inv/slow-moving/" + (slowId + 1) + "/disposals", over);
        assertEquals(1001, overQty.code(), "超量应 400");

        // 正常处置
        Map<String, Object> disposal = new HashMap<>();
        disposal.put("method", "SELL");
        disposal.put("qty", "5");
        disposal.put("expectAmount", "8000");
        disposal.put("remark", "S12 demo 折价出售");
        Resp created = post("/api/v1/inv/slow-moving/" + (slowId + 1) + "/disposals", disposal);
        assertEquals(0, created.code(), created.body().toString());
        Map<String, Object> data = created.data();
        disposalId = Long.valueOf(String.valueOf(data.get("id")));
        assertTrue(String.valueOf(data.get("disposalNo")).startsWith("DP-"), "DP 编号");
        assertEquals("L1", data.get("approvalLevel"), "预期 <1 万应 L1");
    }

    @Test
    @Order(3)
    @DisplayName("审批 → 完成：未审批完成拦截 + 回填台账 DISPOSED")
    void approveAndComplete() {
        loginAs("store@demo");
        Resp early = post("/api/v1/inv/slow-moving/disposals/" + disposalId + "/complete",
                Map.of("actualAmount", "7500"));
        assertEquals(5001, early.code(), "未审批不得完成");
        assertEquals("INV-DISPOSAL-NOT-APPROVED", ((Map<?, ?>) early.body().get("data")).get("blockCode"));

        assertEquals(0, post("/api/v1/inv/slow-moving/disposals/" + disposalId + "/approve",
                Map.of("level", "L1")).code());

        Resp done = post("/api/v1/inv/slow-moving/disposals/" + disposalId + "/complete",
                Map.of("actualAmount", "7500"));
        assertEquals(0, done.code(), done.body().toString());
        assertEquals("7500", String.valueOf(((Map<?, ?>) done.data()).get("actualAmount")));
    }
}
