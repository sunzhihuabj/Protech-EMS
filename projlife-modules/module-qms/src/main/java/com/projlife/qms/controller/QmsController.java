package com.projlife.qms.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.qms.entity.Qms8d;
import com.projlife.qms.entity.QmsGauge;
import com.projlife.qms.entity.QmsInspection;
import com.projlife.qms.service.QmsService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 质量接口（09 册）。
 */
@RestController
@RequestMapping("/api/v1/qms")
public class QmsController {

    private final QmsService qmsService;

    public QmsController(QmsService qmsService) {
        this.qmsService = qmsService;
    }

    @GetMapping("/inspections")
    @RequirePermission("qms:inspect")
    public ApiResponse<PageResp<QmsInspection>> page(PageReq pageReq,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String result) {
        return ApiResponse.ok(qmsService.page(pageReq, type, result));
    }

    @PostMapping("/inspections")
    @RequirePermission("qms:inspect")
    public ApiResponse<Long> create(@RequestBody QmsInspection inspection) {
        return ApiResponse.ok(qmsService.createInspection(inspection));
    }

    /** AI 改判留痕 */
    @PostMapping("/inspections/{id}/correct")
    @RequirePermission("qms:inspect")
    public ApiResponse<Void> correct(@PathVariable Long id, @RequestBody Map<String, String> body) {
        qmsService.correct(id, body.get("result"), body.get("remark"));
        return ApiResponse.ok();
    }

    @PostMapping("/8ds")
    @RequirePermission("qms:8d:manage")
    public ApiResponse<Long> create8d(@RequestBody Qms8d report) {
        return ApiResponse.ok(qmsService.create8d(report));
    }

    @PostMapping("/8ds/{id}/close")
    @RequirePermission("qms:8d:manage")
    public ApiResponse<Void> close8d(@PathVariable Long id) {
        qmsService.close8d(id);
        return ApiResponse.ok();
    }

    @GetMapping("/gauges/usable")
    public ApiResponse<List<QmsGauge>> usableGauges() {
        return ApiResponse.ok(qmsService.usableGauges());
    }

    @PostMapping("/gauges/{id}/calibrate")
    @RequirePermission("qms:gauge:manage")
    public ApiResponse<Void> calibrate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        qmsService.calibrate(id,
                body.get("nextDate") == null ? null : LocalDate.parse(body.get("nextDate")),
                body.get("result"), body.get("certificateNo"));
        return ApiResponse.ok();
    }

    @GetMapping("/assets/{assetId}/mtbf")
    public ApiResponse<Map<String, Object>> mtbf(@PathVariable Long assetId) {
        return ApiResponse.ok(qmsService.mtbf(assetId));
    }
}
