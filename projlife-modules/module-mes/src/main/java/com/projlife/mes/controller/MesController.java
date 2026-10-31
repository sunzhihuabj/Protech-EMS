package com.projlife.mes.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.mes.entity.MesAndon;
import com.projlife.mes.entity.MesProcessOrder;
import com.projlife.mes.entity.MesProductionOrder;
import com.projlife.mes.service.MesService;
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
 * 生产接口（08 册 §2/§3）。
 */
@RestController
@RequestMapping("/api/v1/mes")
public class MesController {

    private final MesService mesService;

    public MesController(MesService mesService) {
        this.mesService = mesService;
    }

    @GetMapping("/orders")
    @RequirePermission("mes:order:view")
    public ApiResponse<PageResp<MesProductionOrder>> page(PageReq pageReq,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(mesService.page(pageReq, status));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<MesProductionOrder> detail(@PathVariable Long id) {
        return ApiResponse.ok(mesService.productionDetail(id));
    }

    @PostMapping("/orders")
    @com.projlife.security.auth.RequirePermission("mes:order:create")
    public ApiResponse<Long> create(@RequestBody MesProductionOrder order) {
        return ApiResponse.ok(mesService.createProductionOrder(order));
    }

    /** FR-MES-03：按步骤序列生成工序工单（steps: [{name, code, needInspection}]） */
    @PostMapping("/orders/{id}/routes")
    @com.projlife.security.auth.RequirePermission("mes:order:create")
    public ApiResponse<Integer> generateRoutes(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stepList = (List<Map<String, Object>>) body.get("steps");
        List<String[]> steps = new java.util.ArrayList<>();
        List<BigDecimal> hours = new java.util.ArrayList<>();
        for (Map<String, Object> step : stepList) {
            steps.add(new String[]{
                    String.valueOf(step.get("name")),
                    step.get("code") == null ? "" : String.valueOf(step.get("code")),
                    Boolean.TRUE.equals(step.get("needInspection")) ? "1" : "0"
            });
            hours.add(step.get("standardHours") == null ? null
                    : new BigDecimal(String.valueOf(step.get("standardHours"))));
        }
        return ApiResponse.ok(mesService.generateRoutes(id, steps, hours));
    }

    @GetMapping("/orders/{id}/processes")
    public ApiResponse<List<MesProcessOrder>> processes(@PathVariable Long id) {
        return ApiResponse.ok(mesService.processesOf(id));
    }

    /** FR-MES-12：开工（串行前置校验） */
    @PostMapping("/processes/{id}/start")
    @com.projlife.security.auth.RequirePermission("mes:process:work")
    public ApiResponse<Void> start(@PathVariable Long id) {
        mesService.startProcess(id);
        return ApiResponse.ok();
    }

    /** FR-MES-13/14/15：完工报工 */
    @PostMapping("/processes/{id}/finish")
    @com.projlife.security.auth.RequirePermission("mes:process:work")
    public ApiResponse<Map<String, Object>> finish(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(mesService.finishProcess(id,
                toDecimal(body.get("goodQty")), toDecimal(body.get("defectQty")),
                toDecimal(body.get("scrapQty")), toDecimal(body.get("workHours")),
                (String) body.get("note")));
    }

    /** FR-MES-16：检验放行 */
    @PostMapping("/processes/{id}/qc")
    @com.projlife.security.auth.RequirePermission("mes:qc:release")
    public ApiResponse<Void> qc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        mesService.qcRelease(id, Boolean.TRUE.equals(body.get("passed")), (String) body.get("rejectReason"));
        return ApiResponse.ok();
    }

    /** FR-MES-28：Andon 触发 */
    @PostMapping("/andon")
    @com.projlife.security.auth.RequirePermission("mes:andon:raise")
    public ApiResponse<MesAndon> raiseAndon(@RequestBody Map<String, Object> body) {
        Long poId = body.get("productionOrderId") == null ? null : Long.valueOf(String.valueOf(body.get("productionOrderId")));
        Long procId = body.get("processOrderId") == null ? null : Long.valueOf(String.valueOf(body.get("processOrderId")));
        return ApiResponse.ok(mesService.raiseAndon(poId, procId,
                (String) body.get("alertType"), (String) body.get("content")));
    }

    /** FR-MES-30：Andon 升级（job 入口） */
    @PostMapping("/andon/escalate")
    @com.projlife.security.auth.RequirePermission("mes:andon:raise")
    public ApiResponse<Integer> escalate() {
        return ApiResponse.ok(mesService.escalateAndons());
    }

    /** FR-APS-05：齐套检查（口径说明版，BOM 级精确齐套随 aps_material_check） */
    @GetMapping("/orders/{id}/material-check")
    public ApiResponse<Map<String, Object>> materialCheck(@PathVariable Long id) {
        return ApiResponse.ok(mesService.materialCheck(id));
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
