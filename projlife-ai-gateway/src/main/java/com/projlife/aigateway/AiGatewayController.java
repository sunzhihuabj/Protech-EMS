package com.projlife.aigateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.aigateway.entity.AiCallLog;
import com.projlife.aigateway.mapper.AiCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 统一入口（13 册）：/ai/v1/call 与 /ai/v1/health。
 * 调用链：鉴权校验（S0 简化为内网网段信任，S9 加网关签名）→ 配置解析 → mock/真实路由 → 写调用日志 → 响应。
 * 任何异常不向业务方抛网络错误：统一返回 DEGRADED，保证业务不阻塞（00 册 C-12 降级链）。
 */
@RestController
@RequestMapping("/ai/v1")
public class AiGatewayController {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayController.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MockAiEngine mockEngine;
    private final AiCallLogMapper callLogMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${projlife.ai.model-endpoint:}")
    private String modelEndpoint;

    @Value("${projlife.ai.timeout-ms:8000}")
    private long timeoutMs;

    public AiGatewayController(MockAiEngine mockEngine, AiCallLogMapper callLogMapper,
                               ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.mockEngine = mockEngine;
        this.callLogMapper = callLogMapper;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/call")
    public Map<String, Object> call(@RequestBody Map<String, Object> request) {
        long start = System.currentTimeMillis();
        String aiCode = String.valueOf(request.get("aiCode"));
        String bizType = String.valueOf(request.getOrDefault("bizType", "COMMON"));
        Object bizIdRaw = request.get("bizId");
        Long bizId = bizIdRaw == null ? null : Long.valueOf(String.valueOf(bizIdRaw));
        Object payload = request.get("payload");
        Object operatorRaw = request.get("operatorId");
        Long operatorId = operatorRaw == null ? null : Long.valueOf(String.valueOf(operatorRaw));

        Map<String, Object> response = new HashMap<>();
        AiCallLog callLog = new AiCallLog();
        callLog.setCallNo("AI-" + LocalDate.now().format(TS) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
        callLog.setAiCode(aiCode);
        callLog.setModelType("DATA");
        callLog.setBizType(bizType);
        callLog.setBizId(bizId);
        callLog.setOperatorId(operatorId == null ? 0L : operatorId);
        callLog.setCreatedAt(LocalDateTime.now());
        callLog.setCreatedBy("ai-gateway");

        try {
            callLog.setRequestJson(objectMapper.writeValueAsString(request));
        } catch (Exception ex) {
            callLog.setRequestJson(null);
        }

        try {
            if (modelEndpoint == null || modelEndpoint.isEmpty()) {
                // mock 模式（S0 默认）：直接返回 mock 结果
                MockAiEngine.MockResult result = mockEngine.invoke(aiCode, bizType, payload);
                response.put("status", "SUCCESS");
                response.put("isMock", true);
                response.put("data", result.data);
                response.put("confidence", result.confidence);
                response.put("modelVersion", result.modelVersion);
                callLog.setStatus("SUCCESS");
                callLog.setIsMock(1);
                callLog.setConfidence(BigDecimal.valueOf(result.confidence));
                callLog.setModelVersion(result.modelVersion);
                callLog.setPromptVersion(result.promptVersion);
            } else {
                // 真实模型模式：带超时调用内网模型服务器；失败降级 mock（S9 联调前保持 mock 即可）
                Map<String, Object> modelResponse = callModel(aiCode, payload);
                response.put("status", "SUCCESS");
                response.put("isMock", false);
                response.put("data", modelResponse);
                callLog.setStatus("SUCCESS");
                callLog.setIsMock(0);
                callLog.setModelVersion("remote");
            }
        } catch (Exception ex) {
            log.warn("AI 调用降级 aiCode={} reason={}", aiCode, ex.getMessage());
            MockAiEngine.MockResult result = mockEngine.invoke(aiCode, bizType, payload);
            response.put("status", "DEGRADED");
            response.put("isMock", true);
            response.put("reason", "模型不可用：" + ex.getMessage());
            response.put("data", result.data);
            callLog.setStatus("DEGRADED");
            callLog.setIsMock(1);
            callLog.setModelVersion(result.modelVersion);
        }

        callLog.setElapsedMs((int) (System.currentTimeMillis() - start));
        try {
            callLog.setResponseJson(objectMapper.writeValueAsString(response));
        } catch (Exception ignored) {
            // 日志序列化失败不影响响应
        }
        try {
            callLogMapper.insert(callLog);
        } catch (Exception ex) {
            log.warn("AI 调用日志写入失败（不影响业务）", ex);
        }
        return response;
    }

    /** 健康检查：未配置真实模型返回 MOCK；配置后探测端点，失败返回 DEGRADED（不抛错） */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        if (modelEndpoint == null || modelEndpoint.isEmpty()) {
            health.put("status", "MOCK");
            health.put("message", "mock 模式（未配置内网模型端点）");
            return health;
        }
        try {
            restTemplate.getForObject(modelEndpoint, String.class);
            health.put("status", "UP");
        } catch (Exception ex) {
            health.put("status", "DEGRADED");
            health.put("message", "模型端点不可达：" + ex.getMessage());
        }
        return health;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callModel(String aiCode, Object payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("aiCode", aiCode);
        body.put("payload", payload);
        Map<String, Object> response = restTemplate.postForObject(modelEndpoint, new HttpEntity<>(body, headers), Map.class);
        if (response == null) {
            throw new IllegalStateException("模型返回空响应");
        }
        return response;
    }
}
