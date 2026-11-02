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
 * SPRINT-KPI1 E2E（F7）：九板指标自动取数——内部端点（白名单）触发 16 项目录取数，
 * 至少 5 项可得快照（部分指标在空库下分母为 0 跳过，属预期）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class S29MetricAutoFetchTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("F7：metric-fetch 内部端点批量取数并落快照（UK 幂等可重跑）")
    void autoFetch() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
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
}
