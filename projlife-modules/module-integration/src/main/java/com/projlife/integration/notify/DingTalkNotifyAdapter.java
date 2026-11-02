package com.projlife.integration.notify;

import com.projlife.common.spi.IntegrationAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 钉钉通知适配器（SPRINT-INT1，替换原 DingtalkStubNotifyAdapter，D-08 通知主通道）：
 * 降级链（风险表）：工作通知（L1，需完整应用凭据）→ 群机器人（L2，最小凭据）→ 站内通知恒可达。
 * available()=false 或全部通道失败时返回 false，调用侧自动降级为仅站内（不抛异常、不阻塞）。
 *
 * A-39：sys_user.id → 钉钉 userid 静态映射（user-mapping 配置）；未映射的接收人跳过外发并记审计。
 */
@Component
public class DingTalkNotifyAdapter {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifyAdapter.class);

    public static final String ADAPTER_CODE = "DINGTALK";

    private final DingTalkConfig config;
    private final DingTalkClient client;

    @Autowired(required = false)
    private IntegrationAuditRecorder auditRecorder;

    public DingTalkNotifyAdapter(DingTalkConfig config, DingTalkClient client) {
        this.config = config;
        this.client = client;
    }

    /** 渠道是否可用（enabled 且 L1/L2 至少一个通道凭据齐备） */
    public boolean available() {
        return config.workNotifyConfigured() || config.robotConfigured();
    }

    /**
     * 通道推送（bid_push.channel 已留位 DINGTALK）。
     * content 支持换行文本，按 markdown 发送。
     */
    public boolean send(Long receiverId, String title, String content, String bizType, String bizId) {
        if (!available()) {
            log.debug("钉钉通道未启用，降级站内 receiverId={} biz={}/{}", receiverId, bizType, bizId);
            return false;
        }
        String text = new StringBuilder("#### ").append(title == null ? "通知" : title).append("\n\n")
                .append(content == null ? "" : content).append('\n').toString();

        // L1 工作通知优先
        if (config.workNotifyConfigured()) {
            String dingUserId = resolveDingUserId(receiverId);
            if (dingUserId != null) {
                DingTalkClient.DingTalkResult r = client.sendWorkNotify(
                        Collections.singletonList(dingUserId), title, text);
                if (r.ok) {
                    return true;
                }
                log.warn("钉钉工作通知失败，降级机器人 receiverId={} reason={}", receiverId, r.reason);
            } else {
                log.debug("receiverId={} 无钉钉 userid 映射（A-39），走机器人通道", receiverId);
            }
        }

        // L2 机器人兜底
        if (config.robotConfigured()) {
            DingTalkClient.DingTalkResult r = client.sendRobot(title, text, false);
            if (r.ok) {
                return true;
            }
            log.warn("钉钉机器人通道失败 biz={}/{} reason={}", bizType, bizId, r.reason);
        }
        if (auditRecorder != null) {
            auditRecorder.record(ADAPTER_CODE, "PUSH", null, "FAILED",
                    "全部钉钉通道失败，业务降级站内 biz=" + bizType + "/" + bizId, null);
        }
        return false;
    }

    /** sys_user.id → 钉钉 userid（静态映射，A-39） */
    private String resolveDingUserId(Long receiverId) {
        if (receiverId == null) {
            return null;
        }
        Map<Long, String> mapping = config.parseUserMapping();
        return mapping.get(receiverId);
    }
}
