package com.projlife.inventory.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.inventory.entity.InvDisposal;
import com.projlife.inventory.entity.InvSlowMoving;
import com.projlife.inventory.service.SlowMovingService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 呆滞与处置接口（V4-04 M07）：/api/v1/inv/slow-moving。
 */
@RestController
@RequestMapping("/api/v1/inv/slow-moving")
public class SlowMovingController {

    private final SlowMovingService slowMovingService;

    public SlowMovingController(SlowMovingService slowMovingService) {
        this.slowMovingService = slowMovingService;
    }

    @PostMapping
    @RequirePermission("inv:slow:manage")
    public ApiResponse<InvSlowMoving> create(@RequestBody InvSlowMoving slow) {
        return ApiResponse.ok(slowMovingService.create(slow));
    }

    /** 确认呆滞（E-38 责任归因必填） */
    @PostMapping("/{id}/confirm")
    @RequirePermission("inv:slow:manage")
    public ApiResponse<InvSlowMoving> confirm(@PathVariable Long id) {
        return ApiResponse.ok(slowMovingService.confirm(id));
    }

    /** 发起处置（E-39 方式，DP 编号，A-31 分级） */
    @PostMapping("/{id}/disposals")
    @RequirePermission("inv:slow:manage")
    public ApiResponse<InvDisposal> createDisposal(@PathVariable Long id, @RequestBody InvDisposal disposal) {
        return ApiResponse.ok(slowMovingService.createDisposal(id, disposal));
    }

    /** 处置审批（L1/L2/L3） */
    @PostMapping("/disposals/{disposalId}/approve")
    @RequirePermission("inv:slow:manage")
    public ApiResponse<InvDisposal> approve(@PathVariable Long disposalId, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(slowMovingService.approve(disposalId, body.get("level")));
    }

    /** 处置完成（回填实际金额） */
    @PostMapping("/disposals/{disposalId}/complete")
    @RequirePermission("inv:slow:manage")
    public ApiResponse<InvDisposal> complete(@PathVariable Long disposalId,
                                             @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(slowMovingService.complete(disposalId,
                body == null || body.get("actualAmount") == null
                        ? null : new BigDecimal(body.get("actualAmount"))));
    }

    @GetMapping
    @RequirePermission("inv:slow:manage")
    public ApiResponse<List<InvSlowMoving>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Long materialId) {
        return ApiResponse.ok(slowMovingService.list(status, materialId));
    }

    /** SPRINT-UID-R15：处置单列表（审批/完成入口数据源） */
    @GetMapping("/disposals")
    @RequirePermission("inv:slow:manage")
    public ApiResponse<List<InvDisposal>> disposals() {
        return ApiResponse.ok(slowMovingService.disposals());
    }
}
