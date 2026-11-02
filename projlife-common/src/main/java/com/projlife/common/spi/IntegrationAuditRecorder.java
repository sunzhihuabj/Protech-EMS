package com.projlife.common.spi;

/**
 * 外部集成审计记录 SPI（SPRINT-INT1）：
 * 集成客户端（module-integration）通过本接口把每次出站/入站调用落 int_sync_log（T-INT 层），
 * 实现在 module-cost（IntSyncRecorderImpl，持表 Mapper）。判空容错：未实现/落库失败不影响业务（铁律 C-12）。
 */
public interface IntegrationAuditRecorder {

    /**
     * 记录一次集成调用。
     *
     * @param adapterCode  适配器编码（DINGTALK/SHB/TPLUS，对应 int_adapter.adapter_code）
     * @param direction    PUSH（出站）/PULL（拉取）/INBOUND（回调入站）
     * @param remoteId     远端单据号（task_id/instanceId/eventNo/工单号等，官方要求持久化保存的 ID）
     * @param status       SUCCESS/FAILED/DEGRADED
     * @param errorMessage 失败原因（≤500 字符，超长截断）
     * @param payloadJson  请求/响应载荷快照（可为 null；webhook 原始报文必存，A-40）
     */
    void record(String adapterCode, String direction, String remoteId, String status,
                String errorMessage, String payloadJson);
}
