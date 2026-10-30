package com.projlife.bid.controller;

import com.projlife.bid.entity.BidFeedback;
import com.projlife.bid.entity.BidMatch;
import com.projlife.bid.entity.BidNotice;
import com.projlife.bid.entity.BidSource;
import com.projlife.bid.entity.BidSubscribe;
import com.projlife.bid.service.BidService;
import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 招标情报接口（V4-05 M18-6 接口约定）：/api/v1/bid/*。
 */
@RestController
@RequestMapping("/api/v1/bid")
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @PostMapping("/sources")
    @RequirePermission("bid:source:manage")
    public ApiResponse<BidSource> createSource(@RequestBody BidSource source) {
        return ApiResponse.ok(bidService.createSource(source));
    }

    /** FR-BID-02：启用前 robots 检查留痕 */
    @PostMapping("/sources/{id}/review")
    @RequirePermission("bid:source:manage")
    public ApiResponse<BidSource> reviewSource(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long robotsStatus = body.get("robotsStatusCode") == null
                ? null : Long.valueOf(String.valueOf(body.get("robotsStatusCode")));
        return ApiResponse.ok(bidService.reviewSource(id, (String) body.get("status"), robotsStatus));
    }

    @GetMapping("/sources")
    @RequirePermission("bid:source:manage")
    public ApiResponse<PageResp<BidSource>> sources(PageReq pageReq,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(bidService.sources(pageReq, status));
    }

    /** FR-BID-06：入库 + 四层去重 */
    @PostMapping("/ingest")
    @RequirePermission("bid:notice:manual")
    public ApiResponse<Map<String, Object>> ingest(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(bidService.ingest(
                Long.valueOf(String.valueOf(body.get("sourceId"))),
                (String) body.get("url"),
                (String) body.get("title"),
                (String) body.get("content"),
                (String) body.get("projectName"),
                (String) body.get("lotNo"),
                body.get("budgetAmount") == null ? null
                        : new java.math.BigDecimal(String.valueOf(body.get("budgetAmount"))),
                body.get("deadlineAt") == null ? null
                        : LocalDateTime.parse((String) body.get("deadlineAt"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                (String) body.get("region"),
                body.get("ownerId") == null ? null : Long.valueOf(String.valueOf(body.get("ownerId"))),
                (String) body.get("changeType")));
    }

    @PostMapping("/notices/{id}/score")
    @RequirePermission("bid:notice:view")
    public ApiResponse<BidMatch> score(@PathVariable Long id) {
        return ApiResponse.ok(bidService.score(id));
    }

    /** FR-BID-12：一键转商机 */
    @PostMapping("/notices/{id}/convert")
    @RequirePermission("bid:notice:view")
    public ApiResponse<Map<String, Object>> convert(@PathVariable Long id) {
        return ApiResponse.ok(bidService.convert(id));
    }

    @GetMapping("/notices")
    @RequirePermission("bid:notice:view")
    public ApiResponse<PageResp<BidNotice>> notices(PageReq pageReq,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String level) {
        return ApiResponse.ok(bidService.notices(pageReq, status, level));
    }

    @GetMapping("/notices/{id}")
    @RequirePermission("bid:notice:view")
    public ApiResponse<BidNotice> noticeDetail(@PathVariable Long id) {
        return ApiResponse.ok(bidService.noticeDetail(id));
    }

    @GetMapping("/notices/{id}/match")
    @RequirePermission("bid:notice:view")
    public ApiResponse<BidMatch> matchOf(@PathVariable Long id) {
        return ApiResponse.ok(bidService.matchOf(id));
    }

    @PostMapping("/subscribe")
    @RequirePermission("bid:push:subscribe")
    public ApiResponse<BidSubscribe> subscribe(@RequestBody BidSubscribe subscribe) {
        return ApiResponse.ok(bidService.saveSubscribe(subscribe));
    }

    /** FR-BID-10：按订阅条件推送 */
    @PostMapping("/notices/{id}/push")
    @RequirePermission("bid:push:subscribe")
    public ApiResponse<Map<String, Object>> push(@PathVariable Long id) {
        return ApiResponse.ok(bidService.push(id));
    }

    /** FR-BID-13：反馈回训 */
    @PostMapping("/feedback")
    @RequirePermission("bid:notice:manual")
    public ApiResponse<Long> feedback(@RequestBody BidFeedback feedback) {
        return ApiResponse.ok(bidService.feedback(feedback));
    }
}
