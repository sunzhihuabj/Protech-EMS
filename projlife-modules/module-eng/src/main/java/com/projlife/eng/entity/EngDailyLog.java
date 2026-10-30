package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_daily_log（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_daily_log")
public class EngDailyLog extends BaseEntity {

    /** 派工单 */
    private Long dispatchId;

    /** 项目 */
    private Long projectId;

    /** 填报人 */
    private Long workerId;

    /** 日志日期 */
    private LocalDate logDate;

    /** 天气 */
    private String weather;

    /** 内容 */
    private String content;

    /** 照片（水印后删原图 BR-07） */
    private String photoIds;

    /** AI-008 分析 */
    private String aiCheckResult;

    /** 离线标记（FR-ENG-10） */
    private Integer offlineFlag;

    /** E-02 */
    private String status;

    public Long getDispatchId() {
        return dispatchId;
    }

    public void setDispatchId(Long dispatchId) {
        this.dispatchId = dispatchId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPhotoIds() {
        return photoIds;
    }

    public void setPhotoIds(String photoIds) {
        this.photoIds = photoIds;
    }

    public String getAiCheckResult() {
        return aiCheckResult;
    }

    public void setAiCheckResult(String aiCheckResult) {
        this.aiCheckResult = aiCheckResult;
    }

    public Integer getOfflineFlag() {
        return offlineFlag;
    }

    public void setOfflineFlag(Integer offlineFlag) {
        this.offlineFlag = offlineFlag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}