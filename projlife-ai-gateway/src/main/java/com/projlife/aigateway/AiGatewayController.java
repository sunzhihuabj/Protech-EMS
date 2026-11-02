package com.projlife.aigateway;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 统一入口（13 册）：/ai/v1/call 与 /ai/v1/health。
 * 调用链：鉴权校验（S0 简化为内网网段信任，S9 加网关签名）→ 配置解析 → mock/真实路由 → 写调用日志 → 响应。
 * 路由三级：① api-key+model-endpoint+model-name 齐备 → OpenAI 兼容 chat/completions（真实推理）；
 *          ② 仅 model-endpoint → 内网模型裸协议透传；
 *          ③ 均为空 → mock（S0 默认）。
 * 任何异常不向业务方抛网络错误：统一返回 DEGRADED，保证业务不阻塞（00 册 C-12 降级链）。
 */
@RestController
@RequestMapping("/ai/v1")
public class AiGatewayController {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayController.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MockAiEngine mockEngine;
    private final PromptRegistry promptRegistry;
    private final AiCallLogMapper callLogMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${projlife.ai.model-endpoint:}")
    private String modelEndpoint;

    /** OpenAI 兼容端点的 Bearer Key；非空即启用真实推理模式 */
    @Value("${projlife.ai.api-key:}")
    private String apiKey;

    /** 真实推理模型名（如 z-ai/glm-5.3-flash） */
    @Value("${projlife.ai.model-name:}")
    private String modelName;

    /** 关闭思维链（chat_template_kwargs.enable_thinking=false）：推理型模型思维链耗时 40s~236s 且可能耗尽 token，业务场景默认关闭 */
    @Value("${projlife.ai.disable-thinking:true}")
    private boolean disableThinking;

    @Value("${projlife.ai.timeout-ms:8000}")
    private long timeoutMs;

    public AiGatewayController(MockAiEngine mockEngine, PromptRegistry promptRegistry,
                               AiCallLogMapper callLogMapper, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.mockEngine = mockEngine;
        this.promptRegistry = promptRegistry;
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
        callLog.setModelType(realMode() ? "LLM" : "DATA");
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
            if (realMode()) {
                // 真实推理模式：OpenAI 兼容 chat/completions
                ChatResult result = callChatModel(aiCode, bizType, payload);
                response.put("status", "SUCCESS");
                response.put("isMock", false);
                response.put("data", result.data);
                response.put("confidence", result.confidence);
                response.put("modelVersion", result.modelVersion);
                response.put("promptVersion", result.promptVersion);
                response.put("usageTokens", result.totalTokens);
                callLog.setStatus("SUCCESS");
                callLog.setIsMock(0);
                callLog.setConfidence(BigDecimal.valueOf(result.confidence));
                callLog.setModelVersion(result.modelVersion);
                callLog.setPromptVersion(result.promptVersion);
            } else if (modelEndpoint != null && !modelEndpoint.isEmpty()) {
                // 内网模型裸协议模式：带超时调用内网模型服务器；失败降级 mock
                Map<String, Object> modelResponse = callModel(aiCode, payload);
                response.put("status", "SUCCESS");
                response.put("isMock", false);
                response.put("data", modelResponse);
                callLog.setStatus("SUCCESS");
                callLog.setIsMock(0);
                callLog.setModelVersion("remote");
            } else {
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
            response.put("callId", callLog.getId());
        } catch (Exception ex) {
            log.warn("AI 调用日志写入失败（不影响业务）", ex);
        }
        return response;
    }

    /**
     * 健康检查：
     * mock 模式返回 MOCK；真实模式探测端点可达性——任何 HTTP 状态码（含 404/405）都说明服务在线，
     * 只有连接失败/超时才算 DEGRADED（不抛错）。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        if (realMode()) {
            health.put("mode", "LLM");
            health.put("model", modelName);
            health.put("message", "OpenAI 兼容真实推理模式");
        } else if (modelEndpoint == null || modelEndpoint.isEmpty()) {
            health.put("mode", "MOCK");
            health.put("status", "MOCK");
            health.put("message", "mock 模式（未配置模型端点）");
            return health;
        } else {
            health.put("mode", "RAW");
            health.put("message", "内网模型裸协议模式");
        }
        try {
            restTemplate.getForObject(modelEndpoint, String.class);
            health.put("status", "UP");
        } catch (HttpStatusCodeException httpEx) {
            // 端点有 HTTP 响应（404/405 等）= 服务在线
            health.put("status", "UP");
            health.put("httpStatus", httpEx.getStatusCode().value());
        } catch (ResourceAccessException | IllegalArgumentException ex) {
            health.put("status", "DEGRADED");
            health.put("message", "模型端点不可达：" + rootMessage(ex));
        } catch (Exception ex) {
            health.put("status", "DEGRADED");
            health.put("message", "模型端点探测失败：" + rootMessage(ex));
        }
        return health;
    }

    /** 是否启用 OpenAI 兼容真实推理：key + endpoint + model 三者齐备 */
    private boolean realMode() {
        return apiKey != null && !apiKey.isEmpty()
                && modelEndpoint != null && !modelEndpoint.isEmpty()
                && modelName != null && !modelName.isEmpty();
    }

