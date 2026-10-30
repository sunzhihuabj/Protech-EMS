package com.projlife.cost.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.cost.entity.KbMetricDef;
import com.projlife.cost.entity.KbMetricSnapshot;
import com.projlife.cost.service.KbMetricService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 看板指标接口（V4-05 M12）：/api/v1/kb/metrics。
 */
@RestController
@RequestMapping("/api/v1/kb/metrics")
public class KbMetricController {

    private final KbMetricService metricService;

    public KbMetricController(KbMetricService metricService) {
        this.metricService = metricService;
    }

    @PostMapping
    @RequirePermission("kb:metric:def")
    public ApiResponse<KbMetricDef> createDef(@RequestBody KbMetricDef def) {
        return ApiResponse.ok(metricService.createDef(def));
    }

    /** 指标快照（灯色判定） */
    @PostMapping("/{metricCode}/snapshots")
    @RequirePermission("kb:metric:def")
    public ApiResponse<KbMetricSnapshot> snapshot(@PathVariable String metricCode,
                                                  @RequestBody Map<String, String> body) {
        return ApiResponse.ok(metricService.snapshot(metricCode, body.get("period"),
                body.get("value") == null ? null : new BigDecimal(body.get("value"))));
    }

    /** 趋势下钻 */
    @GetMapping("/{metricCode}/trend")
    @RequirePermission("kb:metric:def")
    public ApiResponse<Map<String, Object>> trend(@PathVariable String metricCode,
                                                  @RequestParam(defaultValue = "12") int periods) {
        return ApiResponse.ok(metricService.trend(metricCode, periods));
    }
}
