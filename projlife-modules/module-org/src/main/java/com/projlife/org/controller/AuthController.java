package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.context.UserContextHolder;
import com.projlife.org.dto.ChangePasswordReq;
import com.projlife.org.dto.LoginReq;
import com.projlife.org.dto.LoginResp;
import com.projlife.org.dto.RefreshReq;
import com.projlife.org.dto.ThemeReq;
import com.projlife.org.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * 认证接口（公开：login/refresh；登录态：logout/me/password/theme）。
 * 主题切换权限 profile:theme:switch 为全员内置（03 册 §6A.2）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@Valid @RequestBody LoginReq req,
                                        @RequestHeader(value = "X-Forwarded-For", required = false) String forwarded,
                                        javax.servlet.http.HttpServletRequest request) {
        String ip = forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return ApiResponse.ok(authService.login(req, ip));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResp> refresh(@Valid @RequestBody RefreshReq req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        authService.logout(token);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(authService.me(UserContextHolder.currentUserId()));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordReq req,
                                            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.changePassword(UserContextHolder.currentUserId(), req, token);
        return ApiResponse.ok();
    }

    @PutMapping("/me/theme")
    public ApiResponse<Void> saveTheme(@Valid @RequestBody ThemeReq req) {
        authService.saveTheme(UserContextHolder.currentUserId(), req);
        return ApiResponse.ok();
    }
}
