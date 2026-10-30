package com.projlife.mes.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.mes.entity.ApsCapacity;
import com.projlife.mes.entity.ApsSchedule;
import com.projlife.mes.entity.ApsScheduleDetail;
import com.projlife.mes.service.ApsService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * APS 排产接口（V4-04 M06）：/api/v1/aps。
 */
@RestController
@RequestMapping("/api/v1/aps")
public class ApsController {

    private final ApsService apsService;

    public ApsController(ApsService apsService) {
        this.apsService = apsService;
    }

    @PostMapping("/capacities")
    @RequirePermission("aps:schedule")
    public ApiResponse<ApsCapacity> createCapacity(@RequestBody ApsCapacity capacity) {
        return ApiResponse.ok(apsService.createCapacity(capacity));
    }

    /** L1 顺排（产能正向 + 冲突清单） */
    @PostMapping("/schedules/l1")
    @RequirePermission("aps:schedule")
    public ApiResponse<Map<String, Object>> scheduleL1(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) body.get("productionOrderIds");
        List<Long> orderIds = new java.util.ArrayList<>();
        for (Object id : ids) {
            // 全局 Long→String 序列化：逐元素 String→Long
            orderIds.add(Long.valueOf(String.valueOf(id)));
        }
        LocalDate startDate = body.get("startDate") == null ? null
                : LocalDate.parse((String) body.get("startDate"));
        return ApiResponse.ok(apsService.scheduleL1(orderIds, startDate));
    }

    @GetMapping("/schedules/{id}/details")
    @RequirePermission("aps:schedule")
    public ApiResponse<List<ApsScheduleDetail>> details(@PathVariable Long id) {
        return ApiResponse.ok(apsService.detailsOf(id));
    }

    /** L2 人工调整（拖拽） */
    @PostMapping("/details/{id}/adjust")
    @RequirePermission("aps:schedule")
    public ApiResponse<ApsScheduleDetail> adjust(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LocalDateTime start = body.get("plannedStart") == null ? null
                : LocalDateTime.parse(body.get("plannedStart"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime end = body.get("plannedEnd") == null ? null
                : LocalDateTime.parse(body.get("plannedEnd"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return ApiResponse.ok(apsService.adjust(id, start, end));
    }

    /** 发布（齐套缺料阻断） */
    @PostMapping("/schedules/{id}/publish")
    @RequirePermission("aps:schedule")
    public ApiResponse<ApsSchedule> publish(@PathVariable Long id) {
        return ApiResponse.ok(apsService.publish(id));
    }
}
