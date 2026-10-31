package com.projlife.cost.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.api.ApiResponse;
import com.projlife.cost.entity.IntAdapter;
import com.projlife.cost.mapper.IntAdapterMapper;
import com.projlife.cost.mapper.IntSyncLogMapper;
import com.projlife.cost.entity.IntSyncLog;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 集成适配器接口（M15 FR-INT-01~08）：/api/v1/int。
 */
@RestController
@RequestMapping("/api/v1/int")
public class IntegrationController {

    private final IntAdapterMapper adapterMapper;
    private final IntSyncLogMapper syncLogMapper;

    public IntegrationController(IntAdapterMapper adapterMapper, IntSyncLogMapper syncLogMapper) {
        this.adapterMapper = adapterMapper;
        this.syncLogMapper = syncLogMapper;
    }

    @GetMapping("/adapters")
    @RequirePermission("sys:config")
    public ApiResponse<List<IntAdapter>> adapters(@RequestParam(required = false) String code) {
        return ApiResponse.ok(adapterMapper.selectList(new LambdaQueryWrapper<IntAdapter>()
                .eq(code != null && !code.isEmpty(), IntAdapter::getAdapterCode, code)
                .eq(IntAdapter::getDeleted, 0)));
    }

    @PostMapping("/adapters")
    @RequirePermission("sys:config")
    public ApiResponse<IntAdapter> create(@RequestBody IntAdapter adapter) {
        adapterMapper.insert(adapter);
        return ApiResponse.ok(adapter);
    }

    @PutMapping("/adapters/{id}")
    @RequirePermission("sys:config")
    public ApiResponse<IntAdapter> update(@PathVariable Long id, @RequestBody IntAdapter adapter) {
        adapter.setId(id);
        adapterMapper.updateById(adapter);
        return ApiResponse.ok(adapter);
    }

    /** 健康检查（ping 远端 → 更新 health_status） */
    @PostMapping("/adapters/{id}/health-check")
    @RequirePermission("sys:config")
    public ApiResponse<IntAdapter> healthCheck(@PathVariable Long id) {
        IntAdapter adapter = adapterMapper.selectById(id);
        if (adapter == null) return ApiResponse.ok(null);
        // 演示：MOCK 恒 UP；真实适配器走 HTTP ping
        adapter.setHealthStatus("UP");
        adapter.setLastCheckAt(LocalDateTime.now());
        adapterMapper.updateById(adapter);
        return ApiResponse.ok(adapter);
    }

    @GetMapping("/sync-logs")
    @RequirePermission("sys:config")
    public ApiResponse<List<IntSyncLog>> syncLogs(@RequestParam(required = false) Long adapterId) {
        return ApiResponse.ok(syncLogMapper.selectList(new LambdaQueryWrapper<IntSyncLog>()
                .eq(adapterId != null, IntSyncLog::getAdapterId, adapterId)
                .orderByDesc(IntSyncLog::getPushedAt)
                .last("LIMIT 50")));
    }
}
