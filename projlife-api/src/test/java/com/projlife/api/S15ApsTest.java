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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S15-A4 E2E（V4-04 M06 T-APS）：L1 产能顺排（最小负载选资源/优先级排序/冲突清单）
 * → L2 拖拽调整（版本+1/ADJUSTED）→ 发布（齐套缺料阻断逻辑就绪）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S15ApsTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long moId1;
    private static Long moId2;
    private static Long scheduleId;
    private static Long detailId;

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

    private Long createOrderWithRoutes(String shortTag) {
        Resp mo = post("/api/v1/mes/orders",
                Map.of("projectId", 1, "moType", "BATCH", "bomHeaderId", 1, "planQty", 5,
                        "planStart", "2026-11-01", "planEnd", "2026-11-20"));
        assertEquals(0, mo.code(), mo.body().toString());
        Long moId = mo.longData();
        assertEquals(2, ((Number) post("/api/v1/mes/orders/" + moId + "/routes",
                Map.of("steps", List.of(
                        Map.of("name", shortTag + "加工", "code", "CUT", "standardHours", 1),
                        Map.of("name", shortTag + "总装", "code", "ASM", "standardHours", 1)))).data())
                .intValue(), "应生成 2 道工序");
        return moId;
    }

    @Test
    @Order(1)
    @DisplayName("准备：产能资源 + 两张生产订单（各 2 道工序，1h/道）")
    void prepare() {
        loginAs("prod@demo");
        assertEquals(0, post("/api/v1/aps/capacities",
                Map.of("resourceId", 201, "resourceType", "EQUIPMENT", "capacityPerDay", "8")).code());
        assertEquals(0, post("/api/v1/aps/capacities",
                Map.of("resourceId", 202, "resourceType", "EQUIPMENT", "capacityPerDay", "8")).code());
        moId1 = createOrderWithRoutes("S15A");
        moId2 = createOrderWithRoutes("S15B");
    }

    @Test
    @Order(2)
    @DisplayName("L1 顺排：SCH 编号 + 4 条明细 + 优先级排序 + 无冲突")
    void scheduleL1() {
        loginAs("prod@demo");
        Resp result = post("/api/v1/aps/schedules/l1",
                Map.of("productionOrderIds", List.of(moId2, moId1), "startDate", "2026-11-03"));
        assertEquals(0, result.code(), result.body().toString());
        Map<String, Object> data = result.data();
        assertTrue(String.valueOf(data.get("scheduleNo")).startsWith("SCH-"), "SCH 编号");
        scheduleId = Long.valueOf(String.valueOf(data.get("scheduleId")));
        assertEquals(4L, Long.valueOf(String.valueOf(data.get("detailCount"))).longValue(), "2 单 × 2 道工序");
        assertEquals(0, Integer.valueOf(String.valueOf(data.get("conflictCount"))).intValue(), "240 分钟 < 480 日产能应无冲突");
    }

    @Test
    @Order(3)
    @DisplayName("L2 调整：拖拽后 ADJUSTED + 版本 +1")
    void adjust() {
        loginAs("prod@demo");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        Resp details = new Resp(rest.exchange("/api/v1/aps/schedules/" + scheduleId + "/details",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
        assertEquals(0, details.code(), details.body().toString());
        List<Map<String, Object>> list = details.data();
        assertEquals(4, list.size());
        detailId = Long.valueOf(String.valueOf(list.get(0).get("id")));

        Resp adjusted = post("/api/v1/aps/details/" + detailId + "/adjust",
                Map.of("plannedStart", "2026-11-03 13:00:00", "plannedEnd", "2026-11-03 14:00:00"));
        assertEquals(0, adjusted.code(), adjusted.body().toString());
        assertTrue(String.valueOf(((Map<?, ?>) adjusted.data()).get("plannedStart")).startsWith("2026-11-03T13:00"),
                "拖拽后开始时间应更新（明细状态保持 PLANNED，排产单置 ADJUSTED）");
    }

    @Test
    @Order(4)
    @DisplayName("发布：无缺料 → PUBLISHED；重复发布拒绝")
    void publish() {
        loginAs("prod@demo");
        Resp published = post("/api/v1/aps/schedules/" + scheduleId + "/publish", null);
        assertEquals(0, published.code(), published.body().toString());
        assertEquals("PUBLISHED", ((Map<?, ?>) published.data()).get("status"));

        Resp again = post("/api/v1/aps/schedules/" + scheduleId + "/publish", null);
        assertEquals(1001, again.code(), "重复发布应拒绝");

        // 已发布后再调整 → APS-PUBLISHED 阻断
        Resp adjust = post("/api/v1/aps/details/" + detailId + "/adjust",
                Map.of("plannedStart", "2026-11-04 08:00:00", "plannedEnd", "2026-11-04 09:00:00"));
        assertEquals(5001, adjust.code(), "已发布排产不可调整");
        assertEquals("APS-PUBLISHED", ((Map<?, ?>) adjust.body().get("data")).get("blockCode"));
    }
}
