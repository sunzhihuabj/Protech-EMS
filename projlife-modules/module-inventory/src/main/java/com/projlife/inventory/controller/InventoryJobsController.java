package com.projlife.inventory.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.inventory.service.SlowMovingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 库存内部任务入口（S12 遗留：库龄自动跑批识别呆滞，R5 落地）。
 * 前缀 /api/internal/jobs/** 走安全白名单（job 进程调用，A-36）。
 */
@RestController
@RequestMapping("/api/internal/jobs/inv")
public class InventoryJobsController {

    private final SlowMovingService slowMovingService;

    public InventoryJobsController(SlowMovingService slowMovingService) {
        this.slowMovingService = slowMovingService;
    }

    /** 库龄扫描：inv_batch 库龄 ≥180 天且数量>0 → 呆滞 CANDIDATE（同物料已存在未闭环台账则跳过） */
    @PostMapping("/age-scan")
    public ApiResponse<Integer> ageScan() {
        return ApiResponse.ok(slowMovingService.autoScanFromBatches(180));
    }
}
