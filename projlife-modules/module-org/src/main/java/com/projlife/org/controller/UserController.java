package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.org.dto.UserCreateReq;
import com.projlife.org.dto.UserResp;
import com.projlife.org.dto.UserUpdateReq;
import com.projlife.org.mapper.UserPageQuery;
import com.projlife.org.service.UserService;
import com.projlife.org.service.importexport.UserImportHandler;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * 用户管理接口（org:user:manage）。
 */
@RestController
@RequestMapping("/api/v1/org/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequirePermission("org:user:manage")
    public ApiResponse<PageResp<UserResp>> page(PageReq pageReq, UserPageQuery query) {
        return ApiResponse.ok(userService.page(pageReq, query));
    }

    @GetMapping("/{id}")
    @RequirePermission("org:user:manage")
    public ApiResponse<UserResp> detail(@PathVariable Long id) {
        return ApiResponse.ok(userService.detail(id));
    }

    @PostMapping
    @RequirePermission("org:user:manage")
    public ApiResponse<Long> create(@Valid @RequestBody UserCreateReq req) {
        return ApiResponse.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("org:user:manage")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateReq req) {
        userService.update(id, req);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("org:user:manage")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.changeStatus(id, body.get("status"));
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password/reset")
    @RequirePermission("org:user:manage")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return ApiResponse.ok();
    }
}
