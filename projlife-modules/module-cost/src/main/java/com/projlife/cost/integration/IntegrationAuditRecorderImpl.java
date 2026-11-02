package com.projlife.cost.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.spi.IntegrationAuditRecorder;
import com.projlife.cost.entity.IntAdapter;
import com.projlife.cost.entity.IntSyncLog;
import com.projlife.cost.mapper.IntAdapterMapper;
import com.projlife.cost.mapper.IntSyncLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * T-INT 同步日志记录器（SPRINT-INT1）：IntegrationAuditRecorder 的落库实现。
 * 适配器编码→id 解析失败或落库异常一律吞掉（仅 warn），保证集成调用主流程不阻塞。
 */
@Component
public class IntegrationAuditRecorderImpl implements IntegrationAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(IntegrationAuditRecorderImpl.class);

    private final IntAdapterMapper adapterMapper;
    private final IntSyncLogMapper syncLogMapper;
    private final ObjectMapper objectMapper;

    public IntegrationAuditRecorderImpl(IntAdapterMapper adapterMapper, IntSyncLogMapper syncLogMapper,
                                        ObjectMapper objectMapper) {
        this.adapterMapper = adapterMapper;
        this.syncLogMapper = syncLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String adapterCode, String direction, String remoteId, String status,
                       String errorMessage, String payloadJson) {
        try {
            IntAdapter adapter = adapterMapper.selectOne(new LambdaQueryWrapper<IntAdapter>()
                    .eq(IntAdapter::getAdapterCode, adapterCode)
                    .eq(IntAdapter::getDeleted, 0)
                    .last("LIMIT 1"));
            IntSyncLog syncLog = new IntSyncLog();
            syncLog.setAdapterId(adapter == null ? 0L : adapter.getId());
            syncLog.setDirection(direction == null ? "PUSH" : direction);
            syncLog.setRemoteId(remoteId);
            syncLog.setStatus(status == null ? "PENDING" : status);
            if (errorMessage != null && errorMessage.length() > 480) {
                errorMessage = errorMessage.substring(0, 480);
            }
            syncLog.setErrorMessage(errorMessage);
            syncLog.setRetryCount(0);
            syncLog.setPushedAt(LocalDateTime.now());
            syncLog.setCreatedBy(adapterCode);
            if (payloadJson != null) {
                try {
                    syncLog.setPayloadJson(payloadJson.length() > 60000
                            ? payloadJson.substring(0, 60000) : payloadJson);
                } catch (Exception ex) {
                    syncLog.setPayloadJson(null);
                }
            }
            syncLogMapper.insert(syncLog);
        } catch (Exception ex) {
            log.warn("集成审计落库失败（不影响业务）adapter={} remote={} err={}", adapterCode, remoteId, ex.getMessage());
        }
    }
}
