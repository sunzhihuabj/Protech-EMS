package com.projlife.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPRINT-KPI1 E2E（F7）：九板指标自动取数——内部端点（X-Internal-Key 共享密钥门，UID-R17）触发 16 项目录取数，
 * 至少 5 项可得快照（部分指标在空库下分母为 0 跳过，属预期）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class S29MetricAutoFetchTest {

    @Autowired
    private TestRestTemplate rest;

    @org.springframework.boot.web.server.LocalServerPort
    private int port;

    @Test
    @DisplayName("F7：metric-fetch 内部端点批量取数并落快照（UK 幂等可重跑）")
    void autoFetch() {
        HttpHeaders headers = internalHeaders();
        // 第一轮
        Map<?, ?> r1 = ((Map<?, ?>) rest.exchange("/api/internal/jobs/kb/metric-fetch",
                HttpMethod.POST, new HttpEntity<>(headers), Map.class).getBody());
        assertEquals(0, ((Number) r1.get("code")).intValue(), r1.toString());
        Map<?, ?> d1 = (Map<?, ?>) r1.get("data");
        assertNotNull(d1);
        int fetched1 = ((Number) d1.get("fetched")).intValue();
        assertTrue(fetched1 >= 8, "至少 5 项可取数（空库分母 0 项跳过属预期），实测 " + d1);
        assertEquals(16, ((Number) d1.get("catalogSize")).intValue(), "catalog 应收 16 项");
        // 第二轮幂等（UK 修正口径不炸）
        Map<?, ?> r2 = ((Map<?, ?>) rest.exchange("/api/internal/jobs/kb/metric-fetch",
                HttpMethod.POST, new HttpEntity<>(headers), Map.class).getBody());
        assertEquals(0, ((Number) r2.get("code")).intValue(), r2.toString());
        assertEquals(fetched1, ((Number) ((Map<?, ?>) r2.get("data")).get("fetched")).intValue(),
                "幂等重跑 fetched 应一致");
    }

    @Test
    @DisplayName("UID-R17：内部端点缺 X-Internal-Key 一律 401（共享密钥门负向）")
    void internalKeyRequired() {
        // TestRestTemplate 对 401+POST 会触发 HttpClient 认证重试（HttpRetryException），改用原生 RestTemplate 断言状态码
        String url = "http://localhost:" + port + "/api/internal/jobs/kb/metric-fetch";
        org.springframework.web.client.RestTemplate plain = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders noKey = new org.springframework.http.HttpHeaders();
        noKey.setContentType(MediaType.APPLICATION_JSON);
        try {
            plain.postForEntity(url, new HttpEntity<>(noKey), Map.class);
            throw new AssertionError("缺共享密钥头必须 401，实际未抛出");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(401, e.getRawStatusCode(), "缺共享密钥头必须 401");
        }
        org.springframework.http.HttpHeaders badKey = new org.springframework.http.HttpHeaders();
        badKey.setContentType(MediaType.APPLICATION_JSON);
        badKey.set("X-Internal-Key", "wrong-key");
        try {
            plain.postForEntity(url, new HttpEntity<>(badKey), Map.class);
            throw new AssertionError("错误密钥必须 401，实际未抛出");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(401, e.getRawStatusCode(), "错误密钥必须 401");
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(com.projlife.security.web.InternalKeyFilter.HEADER, "dev-only-internal-key");
        return headers;
    }
}
