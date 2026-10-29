package com.projlife.inventory.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.inventory.entity.InvReplenish;
import com.projlife.inventory.entity.InvReplenishLine;
import com.projlife.inventory.entity.InvTransaction;
import com.projlife.inventory.service.InventoryService;
import com.projlife.inventory.service.ReplenishService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 库房接口（07 册：扫码作业/补货/对账）。
 */
@RestController
@RequestMapping("/api/v1/inv")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ReplenishService replenishService;

    public InventoryController(InventoryService inventoryService, ReplenishService replenishService) {
        this.inventoryService = inventoryService;
        this.replenishService = replenishService;
    }

    /** FR-INV-08/09：扫码提交事务（幂等键由前端生成：来源单号+行号+动作） */
    @PostMapping("/transactions")
    @RequirePermission("inv:scan")
    public ApiResponse<InvTransaction> submit(@RequestBody InvTransaction txn) {
        return ApiResponse.ok(inventoryService.submit(txn));
    }

    /** 幂等查询（重复扫码回显首次结果） */
    @GetMapping("/transactions/by-key")
    @RequirePermission("inv:scan")
    public ApiResponse<InvTransaction> byKey(@RequestParam String idempotencyKey) {
        return ApiResponse.ok(inventoryService.byIdempotencyKey(idempotencyKey));
    }

    @PostMapping("/transactions/retry-exceptions")
    @RequirePermission("inv:count:manage")
    public ApiResponse<Integer> retryExceptions() {
        return ApiResponse.ok(inventoryService.retryExceptions());
    }

    @GetMapping("/stock/available")
    @RequirePermission("inv:scan")
    public ApiResponse<BigDecimal> available(@RequestParam Long legalEntityId,
                                             @RequestParam String warehouseCode,
                                             @RequestParam Long materialId) {
        return ApiResponse.ok(inventoryService.availableQty(legalEntityId, warehouseCode, materialId));
    }

    /** FR-INV-41：每日对账 */
    @PostMapping("/reconcile")
    @RequirePermission("inv:count:manage")
    public ApiResponse<Object> reconcile(@RequestBody Map<String, String> body) {
        LocalDate date = LocalDate.parse(body.get("date"));
        Long legalEntityId = Long.valueOf(body.get("legalEntityId"));
        return ApiResponse.ok(inventoryService.dailyReconcile(date, legalEntityId));
    }

    // ---------- 补货 ----------

    @GetMapping("/replenish")
    @RequirePermission("inv:replenish:create")
    public ApiResponse<PageResp<InvReplenish>> page(PageReq pageReq,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(replenishService.page(pageReq, status, null));
    }

    @PostMapping("/replenish")
    @RequirePermission("inv:replenish:create")
    public ApiResponse<Long> create(@RequestBody Map<String, Object> body) {
        InvReplenish replenish = new InvReplenish();
        replenish.setMdProjectId(toLong(body.get("mdProjectId")));
        replenish.setReason((String) body.get("reason"));
        replenish.setUrgency((String) body.getOrDefault("urgency", "NORMAL"));
        replenish.setAmount(toDecimal(body.get("amount")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineList = (List<Map<String, Object>>) body.get("lines");
        List<InvReplenishLine> lines = new ArrayList<>();
        for (Map<String, Object> row : lineList == null ? new ArrayList<Map<String, Object>>() : lineList) {
            InvReplenishLine line = new InvReplenishLine();
            line.setMaterialId(Long.valueOf(String.valueOf(row.get("materialId"))));
            line.setQty(new BigDecimal(String.valueOf(row.get("qty"))));
            line.setUnit((String) row.getOrDefault("unit", "PCS"));
            line.setUseLocation((String) row.get("useLocation"));
            lines.add(line);
        }
        return ApiResponse.ok(replenishService.create(replenish, lines));
    }

    @PostMapping("/replenish/{id}/approve")
    @RequirePermission("inv:replenish:approve")
    public ApiResponse<String> approve(@PathVariable Long id) {
        return ApiResponse.ok(replenishService.approve(id));
    }

    @PostMapping("/replenish/{id}/issue")
    @RequirePermission("inv:scan")
    public ApiResponse<Void> issue(@PathVariable Long id) {
        replenishService.issue(id);
        return ApiResponse.ok();
    }

    @PostMapping("/replenish/{id}/backfill")
    @RequirePermission("inv:replenish:create")
    public ApiResponse<Void> backfill(@PathVariable Long id) {
        replenishService.backfill(id);
        return ApiResponse.ok();
    }

    /** BR-05：25h 未补签锁定（job 每小时入口） */
    @PostMapping("/replenish/lock-overdue")
    public ApiResponse<Integer> lockOverdue() {
        return ApiResponse.ok(replenishService.lockOverdueBackfills());
    }

    @GetMapping("/replenish/{id}/lines")
    @RequirePermission("inv:replenish:create")
    public ApiResponse<List<InvReplenishLine>> replenishLines(@PathVariable Long id) {
        return ApiResponse.ok(replenishService.linesOf(id));
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
