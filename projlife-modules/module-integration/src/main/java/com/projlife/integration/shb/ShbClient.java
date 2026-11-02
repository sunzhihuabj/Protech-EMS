package com.projlife.integration.shb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.spi.IntegrationAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 售后宝客户端（SPRINT-INT1，报告②§4，事件模块全字段【实】；工单模块同构【推】，字段以语雀 V2.8 为准）：
 *  读取类：get_events（分页 pageSize∈{5,10,20,50}）、get_event_types、get_event_type_fields、
 *         get_event_{eventNo}、get_record_{eventNo}
 *  纪律：errorCode=0 判定；accessToken 走 URL Query；读接口 2 QPS → 客户端节流 ≥600ms；
 *  自定义字段读写不含人员/附件/关联字段（附件走 card 接口）。
 *
 * 降级铁律：不抛异常，统一 ShbResult(ok, errorCode, message, data)；审计落 int_sync_log。
 */
@Component
public class ShbClient {

    private static final Logger log = LoggerFactory.getLogger(ShbClient.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(5, 10, 20, 50);

    /** 读接口最小间隔（2 QPS 官方限制 → 600ms 节流） */
    static final long READ_INTERVAL_MS = 600;

    public static final String ADAPTER_CODE = "SHB";

    private final ShbConfig config;
    private final ShbTokenManager tokenManager;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private IntegrationAuditRecorder auditRecorder;

    private volatile long lastReadAt = 0;

    public ShbClient(ShbConfig config, ShbTokenManager tokenManager,
                     RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 统一结果载体 */
    public static class ShbResult {
        public boolean ok;
        public String errorCode;
        public String message;
        public JsonNode data;
        public String raw;

        static ShbResult ok(JsonNode data, String raw) {
            ShbResult r = new ShbResult();
            r.ok = true;
            r.errorCode = "0";
            r.data = data;
            r.raw = raw;
            return r;
        }

        static ShbResult fail(String code, String message, String raw) {
            ShbResult r = new ShbResult();
            r.ok = false;
            r.errorCode = code;
            r.message = message;
            r.raw = raw;
            return r;
        }
    }

    // ==================== 事件模块（受理层） ====================

    /**
     * 事件列表（V2.3+）。state ∈ created/allocated/processing/finished/convert2Task/offed；
     * timeFilter 格式 "yyyy-MM-dd HH:mm:ss~yyyy-MM-dd HH:mm:ss"（仅 4 个时间字段可排序/过滤）。
     */
    public ShbResult getEvents(int pageNum, int pageSize, String state, String timeFilter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNum", Math.max(1, pageNum));
        body.put("pageSize", normalizePageSize(pageSize));
        if (state != null && !state.isEmpty()) {
            body.put("state", state);
        }
        if (timeFilter != null && !timeFilter.isEmpty()) {
            body.put("createTime", timeFilter);
        }
        return post("get_events", "/service/event/get_events", body);
    }

    /** 事件类型列表 */
    public ShbResult getEventTypes() {
        return get("get_event_types", "/service/event/get_event_types", null);
    }

    /** 事件类型自定义字段元数据（写 attribute 前必调：fieldName/formType/setting） */
    public ShbResult getEventTypeFields(String typeId) {
        return get("get_event_type_fields", "/service/event/get_event_type_fields",
                Map.of("typeId", typeId == null ? "" : typeId));
    }

    /** 事件详情 */
    public ShbResult getEventDetail(String eventNo) {
        ShbResult r = get("get_event", "/service/event/get_event_" + eventNo, null);
        auditOne(r, eventNo);
        return r;
    }

    /** 事件日志（操作人/时间/经纬度/动作流水） */
    public ShbResult getEventRecord(String eventNo) {
        return get("get_record", "/service/event/get_record_" + eventNo, null);
    }

    /** 增量对账时间窗（now-hours ~ now） */
    public static String timeWindow(int hours) {
        return LocalDateTime.now().minusHours(hours).format(TS) + "~" + LocalDateTime.now().format(TS);
    }

    // ==================== 内部 ====================

    private ShbResult post(String label, String path, Map<String, Object> body) {
        if (!config.configured()) {
            return ShbResult.fail("NOT_CONFIGURED", "售后宝未配置（appKey/appSecret）", null);
        }
        try {
            String token = tokenManager.getToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = config.getBaseUrl() + path + "?accessToken=" + token;
            JsonNode resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            return envelope(label, resp, body);
        } catch (Exception ex) {
            return handleFailure(label, ex.getMessage(), body);
        }
    }

    private ShbResult get(String label, String path, Map<String, String> query) {
        if (!config.configured()) {
            return ShbResult.fail("NOT_CONFIGURED", "售后宝未配置（appKey/appSecret）", null);
        }
        throttle();
        try {
            String token = tokenManager.getToken();
            StringBuilder url = new StringBuilder(config.getBaseUrl()).append(path).append("?accessToken=").append(token);
            if (query != null) {
                for (Map.Entry<String, String> e : query.entrySet()) {
                    if (!e.getValue().isEmpty()) {
                        url.append('&').append(e.getKey()).append('=').append(e.getValue());
                    }
                }
            }
            JsonNode resp = restTemplate.getForObject(url.toString(), JsonNode.class);
            return envelope(label, resp, query);
        } catch (Exception ex) {
            return handleFailure(label, ex.getMessage(), query);
        }
    }

    private ShbResult envelope(String label, JsonNode resp, Object request) {
        if (resp == null) {
            return handleFailure(label, "售后宝接口空响应", request);
        }
        String errorCode = resp.path("errorCode").asText("-1");
        if (!"0".equals(errorCode)) {
            String message = resp.path("message").asText("");
            log.warn("售后宝 {} 失败 errorCode={} message={}", label, errorCode, message);
            audit(ADAPTER_CODE, "PULL", null, "FAILED", label + " errorCode=" + errorCode + " " + message, toJson(request));
            return ShbResult.fail(errorCode, message, resp.toString());
        }
        return ShbResult.ok(resp.path("data"), resp.toString());
    }

    private ShbResult handleFailure(String label, String message, Object request) {
        log.warn("售后宝 {} 调用失败：{}", label, message);
        audit(ADAPTER_CODE, "PULL", null, "FAILED", label + " " + message, toJson(request));
        return ShbResult.fail("EXCEPTION", message, null);
    }

    /** 官方仅允许 5/10/20/50，越界值就近归一 */
    public static int normalizePageSize(int pageSize) {
        int best = ALLOWED_PAGE_SIZES.get(0);
        for (int allowed : ALLOWED_PAGE_SIZES) {
            if (Math.abs(allowed - pageSize) < Math.abs(best - pageSize)) {
                best = allowed;
            }
        }
        return best;
    }

    /** 读接口节流：保证距上次读取 ≥600ms（2 QPS） */
    private void throttle() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            long wait = lastReadAt + READ_INTERVAL_MS - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastReadAt = System.currentTimeMillis();
        }
    }

    private void auditOne(ShbResult r, String eventNo) {
        audit(ADAPTER_CODE, "PULL", eventNo, r.ok ? "SUCCESS" : "FAILED",
                r.ok ? null : r.message, r.ok ? r.raw : null);
    }

    private void audit(String adapterCode, String direction, String remoteId, String status,
                       String errorMessage, String payloadJson) {
        if (auditRecorder != null) {
            auditRecorder.record(adapterCode, direction, remoteId, status, errorMessage, payloadJson);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception ex) {
            return null;
        }
    }
}
