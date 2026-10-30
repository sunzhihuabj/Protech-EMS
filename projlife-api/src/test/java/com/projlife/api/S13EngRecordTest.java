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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S13-A2 E2E（V4-05 M10 日常记录）：施工日志（AI 合规降级不阻塞）/质量检查 FAIL 缺陷必填/
 * 领退料累计守卫/售后 WO 响应闭环（方案必填+客户确认）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S13EngRecordTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long dispatchId;
    private static Long mdProjectId;
    private static Long issueId;
    private static Long woId;

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
    @DisplayName("准备：项目档案 + 派工（BR-03/04 通过）")
    void prepareDispatch() {
        loginAs("mdm@demo");
        Map<String, Object> md = new HashMap<>();
        md.put("customerId", 2);
        md.put("ownerId", 2);
        md.put("legalEntityId", 2);
        md.put("orderType", "OEM_INSTALL");
        md.put("farmId", 9);
        md.put("farmNameRaw", "Haitan-South-S13");
        md.put("phase", "P9");
        md.put("stdName", "金风科技-海坛南-S13");
        md.put("shortName", "HT-S13-SOUTH");
        md.put("productLines", List.of("TOWER"));
        md.put("setQty", 2);
        md.put("engOwnerId", 4);
        md.put("bizOwnerId", 2);
        md.put("forceNewReason", "S13 测试专用项目，与既有项目无关");
        Resp mdCreated = post("/api/v1/mdm/projects", md);
        assertEquals(0, mdCreated.code(), mdCreated.body().toString());
        mdProjectId = mdCreated.longData();

        loginAs("eng@demo");
        Resp ok = post("/api/v1/eng/dispatch",
                Map.of("mdProjectId", mdProjectId, "farmId", 9, "leaderId", 4,
                        "planStart", "2026-11-05", "planEnd", "2026-11-25"));
        assertEquals(0, ok.code(), ok.body().toString());
        dispatchId = ok.longData();
    }

    @Test
    @Order(2)
    @DisplayName("FR-ENG：施工日志——AI 降级不阻塞记录（ai_check_result 落库）")
    void dailyLog() {
        loginAs("eng@demo");
        Resp created = post("/api/v1/eng/records/daily-logs",
                Map.of("dispatchId", dispatchId, "projectId", mdProjectId, "workerId", 4,
                        "logDate", LocalDate.now().toString(),
                        "content", "S13 demo 风机吊装就位，现场无异常", "weather", "SUNNY"));
        assertEquals(0, created.code(), created.body().toString());
        Map<?, ?> data = (Map<?, ?>) created.data();
        assertTrue(String.valueOf(data.get("aiCheckResult")).length() > 0, "AI 检查结果应落库（含降级标记）");
    }

    @Test
    @Order(3)
    @DisplayName("质量检查：FAIL 缺陷必填（ENG-QC-DEFECT）→ PASS 通过")
    void qualityCheck() {
        loginAs("eng@demo");
        Resp noDefect = post("/api/v1/eng/records/quality-checks",
                Map.of("unitId", 1, "dispatchId", dispatchId, "checkType", "INSTALL",
                        "checkItem", "塔筒法兰螺栓力矩", "result", "FAIL"));
        assertEquals(5001, noDefect.code(), "FAIL 无缺陷描述应 422");
        assertEquals("ENG-QC-DEFECT", ((Map<?, ?>) noDefect.body().get("data")).get("blockCode"));

        Resp fail = post("/api/v1/eng/records/quality-checks",
                Map.of("unitId", 1, "dispatchId", dispatchId, "checkType", "INSTALL",
                        "checkItem", "塔筒法兰螺栓力矩", "result", "FAIL",
                        "defectDesc", "S13 demo 第 3 组螺栓力矩不足"));
        assertEquals(0, fail.code(), fail.body().toString());

        Resp pass = post("/api/v1/eng/records/quality-checks",
                Map.of("unitId", 1, "dispatchId", dispatchId, "checkType", "INSTALL",
                        "checkItem", "机舱对接复检", "result", "PASS"));
        assertEquals(0, pass.code());
    }

    @Test
    @Order(4)
    @DisplayName("现场物料：领 10 → 退 4 → 退 7 超量拦截（ENG-RETURN-OVER）→ 退 6 全退")
    void materialIssueReturn() {
        loginAs("eng@demo");
        Resp issued = post("/api/v1/eng/records/material-issues",
                Map.of("projectId", mdProjectId, "dispatchId", dispatchId,
                        "materialId", 1, "issueQty", "10"));
        assertEquals(0, issued.code(), issued.body().toString());
        issueId = Long.valueOf(String.valueOf(((Map<?, ?>) issued.data()).get("id")));

        assertEquals(0, post("/api/v1/eng/records/material-issues/" + issueId + "/return",
                Map.of("qty", "4")).code());

        Resp over = post("/api/v1/eng/records/material-issues/" + issueId + "/return",
                Map.of("qty", "7"));
        assertEquals(5001, over.code(), "累计退料超领料应 422");
        assertEquals("ENG-RETURN-OVER", ((Map<?, ?>) over.body().get("data")).get("blockCode"));

        Resp full = post("/api/v1/eng/records/material-issues/" + issueId + "/return",
                Map.of("qty", "6"));
        assertEquals(0, full.code());
        assertEquals("RETURNED", ((Map<?, ?>) full.data()).get("status"));
    }

    @Test
    @Order(5)
    @DisplayName("售后工单：WO 编号 → 响应 → 闭环方案必填（ENG-WO-SOLUTION）→ 客户确认")
    void afterSalesLoop() {
        loginAs("eng@demo");
        Resp created = post("/api/v1/eng/records/after-sales",
                Map.of("mdProjectId", mdProjectId, "faultType", "PITCH_FAULT",
                        "urgency", "HIGH", "warrantyFlag", 1));
        assertEquals(0, created.code(), created.body().toString());
        Map<?, ?> woData = (Map<?, ?>) created.data();
        woId = Long.valueOf(String.valueOf(woData.get("id")));
        assertTrue(String.valueOf(woData.get("orderNo")).startsWith("WO-"), "WO 编号");
        assertEquals("TO_DISPATCH", woData.get("status"));

        assertEquals(0, post("/api/v1/eng/records/after-sales/" + woId + "/respond",
                Map.of("handlerId", 4)).code());

        Resp noSolution = post("/api/v1/eng/records/after-sales/" + woId + "/close",
                Map.of("solution", "短"));
        assertEquals(5001, noSolution.code(), "闭环方案 <5 字应 422");
        assertEquals("ENG-WO-SOLUTION", ((Map<?, ?>) noSolution.body().get("data")).get("blockCode"));

        Resp closed = post("/api/v1/eng/records/after-sales/" + woId + "/close",
                Map.of("solution", "S13 demo 变桨电机更换并空载测试通过", "costAmount", "3200"));
        assertEquals(0, closed.code(), closed.body().toString());
        Map<?, ?> closedData = (Map<?, ?>) closed.data();
        assertEquals("CLOSED", closedData.get("status"));
        assertEquals("1", String.valueOf(closedData.get("customerConfirm")));
    }
}
