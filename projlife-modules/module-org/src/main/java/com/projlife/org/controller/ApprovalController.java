package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.org.entity.SysApproval;
import com.projlife.org.entity.SysApprovalNode;
import com.projlife.org.service.ApprovalService;
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
 * 审批流接口（FR-SYS-14，R8 P0）：
 * POST /submit（发起）| GET /my-pending | POST /{nodeId}/approve · /reject · /delegate · /addsign | GET /{approvalId}/nodes
 */
@RestController
@RequestMapping("/api/v1/approval")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/submit")
    @RequirePermission("sys:approval:act")
    public ApiResponse<SysApproval> submit(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("approvalType");
        Long targetId = Long.valueOf(String.valueOf(body.get("targetId")));
        String targetDesc = (String) body.get("targetDesc");
        List<Map<String, Object>> chainRaw = (List<Map<String, Object>>) body.get("chain");
        List<long[]> chain = chainRaw.stream()
                .map(n -> new long[]{ 0, Long.valueOf(String.valueOf(n.get("approverId"))) })
                .collect(java.util.stream.Collectors.toList());
        return ApiResponse.ok(approvalService.submit(type, targetId, targetDesc, chain));
    }

    @GetMapping("/my-pending")
    @RequirePermission("sys:approval:act")
    public ApiResponse<List<SysApprovalNode>> myPending() {
        return ApiResponse.ok(approvalService.myPending());
    }

    @PostMapping("/nodes/{nodeId}/approve")
    @RequirePermission("sys:approval:act")
    public ApiResponse<SysApprovalNode> approve(@PathVariable Long nodeId,
                                                @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(approvalService.approve(nodeId, body == null ? "" : body.get("comment")));
    }

    @PostMapping("/nodes/{nodeId}/reject")
    @RequirePermission("sys:approval:act")
    public ApiResponse<SysApprovalNode> reject(@PathVariable Long nodeId,
                                               @RequestBody Map<String, String> body) {
        return ApiResponse.ok(approvalService.reject(nodeId, body.get("reason")));
    }

    @PostMapping("/nodes/{nodeId}/delegate")
    @RequirePermission("sys:approval:act")
    public ApiResponse<SysApprovalNode> delegate(@PathVariable Long nodeId,
                                                 @RequestBody Map<String, Long> body) {
        return ApiResponse.ok(approvalService.delegate(nodeId, body.get("newApproverId")));
    }

    @PostMapping("/{approvalId}/addsign")
    @RequirePermission("sys:approval:act")
    public ApiResponse<SysApprovalNode> addSign(@PathVariable Long approvalId,
                                                @RequestBody Map<String, Long> body) {
        return ApiResponse.ok(approvalService.addSign(approvalId, body.get("userId")));
    }

    @GetMapping("/{approvalId}/nodes")
    public ApiResponse<List<SysApprovalNode>> nodes(@PathVariable Long approvalId) {
        return ApiResponse.ok(approvalService.nodesOf(approvalId));
    }

    @GetMapping("/target/{type}/{targetId}")
    public ApiResponse<SysApproval> byTarget(@PathVariable String type, @PathVariable Long targetId) {
        return ApiResponse.ok(approvalService.findByTarget(type, targetId));
    }
}
