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
 * V4.0 对齐切片 E2E（V4-03 M01）：FR-CRM-17 报价单建档（QT 编号/版本递增/金额服务端算）/
 * FR-CRM-18 价格基准 / FR-CRM-19 报价转合同（WON 回写 converted_contract_id）/ FR-CRM-20 转化统计 /
 * 商机 QUOTING 状态 / 枚举 E-43—E-52。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V4AlignTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long opportunityId;
    private static Long quotationId;
    private static Long contractDraftId;

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
    @DisplayName("FR-CRM-17 R1/R2/R3：报价单建档（QT 编号/版本 V1/金额=数量×单价×(1+税率)）")
    void createQuotation() {
        loginAs("biz@demo");
        // 先建商机（V4.0 商机状态机：TRACKING → QUOTING）
        Resp opp = post("/api/v1/crm/opportunities",
                Map.of("oppName", "V4 报价链路商机（demo）", "customerId", 6,
                        "intentionLevel", "H", "expectAmount", 500000,
                        "expectSignDate", LocalDate.now().plusDays(20).toString()));
        assertEquals(0, opp.code(), opp.body().toString());
        opportunityId = opp.longData();

        Resp quotation = post("/api/v1/crm/quotations",
                Map.of("opportunityId", opportunityId, "customerId", 6, "productLine", "LADDER",
                        "qty", 5, "unitPrice", 100000, "taxRate", 0.13,
                        "deliveryTerms", "现场交付 60 天", "validUntil", LocalDate.now().plusDays(30).toString(),
                        "lines", List.of(Map.of("materialId", 1, "qty", 5, "price", 100000))));
        assertEquals(0, quotation.code(), quotation.body().toString());
        quotationId = quotation.longData();

        Resp detail = get("/api/v1/crm/quotations?opportunityId=" + opportunityId);
        Map<String, Object> pageData = detail.data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pageData.get("records");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("V1", row.get("version"), "首版 V1");
        String quotationNo = String.valueOf(row.get("quotationNo"));
        assertTrue(quotationNo.matches("QT-\\d{6}-\\d{4}"), "QT-{YYYYMM}-{4位}，实际 " + quotationNo);
        // 金额 = 5 × 100000 × 1.13 = 565000
        assertEquals(0, new BigDecimal("565000.00").compareTo(new BigDecimal(String.valueOf(row.get("amount")))),
                "金额服务端计算");

        // 商机状态 → QUOTING
        Resp oppDetail = get("/api/v1/crm/opportunities/" + opportunityId);
        Map<String, Object> oppData = oppDetail.data();
        assertEquals("QUOTING", oppData.get("status"), "有报价 → QUOTING（M01 §4）");
    }

    @Test
    @Order(2)
    @DisplayName("FR-CRM-17 R3：第二版报价 V2 递增；FR-CRM-17 R4 提交")
    void secondVersionAndSubmit() {
        Resp quotation = post("/api/v1/crm/quotations",
                Map.of("opportunityId", opportunityId, "customerId", 6, "productLine", "LADDER",
                        "qty", 5, "unitPrice", 95000, "taxRate", 0.13,
                        "validUntil", LocalDate.now().plusDays(30).toString()));
        assertEquals(0, quotation.code(), quotation.body().toString());
        Resp page = get("/api/v1/crm/quotations?opportunityId=" + opportunityId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ((Map<String, Object>) page.data()).get("records");
        assertEquals(2, rows.size(), "一商机两版报价");
        assertTrue(rows.stream().anyMatch(r -> "V2".equals(r.get("version"))), "版本递增 V2");

        // 取 V2 提交
        Long v2Id = rows.stream().filter(r -> "V2".equals(r.get("version")))
                .findFirst().map(r -> Long.valueOf(String.valueOf(r.get("id")))).orElseThrow();
        assertEquals(0, post("/api/v1/crm/quotations/" + v2Id + "/submit", null).code());
        quotationId = v2Id;
    }

    @Test
    @Order(3)
    @DisplayName("FR-CRM-18：价格基准接口（crm:cost:view 脱敏由权限矩阵承载）")
    void priceBasis() {
        Resp basis = get("/api/v1/crm/quotations/" + quotationId + "/price-basis");
        assertEquals(0, basis.code(), basis.body().toString());
        assertTrue(String.valueOf(basis.body()).contains("R05"), "含成本告警口径说明");
    }

    @Test
    @Order(4)
    @DisplayName("FR-CRM-19：中标 → 合同草稿生成 + converted_contract_id 回写；商机 WON")
    void decideWon() {
        Resp decided = post("/api/v1/crm/quotations/" + quotationId + "/decide", Map.of("won", true));
        assertEquals(0, decided.code(), decided.body().toString());
        Map<String, Object> decidedData = decided.data();
        contractDraftId = Long.valueOf(String.valueOf(decidedData.get("contractDraftId")));

        Resp page = get("/api/v1/crm/quotations?opportunityId=" + opportunityId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ((Map<String, Object>) page.data()).get("records");
        Map<String, Object> v2 = rows.stream().filter(r -> "V2".equals(r.get("version"))).findFirst().orElseThrow();
        assertEquals("WON", v2.get("status"));
        assertEquals(String.valueOf(contractDraftId), String.valueOf(v2.get("convertedContractId")),
                "回写 converted_contract_id（FR-CRM-19 R2）");

        Resp oppDetail = get("/api/v1/crm/opportunities/" + opportunityId);
        Map<String, Object> oppData = oppDetail.data();
        assertEquals("WON", oppData.get("status"), "商机 WON");
    }

    @Test
    @Order(5)
    @DisplayName("FR-CRM-19 R3：未中标必须填原因")
    void decideLostRequiresReason() {
        // 新商机+报价走 LOST 分支
        Resp opp = post("/api/v1/crm/opportunities",
                Map.of("oppName", "V4 未中标商机（demo）", "customerId", 7,
                        "intentionLevel", "M", "expectAmount", 100000,
                        "expectSignDate", LocalDate.now().plusDays(10).toString()));
        assertEquals(0, opp.code());
        Long opp2 = opp.longData();
        Resp quotation = post("/api/v1/crm/quotations",
                Map.of("opportunityId", opp2, "customerId", 7, "productLine", "CLIMB",
                        "qty", 2, "unitPrice", 50000, "taxRate", 0.13,
                        "validUntil", LocalDate.now().plusDays(15).toString()));
        assertEquals(0, quotation.code());
        Long q2 = quotation.longData();
        post("/api/v1/crm/quotations/" + q2 + "/submit", null);

        Resp noReason = post("/api/v1/crm/quotations/" + q2 + "/decide", Map.of("won", false));
        assertEquals(1001, noReason.code(), "未填原因应拒绝");
        Resp lost = post("/api/v1/crm/quotations/" + q2 + "/decide",
                Map.of("won", false, "lostReason", "价格高于竞对 15%（demo）"));
        assertEquals(0, lost.code(), lost.body().toString());
    }

    @Test
    @Order(6)
    @DisplayName("FR-CRM-20：转化率按来源分组 + 枚举 E-43—E-52 种子存在")
    void conversionAndEnums() {
        loginAs("biz@demo");
        Resp stats = get("/api/v1/crm/quotations/conversion-by-source");
        assertEquals(0, stats.code(), stats.body().toString());
        assertTrue(String.valueOf(stats.body()).contains("MANUAL"), "来源分组含 MANUAL");

        // 枚举抽查：E-49 Agent 身份类型（经字典 by-code 接口）
        Resp dict = get("/api/v1/system/dicts/by-code?typeCode=agt_identity_type");
        Map<String, Object> items = dict.data();
        assertTrue(items.containsKey("EXPERT") && items.containsKey("WORKER") && items.containsKey("ASSISTANT"),
                "E-49 三身份（V4-00 §七术语）");
        Resp light = get("/api/v1/system/dicts/by-code?typeCode=kb_card_light");
        assertTrue(String.valueOf(light.body()).contains("BLOCKED"), "E-52 灯色");
    }
}
