package com.projlife.org.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.api.ApiResponse;
import com.projlife.org.entity.SysDingtalkBinding;
import com.projlife.org.mapper.SysDingtalkBindingMapper;
import com.projlife.org.sync.DingTalkDirectorySyncService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 钉钉通讯录同步端点（SPRINT-INT2）：手动同步触发 + 绑定查询/人工绑定（MANUAL 优先级最高，A-44）。
 * 与 IntegrationOpsController 共用 /api/v1/integ/ops 前缀（方法路径不重叠）。
 */
@RestController
@RequestMapping("/api/v1/integ/ops/dingtalk")
public class DingtalkSyncController {

    private final DingTalkDirectorySyncService syncService;
    private final SysDingtalkBindingMapper bindingMapper;

    public DingtalkSyncController(DingTalkDirectorySyncService syncService,
                                  SysDingtalkBindingMapper bindingMapper) {
        this.syncService = syncService;
        this.bindingMapper = bindingMapper;
    }

    /** 手动触发全量同步（job 可后续按日调度） */
    @PostMapping("/sync-directory")
    @RequirePermission("sys:config")
    public ApiResponse<DingTalkDirectorySyncService.SyncSummary> syncDirectory() {
        return ApiResponse.ok(syncService.syncAll());
    }

    /** 绑定列表（可按匹配类型过滤） */
    @GetMapping("/bindings")
    @RequirePermission("sys:config")
    public ApiResponse<List<SysDingtalkBinding>> bindings(@RequestParam(required = false) String matchType) {
        return ApiResponse.ok(bindingMapper.selectList(new LambdaQueryWrapper<SysDingtalkBinding>()
                .eq(matchType != null && !matchType.isEmpty(), SysDingtalkBinding::getMatchType, matchType)
                .eq(SysDingtalkBinding::getDeleted, 0)
                .orderByDesc(SysDingtalkBinding::getSyncedAt)
                .last("LIMIT 200")));
    }

    /** 人工绑定（MANUAL，同步不覆盖） */
    @PostMapping("/bindings")
    @RequirePermission("sys:config")
    public ApiResponse<SysDingtalkBinding> manualBind(@RequestBody Map<String, Object> body) {
        String dingtalkUserid = String.valueOf(body.get("dingtalkUserid"));
        Long sysUserId = body.get("sysUserId") == null ? null : Long.valueOf(String.valueOf(body.get("sysUserId")));
        SysDingtalkBinding existing = bindingMapper.selectOne(new LambdaQueryWrapper<SysDingtalkBinding>()
                .eq(SysDingtalkBinding::getDingtalkUserid, dingtalkUserid)
                .last("LIMIT 1"));
        SysDingtalkBinding b = existing == null ? new SysDingtalkBinding() : existing;
        b.setDingtalkUserid(dingtalkUserid);
        b.setSysUserId(sysUserId);
        b.setMatchType("MANUAL");
        b.setSyncedAt(LocalDateTime.now());
        if (existing == null) {
            b.setDeleted(0);
            b.setCreatedBy("manual");
            b.setVersion(0);
            bindingMapper.insert(b);
        } else {
            b.setUpdatedBy("manual");
            bindingMapper.updateById(b);
        }
        return ApiResponse.ok(b);
    }
}
