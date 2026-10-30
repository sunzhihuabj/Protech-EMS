package com.projlife.crm.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.crm.entity.CrmQuotation;
import com.projlife.crm.entity.CrmQuotationLine;
import com.projlife.crm.service.QuotationService;
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
 * 报价单接口（V4-03 M01：FR-CRM-17/18/19/20）。
 */
@RestController
@RequestMapping("/api/v1/crm/quotations")
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @GetMapping
    @RequirePermission("opportunity:list")
    public ApiResponse<PageResp<CrmQuotation>> page(PageReq pageReq,
                                                    @RequestParam(required = false) Long opportunityId,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(quotationService.page(pageReq, opportunityId, status));
    }

    @GetMapping("/{id}/lines")
    @RequirePermission("opportunity:view")
    public ApiResponse<List<CrmQuotationLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(quotationService.linesOf(id));
    }

    /** FR-CRM-17 R1：建档（金额服务端算；版本自动递增） */
    @PostMapping
    @RequirePermission("opportunity:create")
    public ApiResponse<Long> create(@RequestBody Map<String, Object> body) {
        CrmQuotation quotation = new CrmQuotation();
        quotation.setOpportunityId(toLong(body.get("opportunityId")));
        quotation.setCustomerId(toLong(body.get("customerId")));
        quotation.setProjectId(toLong(body.get("projectId")));
        quotation.setProductLine((String) body.get("productLine"));
        quotation.setQty(toDecimal(body.get("qty")));
        quotation.setUnitPrice(toDecimal(body.get("unitPrice")));
        quotation.setTaxRate(toDecimal(body.get("taxRate")));
        quotation.setDeliveryTerms((String) body.get("deliveryTerms"));
        quotation.setValidUntil(toDate(body.get("validUntil")));
        quotation.setPriceBasisJson((String) body.get("priceBasisJson"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineList = (List<Map<String, Object>>) body.get("lines");
        List<CrmQuotationLine> lines = new ArrayList<>();
        if (lineList != null) {
            for (Map<String, Object> row : lineList) {
                CrmQuotationLine line = new CrmQuotationLine();
                line.setMaterialId(Long.valueOf(String.valueOf(row.get("materialId"))));
                line.setQty(new BigDecimal(String.valueOf(row.get("qty"))));
                line.setPrice(new BigDecimal(String.valueOf(row.get("price"))));
                line.setRemark((String) row.get("remark"));
                lines.add(line);
            }
        }
        return ApiResponse.ok(quotationService.create(quotation, lines));
    }

    /** FR-CRM-17 R4：提交 */
    @PostMapping("/{id}/submit")
    @RequirePermission("opportunity:edit")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        quotationService.submit(id);
        return ApiResponse.ok();
    }

    /** FR-CRM-18：价格基准（crm:cost:view 才可见成本明细口径） */
    @GetMapping("/{id}/price-basis")
    @RequirePermission("opportunity:view")
    public ApiResponse<Map<String, Object>> priceBasis(@PathVariable Long id) {
        return ApiResponse.ok(quotationService.priceBasis(id));
    }

    /** FR-CRM-19：定标（won=true 转合同草稿；false 须填原因） */
    @PostMapping("/{id}/decide")
    @RequirePermission("opportunity:edit")
    public ApiResponse<Map<String, Object>> decide(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(quotationService.decide(id,
                Boolean.TRUE.equals(body.get("won")), (String) body.get("lostReason")));
    }

    /** FR-CRM-20 R3：转化率按来源分组 */
    @GetMapping("/conversion-by-source")
    @RequirePermission("opportunity:list")
    public ApiResponse<Map<String, Object>> conversionBySource() {
        return ApiResponse.ok(quotationService.conversionBySource());
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private java.time.LocalDate toDate(Object value) {
        return value == null ? null : java.time.LocalDate.parse(String.valueOf(value).substring(0, 10));
    }
}
