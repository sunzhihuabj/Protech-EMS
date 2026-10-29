package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.org.dto.DeptReq;
import com.projlife.org.service.DeptService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 部门树接口：树查询登录即可；维护需 org:dept:manage。
 */
@RestController
@RequestMapping("/api/v1/org/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        return ApiResponse.ok(deptService.tree());
    }

    @PostMapping
    @RequirePermission("org:dept:manage")
    public ApiResponse<Long> create(@Valid @RequestBody DeptReq req) {
        return ApiResponse.ok(deptService.create(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("org:dept:manage")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody DeptReq req) {
        deptService.update(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("org:dept:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResponse.ok();
    }
}
