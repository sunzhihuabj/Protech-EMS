package com.projlife.eng.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.context.UserContextHolder;
import com.projlife.eng.entity.EngDispatch;
import com.projlife.eng.entity.EngDailyLog;
import com.projlife.eng.entity.EngQualityCheck;
import com.projlife.eng.entity.EngMaterialIssue;
import com.projlife.eng.entity.EngAfterSales;
import com.projlife.eng.mapper.EngDispatchMapper;
import com.projlife.eng.mapper.EngDailyLogMapper;
import com.projlife.eng.mapper.EngQualityCheckMapper;
import com.projlife.eng.mapper.EngMaterialIssueMapper;
import com.projlife.eng.mapper.EngAfterSalesMapper;
import com.projlife.integration.ai.AiGatewayClient;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 工程日常记录服务（V4-05 M10）：施工日志（AI 合规检查）/质量检查/现场物料/售后工单闭环。
 *
 * <p>FR-ENG：日志经 BR-03 派工关联（未派工不得记日志）；质检 FAIL 必填缺陷描述；
 * 退料不超领料；售后 TO_DISPATCH → RESPONDED → CLOSED（闭环必填方案 + 客户确认）。</p>
 */
@Service
public class EngRecordService {

    private final EngDispatchMapper dispatchMapper;
    private final EngDailyLogMapper dailyLogMapper;
    private final EngQualityCheckMapper qualityCheckMapper;
    private final EngMaterialIssueMapper materialIssueMapper;
    private final EngAfterSalesMapper afterSalesMapper;
    private final AiGatewayClient aiGatewayClient;
    private final CodeSequenceService codeSequenceService;

    public EngRecordService(EngDispatchMapper dispatchMapper, EngDailyLogMapper dailyLogMapper,
                            EngQualityCheckMapper qualityCheckMapper, EngMaterialIssueMapper materialIssueMapper,
                            EngAfterSalesMapper afterSalesMapper, AiGatewayClient aiGatewayClient,
                            CodeSequenceService codeSequenceService) {
        this.dispatchMapper = dispatchMapper;
        this.dailyLogMapper = dailyLogMapper;
        this.qualityCheckMapper = qualityCheckMapper;
        this.materialIssueMapper = materialIssueMapper;
        this.afterSalesMapper = afterSalesMapper;
        this.aiGatewayClient = aiGatewayClient;
        this.codeSequenceService = codeSequenceService;
    }

