package com.projlife.integration.shb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.spi.IntegrationAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 售后宝 Webhook 接收器（SPRINT-INT1，报告②§4 主动推送，V2.2+ 企业版+）：
 * 工单/事件/客户产品/备件四类推送统一收口。安全口径（A-40）：
 *  推送回调报文格式与签名方式未在公开资料中披露 → ①可选共享密钥校验（query sharedKey）；
 *  ②原始报文全量落 int_sync_log.payload_json 供人工对账；③恒快返 200 防对端重试风暴。
 * 路径走 PUBLIC_PATHS（/api/internal/shb/**，对齐 DMZ 拉模式先例，生产补 mTLS）。
 */
@RestController
@RequestMapping("/api/internal/shb")
public class ShbWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ShbWebhookController.class);

    private final ShbConfig config;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private IntegrationAuditRecorder auditRecorder;

    public ShbWebhookController(ShbConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /** 测试注入口 */
    void setAuditRecorder(IntegrationAuditRecorder recorder) {
        this.auditRecorder = recorder;
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestParam(required = false) String sharedKey,
                                       @RequestBody String raw) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (!config.isEnabled()) {
            resp.put("errorCode", "0");
            resp.put("message", "ignored: SHB 集成未启用");
            return resp;
        }
        if (config.getWebhookSharedKey() != null && !config.getWebhookSharedKey().isEmpty()
                && !config.getWebhookSharedKey().equals(sharedKey)) {
            log.warn("售后宝推送 sharedKey 校验失败（丢弃）");
            resp.put("errorCode", "403");
            resp.put("message", "sharedKey 校验失败");
            return resp;
        }
        // 提取关联键（容忍报文结构差异：eventNo/taskNo/data.eventNo/data.taskNo）
        String remoteId = extractNo(raw);
        auditRecorder.record(ShbClient.ADAPTER_CODE, "INBOUND", remoteId, "SUCCESS", null, raw);
        resp.put("errorCode", "0");
        resp.put("message", "received");
        return resp;
    }

    private String extractNo(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            for (String field : new String[]{"eventNo", "taskNo"}) {
                JsonNode hit = node.findValue(field);
                if (hit != null && !hit.asText().isEmpty()) {
                    return hit.asText();
                }
            }
        } catch (Exception ignored) {
            // 非法 JSON 也照收（原始报文已留档），不重试对端
        }
        return null;
    }
}
