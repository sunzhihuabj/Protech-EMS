package com.projlife.integration.ops;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.integration.notify.DingTalkClient;
import com.projlife.integration.notify.DingTalkConfig;
import com.projlife.integration.notify.DingTalkNotifyAdapter;
import com.projlife.integration.notify.DingTalkTokenManager;
import com.projlife.security.auth.RequirePermission;
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
 * 集成运维端点（SPRINT-INT1）：真实健康检查/测试发送/回查，供 /sys/monitor 与部署 Runbook 冒烟。
 * 全部 sys:config 权限（对齐 IntegrationController 口径）。任何探测失败不抛网络异常，返回结果对象。
 */
@RestController
@RequestMapping("/api/v1/integ/ops")
public class IntegrationOpsController {

    private final DingTalkConfig dingTalkConfig;
    private final DingTalkClient dingTalkClient;
    private final DingTalkNotifyAdapter dingTalkAdapter;
    private final DingTalkTokenManager tokenManager;

    public IntegrationOpsController(DingTalkConfig dingTalkConfig, DingTalkClient dingTalkClient,
                                    DingTalkNotifyAdapter dingTalkAdapter, DingTalkTokenManager tokenManager) {
        this.dingTalkConfig = dingTalkConfig;
        this.dingTalkClient = dingTalkClient;
        this.dingTalkAdapter = dingTalkAdapter;
        this.tokenManager = tokenManager;
    }

    // ==================== 钉钉 ====================

    /** 配置齐备性健康检查（不消耗 token 调用次数以外的任何远端资源） */
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

    /** 测试发送（channel: AUTO/WORK/ROBOT）；WORK 用 dingUserId 或 receiverId(走映射) */
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
        // AUTO：adapter 全链路（含降级链）
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
        return ids.isEmpty() ? Collections.emptyList() : ids;
    }
}
