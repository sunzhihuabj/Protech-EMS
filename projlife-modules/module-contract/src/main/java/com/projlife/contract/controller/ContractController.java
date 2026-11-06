package com.projlife.contract.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.entity.CtContractChange;
import com.projlife.contract.entity.CtContractDispatch;
import com.projlife.contract.entity.CtContractPaymentPlan;
import com.projlife.contract.entity.CtContractRisk;
import com.projlife.contract.service.ContractAiService;
import com.projlife.contract.service.ContractChangeService;
import com.projlife.contract.service.ContractDispatchService;
import com.projlife.contract.service.ContractFinanceService;
import com.projlife.contract.service.ContractService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 合同接口（04 册 §3.2 页面清单对应）。金额字段（contract:view_price）由 R15/R04 持有；
 * 无权限角色调金额接口被 @RequirePermission 拦截（BR-06 服务端防线）。
 */
@RestController
@RequestMapping("/api/v1/contract")
public class ContractController {

    private final ContractService contractService;
    private final ContractFinanceService financeService;
    private final ContractAiService aiService;
    private final ContractChangeService changeService;
    private final ContractDispatchService dispatchService;
    private final com.projlife.contract.service.ContractRelatedService relatedService;

    public ContractController(ContractService contractService, ContractFinanceService financeService,
                              ContractAiService aiService, ContractChangeService changeService,
                              ContractDispatchService dispatchService, com.projlife.contract.service.ContractRelatedService relatedService) {
        this.contractService = contractService;
        this.financeService = financeService;
        this.aiService = aiService;
        this.changeService = changeService;
        this.dispatchService = dispatchService;
        this.relatedService = relatedService;
    }

    // ---------- 起草 / 台账 ----------

