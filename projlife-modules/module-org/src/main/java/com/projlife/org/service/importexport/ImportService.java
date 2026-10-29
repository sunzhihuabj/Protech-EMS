package com.projlife.org.service.importexport;

import com.alibaba.excel.EasyExcel;
import com.projlife.common.exception.BizException;
import com.projlife.org.entity.SysImportBatch;
import com.projlife.org.mapper.SysImportBatchMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入框架（S0-10）：干跑 → 报告 → 事务提交 → 批次留痕。
 * 完成标准：3 行错数据报告准确；有错误时事务整体不提交（回滚），错误报告可下载。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final SysImportBatchMapper batchMapper;

    public ImportService(SysImportBatchMapper batchMapper) {
        this.batchMapper = batchMapper;
    }

    public ImportResult dryRun(String fileName, InputStream inputStream, RowImportHandler<?> handler) {
        return execute(fileName, inputStream, handler, true, null);
    }

    @Transactional
    public ImportResult commit(String fileName, InputStream inputStream, RowImportHandler<?> handler,
                               String errorReportDir) {
        return execute(fileName, inputStream, handler, false, errorReportDir);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ImportResult execute(String fileName, InputStream inputStream, RowImportHandler handler,
                                 boolean dryRun, String errorReportDir) {
        List<Map<Integer, String>> rawRows = readRows(inputStream);
        if (rawRows.isEmpty()) {
            throw BizException.badRequest("导入文件为空");
        }
        rawRows.remove(0); // 表头行

        List<Object> parsed = new ArrayList<>();
        List<String[]> errors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            int rowIdx = i + 2; // Excel 行号：表头占第 1 行
            Object row;
            try {
                row = handler.parse(rowIdx, rawRows.get(i));
            } catch (Exception ex) {
                errors.add(new String[]{String.valueOf(rowIdx), "解析失败：" + ex.getMessage()});
                continue;
            }
            List<String> rowErrors = handler.validate(rowIdx, row);
            if (rowErrors.isEmpty()) {
                parsed.add(row);
            } else {
                errors.add(new String[]{String.valueOf(rowIdx), String.join("；", rowErrors)});
            }
        }

        SysImportBatch batch = new SysImportBatch();
        batch.setBatchNo(batchNo());
        batch.setModule(handler.module());
        batch.setFileName(fileName);
        batch.setTotalRows(rawRows.size());
        batch.setSuccessRows(parsed.size());
        batch.setFailRows(errors.size());
        batch.setRolledBack(0);

        if (dryRun) {
            batch.setStatus("DRY_RUN");
            batchMapper.insert(batch);
            return new ImportResult(batch.getBatchNo(), rawRows.size(), parsed.size(), errors.size(), errors, null);
        }

        if (!errors.isEmpty()) {
            String reportPath = writeErrorReport(errorReportDir, batch.getBatchNo(), handler.headers(), errors);
            batch.setStatus("FAILED");
            batch.setErrorReportPath(reportPath);
            batchMapper.insert(batch);
            throw BizException.badRequest("导入存在 " + errors.size() + " 行错误，已生成错误报告，未提交任何数据")
                    .with("batchNo", batch.getBatchNo());
        }

        handler.persist(parsed);
        batch.setStatus("COMMITTED");
        batchMapper.insert(batch);
        log.info("导入提交 module={} rows={}", handler.module(), parsed.size());
        return new ImportResult(batch.getBatchNo(), rawRows.size(), parsed.size(), 0, errors, null);
    }

    private List<Map<Integer, String>> readRows(InputStream inputStream) {
        try {
            List<Map<Integer, String>> rows = new ArrayList<>();
            EasyExcel.read(inputStream, new AnalysisRowListener(rows)).sheet().doRead();
            return rows;
        } catch (Exception ex) {
            throw BizException.badRequest("文件解析失败，请使用模板提供的 xlsx 格式：" + ex.getMessage());
        }
    }

    private String writeErrorReport(String dir, String batchNo, List<String> headers, List<String[]> errors) {
        try {
            Path dirPath = Paths.get(dir);
            Files.createDirectories(dirPath);
            Path report = dirPath.resolve(batchNo + "-error-report.csv");
            StringBuilder content = new StringBuilder("行号,错误明细\r\n");
            for (String[] error : errors) {
                content.append(error[0]).append(",\"").append(error[1].replace("\"", "\"\"")).append("\"\r\n");
            }
            Files.write(report, content.toString().getBytes(StandardCharsets.UTF_8));
            return report.toString();
        } catch (IOException ex) {
            log.warn("错误报告写入失败 batchNo={}", batchNo, ex);
            return null;
        }
    }

    private String batchNo() {
        return "IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    /** 导入结果（含行级错误明细，供前端表格展示） */
    public static class ImportResult {

        public final String batchNo;
        public final int totalRows;
        public final int successRows;
        public final int failRows;
        public final List<String[]> errors;
        public final String errorReportPath;

        public ImportResult(String batchNo, int totalRows, int successRows, int failRows,
                            List<String[]> errors, String errorReportPath) {
            this.batchNo = batchNo;
            this.totalRows = totalRows;
            this.successRows = successRows;
            this.failRows = failRows;
            this.errors = errors;
            this.errorReportPath = errorReportPath;
        }
    }

    /** EasyExcel 逐行监听：仅缓存原始单元格值（字符串），解析交给 RowImportHandler */
    public static class AnalysisRowListener implements com.alibaba.excel.read.listener.ReadListener<Map<Integer, String>> {

        private final List<Map<Integer, String>> rows;

        public AnalysisRowListener(List<Map<Integer, String>> rows) {
            this.rows = rows;
        }

        @Override
        public void invoke(Map<Integer, String> row, com.alibaba.excel.context.AnalysisContext context) {
            rows.add(new LinkedHashMap<>(row));
        }

        @Override
        public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext context) {
            // 无需处理
        }
    }
}
