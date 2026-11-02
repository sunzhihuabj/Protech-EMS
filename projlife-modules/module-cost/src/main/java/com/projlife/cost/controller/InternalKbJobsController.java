package com.projlife.cost.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.cost.service.KbMetricService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 看板内部任务端点（SPRINT-KPI1，F7）：/api/internal/jobs/** 白名单（SecurityConfig），
 * 由 projlife-job 经 InternalJobInvoker 调用（job-runner 登录，对齐其余 6 个定时任务口径）。
 */
@RestController
@RequestMapping("/api/internal/jobs/kb")
public class InternalKbJobsController {

    private final KbMetricService metricService;

    public InternalKbJobsController(KbMetricService metricService) {
        this.metricService = metricService;
    }

    /** 九板指标自动取数（11 项目录逐码计算 + UK 幂等快照） */
    @PostMapping("/metric-fetch")
    public ApiResponse<Map<String, Object>> metricFetch() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return ApiResponse.ok(metricService.autoFetchAll(period));
    }
}
