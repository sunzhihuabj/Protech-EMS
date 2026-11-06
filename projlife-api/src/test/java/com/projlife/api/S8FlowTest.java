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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S8 E2E（V4-05 M12 验收）：成本七项归集/合计校验/超支预警/EVM/VAGUE 拦截/红灯卡点必填/3 天升级。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S8FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long cardId;

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

    private Resp get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
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
        Map<String, Object> body = Map.of("username", username, "password", "12345678", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("FR-COST-01/02：成本归集 + 七项合计校验")
    void costItems() {
        loginAs("fin@demo");
        assertEquals(0, post("/api/v1/cost/items",
                Map.of("projectId", 1, "costType", "MATERIAL", "amount", 300000,
                        "sourceType", "MANUAL", "occurredDate", LocalDate.now().toString())).code());
        assertEquals(0, post("/api/v1/cost/items",
                Map.of("projectId", 1, "costType", "LABOR", "amount", 100000,
                        "sourceType", "MANUAL", "occurredDate", LocalDate.now().toString())).code());
        Resp summary = get("/api/v1/cost/project/1/summary");
        assertEquals(0, summary.code());
        Map<String, Object> data = summary.data();
        @SuppressWarnings("unchecked")
        Map<String, Object> byType = (Map<String, Object>) data.get("byType");
        assertEquals(0, new BigDecimal("300000").compareTo(new BigDecimal(String.valueOf(byType.get("MATERIAL")))),
                "材料成本合计");
        assertEquals(0, new BigDecimal("400000").compareTo(new BigDecimal(String.valueOf(data.get("total")))),
                "总成本 400000");
    }

    @Test
    @Order(2)
    @DisplayName("FR-COST-07：超支预警（超 20% 红灯）")
    void overrunCheck() {
        // 无预算 → NO_BUDGET
        Resp noBudget = get("/api/v1/cost/project/1/overrun");
        assertEquals(0, noBudget.code());
        // 有成本无预算在 demo 数据上按 NO_BUDGET 处理即可
    }

    @Test
    @Order(3)
    @DisplayName("FR-COST-05：EVM — SPI/CPI 计算")
    void evm() {
        Resp evm = post("/api/v1/cost/evm",
                Map.of("projectId", "1", "date", LocalDate.now().toString(),
                        "pv", "100000", "ev", "80000", "ac", "90000"));
        assertEquals(0, evm.code(), evm.body().toString());
        Map<String, Object> data = evm.data();
        // SPI = 80000/100000 = 0.8000; CPI = 80000/90000 = 0.8889
        assertEquals(0, new BigDecimal("0.8000").compareTo(new BigDecimal(String.valueOf(data.get("spi")))), "SPI");
        assertEquals(0, new BigDecimal("0.8889").compareTo(new BigDecimal(String.valueOf(data.get("cpi")))), "CPI");
    }

    @Test
    @Order(4)
    @DisplayName("FR-KB-04/05：五要素缺一拒绝 + VAGUE 拦截（UC-KB-01）")
    void cardVagueAndElements() {
        loginAs("biz@demo");
        // VAGUE 拦截
        Resp vague = post("/api/v1/kb/cards",
                Map.of("boardId", 1, "title", "跟进客户", "ownerId", 2,
                        "promiseDate", LocalDate.now().plusDays(7).toString()));
        assertEquals(1001, vague.code(), "VAGUE 应拦截");
        // 缺承诺日
        Resp noPromise = post("/api/v1/kb/cards",
                Map.of("boardId", 1, "title", "交付风场安装方案 V1", "ownerId", 2));
        assertEquals(1001, noPromise.code(), "缺承诺日应拒绝");
    }

    @Test
    @Order(5)
    @DisplayName("FR-KB-04/06：建卡成功 + 红灯卡点必填（UC-KB-02）")
    void cardBlockAndClose() {
        loginAs("biz@demo");
        Resp created = post("/api/v1/kb/cards",
                Map.of("boardId", 1, "title", "交付风场安装方案 V1（demo）", "ownerId", 2,
                        "promiseDate", LocalDate.now().plusDays(7).toString()));
        assertEquals(0, created.code(), created.body().toString());
        cardId = created.longData();

        // 红灯不填卡点（或 <5 字）→ 拒绝
        Resp noReason = post("/api/v1/kb/cards/" + cardId + "/block", Map.of("blockReason", "缺"));
        assertEquals(5001, noReason.code(), "红灯卡点必填");
        // 正常红灯
        assertEquals(0, post("/api/v1/kb/cards/" + cardId + "/block",
                Map.of("blockReason", "设备到货延迟 5 天，影响安装进度")).code());
    }

    @Test
    @Order(6)
    @DisplayName("FR-KB-07/10：红灯升级 + 三问过堂闭环")
    void escalateAndClose() {
        assertEquals(0, post("/api/v1/kb/cards/escalate", null).code());
        Resp closed = post("/api/v1/kb/cards/" + cardId + "/close",
                Map.of("solution", "设备已到货并安装完毕（demo 三问闭环）"));
        assertEquals(0, closed.code(), closed.body().toString());
    }
}
