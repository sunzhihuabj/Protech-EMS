package com.projlife.common.spi;

/**
 * 外部通知渠道适配器接口（模块 api 包：module-integration 实现，微信模板消息/钉钉机器人）。
 * 适配器异常不阻塞站内通知落库（S0-12）。
 */
public interface NotifyChannelAdapter {

    /** 发送微信模板消息；返回 true 表示已受理（同步或入队） */
    boolean sendWechat(Long receiverId, String title, String content, String bizType, String bizId);

    /** 渠道是否可用（未配置返回 false，调用方降级为仅站内） */
    boolean wechatAvailable();
}
