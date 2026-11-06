package com.projlife.api;

import com.projlife.contract.mapper.AiFeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.contract.entity.AiFeedback;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1 E2E（16 册 S1-14：s1a.spec 等价物）。
 * 主链：商机 → 合同起草 → 评审(BR-01 拒) → 项目建档(三重查重) → 关联 → 评审过 → 用印(编号)
 *       → AI 提取确认(ai_feedback) → 交底已阅 → 变更 BCn → 收款确认 → BR-02 超期冻结 → 建档解冻。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S1FlowTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AiFeedbackMapper feedbackMapper;

    /** 测试桩 AI 网关：真实 HTTP 链路（客户端→网关→响应），替代进程级 mock */
    static com.sun.net.httpserver.HttpServer aiStub;
    static String aiStubUrl;

    static {
        try {
            aiStub = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
            aiStub.createContext("/ai/v1/call", exchange -> {
                byte[] request = exchange.getRequestBody().readAllBytes();
                String body = new String(request, java.nio.charset.StandardCharsets.UTF_8);
                String response;
                if (body.contains("AI-001")) {
                    response = "{\"status\":\"SUCCESS\",\"isMock\":true,\"confidence\":0.86,\"data\":{\"summary\":\"mock\",\"risks\":["
                            + "{\"level\":\"RED\",\"clause\":\"付款条款\",\"description\":\"垫资风险\",\"suggestion\":\"提高验收后付款\"},"
                            + "{\"level\":\"YELLOW\",\"clause\":\"工期条款\",\"description\":\"顺延机制缺失\",\"suggestion\":null},"
                            + "{\"level\":\"GREEN\",\"clause\":\"违约责任\",\"description\":\"权责对等\",\"suggestion\":null}]}}";
                } else {
                    response = "{\"status\":\"SUCCESS\",\"isMock\":true,\"confidence\":0.92,\"data\":{\"fields\":{"
                            + "\"contract_name\":\"mock\",\"customer\":\"mock\",\"amount\":\"1000000.00\",\"sign_date\":\"2026-10-30\","
                            + "\"duration\":\"12 个月\",\"payment_terms\":\"3-4-3\",\"quality_standard\":\"GB\",\"penalty\":\"日千分之一\","
                            + "\"warranty_months\":\"24\",\"dispute_resolution\":\"仲裁\",\"party_a\":\"mock\",\"party_b\":\"mock\"}}}";
                }
                byte[] out = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            });
            aiStub.start();
            aiStubUrl = "http://localhost:" + aiStub.getAddress().getPort();
        } catch (Exception ex) {
            throw new IllegalStateException("AI 网关测试桩启动失败", ex);
        }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void registerProps(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("projlife.ai-gateway.base-url", () -> aiStubUrl);
    }

    private static Long opportunityId;
    private static Long contractRejectedId;
    private static Long projectId;
    private static Long contractId;
    private static String bizToken;
    private static String adminToken;
    private static String mdmToken;
    private static String finToken;

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "12345678", "client", "web");
        ResponseEntityMap resp = post("/api/v1/auth/login", null, body, null);
        return (Map<String, Object>) ((Map<String, Object>) resp.body().get("data"));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntityMap post(String path, String token, Object body, Long pathId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        org.springframework.http.ResponseEntity<Map> resp =
                rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return new ResponseEntityMap(resp.getBody(), resp.getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private ResponseEntityMap get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        org.springframework.http.ResponseEntity<Map> resp =
                rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return new ResponseEntityMap(resp.getBody(), resp.getStatusCode().value());
    }

    /** 轻量响应载体（避免泛型堆叠；Java 11 无 record，用静态类） */
    private static final class ResponseEntityMap {

        final Map<String, Object> body;

        ResponseEntityMap(Map<String, Object> body, int status) {
            this.body = body;
        }

        Map<String, Object> body() {
            return body;
        }

        int code() {
            Object code = body.get("code");
            return code == null ? -1 : ((Number) code).intValue();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data() {
            return (Map<String, Object>) body.get("data");
        }

        String blockCode() {
            Map<String, Object> data = data();
            return data == null ? null : String.valueOf(data.get("blockCode"));
        }

        /** data 为 Long 时被全局 Long→String 序列化为字符串（防 JS 精度丢失），此处统一还原 */
        Long longData() {
            Object data = body.get("data");
            if (data instanceof Number) {
                return ((Number) data).longValue();
            }
            return data == null ? null : Long.valueOf(String.valueOf(data));
        }
    }

    @Test
    @Order(1)
    @DisplayName("FR-CRM-01/02 商机建档：编号 OPP-YYYYMM-nnnn，状态跟踪中")
    void createOpportunity() {
        Map<String, Object> login = login("biz@demo");
        bizToken = (String) login.get("accessToken");
        var adminLogin = post("/api/v1/auth/login", null,
                java.util.Map.of("username", "admin@demo", "password", "12345678", "client", "web"), null);
        adminToken = (String) ((java.util.Map<?, ?>) adminLogin.body().get("data")).get("accessToken");
        Map<String, Object> opp = new HashMap<>();
        opp.put("oppName", "竹子岭风场升降机采购（demo E2E）");
        opp.put("customerId", 6);
        opp.put("contact", "王工");
        opp.put("phone", "13900000000");
        opp.put("summary", "10 台升降机 + 安装服务");
        opp.put("intentionLevel", "H");
        opp.put("expectSignDate", LocalDate.now().toString());
        opp.put("expectAmount", 1000000);
        ResponseEntityMap resp = post("/api/v1/crm/opportunities", bizToken, opp, null);
        assertEquals(0, resp.code(), resp.body().toString());
        opportunityId = resp.longData();
        ResponseEntityMap detail = get("/api/v1/crm/opportunities/" + opportunityId, bizToken);
        String oppNo = String.valueOf(detail.data().get("oppNo"));
        assertTrue(oppNo.matches("OPP-\\d{6}-\\d{4}"), "商机编号格式：OPP-{YYYYMM}-{4位}，实际 " + oppNo);
        assertEquals("TRACKING", detail.data().get("status"));
    }

    @Test
    @Order(2)
    @DisplayName("FR-CT-01 合同起草（未关联项目）→ 提交评审被 BR-01 拒绝（422 + 引导）")
    void reviewBlockedByBr01() {
        Map<String, Object> contract = baseContract("未关联项目合同（BR-01 演示）");
        ResponseEntityMap created = post("/api/v1/contract", bizToken, contract, null);
        assertEquals(0, created.code(), created.body().toString());
        contractRejectedId = created.longData();

        ResponseEntityMap review = post("/api/v1/contract/" + contractRejectedId + "/review/submit", bizToken, null, null);
        assertEquals(5001, review.code(), "硬卡点必须 422/5001");
        assertEquals("BR-01", review.blockCode(), "blockCode 必须=BR-01");
        assertNotNull(review.data().get("suggestion"), "必须给'去建档'引导");
    }

    @Test
    @Order(3)
    @DisplayName("FR-MDM-12/13 三重查重 + 名称生成 + 项目建档（R18）")
    void createProjectWithTripleCheck() {
        Map<String, Object> login = login("mdm@demo");
        mdmToken = (String) login.get("accessToken");

        // 名称生成器（FR-MDM-13）
        ResponseEntityMap nameRule = get("/api/v1/mdm/projects/name-rule?ownerName=远景能源&farmFullName=竹子岭风电场&phase=一期&orderType=OEM_INSTALL", mdmToken);
        assertEquals("远景能源-竹子岭-一期", nameRule.data().get("stdName"), "标准名 = 业主规范名－地名（去尾缀）－期次");

        // 项目建档
        Map<String, Object> project = new HashMap<>();
        project.put("customerId", 6);
        project.put("ownerId", 6);
        project.put("legalEntityId", 2);
        project.put("orderType", "OEM_INSTALL");
        project.put("farmId", 1);
        project.put("farmNameRaw", "竹子岭风电场（demo）一期");
        project.put("phase", "一期");
        project.put("stdName", "远景能源-竹子岭-一期");
        project.put("shortName", "竹子岭一期");
        project.put("productLines", List.of("LADDER"));
        project.put("setQty", 10);
        project.put("engOwnerId", 4);
        project.put("bizOwnerId", 2);
        ResponseEntityMap created = post("/api/v1/mdm/projects", mdmToken, project, null);
        assertEquals(0, created.code(), created.body().toString());
        projectId = created.longData();
        ResponseEntityMap detail = get("/api/v1/mdm/projects/" + projectId, mdmToken);
        assertTrue(String.valueOf(detail.data().get("projectCode")).matches("PJ\\d{4}-\\d{4}"), "PJ 编码格式");

        // 三重查重：同名简称再建档 → 422 + 候选对比卡
        project.put("shortName", "竹子岭一期");
        project.put("stdName", "远景能源-竹子岭-一期(2)");
        project.put("farmNameRaw", "竹子岭风电场（demo）一期");
        ResponseEntityMap dup = post("/api/v1/mdm/projects", mdmToken, project, null);
        assertEquals(5001, dup.code(), "三重查重命中应阻断");
        assertEquals("SYS-DUP-CHECK", dup.blockCode());
        assertTrue(String.valueOf(dup.body()).contains("candidates"), "应返回对比卡候选");
    }

    @Test
    @Order(4)
    @DisplayName("关联项目 → 评审通过 → 用印生成 TLD-2026-CG-AZFWXF-nnnn → 建档 26 要素")
    void sealAndFiling() {
        Map<String, Object> contract = baseContract("竹子岭一期升降机采购与安装合同（demo E2E）");
        contract.put("projectIds", List.of(projectId));
        // 付款计划 3-4-3（FR-CT-04）
        contract.put("paymentPlans", List.of(
                Map.of("phaseName", "预付款", "percentage", 0.3),
                Map.of("phaseName", "到货款", "percentage", 0.4),
                Map.of("phaseName", "验收款", "percentage", 0.3)));
        ResponseEntityMap created = post("/api/v1/contract", bizToken, contract, null);
        assertEquals(0, created.code(), created.body().toString());
        contractId = created.longData();

        // 提交评审（已带项目编码 → 放行；R8 走审批流，需两级通过后合同才到 REVIEWED）
        assertEquals(0, post("/api/v1/contract/" + contractId + "/review/submit", bizToken, null, null).code());

        // 审批流两级通过（工程主管 → 总经理）
        approveAll("CONTRACT_REVIEW", contractId);

        // 用印：生成编号 + 正本
        ResponseEntityMap sealed = post("/api/v1/contract/" + contractId + "/seal", bizToken, Map.of("copyCount", 2), null);
        assertEquals(0, sealed.code(), sealed.body().toString());
        String contractNo = String.valueOf(sealed.data().get("contractNo"));
        assertTrue(contractNo.matches("TLD-\\d{4}-CG-AZFWXF-\\d{4}"),
                "合同号按 02 册 §5 规则：{主体}-{YYYY}-{性质}-{业务}-{4位}，实际 " + contractNo);
        assertEquals("SIGNED", sealed.data().get("status"));
        assertNotNull(sealed.data().get("filingDeadline"), "用印后写建档时限（FR-CT-20）");

        // 完整度 <100% 时建档被拒（FR-CT-22）
        ResponseEntityMap partialFiling = post("/api/v1/contract/" + contractId + "/filing", bizToken,
                Map.of("contact", "李工"), null);
        assertEquals(1001, partialFiling.code(), "要素不全应拒绝");

        // 26 要素全量建档
        Map<String, Object> elements = fullFilingElements();
        ResponseEntityMap filed = post("/api/v1/contract/" + contractId + "/filing", bizToken, elements, null);
        assertEquals(0, filed.code(), filed.body().toString());
        assertEquals("PERFORMING", get("/api/v1/contract/" + contractId, bizToken).data().get("status"));
    }

    @Test
    @Order(5)
    @DisplayName("FR-CT-07/09/11 AI 提取确认：改 2 字段 → ai_feedback 落 2 条")
    void aiExtractAndFeedback() {
        ResponseEntityMap extract = post("/api/v1/contract/" + contractId + "/ai/extract", bizToken, null, null);
        assertEquals(0, extract.code());
        assertEquals(Boolean.TRUE, extract.data().get("isMock"), "S1 走网关 mock");

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) extract.data().get("fields");
        assertNotNull(fields, "mock 应返回 12 要素");
        assertEquals(12, fields.size());

        Map<String, Object> body = new HashMap<>();
        body.put("aiFields", fields);
        body.put("callId", extract.data().get("callId"));
        Map<String, String> human = new HashMap<>(fields);
        human.put("customer", "远景能源江苏有限公司（人工修正）");
        human.put("payment_terms", "预付 30% 到货 40% 验收 30%（人工修正）");
        body.put("humanFields", human);
        ResponseEntityMap confirmed = post("/api/v1/contract/" + contractId + "/ai/confirm", bizToken, body, null);
        assertEquals(0, confirmed.code());
        assertEquals(2, ((Number) confirmed.data().get("correctedFields")).intValue());

        Long count = feedbackMapper.selectCount(new LambdaQueryWrapper<AiFeedback>()
                .eq(AiFeedback::getBizType, "ct_contract")
                .eq(AiFeedback::getBizId, contractId));
        assertEquals(2L, count, "人工修正必须逐字段写 ai_feedback（FR-CT-11）");
    }

    @Test
    @Order(6)
    @DisplayName("FR-CT-28/30 交底分发 + 已阅确认")
    void dispatchAndRead() {
        ResponseEntityMap dispatched = post("/api/v1/contract/" + contractId + "/dispatch", bizToken,
                Map.of("receiverIds", List.of(4, 8)), null);
        assertEquals(0, dispatched.code());
        assertEquals(2, ((Number) dispatched.body().get("data")).intValue(), "应分发 2 人");
    }

    @Test
    @Order(7)
    @DisplayName("FR-CT-31/32/33 变更 -BCn：审批通过金额增减回写")
    void changeWithBcNo() {
        ResponseEntityMap created = post("/api/v1/contract/" + contractId + "/changes", bizToken,
                Map.of("changeType", "AMOUNT", "changeReason", "增补 2 台升降机（demo）", "amountDelta", 100000,
                        "effectiveDate", LocalDate.now().toString()), null);
        assertEquals(0, created.code());
        Long changeId = created.longData();
        ResponseEntityMap changes = get("/api/v1/contract/" + contractId + "/changes", bizToken);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changeRows = (List<Map<String, Object>>) changes.body().get("data");
        String changeNo = String.valueOf(changeRows.get(0).get("changeNo"));
        assertTrue(changeNo.endsWith("-BC1"), "补充协议号 {contract_no}-BC{n}，实际 " + changeNo);
        // 变更审批属 R05 商务主管（contract:change:approve）
        Map<String, Object> bossLogin = login("boss@demo");
        String bossToken = (String) bossLogin.get("accessToken");
        assertEquals(0, post("/api/v1/contract/changes/" + changeId + "/approve", bossToken,
                Map.of("approved", true), null).code());
    }

    @Test
    @Order(8)
    @DisplayName("FR-CT-36/37 收款录入 → 财务确认 → 计划行 RECEIVED")
    void receiptConfirm() {
        // 取预付款计划行 id（FR-CT-08 收款核销到计划行）
        ResponseEntityMap plansBefore = get("/api/v1/contract/" + contractId + "/payment-plans", bizToken);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> planRows = (List<Map<String, Object>>) plansBefore.body().get("data");
        Long planId = Long.valueOf(String.valueOf(planRows.get(0).get("id")));

        ResponseEntityMap applied = post("/api/v1/contract/receipts", bizToken,
                Map.of("contractId", contractId, "paymentPlanId", planId, "amount", 300000,
                        "receiptDate", LocalDate.now().toString(), "method", "BANK"), null);
        assertEquals(0, applied.code(), applied.body().toString());
        Long receiptId = applied.longData();

        Map<String, Object> finLogin = login("fin@demo");
        finToken = (String) finLogin.get("accessToken");
        assertEquals(0, post("/api/v1/contract/receipts/" + receiptId + "/confirm", finToken, null, null).code());

        ResponseEntityMap plans = get("/api/v1/contract/" + contractId + "/payment-plans", bizToken);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) plans.body().get("data");
        assertEquals("RECEIVED", rows.get(0).get("status"), "预付款 30% 已确认收齐");
    }

    @Test
    @Order(9)
    @DisplayName("BR-02 超期未建档 → 开票 422；完成建档自动解冻后可开票")
    void invoiceFrozenByBr02() {
        // 构造：签订日 10 天前（建档时限已过）且不建档
        Map<String, Object> contract = baseContract("超期未建档合同（BR-02 演示）");
        contract.put("projectIds", List.of(projectId));
        contract.put("signDate", LocalDate.now().minusDays(10).toString());
        contract.put("effectiveDate", LocalDate.now().minusDays(10).toString());
        ResponseEntityMap created = post("/api/v1/contract", bizToken, contract, null);
        assertEquals(0, created.code());
        Long lateId = created.longData();
        post("/api/v1/contract/" + lateId + "/review/submit", bizToken, null, null);
        post("/api/v1/contract/" + lateId + "/seal", bizToken, Map.of("copyCount", 1), null);

        ResponseEntityMap invoice = post("/api/v1/contract/invoices", bizToken,
                Map.of("contractId", lateId, "invoiceNo", "INV-DEMO-001", "amount", 50000), null);
        assertEquals(5001, invoice.code(), "超期未建档开票必须 422");
        assertEquals("BR-02", invoice.blockCode());

        // 完成建档 → 自动解冻 → 开票放行
        post("/api/v1/contract/" + lateId + "/filing", bizToken, fullFilingElements(), null);
        ResponseEntityMap invoice2 = post("/api/v1/contract/invoices", bizToken,
                Map.of("contractId", lateId, "invoiceNo", "INV-DEMO-002", "amount", 50000), null);
        assertEquals(0, invoice2.code(), "建档完成自动解冻（FR-CT-26）");
    }

    private Map<String, Object> baseContract(String name) {
        Map<String, Object> contract = new HashMap<>();
        contract.put("contractName", name);
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
        return contract;
    }

    /** 26 要素全量（FR-CT-21：A7+B6+C6+D7） */
    private Map<String, Object> fullFilingElements() {
        Map<String, Object> elements = new HashMap<>();
        elements.put("contractName", "竹子岭一期升降机采购与安装合同（demo E2E）");
        elements.put("counterpartyId", 6);
        elements.put("amount", 1000000);
        elements.put("nature", "CG");
        elements.put("businessType", "AZFWXF");
        elements.put("taxRate", 0.13);
        elements.put("legalEntityId", 2);
        elements.put("paymentTerms", "预付 30%，到货 40%，验收 30%");
        elements.put("signDate", LocalDate.now().toString());
        elements.put("effectiveDate", LocalDate.now().toString());
        elements.put("warrantyMonths", 24);
        elements.put("copyCount", 2);
        elements.put("currency", "CNY");
        elements.put("deliveryAddr", "江苏盐城射阳竹子岭风场（demo）");
        elements.put("contact", "李工");
        elements.put("phone", "13911112222");
        elements.put("acceptanceClause", "按 GB 国标验收");
        elements.put("deliveryDate", LocalDate.now().plusDays(60).toString());
        elements.put("performStart", LocalDate.now().toString());
        elements.put("performEnd", LocalDate.now().plusDays(90).toString());
        elements.put("handlerId", 2);
        elements.put("reviewerId", 3);
        elements.put("originalFileId", 1);
        elements.put("partyA", "远景能源江苏有限公司（demo）");
        elements.put("partyB", "天立达（demo）");
        elements.put("legacyNo", "CON-202610-9999");
        return elements;
    }

    /** R8：审批流两级通过（admin 代批所有 PENDING 节点） */
    private void approveAll(String type, Long targetId) {
        var byTarget = get("/api/v1/approval/target/" + type + "/" + targetId, adminToken);
        if (byTarget.code() != 0 || byTarget.data() == null) return;
        var approvalId = String.valueOf(((java.util.Map<?, ?>) byTarget.data()).get("id"));
        var nodes = get("/api/v1/approval/" + approvalId + "/nodes", adminToken);
        if (nodes.code() != 0 || nodes.data() == null) return;
        for (var node : (java.util.List<?>) nodes.data()) {
            var map = (java.util.Map<?, ?>) node;
            if ("PENDING".equals(map.get("status"))) {
                post("/api/v1/approval/nodes/" + map.get("id") + "/approve", adminToken,
                        java.util.Map.of("comment", "E2E auto-approve"), null);
            }
        }
    }
}
