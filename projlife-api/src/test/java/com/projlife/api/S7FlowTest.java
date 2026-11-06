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
 * S7 E2E（V4-05 M10）：班组准入（UC-ENG-10）/ 派工 BR-03·BR-04 / 派工状态机 E-32 /
 * 机组状态机 E-29 / 整改闭环 / FR-ENG-08 无未闭环整改校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S7FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long workerId;
    private static Long dispatchId;
    private static Long unitId;
    private static Long rectId;
    private static Long mdProjectId;

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

        Long longData() {
            Object d = body.get("data");
            return d == null ? null : Long.valueOf(String.valueOf(d));
        }

        String blockCode() {
            Object d = body.get("data");
            if (d instanceof Map) {
                Object bc = ((Map<?, ?>) d).get("blockCode");
                return bc == null ? null : String.valueOf(bc);
            }
            return null;
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
    @DisplayName("准备：R18 建项目档案 + FR-ENG-01/02 人员（保险过期激活被拒 UC-ENG-10）")
    void workerOnboarding() {
        loginAs("mdm@demo");
        Map<String, Object> md = new HashMap<>();
        md.put("customerId", 6);
        md.put("ownerId", 6);
        md.put("legalEntityId", 2);
        md.put("orderType", "OEM_INSTALL");
        md.put("farmId", 1);
        md.put("farmNameRaw", "Qilin-West-S7");
        md.put("phase", "P7");
        md.put("stdName", "Goldwind-Qilin-S7-West");
        md.put("shortName", "QL-S7-WEST");
        md.put("productLines", List.of("LADDER"));
        md.put("setQty", 3);
        md.put("engOwnerId", 4);
        md.put("bizOwnerId", 2);
        md.put("forceNewReason", "S7 测试专用项目，与既有项目无关");
        Resp mdCreated = post("/api/v1/mdm/projects", md);
        assertEquals(0, mdCreated.code(), mdCreated.body().toString());
        mdProjectId = mdCreated.longData();

        loginAs("eng@demo");
        Resp created = post("/api/v1/eng/workers",
                Map.of("name", "张三（demo）", "crewId", 1,
                        "insuranceExpire", LocalDate.now().minusDays(1).toString()));
        assertEquals(0, created.code(), created.body().toString());
        workerId = created.longData();

        Resp blocked = post("/api/v1/eng/workers/" + workerId + "/activate", null);
        assertEquals(1001, blocked.code(), "保险过期拒绝授权（UC-ENG-10）");
        assertTrue(String.valueOf(blocked.body().get("message")).contains("过期"),
                String.valueOf(blocked.body()));
    }

    @Test
    @Order(2)
    @DisplayName("FR-ENG-03 + BR-03/BR-04：派工缺项目/缺风场分别被拒；齐备后创建")
    void dispatchHardBlocks() {
        loginAs("eng@demo");
        Resp noProject = post("/api/v1/eng/dispatch",
                Map.of("farmId", 1, "leaderId", 4, "planStart", "2026-11-05", "planEnd", "2026-11-25"));
        assertEquals(5001, noProject.code());
        assertEquals("BR-03", noProject.blockCode());

        Resp noFarm = post("/api/v1/eng/dispatch",
                Map.of("mdProjectId", 1, "leaderId", 4, "planStart", "2026-11-05", "planEnd", "2026-11-25"));
        assertEquals(5001, noFarm.code());
        assertEquals("BR-04", noFarm.blockCode());

        Resp ok = post("/api/v1/eng/dispatch",
                Map.of("mdProjectId", mdProjectId, "farmId", 1, "leaderId", 4,
                        "planStart", "2026-11-05", "planEnd", "2026-11-25"));
        assertEquals(0, ok.code(), ok.body().toString());
        dispatchId = ok.longData();
    }

    @Test
    @Order(3)
    @DisplayName("E-32 派工状态机：TO_DISPATCH→DISPATCHED→DOING→TO_ACCEPT→DONE")
    void dispatchStateMachine() {
        loginAs("eng@demo");
        assertEquals(0, post("/api/v1/eng/dispatch/" + dispatchId + "/send", null).code());
        assertEquals(0, post("/api/v1/eng/dispatch/" + dispatchId + "/start", null).code());
        assertEquals(0, post("/api/v1/eng/dispatch/" + dispatchId + "/accept", null).code());
        assertEquals(0, post("/api/v1/eng/dispatch/" + dispatchId + "/close", null).code());
    }

    @Test
    @Order(4)
    @DisplayName("FR-ENG-21 + E-29：机组建档 → 状态推进 TO_INSTALL→INSTALLING→TO_DEBUG→ACCEPTED")
    void unitLifecycle() {
        loginAs("eng@demo");
        Resp created = post("/api/v1/eng/units",
                Map.of("mdProjectId", mdProjectId, "unitName", "A01（demo）", "productLines", "[\"LADDER\"]"));
        assertEquals(0, created.code(), created.body().toString());
        unitId = created.longData();
        Resp page = get("/api/v1/eng/units?mdProjectId=1");
        // unitNo 从推进接口不可得，这里直接推进验证状态机
        assertEquals(0, post("/api/v1/eng/units/" + unitId + "/advance", Map.of("targetStatus", "INSTALLING")).code());
        assertEquals(0, post("/api/v1/eng/units/" + unitId + "/advance", Map.of("targetStatus", "TO_DEBUG")).code());
        assertEquals(0, post("/api/v1/eng/units/" + unitId + "/advance", Map.of("targetStatus", "ACCEPTED")).code());
        // 非法迁移：ACCEPTED → INSTALLING 应拒绝
        Resp illegal = post("/api/v1/eng/units/" + unitId + "/advance", Map.of("targetStatus", "INSTALLING"));
        assertEquals(1001, illegal.code(), "E-29 非法迁移拒绝");
    }

    @Test
    @Order(5)
    @DisplayName("FR-ENG-14：整改闭环（PENDING→DOING→TO_REVIEW→PASSED→CLOSED）")
    void rectificationLoop() {
        loginAs("eng@demo");
        Resp created = post("/api/v1/eng/rectifications",
                Map.of("projectId", 1, "problemDesc", "接线松动（demo）", "sourceType", "MANUAL",
                        "deadline", LocalDate.now().plusDays(7).toString()));
        assertEquals(0, created.code(), created.body().toString());
        rectId = created.longData();

        assertEquals(0, post("/api/v1/eng/rectifications/" + rectId + "/advance",
                Map.of("targetStatus", "DOING")).code());
        assertEquals(0, post("/api/v1/eng/rectifications/" + rectId + "/advance",
                Map.of("targetStatus", "TO_REVIEW")).code());
        assertEquals(0, post("/api/v1/eng/rectifications/" + rectId + "/advance",
                Map.of("targetStatus", "PASSED")).code());
        assertEquals(0, post("/api/v1/eng/rectifications/" + rectId + "/advance",
                Map.of("targetStatus", "CLOSED")).code());
    }

    @Test
    @Order(6)
    @DisplayName("FR-ENG-08：未闭环整改存在时拒绝整体验收")
    void openRectificationBlocksAcceptance() {
        loginAs("eng@demo");
        // 新建一个未闭环整改
        Resp created = post("/api/v1/eng/rectifications",
                Map.of("projectId", 1, "problemDesc", "验收前发现缺陷（demo）", "sourceType", "SUPERVISOR"));
        assertEquals(0, created.code());
        Long openRect = created.longData();

        Resp check = get("/api/v1/eng/projects/1/rect-open-check");
        assertEquals(1001, check.code(), "未闭环整改应拒绝");
        assertTrue(String.valueOf(check.body().get("message")).contains("FR-ENG-08"));

        // 闭环后通过
        post("/api/v1/eng/rectifications/" + openRect + "/advance", Map.of("targetStatus", "DOING"));
        post("/api/v1/eng/rectifications/" + openRect + "/advance", Map.of("targetStatus", "TO_REVIEW"));
        post("/api/v1/eng/rectifications/" + openRect + "/advance", Map.of("targetStatus", "PASSED"));
        post("/api/v1/eng/rectifications/" + openRect + "/advance", Map.of("targetStatus", "CLOSED"));
        Resp check2 = get("/api/v1/eng/projects/1/rect-open-check");
        assertEquals(0, check2.code(), check2.body().toString());
    }
}
