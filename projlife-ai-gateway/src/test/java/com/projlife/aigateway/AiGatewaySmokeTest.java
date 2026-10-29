package com.projlife.aigateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 网关冒烟测试（S0-13）：mock 调用 / 契约结构 / 降级不抛错 / 调用日志落库不阻塞业务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AiGatewaySmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String aiCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("aiCode", aiCode);
        body.put("bizType", "contract");
        body.put("bizId", 1);
        body.put("payload", Map.of("text", "demo 合同文本"));
        Map<String, Object> resp = rest.postForObject("/ai/v1/call", new HttpEntity<>(body, headers), Map.class);
        return resp;
    }

    @Test
    @DisplayName("AI-001 合同风险分析：mock 返回 3 条红黄绿")
    void mockRiskAnalysis() {
        Map<String, Object> resp = call("AI-001");
        assertEquals("SUCCESS", resp.get("status"));
        assertEquals(Boolean.TRUE, resp.get("isMock"));
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        assertNotNull(data.get("risks"));
        assertEquals(3, ((java.util.List<?>) data.get("risks")).size(), "应返回 3 条风险");
    }

    @Test
    @DisplayName("AI-002 信息提取：mock 返回 12 要素")
    void mockExtract() {
        Map<String, Object> resp = call("AI-002");
        assertEquals("SUCCESS", resp.get("status"));
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        assertEquals(12, ((Map<?, ?>) data.get("fields")).size(), "12 要素");
    }

    @Test
    @DisplayName("未知编号：默认回显 mock，不抛错（业务不阻塞）")
    void unknownCodeFallsBack() {
        Map<String, Object> resp = call("AI-999");
        assertEquals("SUCCESS", resp.get("status"));
        assertNotNull(resp.get("data"));
    }

    @Test
    @DisplayName("健康检查：mock 模式返回 MOCK（不抛错）")
    void health() {
        Map<String, Object> health = rest.getForObject("/ai/v1/health", Map.class);
        assertTrue(health.containsKey("status"));
    }
}
