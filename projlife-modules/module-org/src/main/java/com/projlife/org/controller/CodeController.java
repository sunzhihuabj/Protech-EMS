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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 编码引擎接口（system:config:manage）：S0-7 规则目录 + 取号预览。
 * 每次调用真实取号（允许空档，不回补），预览场景请使用 DEMO 作用域前缀避免污染真实序列。
 */
@RestController
@RequestMapping("/api/v1/system/codes")
public class CodeController {

    private final CodeSequenceService codeSequenceService;

    public CodeController(CodeSequenceService codeSequenceService) {
        this.codeSequenceService = codeSequenceService;
    }

    /** SPRINT-UID-R15：18 类编码规则目录（02 册 §五，CodeTypes 单一入口）——修复 CodeRules 页 404 断链 */
    @GetMapping("/rules")
    @RequirePermission("system:config:manage")
    public ApiResponse<List<Map<String, String>>> rules() {
        List<Map<String, String>> catalog = new ArrayList<>();
        for (String[] row : new String[][]{
                {"CONTRACT", "合同编号", "实体码-年度-性质-业务段-流水", "AZFWXF-2026-CG-****-001"},
                {"CONTRACT_CHANGE", "合同变更单", "合同号-CG-流水", "HT-2026-CG-001"},
                {"LEGACY_CONTRACT", "老合同编号", "年月-流水", "202603-001"},
                {"PROJECT_ARCHIVE", "项目立项编号", "PRJ-年度-流水", "PRJ-2026-001"},
                {"PROJECT_EXEC", "项目执行编号", "PRJX-年度-流水", "PRJX-2026-001"},
                {"WIND_FARM", "风电场编号", "WF-年度-流水", "WF-2026-001"},
                {"CUSTOMER", "客户编码", "C-流水", "C-000001"},
                {"SUPPLIER", "供应商编码", "S-流水", "S-000001"},
                {"MATERIAL", "物料编码", "M-流水", "M-000001"},
                {"VIRTUAL_PART", "虚拟件编码", "V-流水", "V-000001"},
                {"OPPORTUNITY", "商机编号", "OPP-年月-流水", "OPP-202603-001"},
                {"PURCHASE_ORDER", "采购订单号", "PO-年月-流水", "PO-202603-001"},
                {"PRODUCTION_ORDER", "生产订单号", "MO-年月-流水", "MO-202603-001"},
                {"DISPATCH", "派工单号", "DP-日期-流水", "DP-20260301-001"},
                {"AFTER_SALES", "售后工单号", "AS-日期-流水", "AS-20260301-001"},
                {"SHIPMENT", "发货单号", "SH-日期-流水", "SH-20260301-001"},
                {"ECN", "ECN 单号", "ECN-年度-流水", "ECN-2026-001"},
                {"QUOTATION", "报价单号", "QT-年月-流水", "QT-202603-001"}}) {
            Map<String, String> item = new HashMap<>();
            item.put("type", row[0]);
            item.put("name", row[1]);
            item.put("format", row[2]);
            item.put("example", row[3]);
            catalog.add(item);
        }
        return ApiResponse.ok(catalog);
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
}
