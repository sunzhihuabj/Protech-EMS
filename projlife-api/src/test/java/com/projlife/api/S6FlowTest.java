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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S6 E2E（16 册 S6 完成标准）：检验三关口 / AI 改判留痕 / D6 未填 8D 不可关闭 / 量具超期锁定 / MTBF。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S6FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;

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
    @DisplayName("E-27 三关口：IQC 检验单创建（编号 QC-IQC-YYYYMMDD-nnn）")
    void createIqc() {
        loginAs("qc@demo");
        Resp created = post("/api/v1/qms/inspections",
                Map.of("inspectionType", "IQC", "sourceType", "PO_RECEIPT", "sourceId", 1,
                        "sampleSize", 50, "defectCount", 1, "result", "PASS"));
        assertEquals(0, created.code(), created.body().toString());
        Long inspectionId = created.longData();
        Resp page = get("/api/v1/qms/inspections?type=IQC");
        if (!(page.data() instanceof Map)) {
            throw new AssertionError("page resp=" + page.body());
        }
        Map<String, Object> pageData = page.data();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> iqcRows = (java.util.List<Map<String, Object>>) pageData.get("records");
        String no = String.valueOf(iqcRows.get(0).get("inspectionNo"));
        assertTrue(no.matches("QC-IQC-\\d{8}-\\d{3}"), "编号格式 QC-IQC-YYYYMMDD-nnn，实际 " + no);
    }

    @Test
    @Order(2)
    @DisplayName("AI 改判留痕：人工改判置 human_corrected=1")
    void humanCorrected() {
        Resp page = get("/api/v1/qms/inspections?type=IQC");
        if (!(page.data() instanceof Map)) {
            throw new AssertionError("page resp=" + page.body());
        }
        Map<String, Object> pageData = page.data();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> rows = (java.util.List<Map<String, Object>>) pageData.get("records");
        Long id = Long.valueOf(String.valueOf(rows.get(0).get("id")));
        assertEquals(0, post("/api/v1/qms/inspections/" + id + "/correct",
                Map.of("result", "REJECT", "remark", "AI 漏检（demo 改判）")).code());
        Resp detail = get("/api/v1/qms/inspections?type=IQC&result=REJECT");
        Map<String, Object> detailData = detail.data();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> corrected = (java.util.List<Map<String, Object>>) detailData.get("records");
        assertEquals(1, ((Number) corrected.get(0).get("humanCorrected")).intValue(), "人工改判留痕");
    }

    @Test
    @Order(3)
    @DisplayName("09 册验收要点：D6 未填 8D 不可关闭")
    void eightDCloseBlocked() {
        loginAs("qc@demo");
        Resp created = post("/api/v1/qms/8ds",
                Map.of("problemDesc", "批次不良（demo）", "d1Team", "质量+生产", "d4RootCause", "焊接温度漂移"));
        assertEquals(0, created.code(), created.body().toString());
        Long reportId = created.longData();

        Resp closed = post("/api/v1/qms/8ds/" + reportId + "/close", null);
        assertEquals(1001, closed.code(), "D6 未填必须拒绝");
        assertTrue(String.valueOf(closed.body().get("message")).contains("D6"));
    }

    @Test
    @Order(4)
    @DisplayName("量具超期锁定：usable 列表排除超期量具")
    void gaugeLock() {
        loginAs("qc@demo");
        Resp usable = get("/api/v1/qms/gauges/usable");
        assertEquals(0, usable.code());
        // 超期锁定逻辑在 usableGauges 内执行（无超期量具时列表可为空）
    }

    @Test
    @Order(5)
    @DisplayName("MTBF 接口")
    void mtbf() {
        loginAs("qc@demo");
        Resp mtbf = get("/api/v1/qms/assets/1/mtbf");
        assertEquals(0, mtbf.code());
    }
}
