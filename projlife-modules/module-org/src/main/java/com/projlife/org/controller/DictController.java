package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.org.dto.DictItemReq;
import com.projlife.org.dto.DictTypeReq;
import com.projlife.org.service.DictService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 字典接口：类型/项维护需 org:dict:manage；按类型编码取项登录即可（下拉数据源）。
 * 系统内置枚举（E-01~E-42）删除被拒（S0-8 完成标准）。
 */
@RestController
@RequestMapping("/api/v1/system/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    @RequirePermission("org:dict:manage")
    public ApiResponse<List<Map<String, Object>>> types() {
        return ApiResponse.ok(dictService.listTypes());
    }

    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> items(@RequestParam Long typeId) {
        return ApiResponse.ok(dictService.itemsOf(typeId));
    }

    @GetMapping("/by-code")
    public ApiResponse<Map<String, String>> byCode(@RequestParam String typeCode) {
        return ApiResponse.ok(dictService.itemsByCode(typeCode));
    }

    @PostMapping("/types")
    @RequirePermission("org:dict:manage")
    public ApiResponse<Long> createType(@Valid @RequestBody DictTypeReq req) {
        return ApiResponse.ok(dictService.createType(req));
    }

    @PutMapping("/types/{id}")
    @RequirePermission("org:dict:manage")
    public ApiResponse<Void> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeReq req) {
        dictService.updateType(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/types/{id}")
    @RequirePermission("org:dict:manage")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return ApiResponse.ok();
    }

    @PostMapping("/items")
    @RequirePermission("org:dict:manage")
    public ApiResponse<Long> createItem(@Valid @RequestBody DictItemReq req) {
        return ApiResponse.ok(dictService.createItem(req));
    }

    @PutMapping("/items/{id}")
    @RequirePermission("org:dict:manage")
    public ApiResponse<Void> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemReq req) {
        dictService.updateItem(id, req);
        return ApiResponse.ok();
    }
}
