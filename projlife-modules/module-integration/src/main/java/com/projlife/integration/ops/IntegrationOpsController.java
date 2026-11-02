package com.projlife.integration.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.projlife.common.api.ApiResponse;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.spi.IntegrationAuditRecorder;
import com.projlife.integration.notify.DingTalkClient;
import com.projlife.integration.notify.DingTalkConfig;
import com.projlife.integration.notify.DingTalkNotifyAdapter;
import com.projlife.integration.notify.DingTalkTokenManager;
import com.projlife.integration.shb.ShbClient;
import com.projlife.integration.shb.ShbConfig;
import com.projlife.integration.shb.ShbTokenManager;
import com.projlife.integration.tplus.TplusClient;
import com.projlife.integration.tplus.TplusConfig;
import com.projlife.security.auth.RequirePermission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成运维端点（SPRINT-INT1）：真实健康检查/测试发送/手动对账，供 /sys/monitor 与部署 Runbook 冒烟。
 * 全部 sys:config 权限（对齐 IntegrationController 口径）。探测失败不抛网络异常，返回结果对象。
 */
@RestController
@RequestMapping("/api/v1/integ/ops")
public class IntegrationOpsController {

    private static final String DINGTALK = "DINGTALK";
    private static final String SHB = "SHB";

    private final DingTalkConfig dingTalkConfig;
    private final DingTalkClient dingTalkClient;
    private final DingTalkNotifyAdapter dingTalkAdapter;
    private final DingTalkTokenManager tokenManager;
    private final ShbConfig shbConfig;
    private final ShbClient shbClient;
    private final ShbTokenManager shbTokenManager;
    private final TplusConfig tplusConfig;
    private final TplusClient tplusClient;
    private final IntegrationAuditRecorder auditRecorder;

    public IntegrationOpsController(DingTalkConfig dingTalkConfig, DingTalkClient dingTalkClient,
                                    DingTalkNotifyAdapter dingTalkAdapter, DingTalkTokenManager tokenManager,
                                    ShbConfig shbConfig, ShbClient shbClient, ShbTokenManager shbTokenManager,
                                    TplusConfig tplusConfig, TplusClient tplusClient,
                                    ObjectProvider<IntegrationAuditRecorder> auditRecorder) {
        this.dingTalkConfig = dingTalkConfig;
        this.dingTalkClient = dingTalkClient;
        this.dingTalkAdapter = dingTalkAdapter;
        this.tokenManager = tokenManager;
        this.shbConfig = shbConfig;
        this.shbClient = shbClient;
        this.shbTokenManager = shbTokenManager;
        this.tplusConfig = tplusConfig;
        this.tplusClient = tplusClient;
        this.auditRecorder = auditRecorder.getIfAvailable();
    }

    // ==================== 钉钉 ====================

