package com.projlife.plm.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.plm.entity.BomChange;
import com.projlife.plm.entity.BomHeader;
import com.projlife.plm.entity.BomLine;
import com.projlife.plm.entity.PcsProcessDoc;
import com.projlife.plm.entity.PcsRoute;
import com.projlife.plm.entity.PcsRouteStep;
import com.projlife.plm.service.BomService;
import com.projlife.plm.service.PcsService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 研发与工艺接口（06 册 §3 页面清单核心）。JSON 列入参约定传 JSON 字符串（docs/API.md）。
 */
@RestController
@RequestMapping("/api/v1/plm")
public class PlmController {

    private final BomService bomService;
    private final PcsService pcsService;

    public PlmController(BomService bomService, PcsService pcsService) {
        this.bomService = bomService;
        this.pcsService = pcsService;
    }

    // ---------- BOM ----------

    @GetMapping("/bom")
    @RequirePermission("bom:edit")
    public ApiResponse<PageResp<BomHeader>> page(PageReq pageReq,
                                                 @RequestParam(required = false) String form,
                                                 @RequestParam(required = false) String view,
                                                 @RequestParam(required = false) String productLine) {
        return ApiResponse.ok(bomService.page(pageReq, form, view, productLine));
    }

    @GetMapping("/bom/{id}")
    @RequirePermission("bom:edit")
    public ApiResponse<BomHeader> detail(@PathVariable Long id) {
        return ApiResponse.ok(bomService.detail(id));
    }

