package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.common.spi.NotifyChannelAdapter;
import com.projlife.org.entity.SysNotification;
import com.projlife.org.mapper.SysNotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知中心（S0-12）：站内必达；WECHAT/BOTH 渠道走适配器，失败降级仅站内不阻塞。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SysNotificationMapper notificationMapper;
    private final NotifyChannelAdapter channelAdapter;

    public NotificationService(SysNotificationMapper notificationMapper, NotifyChannelAdapter channelAdapter) {
        this.notificationMapper = notificationMapper;
        this.channelAdapter = channelAdapter;
    }

    /** 发送通知：channel=INNER/WECHAT/BOTH */
    public void send(Long receiverId, String msgType, String title, String content,
                     String bizType, String bizId, String channel) {
        SysNotification notification = new SysNotification();
        notification.setReceiverId(receiverId);
        notification.setMsgType(msgType);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setChannel(channel == null ? "INNER" : channel);
        notification.setIsRead(0);
        notificationMapper.insert(notification);

        boolean needWechat = "WECHAT".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel);
        if (needWechat) {
            try {
                if (!channelAdapter.wechatAvailable()
                        || !channelAdapter.sendWechat(receiverId, title, content, bizType, bizId)) {
                    log.warn("微信渠道不可达，降级为仅站内 receiverId={} title={}", receiverId, title);
                }
            } catch (Exception ex) {
                log.warn("微信渠道发送异常，降级为仅站内 receiverId={}", receiverId, ex);
            }
        }
    }

    /** 我的通知（SELF 数据范围由查询条件保证） */
    public PageResp<SysNotification> myPage(Long userId, PageReq pageReq, boolean unreadOnly) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getReceiverId, userId)
                .orderByDesc(SysNotification::getId);
        if (unreadOnly) {
            wrapper.eq(SysNotification::getIsRead, 0);
        }
        Page<SysNotification> page = notificationMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<SysNotification> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getReceiverId, userId)
                .eq(SysNotification::getIsRead, 0));
    }

    public void markRead(Long userId, Long id) {
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw BizException.of(com.projlife.common.exception.ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (!notification.getReceiverId().equals(userId)) {
            throw BizException.forbidden("只能标记自己的通知");
        }
        notification.setIsRead(1);
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    public Map<String, Object> summary(Long userId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("unread", unreadCount(userId));
        return summary;
    }
}
