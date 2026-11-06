package com.projlife.eng.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.eng.entity.EngAfterSales;
import com.projlife.eng.entity.EngDailyLog;
import com.projlife.eng.entity.EngMaterialIssue;
import com.projlife.eng.entity.EngQualityCheck;
import com.projlife.eng.service.EngRecordService;
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
 * 工程日常记录接口（V4-05 M10）：施工日志/质量检查/现场物料/售后工单。
 */
@RestController
@RequestMapping("/api/v1/eng/records")
public class EngRecordController {

    private final EngRecordService recordService;

    public EngRecordController(EngRecordService recordService) {
        this.recordService = recordService;
    }

    /** 施工日志（AI-009 合规检查随记，降级不阻塞） */
    @PostMapping("/daily-logs")
    @RequirePermission("eng:log")
    public ApiResponse<EngDailyLog> dailyLog(@RequestBody EngDailyLog log) {
        return ApiResponse.ok(recordService.dailyLog(log));
    }

    /** 质量检查（FAIL 必填缺陷描述） */
    @PostMapping("/quality-checks")
    @RequirePermission("eng:install")
    public ApiResponse<EngQualityCheck> qualityCheck(@RequestBody EngQualityCheck check) {
        return ApiResponse.ok(recordService.qualityCheck(check));
    }

    @PostMapping("/material-issues")
    @RequirePermission("eng:expense")
    public ApiResponse<EngMaterialIssue> issue(@RequestBody EngMaterialIssue issue) {
        return ApiResponse.ok(recordService.issue(issue));
    }

    /** 领料台账（SPRINT-ORPHAN1：领料后此前无查询/退料入口） */
    @GetMapping("/material-issues")
    @RequirePermission("eng:expense")
    public ApiResponse<List<EngMaterialIssue>> issues(@RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(recordService.issueList(projectId));
    }

    /** 现场退料（累计不超领料） */
    @PostMapping("/material-issues/{id}/return")
    @RequirePermission("eng:expense")
    public ApiResponse<EngMaterialIssue> returnMaterial(@PathVariable Long id,
                                                        @RequestBody Map<String, String> body) {
        return ApiResponse.ok(recordService.returnMaterial(id,
                body.get("qty") == null ? null : new BigDecimal(body.get("qty"))));
    }

    @PostMapping("/after-sales")
    @RequirePermission("eng:after:manage")
    public ApiResponse<EngAfterSales> createAfterSales(@RequestBody EngAfterSales wo) {
        return ApiResponse.ok(recordService.createAfterSales(wo));
    }

    @PostMapping("/after-sales/{id}/respond")
    @RequirePermission("eng:after:manage")
    public ApiResponse<EngAfterSales> respond(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ApiResponse.ok(recordService.respond(id, body.get("handlerId")));
    }

    /** 售后闭环（方案必填 + 客户确认） */
    @PostMapping("/after-sales/{id}/close")
    @RequirePermission("eng:after:manage")
    public ApiResponse<EngAfterSales> close(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(recordService.close(id, body.get("solution"),
                body.get("costAmount") == null ? null : new BigDecimal(body.get("costAmount"))));
    }

    @GetMapping("/after-sales")
    @RequirePermission("eng:after:manage")
    public ApiResponse<List<EngAfterSales>> afterSales(@RequestParam(required = false) String status) {
        return ApiResponse.ok(recordService.afterSalesList(status));
    }
}
