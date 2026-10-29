package com.projlife.integration.ai;

import com.projlife.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 网关客户端（S0-13）。
 * 铁律：业务代码不得直连模型，更不得出现任何公网 AI 服务的 SDK/URL（BR-AI-01/02）。
 * 降级链：网关不可达 → 抛 503 前先返回 DEGRADED 结果由调用方决策；本客户端始终不抛出网络异常，
 * 以"降级响应"返回，保证业务流程不阻塞（00 册 C-12）。
 */
@Component
public class AiGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AiGatewayClient(RestTemplate restTemplate,
                           @Value("${projlife.ai-gateway.base-url:http://localhost:8090}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * 统一调用入口。返回体含 status（SUCCESS/DEGRADED/FAILED）、isMock、data。
     * 网关侧 mock 模式恒可用；真实模型不可用时网关自身降级，这里兜底处理连接失败。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> call(String aiCode, String bizType, Long bizId, Object payload) {
        Map<String, Object> request = new HashMap<>();
        request.put("aiCode", aiCode);
        request.put("bizType", bizType);
        request.put("bizId", bizId == null ? null : String.valueOf(bizId));
        request.put("payload", payload);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            Map<String, Object> response = restTemplate.postForObject(baseUrl + "/ai/v1/call", entity, Map.class);
            if (response == null) {
                return degraded("网关无响应");
            }
            return response;
        } catch (Exception ex) {
            log.warn("AI 网关不可达，业务降级 aiCode={} error={}", aiCode, ex.getMessage());
            return degraded("AI 网关不可达：" + ex.getMessage());
        }
    }

    private Map<String, Object> degraded(String reason) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "DEGRADED");
        response.put("isMock", true);
        response.put("reason", reason);
        response.put("data", null);
        return response;
    }
}
