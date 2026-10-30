package com.projlife.integration.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 钉钉工作通知适配器（S24 轨道三，D-08 钉钉主/企微备）：
 * 独立组件（不占用 NotifyChannelAdapter 单通道口径，避免与企微桩冲突）。
 * 未配置钉钉凭据时 enabled=false，调用侧自动降级为仅站内；
 * 真实推送（工作通知 API / 群机器人 webhook）在生产凭据配置后启用（A-37）。
 */
@Component
public class DingtalkStubNotifyAdapter {

    private static final Logger log = LoggerFactory.getLogger(DingtalkStubNotifyAdapter.class);

    @Value("${projlife.notify.dingtalk.enabled:false}")
    private boolean enabled;

    /** 钉钉企业内部应用 AppKey（生产凭据，勿入库） */
    @Value("${projlife.notify.dingtalk.app-key:}")
    private String appKey;

    /** 机器人 webhook（备选通道） */
    @Value("${projlife.notify.dingtalk.webhook:}")
    private String webhook;

    public boolean available() {
        return enabled;
    }

    /** DINGTALK 通道推送（bid_push.channel 已留位）；未启用时记降级日志并返回 false */
    public boolean send(Long receiverId, String title, String content, String bizType, String bizId) {
        if (!enabled) {
            log.debug("钉钉通道未启用，降级站内 receiverId={} biz={}/{}", receiverId, bizType, bizId);
            return false;
        }
        log.info("[MOCK] 钉钉工作通知 receiverId={} title={} biz={}/{} appKey={}",
                receiverId, title, bizType, bizId, appKey.isEmpty() ? "(未配)" : "(已配)");
        return true;
    }
}
