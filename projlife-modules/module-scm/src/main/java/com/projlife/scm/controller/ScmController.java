package com.projlife.scm.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.scm.entity.ScmPurchaseOrder;
import com.projlife.scm.entity.ScmPurchaseOrderItem;
import com.projlife.scm.entity.ScmPurchaseRequirement;
import com.projlife.scm.entity.ScmSupplierOnboarding;
import com.projlife.scm.service.ScmService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 供应链接口（07 册 §2）。BR-SCM-03：无 scm:order:view_price 角色的 unit_price/amount 为 null（服务端脱敏）。
 */
@RestController
@RequestMapping("/api/v1/scm")
public class ScmController {

    private final ScmService scmService;

    public ScmController(ScmService scmService) {
        this.scmService = scmService;
    }

    @PostMapping("/suppliers/{supplierId}/register")
    @RequirePermission("scm:supplier:manage")
    public ApiResponse<Long> register(@PathVariable Long supplierId) {
        return ApiResponse.ok(scmService.register(supplierId));
    }

    @PostMapping("/suppliers/{supplierId}/advance")
    @RequirePermission("scm:supplier:manage")
    public ApiResponse<ScmSupplierOnboarding> advance(@PathVariable Long supplierId,
                                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(scmService.advanceStage(supplierId,
                Boolean.TRUE.equals(body.get("passed")), (String) body.get("auditResult")));
    }

    @GetMapping("/suppliers/{supplierId}/onboarding")
    @RequirePermission("scm:supplier:manage")
    public ApiResponse<ScmSupplierOnboarding> onboarding(@PathVariable Long supplierId) {
        return ApiResponse.ok(scmService.onboardingOf(supplierId));
    }

    @GetMapping("/requirements")
    @RequirePermission("scm:req:create")
    public ApiResponse<PageResp<ScmPurchaseRequirement>> requirements(PageReq pageReq,
                                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(scmService.requirements(pageReq, status));
    }

    @PostMapping("/requirements")
    @RequirePermission("scm:req:create")
    public ApiResponse<Long> createRequirement(@RequestBody ScmPurchaseRequirement requirement) {
        return ApiResponse.ok(scmService.createRequirement(requirement));
    }

    @GetMapping("/orders")
    @RequirePermission("scm:order:view")
    public ApiResponse<PageResp<Map<String, Object>>> orders(PageReq pageReq,
                                                             @RequestParam(required = false) String status) {
        PageResp<ScmPurchaseOrder> page = scmService.orders(pageReq, status);
        boolean viewPrice = userHasPrice();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScmPurchaseOrder order : page.getRecords()) {
            rows.add(maskOrder(order, viewPrice));
        }
        PageResp<Map<String, Object>> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(rows);
        return ApiResponse.ok(resp);
    }

    @GetMapping("/orders/{id}")
    @RequirePermission("scm:order:view")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable Long id) {
        boolean viewPrice = userHasPrice();
        ScmPurchaseOrder order = scmService.getById(id);
        if (order == null) {
            throw com.projlife.common.exception.BizException.notFound("采购订单不存在");
        }
        Map<String, Object> masked = maskOrder(order, viewPrice);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ScmPurchaseOrderItem item : scmService.itemsOf(id)) {
            Map<String, Object> itemRow = new java.util.HashMap<>();
            itemRow.put("id", item.getId());
            itemRow.put("lineNo", item.getLineNo());
            itemRow.put("materialId", item.getMaterialId());
            itemRow.put("qty", item.getQty());
            itemRow.put("receivedQty", item.getReceivedQty());
            itemRow.put("status", item.getStatus());
            if (viewPrice) {
                itemRow.put("unitPrice", item.getUnitPrice());
                itemRow.put("amount", item.getAmount());
            }
            items.add(itemRow);
        }
        masked.put("items", items);
        return ApiResponse.ok(masked);
    }

    @PostMapping("/orders")
    @RequirePermission("scm:order:create")
    public ApiResponse<Long> createOrder(@RequestBody Map<String, Object> body) {
        ScmPurchaseOrder order = new ScmPurchaseOrder();
        order.setSupplierId(Long.valueOf(String.valueOf(body.get("supplierId"))));
        order.setLegalEntityId(Long.valueOf(String.valueOf(body.get("legalEntityId"))));
        if (body.get("taxRate") != null) {
            order.setTaxRate(new BigDecimal(String.valueOf(body.get("taxRate"))));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) body.get("items");
        List<ScmPurchaseOrderItem> items = new ArrayList<>();
        for (Map<String, Object> row : itemList == null ? new ArrayList<Map<String, Object>>() : itemList) {
            ScmPurchaseOrderItem item = new ScmPurchaseOrderItem();
            if (row.get("projectId") != null) {
                item.setProjectId(Long.valueOf(String.valueOf(row.get("projectId"))));
            }
            item.setMaterialId(Long.valueOf(String.valueOf(row.get("materialId"))));
            item.setQty(new BigDecimal(String.valueOf(row.get("qty"))));
            if (row.get("unitPrice") != null) {
                item.setUnitPrice(new BigDecimal(String.valueOf(row.get("unitPrice"))));
            }
            items.add(item);
        }
        @SuppressWarnings("unchecked")
        List<Object> rawReqIds = (List<Object>) body.get("requirementIds");
        List<Long> requirementIds = null;
        if (rawReqIds != null) {
            requirementIds = new ArrayList<>();
            for (Object rawId : rawReqIds) {
                requirementIds.add(Long.valueOf(String.valueOf(rawId)));
            }
        }
        return ApiResponse.ok(scmService.createOrder(order, items, requirementIds));
    }

    @PostMapping("/orders/{id}/dispatch")
    @RequirePermission("scm:order:dispatch")
    public ApiResponse<Void> dispatch(@PathVariable Long id) {
        scmService.dispatch(id);
        return ApiResponse.ok();
    }

    // ---------- 内部 ----------

    private boolean userHasPrice() {
        return com.projlife.common.context.UserContextHolder.get() != null
                && com.projlife.common.context.UserContextHolder.get().hasPermission("scm:order:view_price");
    }

    private Map<String, Object> maskOrder(ScmPurchaseOrder order, boolean viewPrice) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", order.getId());
        row.put("poNo", order.getPoNo());
        row.put("supplierId", order.getSupplierId());
        row.put("legalEntityId", order.getLegalEntityId());
        row.put("status", order.getStatus());
        row.put("dispatchedAt", order.getDispatchedAt());
        if (viewPrice) {
            row.put("totalAmount", order.getTotalAmount());
        }
        return row;
    }
}
