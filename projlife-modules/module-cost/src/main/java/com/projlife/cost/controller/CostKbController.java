package com.projlife.cost.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.cost.entity.CostEvm;
import com.projlife.cost.entity.CostItem;
import com.projlife.cost.entity.CostSnapshot;
import com.projlife.cost.entity.KbCard;
import com.projlife.cost.service.CostService;
import com.projlife.cost.service.KbCardService;
import com.projlife.cost.service.KbMeetingService;
import com.projlife.cost.service.ArchiveAppraisalService;
import com.projlife.cost.entity.KbMeeting;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 成本与看板接口（V4-05 M12-7 接口约定）。
 */
@RestController
@RequestMapping("/api/v1")
public class CostKbController {

    private final CostService costService;
    private final KbCardService cardService;
    private final KbMeetingService meetingService;
    private final ArchiveAppraisalService appraisalService;

    public CostKbController(CostService costService, KbCardService cardService,
                            KbMeetingService meetingService, ArchiveAppraisalService appraisalService) {
        this.costService = costService;
        this.cardService = cardService;
        this.meetingService = meetingService;
        this.appraisalService = appraisalService;
    }

    // ---------- 成本 ----------

    @GetMapping("/cost/project/{projectId}/items")
    @RequirePermission("cost:view")
    public ApiResponse<PageResp<CostItem>> items(@PathVariable Long projectId, PageReq pageReq) {
        return ApiResponse.ok(costService.items(pageReq, projectId));
    }

    @PostMapping("/cost/items")
    @RequirePermission("cost:budget")
    public ApiResponse<Long> createItem(@RequestBody CostItem item) {
        return ApiResponse.ok(costService.createItem(item));
    }

    @GetMapping("/cost/project/{projectId}/summary")
    @RequirePermission("cost:view")
    public ApiResponse<Map<String, Object>> summary(@PathVariable Long projectId) {
        return ApiResponse.ok(costService.summaryByType(projectId));
    }

    @GetMapping("/cost/project/{projectId}/overrun")
    @RequirePermission("cost:view")
    public ApiResponse<Map<String, Object>> overrun(@PathVariable Long projectId) {
        return ApiResponse.ok(costService.overrunCheck(projectId));
    }

    @PostMapping("/cost/evm")
    @RequirePermission("cost:budget")
    public ApiResponse<CostEvm> evm(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(costService.computeEvm(Long.valueOf(body.get("projectId")),
                LocalDate.parse(body.get("date")),
                toDecimal(body.get("pv")), toDecimal(body.get("ev")), toDecimal(body.get("ac"))));
    }

    @PostMapping("/cost/snapshot")
    @RequirePermission("cost:budget")
    public ApiResponse<CostSnapshot> snapshot(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(costService.snapshot(Long.valueOf(body.get("projectId")),
                LocalDate.parse(body.get("date"))));
    }

    // ---------- 看板卡片 ----------

    @GetMapping("/kb/cards")
    @RequirePermission("kb:card:manage")
    public ApiResponse<PageResp<KbCard>> cards(PageReq pageReq,
                                               @RequestParam(required = false) Long boardId,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(cardService.page(pageReq, boardId, status));
    }

    /** FR-KB-04 五要素 + FR-KB-05 VAGUE 拦截 */
    @PostMapping("/kb/cards")
    @RequirePermission("kb:card:manage")
    public ApiResponse<Long> createCard(@RequestBody KbCard card) {
        return ApiResponse.ok(cardService.create(card));
    }

    /** FR-KB-06 红灯必填卡点 */
    @PostMapping("/kb/cards/{id}/block")
    @RequirePermission("kb:card:manage")
    public ApiResponse<Void> block(@PathVariable Long id, @RequestBody Map<String, String> body) {
        cardService.block(id, body.get("blockReason"));
        return ApiResponse.ok();
    }

    /** FR-KB-07 红灯 3 天升级（job 入口） */
    @PostMapping("/kb/cards/escalate")
    @RequirePermission("kb:card:manage")
    public ApiResponse<Integer> escalate() {
        return ApiResponse.ok(cardService.escalateRedCards());
    }

    /** FR-KB-10 三问过堂闭环 */
    @PostMapping("/kb/cards/{id}/close")
    @RequirePermission("kb:card:manage")
    public ApiResponse<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body) {
        cardService.close(id, body.get("solution"));
        return ApiResponse.ok();
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    // ---------- 会议纪要（FR-KB-11） ----------

    /** 纪要落库：会议模式结束 → kb_meeting */
    @PostMapping("/kb/meetings")
    @RequirePermission("kb:card:manage")
    public ApiResponse<KbMeeting> saveMeeting(@RequestBody KbMeeting meeting) {
        return ApiResponse.ok(meetingService.save(meeting));
    }

    /** 纪要列表（按板） */
    @GetMapping("/kb/meetings")
    @RequirePermission("kb:card:manage")
    public ApiResponse<java.util.List<KbMeeting>> meetings(@RequestParam Long boardId) {
        return ApiResponse.ok(meetingService.listByBoard(boardId));
    }

    /** 纪要详情（含逐卡结论 items，SPRINT-KB1） */
    @GetMapping("/kb/meetings/{id}")
    @RequirePermission("kb:card:manage")
    public ApiResponse<java.util.Map<String, Object>> meetingDetail(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.detail(id));
    }

    /** 看板清单（boardCode→id 解析，SPRINT-KB1） */
    @GetMapping("/kb/boards")
    @RequirePermission("kb:card:manage")
    public ApiResponse<java.util.List<com.projlife.cost.entity.KbBoard>> boards() {
        return ApiResponse.ok(meetingService.boards());
    }

    /** 纪要 PDF 导出（FR-KB-11，PDFBox，亮色白底） */
    @GetMapping("/kb/meetings/{id}/pdf")
    @RequirePermission("kb:card:manage")
    public ResponseEntity<byte[]> meetingPdf(@PathVariable Long id) {
        byte[] pdf = meetingService.meetingPdf(id);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.set("Content-Disposition", "attachment; filename=meeting-" + id + ".pdf");
        return new ResponseEntity<>(pdf, headers, org.springframework.http.HttpStatus.OK);
    }

    /** 档案鉴定提醒扫描（job 入口，A-36 口径） */
    @PostMapping("/arc/appraisal-scan")
    @RequirePermission("arc:manage")
    public ApiResponse<Integer> appraisalScan() {
        return ApiResponse.ok(appraisalService.appraisalScan());
    }
}
