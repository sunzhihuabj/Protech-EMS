package com.projlife.inventory.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.inventory.entity.InvCount;
import com.projlife.inventory.entity.InvCountLine;
import com.projlife.inventory.entity.InvDiff;
import com.projlife.inventory.service.InventoryCountService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 盘点接口（V4-04 M07）：/api/v1/inv/counts。
 */
@RestController
@RequestMapping("/api/v1/inv/counts")
public class InventoryCountController {

    private final InventoryCountService countService;

    public InventoryCountController(InventoryCountService countService) {
        this.countService = countService;
    }

    @PostMapping
    @RequirePermission("inv:count:manage")
    public ApiResponse<InvCount> create(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> countMap = (Map<String, Object>) body.get("count");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineMaps = (List<Map<String, Object>>) body.get("lines");
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        InvCount count = om.convertValue(countMap, InvCount.class);
        List<InvCountLine> lines = new java.util.ArrayList<>();
        for (Map<String, Object> lineMap : lineMaps) {
            lines.add(om.convertValue(lineMap, InvCountLine.class));
        }
        return ApiResponse.ok(countService.create(count, lines));
    }

    @GetMapping("/{id}/lines")
    @RequirePermission("inv:count:manage")
    public ApiResponse<List<InvCountLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(countService.lines(id));
    }

    @PostMapping("/{id}/start")
    @RequirePermission("inv:count:manage")
    public ApiResponse<InvCount> start(@PathVariable Long id) {
        return ApiResponse.ok(countService.start(id));
    }

    @PostMapping("/{id}/entry")
    @RequirePermission("inv:count:manage")
    public ApiResponse<Integer> entry(@PathVariable Long id, @RequestBody List<InvCountLine> actuals) {
        return ApiResponse.ok(countService.entry(id, actuals));
    }

    /** 复核生成差异单（E-37 无归因不调账） */
    @PostMapping("/{id}/review")
    @RequirePermission("inv:count:manage")
    public ApiResponse<List<InvDiff>> review(@PathVariable Long id) {
        return ApiResponse.ok(countService.review(id));
    }

    /** 差异审批（L1/L2/L3） */
    @PostMapping("/diffs/{diffId}/approve")
    @RequirePermission("inv:count:manage")
    public ApiResponse<InvDiff> approve(@PathVariable Long diffId, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(countService.approve(diffId, body.get("level")));
    }
}