    @GetMapping("/bom/{id}/lines")
    @RequirePermission("bom:edit")
    public ApiResponse<List<BomLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(bomService.linesOf(id));
    }

    @PostMapping("/bom")
    @RequirePermission("bom:create")
    public ApiResponse<Long> create(@RequestBody BomHeader header) {
        return ApiResponse.ok(bomService.create(header));
    }

    @PostMapping("/bom/{id}/lines")
    @RequirePermission("bom:edit")
    public ApiResponse<Long> addLine(@PathVariable Long id, @RequestBody BomLine line) {
        line.setBomHeaderId(id);
        return ApiResponse.ok(bomService.addLine(line));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/bom/lines/{lineId}")
    @RequirePermission("bom:edit")
    public ApiResponse<Void> removeLine(@PathVariable Long lineId) {
        bomService.removeLine(lineId);
        return ApiResponse.ok();
    }

    /** 13 条校验（保存实时） */
    /** FR-PLM：13 条校验批量跑批（结果快照落 bom_header.qc_result，A-34） */
    @PostMapping("/bom/qc-batch")
    @RequirePermission("bom:release")
    public ApiResponse<Map<String, Object>> runQcBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<Object> ids = (java.util.List<Object>) body.get("bomIds");
        java.util.List<Long> bomIds = new java.util.ArrayList<>();
        for (Object id : ids) {
            bomIds.add(Long.valueOf(String.valueOf(id)));
        }
        return ApiResponse.ok(bomService.runQcBatch(bomIds));
    }

    @GetMapping("/bom/{id}/qc-result")
    public ApiResponse<Map<String, Object>> qcResult(@PathVariable Long id) {
        return ApiResponse.ok(bomService.qcResultOf(id));
    }

    @GetMapping("/bom/{id}/validate")
    @RequirePermission("bom:edit")
    public ApiResponse<List<Map<String, Object>>> validate(@PathVariable Long id) {
        return ApiResponse.ok(bomService.validate(id));
    }

    @PostMapping("/bom/{id}/release")
    @RequirePermission("bom:release")
    public ApiResponse<Map<String, Object>> release(@PathVariable Long id) {
        return ApiResponse.ok(bomService.release(id));
    }

    @PostMapping("/bom/{goldenId}/derive")
    @RequirePermission("bom:create")
    public ApiResponse<Long> derive(@PathVariable Long goldenId, @RequestParam Long mdProjectId) {
        return ApiResponse.ok(bomService.derive(goldenId, mdProjectId));
    }

    @PostMapping("/bom/{sourceId}/convert")
    @RequirePermission("bom:edit")
    public ApiResponse<Long> convert(@PathVariable Long sourceId, @RequestParam String targetView) {
        return ApiResponse.ok(bomService.convert(sourceId, targetView));
    }

    @GetMapping("/bom/diff")
    @RequirePermission("bom:edit")
    public ApiResponse<Map<String, Object>> diff(@RequestParam Long bomAId, @RequestParam Long bomBId) {
        return ApiResponse.ok(bomService.diff(bomAId, bomBId));
    }

    @PostMapping("/bom/{templateId}/expand")
    @RequirePermission("bom:create")
    public ApiResponse<Long> expand(@PathVariable Long templateId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        Object rawProject = body.get("mdProjectId");
        Long mdProjectId = rawProject == null ? null : Long.valueOf(String.valueOf(rawProject));
        return ApiResponse.ok(bomService.expand(templateId, params, mdProjectId));
    }

    // ---------- ECN ----------

    @PostMapping("/bom/{bomId}/ecn")
    @RequirePermission("bom:ecn:create")
    public ApiResponse<Long> createEcn(@PathVariable Long bomId, @RequestBody BomChange change) {
        change.setBomHeaderId(bomId);
        return ApiResponse.ok(bomService.createEcn(change));
    }

    /** ECN 台账（SPRINT-ORPHAN1：提交后此前无任何查询/审批入口，断半环） */
    @GetMapping("/ecn")
    @RequirePermission("bom:edit")
    public ApiResponse<List<BomChange>> ecnList(@RequestParam(required = false) String status) {
        return ApiResponse.ok(bomService.ecnList(status));
    }

    @PostMapping("/ecn/{changeId}/publish")
    @RequirePermission("bom:ecn:approve")
    public ApiResponse<Map<String, Object>> publishEcn(@PathVariable Long changeId,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(bomService.publishEcn(changeId, Boolean.TRUE.equals(body.get("approved"))));
    }

    // ---------- 工艺 ----------

    @GetMapping("/pcs/docs")
    @RequirePermission("pcs:manage")
    public ApiResponse<PageResp<PcsProcessDoc>> docs(PageReq pageReq,
                                                     @RequestParam(required = false) String productLine,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.ok(pcsService.docs(pageReq, productLine, status));
    }

    @PostMapping("/pcs/docs")
    @RequirePermission("pcs:manage")
    public ApiResponse<Long> createDoc(@RequestBody PcsProcessDoc doc) {
        return ApiResponse.ok(pcsService.createDoc(doc));
    }

    @PostMapping("/pcs/docs/{id}/publish")
    @RequirePermission("pcs:manage")
    public ApiResponse<Void> publishDoc(@PathVariable Long id) {
        pcsService.publish(id);
        return ApiResponse.ok();
    }

    @PostMapping("/pcs/docs/{id}/pause")
    @RequirePermission("pcs:manage")
    public ApiResponse<Void> pauseDoc(@PathVariable Long id) {
        pcsService.pause(id);
        return ApiResponse.ok();
    }

    @PostMapping("/pcs/routes")
    @RequirePermission("pcs:manage")
    public ApiResponse<Long> createRoute(@RequestBody Map<String, Object> body) {
        PcsRoute route = new PcsRoute();
        route.setBomHeaderId(Long.valueOf(String.valueOf(body.get("bomHeaderId"))));
        route.setRouteName((String) body.get("routeName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stepList = (List<Map<String, Object>>) body.get("steps");
        List<PcsRouteStep> steps = new java.util.ArrayList<>();
        if (stepList != null) {
            for (Map<String, Object> row : stepList) {
                PcsRouteStep step = new PcsRouteStep();
                step.setStepName((String) row.get("stepName"));
                if (row.get("standardHours") != null) {
                    step.setStandardHours(new java.math.BigDecimal(String.valueOf(row.get("standardHours"))));
                }
                step.setIsKey(Boolean.TRUE.equals(row.get("isKey")) ? 1 : 0);
                step.setNeedInspection(Boolean.TRUE.equals(row.get("needInspection")) ? 1 : 0);
                steps.add(step);
            }
        }
        return ApiResponse.ok(pcsService.createRoute(route, steps));
    }

    @GetMapping("/pcs/routes/{id}/steps")
    @RequirePermission("pcs:manage")
    public ApiResponse<List<PcsRouteStep>> steps(@PathVariable Long id) {
        return ApiResponse.ok(pcsService.stepsOf(id));
    }

    /** FR-PCS-11 覆盖度矩阵 */
    @GetMapping("/pcs/coverage")
    public ApiResponse<List<Map<String, Object>>> coverage() {
        return ApiResponse.ok(pcsService.coverageMatrix());
    }
}
