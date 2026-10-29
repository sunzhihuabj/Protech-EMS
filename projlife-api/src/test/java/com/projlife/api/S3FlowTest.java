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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 E2E（16 册 S3 验收要点）：BOM 二维矩阵（C 类 MBOM 不适用）/物料校验/13 条校验发布拦截/
 * Golden 派生与差异/SBOM 转换/ECN BR-09/工艺发布 BR-PCS-02/覆盖度矩阵。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String currentToken;
    private static Long goldenBomId;
    private static Long mdProjectId;
    private static Long materialControlled;
    private static Long materialNormal;
    private static Long materialSeqTrap;

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

    private void login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "ProjLife@2026", "client", "web");
        currentToken = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

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

    private Resp delete(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(currentToken);
        return new Resp(rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class).getBody());
    }

    @Test
    @Order(1)
    @DisplayName("准备：R18 建物料（含序号陷阱码 12）+ 项目档案")
    void prepare() {
        login("mdm@demo");
        materialControlled = post("/api/v1/mdm/materials",
                Map.of("materialCode", "30000001", "materialName", "Bolt-M6-Demo", "unit", "PCS",
                        "category", "STD", "isControlled", 1)).longData();
        materialNormal = post("/api/v1/mdm/materials",
                Map.of("materialCode", "30000002", "materialName", "Cable-Demo", "unit", "M",
                        "category", "PURCHASE")).longData();
        Resp trapResp = post("/api/v1/mdm/materials",
                Map.of("materialCode", "12", "materialName", "Seq-Trap-Demo", "unit", "PCS", "category", "STD"));
        assertEquals(0, trapResp.code(), trapResp.body().toString());
        materialSeqTrap = trapResp.longData();
        assertNotEquals(null, materialControlled);

        Map<String, Object> md = new HashMap<>();
        md.put("customerId", 6);
        md.put("ownerId", 6);
        md.put("legalEntityId", 2);
        md.put("orderType", "OEM_INSTALL");
        md.put("farmId", 1);
        md.put("farmNameRaw", "DongLing-S3");
        md.put("phase", "P3");
        md.put("stdName", "Envision-DongLing-S3");
        md.put("shortName", "DLS3-UNIQUE");
        md.put("productLines", List.of("RAIL"));
        md.put("setQty", 5);
        md.put("engOwnerId", 4);
        md.put("bizOwnerId", 2);
        Resp mdCreated = post("/api/v1/mdm/projects", md);
        assertEquals(0, mdCreated.code(), mdCreated.body().toString());
        mdProjectId = mdCreated.longData();
        login("tech@demo");
    }

    @Test
    @Order(2)
    @DisplayName("FR-PLM-11/13/15：A 类 Golden EBOM 新建 + 加行（物料必选/单位受控）")
    void createGoldenBom() {
        Map<String, Object> bom = new HashMap<>();
        bom.put("bomForm", "A");
        bom.put("bomView", "EBOM");
        bom.put("productLine", "LADDER");
        bom.put("productModel", "TLDL-300");
        goldenBomId = post("/api/v1/plm/bom", bom).longData();
        Map<String, Object> bomInfo = get("/api/v1/plm/bom/" + goldenBomId).data();
        String bomNo = String.valueOf(bomInfo.get("bomNo"));
        assertTrue(bomNo.startsWith("BOM-AEBOM-TLDL-300-"), "BOM 编号格式，实际 " + bomNo);

        assertEquals(0, post("/api/v1/plm/bom/" + goldenBomId + "/lines",
                Map.of("lineNo", "1", "materialId", materialControlled, "qtyPerUnit", 4, "unit", "PCS")).code());
        assertEquals(0, post("/api/v1/plm/bom/" + goldenBomId + "/lines",
                Map.of("lineNo", "2", "materialId", materialNormal, "qtyPerUnit", 10, "unit", "M")).code());
        Resp badUnit = post("/api/v1/plm/bom/" + goldenBomId + "/lines",
                Map.of("lineNo", "3", "materialId", materialNormal, "qtyPerUnit", 1, "unit", "根"));
        assertEquals(1001, badUnit.code(), "单位必须 E-20 受控 8 种");
        Resp badQty = post("/api/v1/plm/bom/" + goldenBomId + "/lines",
                Map.of("lineNo", "3", "materialId", materialNormal, "qtyPerUnit", 0, "unit", "PCS"));
        assertEquals(1001, badQty.code(), "数量必须 >0");
    }

    @Test
    @Order(3)
    @DisplayName("06 册 §5.1 13 条校验：序号码(规则2) ERROR 拦截发布 → 清理后发布成功 is_current")
    void validateAndRelease() {
        Resp trapAdd = post("/api/v1/plm/bom/" + goldenBomId + "/lines",
                Map.of("lineNo", "3", "materialId", materialSeqTrap, "qtyPerUnit", 1, "unit", "PCS"));
        assertEquals(0, trapAdd.code(), trapAdd.body().toString());
        Long trapLine = trapAdd.longData();
        Resp issues = get("/api/v1/plm/bom/" + goldenBomId + "/validate");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = issues.data();
        assertTrue(rows.stream().anyMatch(r -> "2".equals(String.valueOf(r.get("rule")))
                && "ERROR".equals(r.get("level"))), "规则 2 编码疑似序号应 ERROR");
        assertTrue(rows.stream().anyMatch(r -> "13".equals(String.valueOf(r.get("rule")))
                && "INFO".equals(r.get("level"))), "规则 13 工艺未关联应 INFO");

        Resp blocked = post("/api/v1/plm/bom/" + goldenBomId + "/release", null);
        assertEquals(1001, blocked.code(), "ERROR 级问题必须拦截发布");

        assertEquals(0, delete("/api/v1/plm/bom/lines/" + trapLine).code());
        Resp released = post("/api/v1/plm/bom/" + goldenBomId + "/release", null);
        assertEquals(0, released.code(), released.body().toString());
        Map<String, Object> releasedBom = get("/api/v1/plm/bom/" + goldenBomId).data();
        assertEquals("RELEASED", releasedBom.get("status"));
        assertEquals(1, ((Number) releasedBom.get("isCurrent")).intValue());
    }

    @Test
    @Order(4)
    @DisplayName("BR-PLM-07：C 类 + MBOM → 422 显式不适用")
    void cFormMbomNotApplicable() {
        Resp resp = post("/api/v1/plm/bom",
                Map.of("bomForm", "C", "bomView", "MBOM", "productLine", "CCTV",
                        "productModel", "CCTV-DEMO", "mdProjectId", mdProjectId));
        assertEquals(5001, resp.code());
        assertEquals("SYS-BOM-NOT-APPLICABLE", resp.blockCode());
    }

    @Test
    @Order(5)
    @DisplayName("FR-PLM-18/19：Golden 派生项目 BOM（差异 0 处）")
    void deriveProjectBom() {
        Long derivedBomId = post("/api/v1/plm/bom/" + goldenBomId + "/derive?mdProjectId=" + mdProjectId, null).longData();
        Resp diff = get("/api/v1/plm/bom/diff?bomAId=" + goldenBomId + "&bomBId=" + derivedBomId);
        Map<String, Object> diffData = diff.data();
        assertEquals(0, ((List<?>) diffData.get("added")).size(), "派生后无新增");
        assertEquals(0, ((List<?>) diffData.get("removed")).size(), "派生后无删除");
        assertEquals(0, ((List<?>) diffData.get("qtyChanged")).size(), "派生后无改量");
    }

    @Test
    @Order(6)
    @DisplayName("FR-PLM-21：EBOM→SBOM 转换（只留受控件，A-17 口径）")
    void convertToSbom() {
        Long sbomId = post("/api/v1/plm/bom/" + goldenBomId + "/convert?targetView=SBOM", null).longData();
        List<Map<String, Object>> rows = get("/api/v1/plm/bom/" + sbomId + "/lines").data();
        assertEquals(1, rows.size(), "SBOM 只保留受控件（螺栓 controlled=1）");
        assertEquals("30000001", rows.get(0).get("materialCode"));
    }

    @Test
    @Order(7)
    @DisplayName("FR-PLM-28/30 + BR-09：ECN 无旧料处置被拒；补齐后发布 → V2 现行、旧版 OBSOLETE")
    void ecnFlow() {
        Resp rejected = post("/api/v1/plm/bom/" + goldenBomId + "/ecn",
                Map.of("changeType", "设计", "changeReason", "安全钳供应商切换", "impactConfirmed", 1));
        assertEquals(5001, rejected.code());
        assertEquals("BR-09", rejected.blockCode());

        Resp created = post("/api/v1/plm/bom/" + goldenBomId + "/ecn",
                Map.of("changeType", "替换料", "changeReason", "安全钳供应商切换（demo）",
                        "impactConfirmed", 1,
                        "oldDisposalPlan", "旧安全钳 12 台优先领用至备件库，其余退供应商（demo 说明）"));
        assertEquals(0, created.code(), created.body().toString());
        Long ecnId = created.longData();

        Resp published = post("/api/v1/plm/ecn/" + ecnId + "/publish", Map.of("approved", true));
        assertEquals(0, published.code(), published.body().toString());
        Map<String, Object> publishData = published.data();
        Long newBomId = Long.valueOf(String.valueOf(publishData.get("newBomId")));
        assertEquals("V2.0", publishData.get("newVersion"));
        Map<String, Object> newBomInfo = get("/api/v1/plm/bom/" + newBomId).data();
        assertEquals("RELEASED", newBomInfo.get("status"));
        Map<String, Object> oldBomInfo = get("/api/v1/plm/bom/" + goldenBomId).data();
        assertEquals("OBSOLETE", oldBomInfo.get("status"));
    }

    @Test
    @Order(8)
    @DisplayName("FR-PCS-01/05 + BR-PCS-02：工艺文件发布（BOM 未发布被拒/已发布放行）+ 覆盖度矩阵")
    void processDocAndCoverage() {
        // 关联 DRAFT 状态的项目 BOM → BR-PCS-02 拒绝
        Long draftBom = post("/api/v1/plm/bom/" + goldenBomId + "/derive?mdProjectId=" + mdProjectId, null).longData();
        Resp docCreated = post("/api/v1/plm/pcs/docs",
                Map.of("productLine", "LADDER", "docType", "作业指导书", "docName", "升降机装配作业指导书（demo）",
                        "bomHeaderId", draftBom));
        if (docCreated.body().get("code") == null) {
            throw new AssertionError("非标准响应: " + docCreated.body());
        }
        assertEquals(0, docCreated.code(), docCreated.body().toString());
        Long docId = docCreated.longData();
        Resp docsPage = get("/api/v1/plm/pcs/docs?productLine=LADDER&status=DRAFT");
        Map<String, Object> docsData = docsPage.data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> docRows = (List<Map<String, Object>>) docsData.get("records");

        String docNo = String.valueOf(docRows.get(0).get("docNo"));
        assertTrue(docNo.matches("TLD-GY101-\\d{3}"), "工艺文件编号格式 TLD-GY101-nnn，实际 " + docNo);
        Resp blocked = post("/api/v1/plm/pcs/docs/" + docId + "/publish", null);
        assertEquals(1001, blocked.code(), "BR-PCS-02：对应 BOM 未发布不得发布工艺");

        // 找 ECN 发布产生的 V2.0 现行 BOM → 关联后发布成功
        Resp bomPage = get("/api/v1/plm/bom?form=A&view=EBOM&productLine=LADDER");
        Map<String, Object> bomData = bomPage.data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bomRows = (List<Map<String, Object>>) bomData.get("records");

        Long v2Id = null;
        for (Map<String, Object> row : bomRows) {
            if ("V2.0".equals(row.get("version"))) {
                v2Id = Long.valueOf(String.valueOf(row.get("id")));
            }
        }
        assertTrue(v2Id != null, "V2 BOM 应存在");
        Long doc2 = post("/api/v1/plm/pcs/docs",
                Map.of("productLine", "LADDER", "docType", "作业指导书", "docName", "升降机装配指导书 V2（demo）",
                        "bomHeaderId", v2Id)).longData();
        assertEquals(0, post("/api/v1/plm/pcs/docs/" + doc2 + "/publish", null).code(), "BR-PCS-02 通过后放行");

        // 覆盖度矩阵（FR-PCS-11）
        Resp coverage = get("/api/v1/plm/pcs/coverage");
        assertTrue(String.valueOf(coverage.body()).contains("LADDER"));
        assertTrue(String.valueOf(coverage.body()).contains("verdict"), "判定字段（零覆盖/严重不足/偏低/尚可）");
    }
}
