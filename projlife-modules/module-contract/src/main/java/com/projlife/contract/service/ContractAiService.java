package com.projlife.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.contract.entity.AiFeedback;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.entity.CtContractRisk;
import com.projlife.contract.mapper.AiFeedbackMapper;
import com.projlife.contract.mapper.CtContractMapper;
import com.projlife.contract.mapper.CtContractRiskMapper;
import com.projlife.integration.ai.AiGatewayClient;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同 AI 能力（04 册 FR-CT-05~11）：风险分析 AI-001 / 信息提取 AI-002。
 * 铁律：业务只经 ai-gateway 客户端；mock/降级恒可用（FR-CT-10 不阻塞归档）；
 * FR-CT-11 人工修正逐字段写 ai_feedback（模型迭代数据源）。
 */
@Service
public class ContractAiService {

    private final AiGatewayClient gatewayClient;
    private final CtContractMapper contractMapper;
    private final CtContractRiskMapper riskMapper;
    private final AiFeedbackMapper feedbackMapper;

    public ContractAiService(AiGatewayClient gatewayClient, CtContractMapper contractMapper,
                             CtContractRiskMapper riskMapper, AiFeedbackMapper feedbackMapper) {
        this.gatewayClient = gatewayClient;
        this.contractMapper = contractMapper;
        this.riskMapper = riskMapper;
        this.feedbackMapper = feedbackMapper;
    }

