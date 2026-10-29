package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.org.dto.RoleReq;
import com.projlife.org.service.RoleService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 角色与权限分配接口（org:role:manage）。
 */
@RestController
@RequestMapping("/api/v1/org/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission("org:role:manage")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissionTree() {
        return ApiResponse.ok(roleService.permissionTree());
    }

    @PostMapping
    @RequirePermission("org:role:manage")
    public ApiResponse<Long> create(@Valid @RequestBody RoleReq req) {
        return ApiResponse.ok(roleService.create(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("org:role:manage")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody RoleReq req) {
        roleService.update(id, req);
        return ApiResponse.ok();
    }
}
