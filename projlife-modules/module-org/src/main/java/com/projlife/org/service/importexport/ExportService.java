package com.projlife.org.service.importexport;

import com.alibaba.excel.EasyExcel;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * 通用导出（S0-11）：EasyExcel 流式写出；脱敏在调用方组装行数据时完成（如 R11 无金额列、手机号掩码）。
 */
@Service
public class ExportService {

    public void write(HttpServletResponse response, String fileName, String sheetName,
                      List<List<String>> head, List<List<String>> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet(sheetName).doWrite(rows);
    }
}
