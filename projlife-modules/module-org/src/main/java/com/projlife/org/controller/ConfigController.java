package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.org.dto.ConfigReq;
import com.projlife.org.service.ConfigService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 系统参数接口（system:config:manage）：含系统默认主题 app.theme.default（03 册 §6A.1）。
 */
@RestController
@RequestMapping("/api/v1/system/configs")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @RequirePermission("system:config:manage")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String group) {
        return ApiResponse.ok(configService.list(group));
    }

    @GetMapping("/public/app.theme.default")
    public ApiResponse<String> defaultTheme() {
        return ApiResponse.ok(configService.getValue("app.theme.default"));
    }

    @PutMapping
    @RequirePermission("system:config:manage")
    public ApiResponse<Void> save(@Valid @RequestBody ConfigReq req) {
        configService.save(req.configKey, req.configValue, req.remark);
        return ApiResponse.ok();
    }
}
