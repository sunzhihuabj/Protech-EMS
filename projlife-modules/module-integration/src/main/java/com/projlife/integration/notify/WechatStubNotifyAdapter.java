package com.projlife.integration.notify;

import com.projlife.common.spi.NotifyChannelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信模板消息适配器（S0-12 桩实现）：未配置企微凭据时不可用，通知自动降级为仅站内。
 * 真实实现（模板消息/订阅消息 + openid 绑定）在 S7 小程序切片落地。
 */
@Component
public class WechatStubNotifyAdapter implements NotifyChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WechatStubNotifyAdapter.class);

    @Value("${projlife.notify.wechat.enabled:false}")
    private boolean enabled;

    @Override
    public boolean sendWechat(Long receiverId, String title, String content, String bizType, String bizId) {
        log.info("[MOCK] 微信模板消息 receiverId={} title={} biz={}/{}", receiverId, title, bizType, bizId);
        return true;
    }

    @Override
    public boolean wechatAvailable() {
        return enabled;
    }
}
