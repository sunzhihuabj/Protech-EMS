package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

/**
 * T-SYS 导入批次（干跑/报告/事务/回滚，S0-10）。
 */
@TableName("sys_import_batch")
public class SysImportBatch extends BaseEntity {

    private String batchNo;

    private String module;

    private String fileName;

    private Integer totalRows;

    private Integer successRows;

    private Integer failRows;

    /** DRY_RUN/COMMITTED/FAILED/ROLLED_BACK */
    private String status;

    private String errorReportPath;

    private Integer rolledBack;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getSuccessRows() {
        return successRows;
    }

    public void setSuccessRows(Integer successRows) {
        this.successRows = successRows;
    }

    public Integer getFailRows() {
        return failRows;
    }

    public void setFailRows(Integer failRows) {
        this.failRows = failRows;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorReportPath() {
        return errorReportPath;
    }

    public void setErrorReportPath(String errorReportPath) {
        this.errorReportPath = errorReportPath;
    }

    public Integer getRolledBack() {
        return rolledBack;
    }

    public void setRolledBack(Integer rolledBack) {
        this.rolledBack = rolledBack;
    }
}
