package com.projlife.crm.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.crm.entity.CrmOpportunity;
import com.projlife.crm.entity.CrmOpportunityFollowup;
import com.projlife.crm.service.CustomerProfileService;
import com.projlife.crm.service.OpportunityService;
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
 * 商机接口（04 册 §2 页面清单）。SELF 数据范围由数据层 + 查询参数保证（R04 只看自己）。
 */
@RestController
@RequestMapping("/api/v1/crm")
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final CustomerProfileService profileService;

    public OpportunityController(OpportunityService opportunityService, CustomerProfileService profileService) {
        this.opportunityService = opportunityService;
        this.profileService = profileService;
    }

    @GetMapping("/opportunities")
    @RequirePermission("opportunity:list")
    public ApiResponse<PageResp<CrmOpportunity>> page(PageReq pageReq,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String intentionLevel,
                                                      @RequestParam(required = false) Long ownerId) {
        return ApiResponse.ok(opportunityService.page(pageReq, keyword, status, intentionLevel, ownerId));
    }

    @GetMapping("/opportunities/{id}")
    @RequirePermission("opportunity:view")
    public ApiResponse<CrmOpportunity> detail(@PathVariable Long id) {
        return ApiResponse.ok(opportunityService.detail(id));
    }

    @GetMapping("/opportunities/{id}/followups")
    @RequirePermission("opportunity:view")
    public ApiResponse<List<CrmOpportunityFollowup>> followups(@PathVariable Long id) {
        return ApiResponse.ok(opportunityService.followupsOf(id));
    }

    @PostMapping("/opportunities")
    @RequirePermission("opportunity:create")
    public ApiResponse<Long> create(@RequestBody CrmOpportunity opportunity) {
        return ApiResponse.ok(opportunityService.create(opportunity));
    }

    @PostMapping("/opportunities/{id}/followups")
    @RequirePermission("opportunity:edit")
    public ApiResponse<Long> addFollowup(@PathVariable Long id, @RequestBody CrmOpportunityFollowup followup) {
        followup.setOpportunityId(id);
        return ApiResponse.ok(opportunityService.addFollowup(followup));
    }

    /** FR-CRM-06 转合同：商机 WON 并关联合同（合同创建由合同模块先完成） */
    @PostMapping("/opportunities/{id}/convert")
    @RequirePermission("opportunity:convert")
    public ApiResponse<Void> convert(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        opportunityService.convert(id, body.get("contractId"));
        return ApiResponse.ok();
    }

    @PostMapping("/opportunities/{id}/lose")
    @RequirePermission("opportunity:close")
    public ApiResponse<Void> lose(@PathVariable Long id, @RequestBody Map<String, String> body) {
        opportunityService.lose(id, body.get("lostReason"));
        return ApiResponse.ok();
    }

    @PostMapping("/opportunities/{id}/reopen")
    @RequirePermission("opportunity:reassign")
    public ApiResponse<Void> reopen(@PathVariable Long id) {
        opportunityService.reopen(id);
        return ApiResponse.ok();
    }

    @GetMapping("/funnel")
    @RequirePermission("opportunity:list")
    public ApiResponse<Map<String, Object>> funnel() {
        return ApiResponse.ok(opportunityService.funnelStats());
    }

    @GetMapping("/customers/{id}/profile")
    @RequirePermission("opportunity:view")
    public ApiResponse<Map<String, Object>> profile360(@PathVariable Long id) {
        return ApiResponse.ok(profileService.profile360(id));
    }
}
