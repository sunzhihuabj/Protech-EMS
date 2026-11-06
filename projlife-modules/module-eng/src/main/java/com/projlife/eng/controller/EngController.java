package com.projlife.eng.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.eng.entity.EngDispatch;
import com.projlife.eng.entity.EngRectification;
import com.projlife.eng.entity.EngUnit;
import com.projlife.eng.entity.EngWorker;
import com.projlife.eng.service.EngService;
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
 * 工程交付接口（V4-05 M10-7 接口约定）。
 */
@RestController
@RequestMapping("/api/v1/eng")
public class EngController {

    private final EngService engService;

    public EngController(EngService engService) {
        this.engService = engService;
    }

    // ---------- 人员准入 ----------

    /** 班组台账（SPRINT-A：派工联动选择器数据源） */
    @GetMapping("/crews")
    @RequirePermission("eng:crew:manage")
    public ApiResponse<List<com.projlife.eng.entity.EngCrew>> crews() {
        return ApiResponse.ok(engService.crews());
    }

    @PostMapping("/workers")
    @RequirePermission("eng:crew:manage")
    public ApiResponse<Long> createWorker(@RequestBody EngWorker worker) {
        return ApiResponse.ok(engService.assertWorkerAndCreate(worker));
    }

    /** 人员台账（FR-ENG-01/02 准入状态可视；crewId 供派工联动按班组过滤） */
    @GetMapping("/workers")
    @RequirePermission("eng:crew:manage")
    public ApiResponse<PageResp<EngWorker>> workers(PageReq pageReq,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long crewId) {
        return ApiResponse.ok(engService.workers(pageReq, status, crewId));
    }

    @PostMapping("/workers/{id}/activate")
    @RequirePermission("eng:crew:manage")
    public ApiResponse<Void> activateWorker(@PathVariable Long id) {
        engService.activateWorker(id);
        return ApiResponse.ok();
    }

    // ---------- 派工（BR-03/04） ----------

    @GetMapping("/dispatch")
    @RequirePermission("eng:dispatch")
    public ApiResponse<PageResp<EngDispatch>> dispatchPage(PageReq pageReq,
                                                           @RequestParam(required = false) String status) {
        return ApiResponse.ok(engService.dispatchPage(pageReq, status));
    }

    @PostMapping("/dispatch")
    @RequirePermission("eng:dispatch")
    public ApiResponse<Long> createDispatch(@RequestBody EngDispatch dispatch) {
        return ApiResponse.ok(engService.createDispatch(dispatch));
    }

    @PostMapping("/dispatch/{id}/send")
    @RequirePermission("eng:dispatch")
    public ApiResponse<Void> send(@PathVariable Long id) {
        engService.sendDispatch(id);
        return ApiResponse.ok();
    }

    @PostMapping("/dispatch/{id}/start")
    @RequirePermission("eng:dispatch")
    public ApiResponse<Void> start(@PathVariable Long id) {
        engService.startDispatch(id);
        return ApiResponse.ok();
    }

    @PostMapping("/dispatch/{id}/accept")
    @RequirePermission("eng:dispatch")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        engService.acceptDispatch(id);
        return ApiResponse.ok();
    }

    @PostMapping("/dispatch/{id}/close")
    @RequirePermission("eng:dispatch")
    public ApiResponse<Void> close(@PathVariable Long id) {
        engService.closeDispatch(id);
        return ApiResponse.ok();
    }

    // ---------- 机组（一机一档） ----------

    @PostMapping("/units")
    @RequirePermission("eng:install")
    public ApiResponse<Long> createUnit(@RequestBody EngUnit unit) {
        return ApiResponse.ok(engService.createUnit(unit));
    }

    /** 机组台账（SPRINT-UID-R20 补列表，一机一档可视） */
    @GetMapping("/units")
    @RequirePermission("eng:install")
    public ApiResponse<PageResp<EngUnit>> units(PageReq pageReq,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(engService.units(pageReq, projectId, status));
    }

    /** E-29 状态推进（INSTALLING/TO_DEBUG/ACCEPTED） */
    @PostMapping("/units/{id}/advance")
    @RequirePermission("eng:install")
    public ApiResponse<Void> advanceUnit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        engService.advanceUnit(id, body.get("targetStatus"));
        return ApiResponse.ok();
    }

    // ---------- 整改 ----------

    @PostMapping("/rectifications")
    @RequirePermission("eng:rect:manage")
    public ApiResponse<Long> createRectification(@RequestBody EngRectification rect) {
        return ApiResponse.ok(engService.createRectification(rect));
    }

    /** 整改台账（SPRINT-UID-R20 补列表，UC-ENG-08 闭环可视） */
    @GetMapping("/rectifications")
    @RequirePermission("eng:rect:manage")
    public ApiResponse<PageResp<EngRectification>> rectifications(PageReq pageReq,
                                                                  @RequestParam(required = false) Long projectId,
                                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(engService.rectifications(pageReq, projectId, status));
    }

    @PostMapping("/rectifications/{id}/advance")
    @RequirePermission("eng:rect:manage")
    public ApiResponse<Void> advanceRectification(@PathVariable Long id, @RequestBody Map<String, String> body) {
        engService.advanceRectification(id, body.get("targetStatus"), body.get("rejectReason"));
        return ApiResponse.ok();
    }

    /** FR-ENG-08：整体验收前置校验 */
    @GetMapping("/projects/{projectId}/rect-open-check")
    @RequirePermission("eng:install")
    public ApiResponse<String> rectOpenCheck(@PathVariable Long projectId) {
        engService.assertNoOpenRectification(projectId);
        return ApiResponse.ok("无未闭环整改，可提交整体验收");
    }
}