    /** 配置齐备性健康检查（不发起任何远端调用） */
    @GetMapping("/dingtalk/health")
    @RequirePermission("sys:config")
    public ApiResponse<Map<String, Object>> dingtalkHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("enabled", dingTalkConfig.isEnabled());
        health.put("workNotify", dingTalkConfig.workNotifyConfigured());
        health.put("robot", dingTalkConfig.robotConfigured());
        health.put("available", dingTalkAdapter.available());
        health.put("userMappingSize", dingTalkConfig.parseUserMapping().size());
        return ApiResponse.ok(health);
    }

    /** 测试发送（channel: AUTO/WORK/ROBOT）；WORK 用 dingUserId 或 userIds */
    @PostMapping("/dingtalk/test-notify")
    @RequirePermission("sys:config")
    public ApiResponse<Object> dingtalkTestNotify(@RequestBody Map<String, Object> body) {
        String channel = String.valueOf(body.getOrDefault("channel", "AUTO"));
        String title = String.valueOf(body.getOrDefault("title", "Protech 钉钉通道测试"));
        String content = String.valueOf(body.getOrDefault("content", "这是一条来自业务总台的测试通知。"));
        if ("ROBOT".equals(channel)) {
            return ApiResponse.ok(dingTalkClient.sendRobot(title, "#### " + title + "\n\n" + content, false));
        }
        if ("WORK".equals(channel)) {
            return ApiResponse.ok(dingTalkClient.sendWorkNotify(resolveUserIds(body), title,
                    "#### " + title + "\n\n" + content));
        }
        // AUTO：adapter 全链路（含 L1→L2 降级链）
        Object receiverRaw = body.get("receiverId");
        Long receiverId = receiverRaw == null ? null : Long.valueOf(String.valueOf(receiverRaw));
        boolean sent = dingTalkAdapter.send(receiverId, title, content, "OPS_TEST", null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("available", dingTalkAdapter.available());
        return ApiResponse.ok(result);
    }

    /** 工作通知假成功二次回查（getsendprogress + getsendresult） */
    @GetMapping("/dingtalk/notify-result")
    @RequirePermission("sys:config")
    public ApiResponse<Object> dingtalkNotifyResult(@RequestParam String taskId) {
        return ApiResponse.ok(dingTalkClient.querySendResult(taskId));
    }

    /** 手动刷新 token（凭据更换后） */
    @PostMapping("/dingtalk/refresh-token")
    @RequirePermission("sys:config")
    public ApiResponse<Map<String, Object>> dingtalkRefreshToken() {
        tokenManager.invalidate();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            tokenManager.getToken();
            result.put("refreshed", true);
        } catch (Exception ex) {
            result.put("refreshed", false);
            result.put("reason", ex.getMessage());
        }
        return ApiResponse.ok(result);
    }

    // ==================== 售后宝 ====================

    /** 配置齐备性健康检查（禁用 token 端点探测——50 次/天纪律） */
    @GetMapping("/shb/health")
    @RequirePermission("sys:config")
    public ApiResponse<Map<String, Object>> shbHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("enabled", shbConfig.isEnabled());
        health.put("configured", shbConfig.configured());
        health.put("baseUrl", shbConfig.getBaseUrl());
        health.put("webhookKeySet", shbConfig.getWebhookSharedKey() != null && !shbConfig.getWebhookSharedKey().isEmpty());
        health.put("todayTokenFetch", shbTokenManager.todayFetchCount());
        return ApiResponse.ok(health);
    }

    /**
     * 手动增量对账（拉取事件列表，逐事件落 int_sync_log）。
     * body: {hours:24, pageSize:50, maxPages:10}；对报告②§七.6"Webhook+定时对账双通道"的兜底跳。
     */
    @PostMapping("/shb/sync-events")
    @RequirePermission("sys:config")
    public ApiResponse<Map<String, Object>> shbSyncEvents(@RequestBody(required = false) Map<String, Object> body) {
        int hours = intOf(body, "hours", shbConfig.getSyncWindowHours());
        int pageSize = intOf(body, "pageSize", shbConfig.getPageSize());
        int maxPages = intOf(body, "maxPages", 10);
        String timeFilter = ShbClient.timeWindow(hours);

        Map<String, Object> summary = new LinkedHashMap<>();
        int total = 0;
        int pages = 0;
        String lastError = null;
        for (int pageNum = 1; pageNum <= maxPages; pageNum++) {
            ShbClient.ShbResult r = shbClient.getEvents(pageNum, pageSize, null, timeFilter);
            if (!r.ok) {
                lastError = r.errorCode + " " + r.message;
                break;
            }
            pages = pageNum;
            JsonNode list = r.data.path("list");
            if (list.isMissingNode() || !list.isArray() || list.isEmpty()) {
                break;
            }
            total += list.size();
            for (JsonNode event : list) {
                audit(SHB, "PULL", event.path("eventNo").asText(null), "SUCCESS", null, truncate(event.toString()));
            }
            if (list.size() < ShbClient.normalizePageSize(pageSize)) {
                break;
            }
        }
        summary.put("total", total);
        summary.put("pages", pages);
        summary.put("timeFilter", timeFilter);
        if (lastError != null) {
            summary.put("lastError", lastError);
        }
        return ApiResponse.ok(summary);
    }

    // ==================== 捷信通T+ ====================

    /** 实例存活探测（GET 门户页，有 HTTP 响应即 UP）+ 配置齐备性 */
    @GetMapping("/tplus/health")
    @RequirePermission("sys:config")
    public ApiResponse<Map<String, Object>> tplusHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("enabled", tplusConfig.isEnabled());
        health.put("configured", tplusConfig.configured());
        health.put("jwtConfigured", tplusConfig.jwtConfigured());
        health.put("tokenMode", tplusConfig.getTokenMode());
        health.put("baseUrl", tplusConfig.getBaseUrl());
        if (tplusConfig.isEnabled()) {
            TplusClient.TplusResult probe = tplusClient.probe();
            health.put("probe", probe.ok ? "UP" : "DOWN");
            health.put("probeDetail", probe.ok ? probe.raw : probe.message);
        }
        return ApiResponse.ok(health);
    }

    /**
     * 通用 v2 查询透传（sys:config 管控）。
     * body: {service:"Inventory", method:"Query", args:{...}}；13.0 可用性以官方接口版本标注为准。
     */
    @PostMapping("/tplus/query")
    @RequirePermission("sys:config")
    public ApiResponse<Object> tplusQuery(@RequestBody Map<String, Object> body) {
        String service = String.valueOf(body.getOrDefault("service", "Inventory"));
        String method = String.valueOf(body.getOrDefault("method", "Query"));
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) body.getOrDefault("args", new LinkedHashMap<>());
        return ApiResponse.ok(tplusClient.call(service, method, args));
    }

    // ---- 工具 ----

    private List<String> resolveUserIds(Map<String, Object> body) {
        Object userIds = body.get("userIds");
        List<String> ids = new ArrayList<>();
        if (userIds instanceof List) {
            for (Object o : (List<?>) userIds) {
                if (o != null) {
                    ids.add(String.valueOf(o));
                }
            }
        } else if (body.get("dingUserId") != null) {
            ids.add(String.valueOf(body.get("dingUserId")));
        }
        if (ids.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "工作通知测试需提供 dingUserId 或 userIds");
        }
        return ids;
    }

    private static int intOf(Map<String, Object> body, String key, int def) {
        if (body == null || body.get(key) == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(body.get(key)));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 60000 ? s.substring(0, 60000) : s);
    }

    private void audit(String adapterCode, String direction, String remoteId, String status,
                       String errorMessage, String payloadJson) {
        if (auditRecorder != null) {
            auditRecorder.record(adapterCode, direction, remoteId, status, errorMessage, payloadJson);
        }
    }
}