    /** FR-CT-05/06：风险分析——结果随合同保存，不影响归档（AI 是辅助不是门禁） */
    @SuppressWarnings("unchecked")
    @Audited(module = "contract", action = "ai_risk_analysis", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public Map<String, Object> analyzeRisk(Long contractId, String textDigest) {
        CtContract contract = mustGet(contractId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractName", contract.getContractName());
        payload.put("amount", contract.getAmount());
        payload.put("paymentTerms", contract.getPaymentTerms());
        payload.put("textDigest", textDigest == null ? "" : textDigest.substring(0, Math.min(textDigest.length(), 2000)));

        Map<String, Object> response = gatewayClient.call("AI-001", "ct_contract", contractId, payload);
        Object data = response.get("data");
        int saved = 0;
        if (data instanceof Map) {
            Object risks = ((Map<String, Object>) data).get("risks");
            if (risks instanceof List) {
                for (Object item : (List<Object>) risks) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> risk = (Map<String, Object>) item;
                    CtContractRisk entity = new CtContractRisk();
                    entity.setContractId(contractId);
                    entity.setRiskType(String.valueOf(risk.getOrDefault("clause", "GENERAL")));
                    entity.setRiskLevel(mapLevel(risk.get("level")));
                    entity.setRiskDesc(String.valueOf(risk.getOrDefault("description", "")));
                    entity.setSuggestion(risk.get("suggestion") == null ? null : String.valueOf(risk.get("suggestion")));
                    riskMapper.insert(entity);
                    saved++;
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", response.get("status"));
        result.put("isMock", response.get("isMock"));
        result.put("savedRisks", saved);
        result.put("note", "DEGRADED".equals(response.get("status")) ? "AI 服务降级，返回的是兜底结果" : "ok");
        return result;
    }

    /** 采纳/忽略（FR-CT-05：可采纳/忽略） */
    @Transactional
    public void adoptRisk(Long riskId, boolean adopted, Long reviewerId) {
        CtContractRisk risk = riskMapper.selectById(riskId);
        if (risk == null) {
            throw BizException.notFound("风险条目不存在");
        }
        risk.setIsAdopted(adopted ? 1 : 0);
        risk.setReviewerId(reviewerId == null ? UserContextHolder.currentUserId() : reviewerId);
        riskMapper.updateById(risk);
    }

    public List<CtContractRisk> risksOf(Long contractId) {
        return riskMapper.selectList(new LambdaQueryWrapper<CtContractRisk>()
                .eq(CtContractRisk::getContractId, contractId)
                .orderByDesc(CtContractRisk::getId));
    }

    /**
     * FR-CT-07：信息提取（12 字段）。
     * 网关 mock 恒可用；真实模型/网关不可用 → status=DEGRADED，前端提示手工填写（FR-CT-10）。
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> extract(Long contractId) {
        CtContract contract = mustGet(contractId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractName", contract.getContractName());
        payload.put("fileId", contract.getOriginalFileId());
        Map<String, Object> response = gatewayClient.call("AI-002", "ct_contract", contractId, payload);
        Map<String, Object> result = new HashMap<>();
        result.put("status", response.get("status"));
        result.put("isMock", response.get("isMock"));
        result.put("confidence", response.get("confidence"));
        result.put("callId", response.get("callId"));
        result.put("fields", fieldsOf(response.get("data")));
        if ("DEGRADED".equals(response.get("status"))) {
            result.put("note", "AI 提取不可用，请手工填写（FR-CT-10），不阻塞归档");
        }
        return result;
    }

    private Map<String, Object> fieldsOf(Object data) {
        if (data instanceof Map) {
            Object fields = ((Map<String, Object>) data).get("fields");
            if (fields instanceof Map) {
                return (Map<String, Object>) fields;
            }
        }
        return null;
    }

    /**
     * FR-CT-09/11：确认归档——修正后的字段写回合同主记录（锁定后续须走变更），
     * 与 AI 原值逐字段比对，改动记 ai_feedback。
     */
    @Audited(module = "contract", action = "confirm_extract", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public Map<String, Object> confirmExtract(Long contractId, Long callId,
                                              Map<String, String> aiFields, Map<String, String> humanFields) {
        CtContract contract = mustGet(contractId);
        int corrected = 0;
        for (Map.Entry<String, String> entry : humanFields.entrySet()) {
            String field = entry.getKey();
            String humanValue = entry.getValue();
            String aiValue = aiFields.get(field);
            if (aiValue != null && humanValue != null && !aiValue.equals(humanValue)) {
                AiFeedback feedback = new AiFeedback();
                feedback.setCallId(callId == null ? 0L : callId);
                feedback.setBizType("ct_contract");
                feedback.setBizId(contractId);
                feedback.setAiField(field);
                feedback.setAiValue(aiValue);
                feedback.setHumanValue(humanValue);
                feedback.setIsCorrect(0);
                feedback.setOperatorId(UserContextHolder.currentUserId());
                feedbackMapper.insert(feedback);
                corrected++;
            }
        }
        applyExtractedFields(contract, humanFields);
        contract.setAiExtracted(1);
        contractMapper.updateById(contract);
        Map<String, Object> result = new HashMap<>();
        result.put("correctedFields", corrected);
        result.put("aiExtracted", true);
        return result;
    }

    private void applyExtractedFields(CtContract contract, Map<String, String> fields) {
        // 白名单映射：仅 12 提取要素中由合同实体承载的字段
        mapField(contract, fields, "contractName", "setContractName", String.class);
        mapField(contract, fields, "amount", "setAmount", java.math.BigDecimal.class);
        mapField(contract, fields, "deliveryAddr", "setDeliveryAddr", String.class);
        mapField(contract, fields, "contact", "setContact", String.class);
        mapField(contract, fields, "phone", "setPhone", String.class);
        mapField(contract, fields, "deliveryDate", "setDeliveryDate", java.time.LocalDate.class);
        mapField(contract, fields, "acceptanceClause", "setAcceptanceClause", String.class);
        mapField(contract, fields, "paymentTerms", "setPaymentTerms", String.class);
    }

    private void mapField(CtContract contract, Map<String, String> fields, String key, String setter, Class<?> type) {
        String value = fields.get(key);
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            java.lang.reflect.Method method = CtContract.class.getMethod(setter, type);
            Object coerced = value;
            if (type == java.math.BigDecimal.class) {
                coerced = new java.math.BigDecimal(value.replaceAll("[^0-9.]", ""));
            } else if (type == java.time.LocalDate.class) {
                coerced = java.time.LocalDate.parse(value.substring(0, 10));
            }
            method.invoke(contract, coerced);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ex) {
            // 提取值格式非法时不回填（保留人工值由建档流程修正），不阻塞归档
        }
    }

    private String mapLevel(Object level) {
        String value = String.valueOf(level);
        if ("RED".equals(value)) {
            return "H";
        }
        if ("YELLOW".equals(value)) {
            return "M";
        }
        return "L";
    }

    private CtContract mustGet(Long id) {
        CtContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw BizException.notFound("合同不存在");
        }
        return contract;
    }
}
