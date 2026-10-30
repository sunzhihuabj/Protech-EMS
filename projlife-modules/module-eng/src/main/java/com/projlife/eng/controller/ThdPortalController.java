package com.projlife.eng.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.eng.entity.ThdBlacklist;
import com.projlife.eng.entity.ThdDeliveryNote;
import com.projlife.eng.entity.ThdEvaluation;
import com.projlife.eng.entity.ThdWorkerProject;
import com.projlife.eng.service.ThdPortalService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 第三方门户接口（V4-05 M11）：送货单/人员授权/考核评价/黑名单。
 */
@RestController
@RequestMapping("/api/v1/thd")
public class ThdPortalController {

    private final ThdPortalService portalService;

    public ThdPortalController(ThdPortalService portalService) {
        this.portalService = portalService;
    }

    @PostMapping("/delivery-notes")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdDeliveryNote> createNote(@RequestBody ThdDeliveryNote note) {
        return ApiResponse.ok(portalService.createDeliveryNote(note));
    }

    @PostMapping("/delivery-notes/{id}/print")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdDeliveryNote> print(@PathVariable Long id) {
        return ApiResponse.ok(portalService.print(id));
    }

    @PostMapping("/worker-projects")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdWorkerProject> grantWorker(@RequestBody ThdWorkerProject grant) {
        return ApiResponse.ok(portalService.grantWorker(grant));
    }

    @PostMapping("/worker-projects/{id}/revoke")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdWorkerProject> revoke(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(portalService.revoke(id, body.get("reason")));
    }

    @PostMapping("/evaluations")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdEvaluation> evaluate(@RequestBody ThdEvaluation evaluation) {
        return ApiResponse.ok(portalService.evaluate(evaluation));
    }

    @PostMapping("/blacklist")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdBlacklist> blacklistAdd(@RequestBody ThdBlacklist blacklist) {
        return ApiResponse.ok(portalService.blacklistAdd(blacklist));
    }

    @PostMapping("/blacklist/{id}/release")
    @RequirePermission("thd:portal")
    public ApiResponse<ThdBlacklist> blacklistRelease(@PathVariable Long id) {
        return ApiResponse.ok(portalService.blacklistRelease(id));
    }
}
