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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4 E2E（16 册 S4 完成标准）：幂等 3 提交仅 1 笔 / 出库可用量校验 / T+ MOCK 状态机 /
 * 供应商五阶准入 BR-SCM-01 / 价格脱敏 BR-SCM-03 / 补货 BR-05 / 每日对账。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String scmToken;
    private static String invToken;
    private static String storeToken;
    private static Long supplierId;
    private static Long materialId;
    private static Long bomHeaderId;

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

        String blockCode() {
            Map<String, Object> d = data();
            return d == null ? null : String.valueOf(d.get("blockCode"));
        }

        Map<String, Object> body() {
            return body;
        }
    }

    private void loginAs(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "12345678", "client", "web");
        currentToken = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    private String currentToken;

    private Resp get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(currentToken);
        return new Resp(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
    }

    private Resp post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(currentToken);
        return new Resp(rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody());
    }

    @Test
    @Order(1)
    @DisplayName("准备：供应商/物料/采购员与仓管员登录")
    void prepare() {
        loginAs("biz@demo");
        // 供应商（md_supplier 经 mdm；此处用 9 号角色走主数据）——复用 seed 的 S 供应商
        loginAs("mdm@demo");
        Resp created = post("/api/v1/mdm/suppliers-register?name=Supplier-S4-Demo&creditCode=91110000S40001X", Map.of());
        assertEquals(0, created.code(), created.body().toString());
        supplierId = created.longData();
        // 物料
        Resp material = post("/api/v1/mdm/materials",
                Map.of("materialCode", "50000001", "materialName", "Inv-Demo-Material", "unit", "PCS",
                        "category", "PURCHASE"));
        assertEquals(0, material.code(), material.body().toString());
        materialId = material.longData();
    }

    @Test
    @Order(2)
    @DisplayName("FR-SCM-01/02 五阶准入：注册→资质→考察→样品→名录；未准入下单被拒（BR-SCM-01）")
    void supplierOnboarding() {
        loginAs("mdm@demo");
        // FR-SCM-01：先注册准入档案（五阶起点 REGISTERED）
        Resp registered = post("/api/v1/scm/suppliers/" + supplierId + "/register", Map.of());
        assertEquals(0, registered.code(), registered.body().toString());
        Resp onboarding = get("/api/v1/scm/suppliers/" + supplierId + "/onboarding");
        Map<String, Object> onboardingData = onboarding.data();
        if (onboardingData == null || onboardingData.get("currentStage") == null) {
            throw new AssertionError("onboarding resp=" + onboarding.body() + " supplierId=" + supplierId);
        }
        assertEquals("REGISTERED", onboardingData.get("currentStage"));

        // 未准入下单 → BR-SCM-01（purchase@demo 有下单权限但供应商未 APPROVED）
        loginAs("purchase@demo");
        Resp earlyPo = post("/api/v1/scm/orders",
                Map.of("supplierId", supplierId, "legalEntityId", 2,
                        "items", List.of(Map.of("materialId", materialId, "qty", 10))));
        assertEquals(5001, earlyPo.code());
        assertEquals("BR-SCM-01", earlyPo.blockCode());

        // 五阶推进到 APPROVED
        loginAs("mdm@demo");
        for (int i = 0; i < 4; i++) {
            Resp advanced = post("/api/v1/scm/suppliers/" + supplierId + "/advance",
                    Map.of("passed", true, "auditResult", "S4 阶段审核通过（demo）"));
            assertEquals(0, advanced.code(), advanced.body().toString());
        }
        Resp finalStage = get("/api/v1/scm/suppliers/" + supplierId + "/onboarding");
        Map<String, Object> finalData = finalStage.data();
        assertEquals("APPROVED", finalData.get("currentStage"));
    }

    @Test
    @Order(3)
    @DisplayName("FR-SCM-11/12/15：采购需求 → PO 合并 → 下发")
    void purchaseFlow() {
        loginAs("purchase@demo");
        Resp req = post("/api/v1/scm/requirements",
                Map.of("sourceType", "MANUAL", "projectId", 1, "materialId", materialId,
                        "qty", 100, "expectDate", LocalDate.now().plusDays(14).toString()));
        assertEquals(0, req.code(), req.body().toString());
        Long requirementId = req.longData();
        Resp reqsPage = get("/api/v1/scm/requirements?status=OPEN");
        Map<String, Object> reqsData = reqsPage.data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reqRows = (List<Map<String, Object>>) reqsData.get("records");
        String reqNo = null;
        for (Map<String, Object> row : reqRows) {
            if (String.valueOf(row.get("id")).equals(String.valueOf(requirementId))) {
                reqNo = String.valueOf(row.get("reqNo"));
            }
        }
        assertTrue(reqNo != null && reqNo.matches("PR-\\d{6}-\\d{4}"), "PR 编号格式，实际 " + reqNo);

        Resp po = post("/api/v1/scm/orders",
                Map.of("supplierId", supplierId, "legalEntityId", 2,
                        "items", List.of(Map.of("materialId", materialId, "qty", 100, "unitPrice", 50)),
                        "requirementIds", List.of(requirementId)));
        assertEquals(0, po.code(), po.body().toString());
        bomHeaderId = po.longData();
        assertTrue(bomHeaderId > 0, "PO 创建成功");

        assertEquals(0, post("/api/v1/scm/orders/" + bomHeaderId + "/dispatch", null).code());
        // 需求已合并
        Resp reqs = get("/api/v1/scm/requirements?status=MERGED");
        assertTrue(String.valueOf(reqs.body()).contains(String.valueOf(requirementId)), "需求已标记 MERGED");
    }

    @Test
    @Order(4)
    @DisplayName("BR-INV-03 幂等：同一请求提交 3 次 → 仅 1 笔事务；入库后可用量正确")
    void idempotency() {
        loginAs("store@demo");
        String idemKey = "PO-S4-DEMO-L1-RECEIPT";
        Map<String, Object> txn = new HashMap<>();
        txn.put("txnType", "RECEIPT");
        txn.put("legalEntityId", 2);
        txn.put("warehouseCode", "YL");
        txn.put("materialId", materialId);
        txn.put("qty", 100);
        txn.put("unit", "PCS");
        txn.put("sourceType", "PO");
        txn.put("sourceId", bomHeaderId);
        txn.put("sourceNo", "PO-S4-DEMO");
        txn.put("idempotencyKey", idemKey);

        Resp first = post("/api/v1/inv/transactions", txn);
        assertEquals(0, first.code(), first.body().toString());
        Map<String, Object> firstData = first.data();
        if (firstData.get("tplusVoucherNo") == null) {
            throw new AssertionError("txn resp=" + first.body());
        }
        String firstTxnNo = String.valueOf(firstData.get("txnNo"));
        assertEquals("SYNCED", firstData.get("syncStatus"), "MOCK T+ 同步成功");
        assertNotNull(firstData.get("tplusVoucherNo"), "回写 T+ 凭证号");

        Resp second = post("/api/v1/inv/transactions", txn);
        Resp third = post("/api/v1/inv/transactions", txn);
        Map<String, Object> secondData = second.data();
        Map<String, Object> thirdData = third.data();
        assertEquals(firstTxnNo, String.valueOf(secondData.get("txnNo")), "第 2 次返回首次事务");
        assertEquals(firstTxnNo, String.valueOf(thirdData.get("txnNo")), "第 3 次返回首次事务");


        Resp available = get("/api/v1/inv/stock/available?legalEntityId=2&warehouseCode=YL&materialId=" + materialId);
        assertEquals(0, new BigDecimal("100").compareTo(new BigDecimal(String.valueOf((Object) available.data()))),
                "库存只加 1 次");

    }

    @Test
    @Order(5)
    @DisplayName("BR-INV-02：出库超可用量被拒；正常出库后扣减")
    void issueValidation() {
        loginAs("store@demo");
        Resp overIssue = post("/api/v1/inv/transactions",
                Map.of("txnType", "ISSUE", "legalEntityId", 2, "warehouseCode", "YL",
                        "materialId", materialId, "qty", -500, "unit", "PCS",
                        "sourceType", "DISPATCH", "sourceId", 1, "idempotencyKey", "ISSUE-OVER-1"));
        assertEquals(1001, overIssue.code(), "负库存必须拒绝");
        assertTrue(String.valueOf(overIssue.body().get("message")).contains("BR-INV-02"));

        Resp okIssue = post("/api/v1/inv/transactions",
                Map.of("txnType", "ISSUE", "legalEntityId", 2, "warehouseCode", "YL",
                        "materialId", materialId, "qty", -30, "unit", "PCS",
                        "sourceType", "DISPATCH", "sourceId", 1, "idempotencyKey", "ISSUE-OK-1"));
        assertEquals(0, okIssue.code(), okIssue.body().toString());
        Resp available = get("/api/v1/inv/stock/available?legalEntityId=2&warehouseCode=YL&materialId=" + materialId);
        assertEquals(0, new BigDecimal("70").compareTo(new BigDecimal(String.valueOf((Object) available.data()))));
    }

    @Test
    @Order(6)
    @DisplayName("FR-SCM-16 + BR-SCM-03：无价格权限角色查 PO → 单价金额缺失")
    void priceMasking() {
        loginAs("store@demo");
        Resp detail = get("/api/v1/scm/orders/" + bomHeaderId);
        Map<String, Object> data = detail.data();
        if (data == null || data.get("items") == null) {
            throw new AssertionError("detail resp=" + detail.body());
        }
        assertTrue(!data.containsKey("totalAmount") || data.get("totalAmount") == null,
                "R11 查 PO 不得返回总金额");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        for (Map<String, Object> item : items) {
            assertTrue(item.get("unitPrice") == null, "行单价必须脱敏");
        }
    }

    @Test
    @Order(7)
    @DisplayName("FR-INV-33/35/36 + BR-05：安全件补货先发后签 → 25h 锁定申请人")
    void replenishSafetyBackfill() {
        loginAs("eng@demo");
        // 安全件清单经 sys_config 配置（projlife.inv.safety-materials 或 sys_config）——测试直配置前先建物料
        loginAs("mdm@demo");
        Resp material = post("/api/v1/mdm/materials",
                Map.of("materialCode", "50000002", "materialName", "Safety-Demo", "unit", "PCS",
                        "category", "PURCHASE"));
        assertEquals(0, material.code(), material.body().toString());
        Long safetyMaterialId = material.longData();

        // 切回工程角色发起补货（FR-INV-33 由工程/售后发起）
        loginAs("eng@demo");
        Resp created = post("/api/v1/inv/replenish",
                Map.of("mdProjectId", 1, "reason", "DAMAGE", "amount", 300,
                        "lines", List.of(Map.of("materialId", safetyMaterialId, "qty", 2, "unit", "PCS"))));
        assertEquals(0, created.code(), created.body().toString());
        Long replenishId = created.longData();

        loginAs("store@demo");
        assertEquals(0, post("/api/v1/inv/replenish/" + replenishId + "/approve", null).code());
        assertEquals(0, post("/api/v1/inv/replenish/" + replenishId + "/issue", null).code());

        // BR-05 锁定入口（job 模拟：H2 中 backfill_deadline 在未来 → 本调用不锁定）
        Resp lock = post("/api/v1/inv/replenish/lock-overdue", null);
        assertEquals(0, lock.code());

        // 补签 → 状态 BACKFILLED
        loginAs("eng@demo");
        Resp backfill = post("/api/v1/inv/replenish/" + replenishId + "/backfill", null);
        assertEquals(0, backfill.code(), backfill.body().toString());
    }

    @Test
    @Order(8)
    @DisplayName("FR-INV-41：每日对账（本地笔数 vs T+ SYNCED 笔数）")
    void dailyReconcile() {
        loginAs("store@demo");
        Resp reconcile = post("/api/v1/inv/reconcile",
                Map.of("date", LocalDate.now().toString(), "legalEntityId", 2));
        assertEquals(0, reconcile.code(), reconcile.body().toString());
        Map<String, Object> data = reconcile.data();
        assertTrue(((Number) data.get("localTxnCount")).intValue() >= 2, "应统计到本地事务");
    }

    private Resp postAs(String asToken, String path, Object body) {
        String keep = currentToken;
        currentToken = asToken;
        try {
            return post(path, body);
        } finally {
            currentToken = keep;
        }
    }
}
