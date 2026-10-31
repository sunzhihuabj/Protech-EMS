package com.projlife.qms.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.qms.entity.EamAsset;
import com.projlife.qms.mapper.EamAssetMapper;
import com.projlife.security.auth.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 设备资产接口（M09 FR-EAM-01~22）：台账 CRUD + 状态筛选。
 */
@RestController
@RequestMapping("/api/v1/eam")
public class EamController {

    private final EamAssetMapper assetMapper;

    public EamController(EamAssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    @GetMapping("/assets")
    @RequirePermission("qms:inspect")
    public ApiResponse<PageResp<EamAsset>> assets(PageReq pageReq,
            @RequestParam(required = false) String keyword) {
        var wrapper = new LambdaQueryWrapper<EamAsset>()
                .eq(EamAsset::getDeleted, 0)
                .like(keyword != null && !keyword.isEmpty(), EamAsset::getAssetName, keyword)
                .orderByDesc(EamAsset::getId);
        Page<EamAsset> page = assetMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        return ApiResponse.ok(new PageResp<>(page.getTotal(), 0, page.getRecords()));
    }

    @PostMapping("/assets")
    @RequirePermission("qms:inspect")
    public ApiResponse<Long> createAsset(@RequestBody EamAsset asset) {
        assetMapper.insert(asset);
        return ApiResponse.ok(asset.getId());
    }
}
