package com.projlife.org.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.context.UserContextHolder;
import com.projlife.org.service.audit.OperationLogService;
import com.projlife.org.service.importexport.ExportService;
import com.projlife.org.service.importexport.ImportService;
import com.projlife.org.service.importexport.UserImportHandler;
import com.projlife.security.auth.RequirePermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入导出接口（org:user:manage）。
 * 导入两段式：dryRun=true 干跑出报告 → 确认后 dryRun=false 正式提交；有错行时事务整体不提交。
 * 导出：手机号已脱敏（R11 等无金额权限角色场景在导出组装层完成列裁剪，BR-06）。
 */
@RestController
@RequestMapping("/api/v1/org/users")
public class ImportExportController {

    private final ImportService importService;
    private final UserImportHandler userImportHandler;
    private final ExportService exportService;
    private final OperationLogService operationLogService;

    @Value("${projlife.storage.root:./data/storage}")
    private String storageRoot;

    public ImportExportController(ImportService importService, UserImportHandler userImportHandler,
                                  ExportService exportService, OperationLogService operationLogService) {
        this.importService = importService;
        this.userImportHandler = userImportHandler;
        this.exportService = exportService;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/import")
    @RequirePermission("org:user:manage")
    public ApiResponse<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(defaultValue = "true") boolean dryRun)
            throws IOException {
        ImportService.ImportResult result;
        if (dryRun) {
            result = importService.dryRun(file.getOriginalFilename(), file.getInputStream(), userImportHandler);
        } else {
            result = importService.commit(file.getOriginalFilename(), file.getInputStream(),
                    userImportHandler, storageRoot + "/import-reports");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", result.batchNo);
        body.put("totalRows", result.totalRows);
        body.put("successRows", result.successRows);
        body.put("failRows", result.failRows);
        body.put("errors", result.errors);
        body.put("dryRun", dryRun);
        return ApiResponse.ok(body);
    }

    @PostMapping("/export")
    @RequirePermission("org:user:manage")
    public void export(HttpServletResponse response) throws IOException {
        List<List<String>> head = new ArrayList<>();
        for (String title : Arrays.asList("工号", "登录名", "姓名", "手机号(脱敏)", "部门ID", "岗位", "状态")) {
            head.add(Arrays.asList(title));
        }
        // S0 演示数据集：生产实现接入 UserService 分页拉取 + 行级脱敏组装
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("E0001", "admin@demo", "系统管理员", "138****0001", "1", "系统管理", "ACTIVE"));
        exportService.write(response, "用户清单", "用户", head, rows);
        operationLogService.recordManual("org", "export_users", "sys_user", null, null, "rows=1;fields=工号,登录名,姓名,手机号(脱敏)");
    }
}
