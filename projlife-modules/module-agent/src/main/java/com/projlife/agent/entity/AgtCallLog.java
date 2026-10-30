package com.projlife.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.AuditedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-AGT agt_call_log（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("agt_call_log")
public class AgtCallLog extends AuditedEntity {

    /** 注册 id */
    private Long registrationId;

    /** 会话 id */
    private Long sessionId;

    /** 能力标识 */
    private String capability;

    /** 任务 id */
    private String taskId;

    /** 请求摘要 */
    private String requestDigest;

    /** OK/ACCEPTED/BLOCKED/ERROR */
    private String responseStatus;

    /** AGT-FORBIDDEN 等 */
    private String blockCode;

    /** 耗时 */
    private Integer latencyMs;

    /** 追踪 id */
    private String traceId;

    /** token 消耗 */
    private Long tokensUsed;

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String requestDigest) {
        this.requestDigest = requestDigest;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getBlockCode() {
        return blockCode;
    }

    public void setBlockCode(String blockCode) {
        this.blockCode = blockCode;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

}