package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.org.entity.SysNotification;
import com.projlife.org.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知接口：我的通知列表/未读数/标记已读（登录即可，数据范围=本人）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResp<SysNotification>> my(PageReq pageReq,
                                                     @RequestParam(required = false) boolean unreadOnly) {
        return ApiResponse.ok(notificationService.myPage(UserContextHolder.currentUserId(), pageReq, unreadOnly));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(notificationService.summary(UserContextHolder.currentUserId()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(UserContextHolder.currentUserId(), id);
        return ApiResponse.ok();
    }
}