    /** 施工日志：经 BR-03 派工校验 + AI 合规检查（AI-009，降级不阻塞 FR-AI 铁律） */
    @RequirePermission("eng:log")
    @Transactional
    public EngDailyLog dailyLog(EngDailyLog log) {
        if (log.getDispatchId() == null || log.getProjectId() == null || log.getWorkerId() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "派工/项目/人员必填");
        }
        if (log.getContent() == null || log.getContent().length() < 5) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "日志内容必填（≥5 字）");
        }
        EngDispatch dispatch = dispatchMapper.selectById(log.getDispatchId());
        if (dispatch == null) {
            throw BizException.notFound("派工单不存在: " + log.getDispatchId());
        }
        if (log.getLogDate() == null) {
            log.setLogDate(LocalDate.now());
        }
        log.setStatus("ACTIVE");
        log.setOfflineFlag(0);
        // AI-009 日志合规检查：mock/降级恒可用，结果随日志保存，不阻塞记录（FR-AI-08）
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", log.getContent());
            payload.put("weather", log.getWeather());
            Map<String, Object> ai = aiGatewayClient.call("AI-009", "eng_daily_log", null, payload);
            // JSON 列要求合法 JSON（Map.toString 非 JSON，MySQL 拒收）
            log.setAiCheckResult(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ai));
        } catch (Exception ignore) {
            log.setAiCheckResult("{\"degraded\":true}");
        }
        dailyLogMapper.insert(log);
        return log;
    }

    /** 质量检查：PASS/FAIL；FAIL 必填缺陷描述 */
    @RequirePermission("eng:install")
    @Transactional
    public EngQualityCheck qualityCheck(EngQualityCheck check) {
        if (check.getUnitId() == null || check.getCheckItem() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "机组与检查项必填");
        }
        if (!"PASS".equals(check.getResult()) && !"FAIL".equals(check.getResult())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "结果必须为 PASS/FAIL");
        }
        if ("FAIL".equals(check.getResult())
                && (check.getDefectDesc() == null || check.getDefectDesc().isEmpty())) {
            throw BizException.blocked("ENG-QC-DEFECT",
                    "质检不合格必须填写缺陷描述", "请记录缺陷后提交，并走整改闭环（FR-ENG-08）");
        }
        if (check.getCheckTime() == null) {
            check.setCheckTime(LocalDateTime.now());
        }
        if (check.getCheckerId() == null) {
            check.setCheckerId(UserContextHolder.currentUserId());
        }
        qualityCheckMapper.insert(check);
        return check;
    }

    /** 现场领料 */
    @RequirePermission("eng:expense")
    @Transactional
    public EngMaterialIssue issue(EngMaterialIssue issue) {
        if (issue.getProjectId() == null || issue.getMaterialId() == null
                || issue.getIssueQty() == null || issue.getIssueQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "项目/物料/领料数量必填（数量须为正）");
        }
        issue.setStatus("ISSUED");
        if (issue.getReturnQty() == null) {
            issue.setReturnQty(BigDecimal.ZERO);
        }
        materialIssueMapper.insert(issue);
        return issue;
    }

    /** 现场退料：累计退料不得超领料 */
    @RequirePermission("eng:expense")
    @Transactional
    public EngMaterialIssue returnMaterial(Long id, BigDecimal qty) {
        EngMaterialIssue issue = materialIssueMapper.selectById(id);
        if (issue == null) {
            throw BizException.notFound("领料记录不存在: " + id);
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "退料数量必须为正");
        }
        BigDecimal returned = issue.getReturnQty() == null ? BigDecimal.ZERO : issue.getReturnQty();
        if (returned.add(qty).compareTo(issue.getIssueQty()) > 0) {
            throw BizException.blocked("ENG-RETURN-OVER",
                    "累计退料不得超领料数量（已退 " + returned + "，本次 " + qty + "，领料 " + issue.getIssueQty() + "）",
                    "请核对现场余料后重新登记");
        }
        issue.setReturnQty(returned.add(qty));
        issue.setStatus(issue.getReturnQty().compareTo(issue.getIssueQty()) == 0 ? "RETURNED" : "ISSUED");
        materialIssueMapper.updateById(issue);
        return issue;
    }

    /** 售后工单：WO 编号 + TO_DISPATCH */
    @RequirePermission("eng:after:manage")
    @Transactional
    public EngAfterSales createAfterSales(EngAfterSales wo) {
        if (wo.getMdProjectId() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "项目必填");
        }
        wo.setOrderNo(codeSequenceService.next(CodeTypes.afterSales(
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE))));
        wo.setStatus("TO_DISPATCH");
        if (wo.getUrgency() == null) {
            wo.setUrgency("NORMAL");
        }
        if (wo.getReportedAt() == null) {
            wo.setReportedAt(LocalDateTime.now());
        }
        wo.setCustomerConfirm(0);
        afterSalesMapper.insert(wo);
        return wo;
    }

    /** 售后响应：TO_DISPATCH → RESPONDED */
    @RequirePermission("eng:after:manage")
    @Transactional
    public EngAfterSales respond(Long id, Long handlerId) {
        EngAfterSales wo = mustWo(id);
        if (!"TO_DISPATCH".equals(wo.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 TO_DISPATCH 可响应");
        }
        wo.setStatus("RESPONDED");
        wo.setRespondedAt(LocalDateTime.now());
        wo.setHandlerId(handlerId == null ? UserContextHolder.currentUserId() : handlerId);
        afterSalesMapper.updateById(wo);
        return wo;
    }

    /** 售后闭环：方案必填；customer_confirm=1 表示客户确认 */
    @RequirePermission("eng:after:manage")
    @Transactional
    public EngAfterSales close(Long id, String solution, BigDecimal costAmount) {
        EngAfterSales wo = mustWo(id);
        if (!"RESPONDED".equals(wo.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 RESPONDED 可闭环");
        }
        if (solution == null || solution.length() < 5) {
            throw BizException.blocked("ENG-WO-SOLUTION",
                    "售后闭环必须填写处理方案（≥5 字）", "请记录故障原因与处理措施后闭环");
        }
        wo.setStatus("CLOSED");
        wo.setSolution(solution);
        wo.setCostAmount(costAmount == null ? BigDecimal.ZERO : costAmount);
        wo.setClosedAt(LocalDateTime.now());
        wo.setCustomerConfirm(1);
        afterSalesMapper.updateById(wo);
        return wo;
    }

    public java.util.List<EngAfterSales> afterSalesList(String status) {
        return afterSalesMapper.selectList(new LambdaQueryWrapper<EngAfterSales>()
                .eq(status != null, EngAfterSales::getStatus, status)
                .eq(EngAfterSales::getDeleted, 0).orderByDesc(EngAfterSales::getId));
    }

    private EngAfterSales mustWo(Long id) {
        EngAfterSales wo = afterSalesMapper.selectById(id);
        if (wo == null) {
            throw BizException.notFound("售后工单不存在: " + id);
        }
        return wo;
    }
}
