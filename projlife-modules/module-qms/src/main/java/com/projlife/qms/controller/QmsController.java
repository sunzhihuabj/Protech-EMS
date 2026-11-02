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
    private final com.projlife.qms.mapper.QmsSpcDataMapper spcDataMapper;

    public QmsController(QmsService qmsService,
                         com.projlife.qms.mapper.QmsSpcDataMapper spcDataMapper) {
        this.qmsService = qmsService;
        this.spcDataMapper = spcDataMapper;
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

    // ---------- SPC（FR-QMS-09） ----------

    @GetMapping("/spc")
    @RequirePermission("qms:inspection")
    public ApiResponse<java.util.List<com.projlife.qms.entity.QmsSpcData>> spc(
            @RequestParam(required = false) String characteristic) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.projlife.qms.entity.QmsSpcData>()
                .eq(com.projlife.qms.entity.QmsSpcData::getDeleted, 0)
                .orderByAsc(com.projlife.qms.entity.QmsSpcData::getSampleTime);
        if (characteristic != null && !characteristic.isEmpty()) {
            wrapper.eq(com.projlife.qms.entity.QmsSpcData::getCharacteristic, characteristic);
        }
        java.util.List<com.projlife.qms.entity.QmsSpcData> rows = spcDataMapper.selectList(wrapper.last("LIMIT 100"));
        // SPRINT-OPS1 F10：X 单值图控制限 mean±3σ（≥3 点才计算；PtSpcChart 直接读行上的 ucl/cl/lcl）
        if (!rows.isEmpty()) {
            java.util.Map<String, java.util.List<com.projlife.qms.entity.QmsSpcData>> groups = new java.util.LinkedHashMap<>();
            rows.forEach(r -> groups.computeIfAbsent(r.getCharacteristic(), k -> new java.util.ArrayList<>()).add(r));
            for (java.util.List<com.projlife.qms.entity.QmsSpcData> g : groups.values()) {
                if (g.size() < 3) {
                    continue;
                }
                double mean = g.stream().mapToDouble(r -> r.getValueX().doubleValue()).average().orElse(0);
                double var = g.stream().mapToDouble(r -> {
                    double d = r.getValueX().doubleValue() - mean;
                    return d * d;
                }).average().orElse(0);
                double sigma = Math.sqrt(var);
                double ucl = mean + 3 * sigma;
                double lcl = Math.max(0, mean - 3 * sigma);
                for (com.projlife.qms.entity.QmsSpcData r : g) {
                    double v = r.getValueX().doubleValue();
                    r.setCl(java.math.BigDecimal.valueOf(mean).setScale(4, java.math.RoundingMode.HALF_UP));
                    r.setUcl(java.math.BigDecimal.valueOf(ucl).setScale(4, java.math.RoundingMode.HALF_UP));
                    r.setLcl(java.math.BigDecimal.valueOf(lcl).setScale(4, java.math.RoundingMode.HALF_UP));
                    r.setIsOutOfControl(v > ucl || v < lcl ? 1 : 0);
                }
            }
        }
        return ApiResponse.ok(rows);
    }
}
