package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 编码引擎预览接口（system:config:manage）：S0-7 演示与联调取号入口。
 * 每次调用真实取号（允许空档，不回补），预览场景请使用 DEMO 作用域前缀避免污染真实序列。
 */
@RestController
@RequestMapping("/api/v1/system/codes")
public class CodeController {

    private final CodeSequenceService codeSequenceService;

    public CodeController(CodeSequenceService codeSequenceService) {
        this.codeSequenceService = codeSequenceService;
    }

    @GetMapping("/preview")
    @RequirePermission("system:config:manage")
    public ApiResponse<Map<String, String>> preview(@RequestParam String type) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String year = String.valueOf(LocalDate.now().getYear());
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        CodeTypes.CodeRequest request;
        switch (type) {
            case "CONTRACT":
                request = CodeTypes.contract("TLD", year, "CG", "AZFWXF");
                break;
            case "PROJECT_ARCHIVE":
                request = CodeTypes.projectArchive(year);
                break;
            case "OPPORTUNITY":
                request = CodeTypes.opportunity(month);
                break;
            case "PURCHASE_ORDER":
                request = CodeTypes.purchaseOrder(month);
                break;
            case "DISPATCH":
                request = CodeTypes.dispatch(today);
                break;
            case "ECN":
                request = CodeTypes.ecn(year);
                break;
            case "CUSTOMER":
                request = CodeTypes.customer();
                break;
            case "SUPPLIER":
                request = CodeTypes.supplier();
                break;
            default:
                request = null;
        }
        if (request == null) {
            return ApiResponse.ok(new HashMap<>());
        }
        Map<String, String> result = new HashMap<>();
        result.put("scopeKey", request.getScopeKey());
        result.put("next", codeSequenceService.next(request));
        return ApiResponse.ok(result);
    }

    @GetMapping("/codes")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> codes() {
        var rows = codeSequenceService.listAll();
        return ApiResponse.ok(rows);
    }
}