    /** 内网模型裸协议（历史行为保留） */
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

    /** 真实推理结果载体 */
    private static class ChatResult {
        Map<String, Object> data;
        double confidence = 0.85;
        String modelVersion;
        String promptVersion;
        int totalTokens;
    }

    /**
     * OpenAI 兼容 chat/completions 调用（NVIDIA integrate API 同协议适配）。
     * 请求：{model, messages:[system 契约提示词, user 业务载荷], temperature, max_tokens}
     * 响应：choices[0].message.content → 提取 JSON → data；usage.total_tokens 记账。
     */
    private ChatResult callChatModel(String aiCode, String bizType, Object payload) throws Exception {
        String systemPrompt = promptRegistry.defined(aiCode)
                ? promptRegistry.systemPrompt(aiCode)
                : promptRegistry.genericPrompt(aiCode);
        String userContent = "bizType=" + bizType + " aiCode=" + aiCode + "\n业务数据："
                + objectMapper.writeValueAsString(payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);
        Map<String, String> usr = new HashMap<>();
        usr.put("role", "user");
        usr.put("content", userContent);
        messages.add(usr);
        body.put("messages", messages);
        body.put("temperature", 0.2);
        // 推理型模型（glm/deepseek 系列）思维链消耗 token，给足余量避免 content 截断为 null
        body.put("max_tokens", 2048);
        if (disableThinking) {
            // NIM 系端点（NVIDIA integrate 等）支持该参数关闭思维链；不支持端点返回 4xx 时下方自动去除重试
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("enable_thinking", false);
            body.put("chat_template_kwargs", kwargs);
        }

        JsonNode resp;
        try {
            resp = restTemplate.postForObject(modelEndpoint, new HttpEntity<>(body, headers), JsonNode.class);
        } catch (HttpStatusCodeException ex) {
            if (disableThinking && ex.getStatusCode().is4xxClientError()) {
                // 端点不认识 chat_template_kwargs：去除后重试一次
                body.remove("chat_template_kwargs");
                resp = restTemplate.postForObject(modelEndpoint, new HttpEntity<>(body, headers), JsonNode.class);
            } else {
                throw ex;
            }
        }
        if (resp == null || !resp.hasNonNull("choices") || resp.path("choices").isEmpty()) {
            throw new IllegalStateException("模型返回空响应");
        }
        String content = resp.path("choices").get(0).path("message").path("content").asText("");
        if (content.isEmpty()) {
            throw new IllegalStateException("模型 content 为空（可能 token 预算被思维链耗尽）");
        }

        ChatResult result = new ChatResult();
        result.modelVersion = resp.path("model").asText(modelName);
        result.totalTokens = resp.path("usage").path("total_tokens").asInt(0);
        result.data = extractJson(content);
        if (result.data.containsKey("confidence")) {
            try {
                double c = Double.parseDouble(String.valueOf(result.data.get("confidence")));
                if (c >= 0 && c <= 1) {
                    result.confidence = c;
                }
            } catch (NumberFormatException ignored) {
                // 模型给的 confidence 不合法则用默认值
            }
        }
        result.promptVersion = "p-" + aiCode.toLowerCase() + "-1.0";
        return result;
    }

    /** 从模型输出中提取 JSON 对象（容忍 markdown 代码块围栏与前后杂讯） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractJson(String content) throws Exception {
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception ex) {
            // 模型未按契约输出：降级为文本包装，调用方仍可读
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("text", content);
            return wrapped;
        }
    }

    private String rootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? ex.getMessage() : t.getMessage();
    }
}
