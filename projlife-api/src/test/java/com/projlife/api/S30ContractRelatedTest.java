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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPRINT-PRJ1 E2E（审计 F9）：合同关联单据链 合同→项目→开票→收款计划（只读聚合）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S30ContractRelatedTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long contractId;

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
    @DisplayName("创建合同（biz@demo）")
    void createContract() {
        loginAs("biz@demo");
        Map<String, Object> contract = new HashMap<>();
        contract.put("contractName", "SPRINT-PRJ1 关联链演示合同");
        contract.put("nature", "CG");
        contract.put("partyA", "远景能源江苏有限公司（demo）");
        contract.put("partyB", "天立达（demo）");
        contract.put("counterpartyId", 6);
        contract.put("legalEntityId", 2);
        contract.put("businessType", "AZFWXF");
        contract.put("amount", 1000000);
        contract.put("taxRate", 0.13);
        contract.put("paymentTerms", "预付 30%，到货 40%，验收 30%");
        contract.put("contact", "王工");
        contract.put("phone", "13900000000");
        contract.put("signDate", LocalDate.now().toString());
        contract.put("effectiveDate", LocalDate.now().toString());
        contract.put("warrantyMonths", 24);
        contract.put("currency", "CNY");
        Resp created = post("/api/v1/contract", contract);
        assertEquals(0, created.code(), created.body().toString());
        contractId = Long.valueOf(String.valueOf((Object) created.data()));
    }

    @Test
    @Order(2)
    @DisplayName("F9：关联单据链返回三段（项目/开票/收款计划，空链为空数组不造假）")
    void relatedChain() {
        loginAs("biz@demo");
        Resp related = get("/api/v1/contract/" + contractId + "/related");
        assertEquals(0, related.code(), related.body().toString());
        Map<?, ?> data = (Map<?, ?>) related.data();
        assertNotNull(data);
        assertTrue(data.containsKey("projects"), "应含 projects 段");
        assertTrue(data.containsKey("invoices"), "应含 invoices 段");
        assertTrue(data.containsKey("paymentPlans"), "应含 paymentPlans 段");
        // 新合同无关联项目/发票 → 空数组（P5 禁假数据口径）
        assertTrue(((java.util.List<?>) data.get("projects")).isEmpty());
        assertTrue(((java.util.List<?>) data.get("invoices")).isEmpty());
    }
}
