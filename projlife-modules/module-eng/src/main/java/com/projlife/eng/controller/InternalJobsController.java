package com.projlife.eng.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.eng.service.ThdPortalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部任务入口（S24 轨道三装配）：供 job 进程调用的扫描端点。
 * 前缀 /api/internal/** 走安全白名单（DMZ/内网 mTLS 占位，V4-05 M18 拉模式同款，A-36）。
 */
@RestController
@RequestMapping("/api/internal/jobs")
public class InternalJobsController {

    private final ThdPortalService thdPortalService;

    public InternalJobsController(ThdPortalService thdPortalService) {
        this.thdPortalService = thdPortalService;
    }

    /** 人员项目授权到期扫描：GRANTED 且 auth_end 已过 → EXPIRED */
    @PostMapping("/thd/expire-scan")
    public ApiResponse<Integer> thdExpireScan() {
        return ApiResponse.ok(thdPortalService.expireScan());
    }
}
