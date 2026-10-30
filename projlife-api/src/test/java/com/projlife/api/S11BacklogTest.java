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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S11 收尾批 E2E（表已就绪的服务落地）：
 * 盘点全链路（创建/开始/录入/复核 E-37 无归因不调账/L1 审批闭环）
 * + 档案管理（建档/借阅/重复借出拦截/归还/销毁鉴定意见必填/移交确认）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S11BacklogTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long countId;
    private static Long lineId;
    private static Long diffId;
    private static Long archiveId;
    private static Long borrowId;

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
        Map<String, Object> body = Map.of("username", username, "password", "ProjLife@2026", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("盘点：创建 → 开始 → 录入 → 复核无归因拦截（E-37）→ 补归因 → 差异单 L1")
    void countFlow() {
        loginAs("store@demo");
        Map<String, Object> count = new HashMap<>();
        count.put("countType", "A");
        count.put("scopeJson", "[{\"warehouse\":\"WH01\"}]");
        List<Map<String, Object>> lines = List.of(
                Map.of("materialId", 1, "bookQty", "100"),
                Map.of("materialId", 2, "bookQty", "50"));
        Resp created = post("/api/v1/inv/counts", Map.of("count", count, "lines", lines));
        assertEquals(0, created.code(), created.body().toString());
        Map<String, Object> countData = created.data();
        countId = Long.valueOf(String.valueOf(countData.get("id")));
        assertTrue(String.valueOf(countData.get("countNo")).startsWith("CT-"), "CT 编号");
        assertEquals("CREATED", countData.get("status"));

        assertEquals(0, post("/api/v1/inv/counts/" + countId + "/start", null).code());

        // 行 id 从详情拿（简化：直接查 created 返回不含行，需另行查询——通过 entry 请求全量行）
        Resp linesResp = get("/api/v1/inv/counts/" + countId + "/lines");
        assertEquals(0, linesResp.code());
        List<Map<String, Object>> lineList = linesResp.data();
        lineId = Long.valueOf(String.valueOf(lineList.get(0).get("id")));

        // 录入差异 98（少 2），未填归因
        Resp entered = post("/api/v1/inv/counts/" + countId + "/entry",
                List.of(Map.of("id", lineId, "actualQty", "98")));
        assertEquals(0, entered.code());
        // 复核 → 无归因不调账
        Resp blocked = post("/api/v1/inv/counts/" + countId + "/review", null);
        assertEquals(5001, blocked.code(), "无归因应 422");
        assertEquals("INV-NO-CAUSE", ((Map<?, ?>) blocked.body().get("data")).get("blockCode"));
        // 补归因（E-37 六类之一）后复核
        assertEquals(0, post("/api/v1/inv/counts/" + countId + "/entry",
                List.of(Map.of("id", lineId, "actualQty", "98", "cause", "LOST"))).code());
        Resp reviewed = post("/api/v1/inv/counts/" + countId + "/review", null);
        assertEquals(0, reviewed.code(), reviewed.body().toString());
        List<Map<String, Object>> diffs = reviewed.data();
        assertEquals(1, diffs.size());
        diffId = Long.valueOf(String.valueOf(diffs.get(0).get("id")));
        assertEquals("L1", diffs.get(0).get("approvalLevel"), "差异 <1 万应 L1");
        assertEquals("LOST", diffs.get(0).get("cause"));
    }

    @Test
    @Order(2)
    @DisplayName("差异审批：L1 权限闭环")
    void diffApprove() {
        loginAs("store@demo");
        Resp approved = post("/api/v1/inv/counts/diffs/" + diffId + "/approve", Map.of("level", "L1"));
        assertEquals(0, approved.code(), approved.body().toString());
        assertEquals("0", String.valueOf(((Map<?, ?>) approved.data()).get("closeDays")));
    }

    @Test
    @Order(3)
    @DisplayName("档案：建档 → 借阅 → 重复借出拦截 → 归还")
    void archiveBorrowFlow() {
        loginAs("admin@demo");
        Map<String, Object> archive = new HashMap<>();
        archive.put("title", "S11 demo 合同正本归档");
        archive.put("category", "HT");
        archive.put("retention", "P30");
        archive.put("archiveYear", 2026);
        Resp created = post("/api/v1/arc/archives", archive);
        assertEquals(0, created.code(), created.body().toString());
        Map<String, Object> archiveData = created.data();
        archiveId = Long.valueOf(String.valueOf(archiveData.get("id")));
        assertTrue(String.valueOf(archiveData.get("archiveNo")).startsWith("PRT-"), "PRT 编号");

        Map<String, Object> borrow = new HashMap<>();
        borrow.put("archiveId", archiveId);
        borrow.put("borrowerId", 1);
        borrow.put("purpose", "S11 demo 借阅查阅");
        borrow.put("borrowDate", LocalDate.now().toString());
        borrow.put("dueDate", LocalDate.now().plusDays(7).toString());
        Resp borrowed = post("/api/v1/arc/borrows", borrow);
        assertEquals(0, borrowed.code(), borrowed.body().toString());
        Map<String, Object> borrowData = borrowed.data();
        borrowId = Long.valueOf(String.valueOf(borrowData.get("id")));
        assertTrue(String.valueOf(borrowData.get("borrowNo")).startsWith("JY-"), "JY 编号");

        Resp again = post("/api/v1/arc/borrows", borrow);
        assertEquals(4001, again.code(), "重复借出应 409");

        Resp returned = post("/api/v1/arc/borrows/" + borrowId + "/return", null);
        assertEquals(0, returned.code());
        assertEquals("RETURNED", ((Map<?, ?>) returned.data()).get("status"));
    }

    @Test
    @Order(4)
    @DisplayName("档案：销毁鉴定意见必填 + 移交确认")
    void archiveDestroyAndTransfer() {
        loginAs("admin@demo");
        Resp noOpinion = post("/api/v1/arc/destroys", Map.of(
                "archiveIds", String.valueOf(archiveId), "reason", "保管期满"));
        assertEquals(5001, noOpinion.code(), "无鉴定意见应 422");
        assertEquals("ARC-NO-APPRAISAL", ((Map<?, ?>) noOpinion.body().get("data")).get("blockCode"));

        Resp destroyed = post("/api/v1/arc/destroys", Map.of(
                "archiveIds", String.valueOf(archiveId), "reason", "保管期满",
                "appraisalOpinion", "鉴定小组意见：同意销毁（S11 demo）"));
        assertEquals(0, destroyed.code(), destroyed.body().toString());
        assertTrue(String.valueOf(((Map<?, ?>) destroyed.data()).get("destroyNo")).startsWith("XH-"), "XH 编号");

        // 新建一份用于移交
        Map<String, Object> archive2 = new HashMap<>();
        archive2.put("title", "S11 demo 图纸移交批");
        archive2.put("category", "TZ");
        archive2.put("retention", "P10");
        archive2.put("archiveYear", 2026);
        Resp created2 = post("/api/v1/arc/archives", archive2);
        assertEquals(0, created2.code());
        Long archive2Id = Long.valueOf(String.valueOf(((Map<?, ?>) created2.data()).get("id")));

        Resp transfer = post("/api/v1/arc/transfers", Map.of(
                "fromDept", 12, "toDept", 15,
                "archiveIds", String.valueOf(archive2Id),
                "transferDate", LocalDate.now().toString(),
                "transferor", 1, "receiver", 4));
        assertEquals(0, transfer.code(), transfer.body().toString());
        Long transferId = Long.valueOf(String.valueOf(((Map<?, ?>) transfer.data()).get("id")));
        assertEquals(0, post("/api/v1/arc/transfers/" + transferId + "/confirm", null).code());

        // 在借档案不得销毁
        Map<String, Object> archive3 = new HashMap<>();
        archive3.put("title", "S11 demo 在借销毁拦截");
        archive3.put("category", "HT");
        archive3.put("retention", "P30");
        archive3.put("archiveYear", 2026);
        Resp created3 = post("/api/v1/arc/archives", archive3);
        Long archive3Id = Long.valueOf(String.valueOf(((Map<?, ?>) created3.data()).get("id")));
        post("/api/v1/arc/borrows", Map.of("archiveId", archive3Id, "borrowerId", 1,
                "purpose", "demo", "borrowDate", LocalDate.now().toString(),
                "dueDate", LocalDate.now().plusDays(3).toString()));
        Resp blocked = post("/api/v1/arc/destroys", Map.of(
                "archiveIds", String.valueOf(archive3Id), "reason", "误操作",
                "appraisalOpinion", "demo 意见"));
        assertEquals(5001, blocked.code(), "在借销毁应 422");
        assertEquals("ARC-BORROWED", ((Map<?, ?>) blocked.body().get("data")).get("blockCode"));
    }
}
