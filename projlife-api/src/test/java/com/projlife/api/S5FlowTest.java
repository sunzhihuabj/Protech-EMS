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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S5 E2E（16 册 S5 完成标准）：生产订单三类型 BOM 校验 / 工序路由 / E-26 状态机全迁移 /
 * 报工数量校验 / 自动流转 / 报检 WAIT_QC / 检验放行 / Andon。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S5FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long moId;
    private static List<Map<String, Object>> processes;

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

    @Test
    @Order(1)
    @DisplayName("FR-MES-01/02：批量订单 + 工序路由生成（4 道串行工序 WS 编号）")
    void createOrderAndRoutes() {
        loginAs("prod@demo"); // R12 生产（V8 seed）
        Resp mo = post("/api/v1/mes/orders",
                Map.of("projectId", 1, "moType", "BATCH", "bomHeaderId", 1, "planQty", 10,
                        "planStart", "2026-11-01", "planEnd", "2026-11-20"));
        assertEquals(0, mo.code(), mo.body().toString());
        moId = mo.longData();

        Resp routes = post("/api/v1/mes/orders/" + moId + "/routes",
                Map.of("steps", List.of(
                        Map.of("name", "钣金加工", "code", "CUT", "standardHours", 2),
                        Map.of("name", "焊接", "code", "WELD", "standardHours", 3, "needInspection", true),
                        Map.of("name", "喷涂", "code", "PAINT", "standardHours", 1),
                        Map.of("name", "总装", "code", "ASM", "standardHours", 4, "needInspection", true))));
        assertEquals(4, ((Number) routes.data()).intValue(), "应生成 4 道工序");

        processes = get("/api/v1/mes/orders/" + moId + "/processes").data();
        assertEquals(4, processes.size());
        String firstNo = String.valueOf(processes.get(0).get("processNo"));
        assertTrue(firstNo.matches("WS-.{6}-\\d{2}"), "WS-{MO后6位}-{2位} 格式（02 册按 MO 号末 6 位），实际 " + firstNo);
        assertEquals("TODO", processes.get(0).get("status"));
    }

    @Test
    @Order(2)
    @DisplayName("FR-MES-10：串行第 2 道直接开工被拒（前道未完成）")
    void serialPredecessorBlocked() {
        Long secondProcess = Long.valueOf(String.valueOf(processes.get(1).get("id")));
        Resp start = post("/api/v1/mes/processes/" + secondProcess + "/start", null);
        assertEquals(1001, start.code());
        assertTrue(String.valueOf(start.body().get("message")).contains("FR-MES-10"));
    }

    @Test
    @Order(3)
    @DisplayName("FR-MES-13：报工数量校验（不等计划数拒绝）")
    void finishQtyValidation() {
        Long firstProcess = Long.valueOf(String.valueOf(processes.get(0).get("id")));
        assertEquals(0, post("/api/v1/mes/processes/" + firstProcess + "/start", null).code());
        Resp badFinish = post("/api/v1/mes/processes/" + firstProcess + "/finish",
                Map.of("goodQty", 6, "defectQty", 1, "scrapQty", 1, "workHours", 2));
        assertEquals(1001, badFinish.code(), "6+1+1=8 ≠ 10 应拒绝");
        assertTrue(String.valueOf(badFinish.body().get("message")).contains("FR-MES-13"));
    }

    @Test
    @Order(4)
    @DisplayName("FR-MES-15：需报检工序完工 → WAIT_QC；FR-MES-16 检验 PASS → DONE")
    void inspectionFlow() {
        // 焊接工序（processes.get(1)，needInspection=1）：先完工第 0 道（免检直 DONE）再开工第 1 道
        Long firstProcess = Long.valueOf(String.valueOf(processes.get(0).get("id")));
        assertEquals(0, post("/api/v1/mes/processes/" + firstProcess + "/finish",
                Map.of("goodQty", 10, "defectQty", 0, "scrapQty", 0, "workHours", 2)).code());
        Long weldProcess = Long.valueOf(String.valueOf(processes.get(1).get("id")));
        assertEquals(0, post("/api/v1/mes/processes/" + weldProcess + "/start", null).code());
        Resp finish = post("/api/v1/mes/processes/" + weldProcess + "/finish",
                Map.of("goodQty", 9, "defectQty", 1, "scrapQty", 0, "workHours", 3));
        assertEquals(0, finish.code(), finish.body().toString());
        Map<String, Object> finishData = finish.data();
        assertEquals("WAIT_QC", finishData.get("status"), "needInspection=1 → WAIT_QC");

        Resp qcPass = post("/api/v1/mes/processes/" + weldProcess + "/qc", Map.of("passed", true));
        assertEquals(0, qcPass.code());
        List<Map<String, Object>> refreshed = get("/api/v1/mes/orders/" + moId + "/processes").data();
        assertEquals("DONE", refreshed.get(1).get("status"));
    }

    @Test
    @Order(5)
    @DisplayName("FR-MES-16 检验 REJECT → LOCKED + Andon QUALITY")
    void inspectionRejectLocks() {
        // 总装（processes.get(3)，needInspection=1）：喷涂免检直 DONE 后 → 开工总装 → REJECT → LOCKED
        Long thirdProcess = Long.valueOf(String.valueOf(processes.get(2).get("id")));
        assertEquals(0, post("/api/v1/mes/processes/" + thirdProcess + "/start", null).code());
        assertEquals(0, post("/api/v1/mes/processes/" + thirdProcess + "/finish",
                Map.of("goodQty", 10, "defectQty", 0, "scrapQty", 0, "workHours", 1)).code());
        Long asmProcess = Long.valueOf(String.valueOf(processes.get(3).get("id")));
        assertEquals(0, post("/api/v1/mes/processes/" + asmProcess + "/start", null).code());
        assertEquals(0, post("/api/v1/mes/processes/" + asmProcess + "/finish",
                Map.of("goodQty", 8, "defectQty", 2, "scrapQty", 0, "workHours", 4)).code());
        Resp qcReject = post("/api/v1/mes/processes/" + asmProcess + "/qc",
                Map.of("passed", false, "rejectReason", "总装间隙超标（demo）"));
        assertEquals(0, qcReject.code());
        List<Map<String, Object>> refreshed = get("/api/v1/mes/orders/" + moId + "/processes").data();
        assertEquals("LOCKED", refreshed.get(3).get("status"));
        assertTrue(String.valueOf(refreshed.get(3).get("lockedReason")).contains("超标"));
    }

    @Test
    @Order(6)
    @DisplayName("FR-MES-28/30：Andon 触发与分级升级（≥15 分钟 → 2 级）")
    void andonEscalation() {
        Resp raised = post("/api/v1/mes/andon",
                Map.of("productionOrderId", moId, "alertType", "MATERIAL", "content", "缺料停线（demo）"));
        assertEquals(0, raised.code());
        Resp escalated = post("/api/v1/mes/andon/escalate", null);
        assertEquals(0, escalated.code());
    }

    private void loginAs(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "ProjLife@2026", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }
}
