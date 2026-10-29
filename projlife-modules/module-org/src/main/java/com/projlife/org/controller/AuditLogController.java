package com.projlife.org.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.org.entity.SysOperationLog;
import com.projlife.org.mapper.SysOperationLogMapper;
import com.projlife.security.auth.RequirePermission;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询（system:log:view）。只读：审计表禁改（03 册 §七）。
 */
@RestController
@RequestMapping("/api/v1/system/logs")
public class AuditLogController {

    private final SysOperationLogMapper logMapper;

    public AuditLogController(SysOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @GetMapping
    @RequirePermission("system:log:view")
    public ApiResponse<PageResp<SysOperationLog>> page(PageReq pageReq,
                                                       @RequestParam(required = false) String module,
                                                       @RequestParam(required = false) String action) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .orderByDesc(SysOperationLog::getId);
        if (StringUtils.hasText(module)) {
            wrapper.eq(SysOperationLog::getModule, module);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(SysOperationLog::getAction, action);
        }
        Page<SysOperationLog> page = logMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<SysOperationLog> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return ApiResponse.ok(resp);
    }
}
