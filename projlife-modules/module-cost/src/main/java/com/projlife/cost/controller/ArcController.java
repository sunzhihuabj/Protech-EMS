package com.projlife.cost.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.cost.entity.ArcArchive;
import com.projlife.cost.entity.ArcBorrow;
import com.projlife.cost.entity.ArcDestroy;
import com.projlife.cost.entity.ArcTransfer;
import com.projlife.cost.service.ArchiveService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案管理接口（V4-02 M13）：/api/v1/arc。
 */
@RestController
@RequestMapping("/api/v1/arc")
public class ArcController {

    private final ArchiveService archiveService;

    public ArcController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/archives")
    @RequirePermission("arc:manage")
    public ApiResponse<ArcArchive> create(@RequestBody ArcArchive archive) {
        return ApiResponse.ok(archiveService.create(archive));
    }

    @PostMapping("/borrows")
    @RequirePermission("arc:manage")
    public ApiResponse<ArcBorrow> borrow(@RequestBody ArcBorrow borrow) {
        return ApiResponse.ok(archiveService.borrow(borrow));
    }

    @PostMapping("/borrows/{id}/return")
    @RequirePermission("arc:manage")
    public ApiResponse<ArcBorrow> giveBack(@PathVariable Long id) {
        return ApiResponse.ok(archiveService.giveBack(id));
    }

    @PostMapping("/destroys")
    @RequirePermission("arc:manage")
    public ApiResponse<ArcDestroy> destroy(@RequestBody ArcDestroy destroy) {
        return ApiResponse.ok(archiveService.destroy(destroy));
    }

    @PostMapping("/transfers")
    @RequirePermission("arc:manage")
    public ApiResponse<ArcTransfer> transfer(@RequestBody ArcTransfer transfer) {
        return ApiResponse.ok(archiveService.transfer(transfer));
    }

    @PostMapping("/transfers/{id}/confirm")
    @RequirePermission("arc:manage")
    public ApiResponse<ArcTransfer> confirm(@PathVariable Long id) {
        return ApiResponse.ok(archiveService.confirmTransfer(id));
    }

    /** 逾期扫描（job 入口） */
    @PostMapping("/borrows/overdue-scan")
    public ApiResponse<Integer> overdueScan() {
        return ApiResponse.ok(archiveService.overdueScan());
    }
}
