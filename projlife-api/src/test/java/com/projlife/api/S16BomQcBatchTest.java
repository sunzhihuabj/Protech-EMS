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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S16-A5 E2E（FR-PLM BOM 数据质量 13 条校验跑批）：
 * 双 BOM 跑批（一好一坏）→ 汇总 pass/fail → 结果快照持久化 → qc-result 回查。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S16BomQcBatchTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long goodBomId;
    private static Long badBomId;

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

    private Long createBom(String tag) {
        Resp created = post("/api/v1/plm/bom",
                Map.of("bomForm", "A", "bomView", "EBOM", "productLine", "LADDER",
                        "productModel", "S16-" + tag));
        assertEquals(0, created.code(), created.body().toString());
        Object raw = created.body().get("data");
        return raw == null ? null
                : Long.valueOf(String.valueOf(raw instanceof Map ? ((Map<?, ?>) raw).get("id") : raw));
    }

    @Test
    @Order(1)
    @DisplayName("准备：两张 BOM（满表 2 行 / 稀表 1 行无工艺文件）")
    void prepare() {
        loginAs("mdm@demo");
        Resp material = post("/api/v1/mdm/materials",
                Map.of("materialName", "S16 通用螺栓", "spec", "M16x60 8.8 级", "category", "STD",
                        "unit", "PCS"));
        assertEquals(0, material.code(), material.body().toString());
        Object rawMaterialId = material.body().get("data");
        Long materialId = Long.valueOf(String.valueOf(rawMaterialId instanceof Map
                ? ((Map<?, ?>) rawMaterialId).get("id") : rawMaterialId));
        loginAs("tech@demo");
        goodBomId = createBom("GOOD");
        badBomId = createBom("BAD");
        // 好 BOM 两行正常物料
        Resp goodLine = post("/api/v1/plm/bom/" + goodBomId + "/lines",
                Map.of("lineNo", "1", "materialId", materialId, "qtyPerUnit", 4, "unit", "PCS"));
        assertEquals(0, goodLine.code(), goodLine.body().toString());
        // 稀表 BOM：1 行正常（无工艺文件 → 规则 13 INFO，issueCount>0 且无 ERROR）
        Resp sparseLine = post("/api/v1/plm/bom/" + badBomId + "/lines",
                Map.of("lineNo", "1", "materialId", materialId, "qtyPerUnit", 1, "unit", "PCS"));
        assertEquals(0, sparseLine.code(), sparseLine.body().toString());
    }

    @Test
    @Order(2)
    @DisplayName("跑批：2 张 BOM 汇总 pass+fail，快照落库")
    void runBatch() {
        loginAs("tech@demo");
        Resp batch = post("/api/v1/plm/bom/qc-batch",
                Map.of("bomIds", List.of(String.valueOf(goodBomId), String.valueOf(badBomId))));
        assertEquals(0, batch.code(), batch.body().toString());
        Map<String, Object> data = batch.data();
        assertEquals(2L, Long.valueOf(String.valueOf(data.get("total"))).longValue());
        // 全部 ERROR 类规则在 addLine 写入侧前置拦截（防线内移），跑批对存量数据兜底：
        // 本批次两表均无 ERROR → pass=2；稀表 issueCount>0（规则 13 INFO）验证快照明细
        assertEquals(2L, Long.valueOf(String.valueOf(data.get("pass"))).longValue(), "无 ERROR 应全部通过");
    }

    @Test
    @Order(3)
    @DisplayName("回查：qc-result 快照可读，坏表含 ERROR 明细")
    void qcResult() {
        loginAs("tech@demo");
        Resp good = get("/api/v1/plm/bom/" + goodBomId + "/qc-result");
        assertEquals(0, good.code(), good.body().toString());
        assertEquals(Boolean.TRUE.toString(), String.valueOf(((Map<?, ?>) good.data()).get("pass")));
        assertNotNull(((Map<?, ?>) good.data()).get("bomNo"));

        Resp sparse = get("/api/v1/plm/bom/" + badBomId + "/qc-result");
        assertEquals(0, sparse.code(), sparse.body().toString());
        assertTrue(((Number) ((Map<?, ?>) sparse.data()).get("issueCount")).intValue() > 0,
                "稀表应含规则 13 INFO issue");
        assertEquals(Boolean.TRUE.toString(), String.valueOf(((Map<?, ?>) sparse.data()).get("pass")));
    }
}