    @GetMapping
    @RequirePermission("contract:list")
    public ApiResponse<PageResp<CtContract>> page(PageReq pageReq,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(contractService.page(pageReq, keyword, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("contract:view")
    public ApiResponse<CtContract> detail(@PathVariable Long id) {
        return ApiResponse.ok(contractService.detail(id));
    }

    @PostMapping
    @RequirePermission("contract:create")
    public ApiResponse<Long> create(@RequestBody Map<String, Object> body) {
        CtContract contract = bindContract(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> planList = (List<Map<String, Object>>) body.get("paymentPlans");
        List<CtContractPaymentPlan> plans = planList == null ? null : new java.util.ArrayList<>();
        if (planList != null) {
            for (Map<String, Object> row : planList) {
                CtContractPaymentPlan plan = new CtContractPaymentPlan();
                plan.setPhaseName((String) row.get("phaseName"));
                Object percentage = row.get("percentage");
                if (percentage != null) {
                    plan.setPercentage(new java.math.BigDecimal(String.valueOf(percentage)));
                }
                Object amount = row.get("amount");
                if (amount != null) {
                    plan.setAmount(new java.math.BigDecimal(String.valueOf(amount)));
                }
                Object planDate = row.get("planDate");
                if (planDate != null) {
                    plan.setPlanDate(java.time.LocalDate.parse(String.valueOf(planDate).substring(0, 10)));
                }
                plans.add(plan);
            }
        }
        @SuppressWarnings("unchecked")
        List<Object> rawProjectIds = (List<Object>) body.get("projectIds");
        List<Long> projectIds = null;
        if (rawProjectIds != null) {
            projectIds = new java.util.ArrayList<>();
            for (Object rawId : rawProjectIds) {
                projectIds.add(Long.valueOf(String.valueOf(rawId)));
            }
        }
        return ApiResponse.ok(contractService.create(contract, plans, projectIds));
    }

    /** FR-CT-18：编号预览（不占号） */
    @GetMapping("/{id}/number-preview")
    @RequirePermission("contract:view")
    public ApiResponse<String> numberPreview(@PathVariable Long id) {
        return ApiResponse.ok(contractService.previewNo(contractService.detail(id)));
    }

    // ---------- 评审 / 用印 / 建档 ----------

    /** 提交评审：BR-01（无项目编码 → 422 + blockCode + 去建档引导） */
    @PostMapping("/{id}/review/submit")
    @RequirePermission("contract:review:submit")
    public ApiResponse<Void> submitReview(@PathVariable Long id) {
        contractService.submitReview(id);
        return ApiResponse.ok();
    }

    /** 用印登记：生成合同号 + filing_deadline（FR-CT-17/19/20） */
    @PostMapping("/{id}/seal")
    @RequirePermission("contract:seal")
    public ApiResponse<CtContract> seal(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return ApiResponse.ok(contractService.seal(id, body.get("copyCount")));
    }

    /** 完整度查询（FR-CT-22 顶部进度条数据源） */
    @GetMapping("/{id}/completeness")
    @RequirePermission("contract:view")
    public ApiResponse<Map<String, Object>> completeness(@PathVariable Long id) {
        return ApiResponse.ok(contractService.completeness(contractService.detail(id)));
    }

    /** 建档备案：26 要素全量校验（FR-CT-21/22/23） */
    @PostMapping("/{id}/filing")
    @RequirePermission("contract:confirm")
    public ApiResponse<Map<String, Object>> filing(@PathVariable Long id, @RequestBody Map<String, Object> elements) {
        return ApiResponse.ok(contractService.filing(id, elements));
    }

    // ---------- AI ----------

    @PostMapping("/{id}/ai/risk")
    @RequirePermission("contract:edit")
    public ApiResponse<Map<String, Object>> analyzeRisk(@PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(aiService.analyzeRisk(id, body == null ? null : body.get("text")));
    }

    @GetMapping("/{id}/ai/risks")
    @RequirePermission("contract:view")
    public ApiResponse<List<CtContractRisk>> risks(@PathVariable Long id) {
        contractService.assertVisible(id);
        return ApiResponse.ok(aiService.risksOf(id));
    }

    @PostMapping("/ai/risks/{riskId}/adopt")
    @RequirePermission("contract:edit")
    public ApiResponse<Void> adoptRisk(@PathVariable Long riskId, @RequestBody Map<String, Object> body) {
        aiService.adoptRisk(riskId, Boolean.TRUE.equals(body.get("adopted")), null);
        return ApiResponse.ok();
    }

    /** FR-CT-07：提取 12 字段（mock 恒可用；降级返回手工填写提示） */
    @PostMapping("/{id}/ai/extract")
    @RequirePermission("contract:edit")
    public ApiResponse<Map<String, Object>> extract(@PathVariable Long id) {
        return ApiResponse.ok(aiService.extract(id));
    }

    /** FR-CT-09/11：确认归档 + 人工修正留痕 */
    @PostMapping("/{id}/ai/confirm")
    @RequirePermission("contract:confirm")
    public ApiResponse<Map<String, Object>> confirmExtract(@PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> aiFields = (Map<String, String>) body.get("aiFields");
        @SuppressWarnings("unchecked")
        Map<String, String> humanFields = (Map<String, String>) body.get("humanFields");
        Object rawCallId = body.get("callId");
        Long callId = rawCallId == null ? null : Long.valueOf(String.valueOf(rawCallId));
        return ApiResponse.ok(aiService.confirmExtract(id, callId, aiFields, humanFields));
    }

    // ---------- 变更 / 交底 ----------

    @PostMapping("/{id}/changes")
    @RequirePermission("contract:change")
    public ApiResponse<Long> createChange(@PathVariable Long id, @RequestBody CtContractChange change) {
        change.setContractId(id);
        return ApiResponse.ok(changeService.create(change));
    }

    @GetMapping("/{id}/changes")
    @RequirePermission("contract:view")
    public ApiResponse<List<CtContractChange>> changes(@PathVariable Long id) {
        contractService.assertVisible(id);
        return ApiResponse.ok(changeService.changesOf(id));
    }

    @PostMapping("/changes/{changeId}/approve")
    @RequirePermission("contract:change:approve")
    public ApiResponse<Void> approveChange(@PathVariable Long changeId, @RequestBody Map<String, Object> body) {
        changeService.approve(changeId, Boolean.TRUE.equals(body.get("approved")), null);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/dispatch")
    @RequirePermission("contract:dispatch")
    public ApiResponse<Integer> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawReceivers = (List<Object>) body.get("receiverIds");
        List<Long> receiverIds = new java.util.ArrayList<>();
        if (rawReceivers != null) {
            for (Object rawId : rawReceivers) {
                receiverIds.add(Long.valueOf(String.valueOf(rawId)));
            }
        }
        return ApiResponse.ok(dispatchService.dispatch(id, (String) body.get("summary"), receiverIds));
    }

    @GetMapping("/{id}/dispatches")
    @RequirePermission("contract:view")
    public ApiResponse<List<CtContractDispatch>> dispatches(@PathVariable Long id) {
        contractService.assertVisible(id);
        return ApiResponse.ok(dispatchService.dispatchesOf(id));
    }

    @PostMapping("/dispatches/{dispatchId}/read")
    public ApiResponse<Void> confirmRead(@PathVariable Long dispatchId) {
        dispatchService.confirmRead(dispatchId, com.projlife.common.context.UserContextHolder.currentUserId());
        return ApiResponse.ok();
    }

    // ---------- 收款 / 开票 ----------

    /** SPRINT-PRJ1（审计 F9）：关联单据链 合同→项目→开票→收款计划 */
    @GetMapping("/{id}/related")
    @RequirePermission("contract:view")
    public ApiResponse<java.util.Map<String, Object>> related(@PathVariable Long id) {
        contractService.assertVisible(id);
        return ApiResponse.ok(relatedService.related(id));
    }

    @GetMapping("/{id}/payment-plans")
    @RequirePermission("contract:view")
    public ApiResponse<List<CtContractPaymentPlan>> paymentPlans(@PathVariable Long id) {
        contractService.assertVisible(id);
        return ApiResponse.ok(financeService.plansOf(id));
    }

    @PostMapping("/receipts")
    @RequirePermission("contract:payment:create")
    public ApiResponse<Long> applyReceipt(@RequestBody com.projlife.contract.entity.CtReceipt receipt) {
        return ApiResponse.ok(financeService.applyReceipt(receipt));
    }

    @PostMapping("/receipts/{receiptId}/confirm")
    @RequirePermission("contract:payment:confirm")
    public ApiResponse<Void> confirmReceipt(@PathVariable Long receiptId) {
        financeService.confirmReceipt(receiptId);
        return ApiResponse.ok();
    }

    @PostMapping("/invoices")
    @RequirePermission("contract:invoice:apply")
    public ApiResponse<Long> applyInvoice(@RequestBody com.projlife.contract.entity.CtInvoice invoice) {
        return ApiResponse.ok(financeService.applyInvoice(invoice));
    }

    @PostMapping("/invoices/{invoiceId}/confirm")
    @RequirePermission("contract:invoice:confirm")
    public ApiResponse<Void> confirmInvoice(@PathVariable Long invoiceId,
                                            @RequestBody(required = false) Map<String, String> body) {
        financeService.confirmInvoice(invoiceId, body == null ? null : body.get("tPlusRef"));
        return ApiResponse.ok();
    }

    /** FR-CT-26：紧急解冻（R02） */
    @PostMapping("/{id}/unfreeze")
    @RequirePermission("contract:unfreeze")
    public ApiResponse<Void> unfreeze(@PathVariable Long id, @RequestBody Map<String, String> body) {
        financeService.unfreeze(id, body.get("reason"));
        return ApiResponse.ok();
    }

    /** BR-CT-04：用印人≠建档人时，任务仍派给经办人（handler 通知） */
    private CtContract bindContract(Map<String, Object> body) {
        CtContract contract = new CtContract();
        contract.setContractName((String) body.get("contractName"));
        contract.setNature((String) body.get("nature"));
        contract.setPartyA((String) body.get("partyA"));
        contract.setPartyB((String) body.get("partyB"));
        contract.setCounterpartyId(toLong(body.get("counterpartyId")));
        contract.setLegalEntityId(toLong(body.get("legalEntityId")));
        contract.setBusinessType((String) body.get("businessType"));
        contract.setAmount(toDecimal(body.get("amount")));
        contract.setTaxRate(toDecimal(body.get("taxRate")));
        contract.setPaymentTerms((String) body.get("paymentTerms"));
        contract.setDeliveryAddr((String) body.get("deliveryAddr"));
        contract.setContact((String) body.get("contact"));
        contract.setPhone((String) body.get("phone"));
        contract.setDeliveryDate(toDate(body.get("deliveryDate")));
        contract.setWarrantyMonths(toInt(body.get("warrantyMonths")));
        contract.setAcceptanceClause((String) body.get("acceptanceClause"));
        contract.setSignDate(toDate(body.get("signDate")));
        contract.setEffectiveDate(toDate(body.get("effectiveDate")));
        contract.setPerformStart(toDate(body.get("performStart")));
        contract.setPerformEnd(toDate(body.get("performEnd")));
        contract.setCurrency((String) body.getOrDefault("currency", "CNY"));
        return contract;
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private Integer toInt(Object value) {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private java.math.BigDecimal toDecimal(Object value) {
        return value == null ? null : new java.math.BigDecimal(String.valueOf(value));
    }

    private java.time.LocalDate toDate(Object value) {
        return value == null ? null : java.time.LocalDate.parse(String.valueOf(value).substring(0, 10));
    }
}
