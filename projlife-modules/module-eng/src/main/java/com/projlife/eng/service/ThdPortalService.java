package com.projlife.eng.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.eng.entity.ThdBlacklist;
import com.projlife.eng.entity.ThdDeliveryNote;
import com.projlife.eng.entity.ThdEvaluation;
import com.projlife.eng.entity.ThdWorkerProject;
import com.projlife.eng.mapper.ThdBlacklistMapper;
import com.projlife.eng.mapper.ThdDeliveryNoteMapper;
import com.projlife.eng.mapper.ThdEvaluationMapper;
import com.projlife.eng.mapper.ThdWorkerProjectMapper;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 第三方门户服务（V4-05 M11 T-THD）：送货单打印/人员项目授权/考核评价/黑名单。
 *
 * <p>FR-THD：评价四维（质量/交付/安全/服务）均分定级 A≥90/B≥75/C≥60/D；
 * 黑名单生效期间不得授权进场；授权到期扫描 EXPIRED；送货单打印留痕。</p>
 */
@Service
public class ThdPortalService {

    private final ThdDeliveryNoteMapper noteMapper;
    private final ThdWorkerProjectMapper workerProjectMapper;
    private final ThdEvaluationMapper evaluationMapper;
    private final ThdBlacklistMapper blacklistMapper;
    private final CodeSequenceService codeSequenceService;

    public ThdPortalService(ThdDeliveryNoteMapper noteMapper, ThdWorkerProjectMapper workerProjectMapper,
                            ThdEvaluationMapper evaluationMapper, ThdBlacklistMapper blacklistMapper,
                            CodeSequenceService codeSequenceService) {
        this.noteMapper = noteMapper;
        this.workerProjectMapper = workerProjectMapper;
        this.evaluationMapper = evaluationMapper;
        this.blacklistMapper = blacklistMapper;
        this.codeSequenceService = codeSequenceService;
    }

    // ---------- 送货单（FR-THD：第三方打印，无价格字段 BR-06 联动） ----------

    @RequirePermission("thd:portal")
    @Transactional
    public ThdDeliveryNote createDeliveryNote(ThdDeliveryNote note) {
        if (note.getPoId() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "采购订单必填");
        }
        note.setNoteNo(codeSequenceService.next(CodeTypes.deliveryNote(
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        noteMapper.insert(note);
        return note;
    }

    /** 打印留痕（BR-07 同源：打印动作留痕） */
    @RequirePermission("thd:portal")
    @Transactional
    public ThdDeliveryNote print(Long id) {
        ThdDeliveryNote note = mustNote(id);
        note.setPrintedAt(LocalDateTime.now());
        note.setPrintedBy(UserContextHolder.currentUserId());
        noteMapper.updateById(note);
        return note;
    }

    // ---------- 人员项目授权（FR-THD：工人 MP 可见范围即授权范围） ----------

    @RequirePermission("thd:portal")
    @Transactional
    public ThdWorkerProject grantWorker(ThdWorkerProject grant) {
        if (grant.getWorkerId() == null || grant.getMdProjectId() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "人员与项目必填");
        }
        if (grant.getAuthStart() == null || grant.getAuthEnd() == null
                || grant.getAuthEnd().isBefore(grant.getAuthStart())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "授权起止日期非法");
        }
        if (isBlacklisted("WORKER", grant.getWorkerId())) {
            throw BizException.blocked("THD-BLACKLISTED",
                    "该人员已在黑名单，禁止授权进场", "请先完成黑名单解除审批");
        }
        grant.setStatus("GRANTED");
        workerProjectMapper.insert(grant);
        return grant;
    }

    /** 撤销授权 */
    @RequirePermission("thd:portal")
    @Transactional
    public ThdWorkerProject revoke(Long id, String reason) {
        ThdWorkerProject grant = workerProjectMapper.selectById(id);
        if (grant == null) {
            throw BizException.notFound("授权记录不存在: " + id);
        }
        grant.setStatus("REVOKED");
        grant.setFrozenReason(reason == null ? "人工撤销" : reason);
        workerProjectMapper.updateById(grant);
        return grant;
    }

    /** 到期扫描（job 入口）：GRANTED 且 auth_end 已过 → EXPIRED */
    @Transactional
    public int expireScan() {
        List<ThdWorkerProject> grants = workerProjectMapper.selectList(
                new LambdaQueryWrapper<ThdWorkerProject>().eq(ThdWorkerProject::getStatus, "GRANTED"));
        int n = 0;
        LocalDate today = LocalDate.now();
        for (ThdWorkerProject grant : grants) {
            if (grant.getAuthEnd().isBefore(today)) {
                grant.setStatus("EXPIRED");
                workerProjectMapper.updateById(grant);
                n++;
            }
        }
        return n;
    }

    /** SPRINT-UID-R15：授权列表（门户管理数据源，可按 status 过滤，倒序） */
    @RequirePermission("thd:portal")
    public List<ThdWorkerProject> grants(String status) {
        LambdaQueryWrapper<ThdWorkerProject> wrapper = new LambdaQueryWrapper<ThdWorkerProject>()
                .orderByDesc(ThdWorkerProject::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ThdWorkerProject::getStatus, status);
        }
        return workerProjectMapper.selectList(wrapper);
    }

    /** SPRINT-UID-R15：黑名单列表（含生效/已解除，倒序） */
    @RequirePermission("thd:portal")
    public List<ThdBlacklist> blacklists() {
        return blacklistMapper.selectList(new LambdaQueryWrapper<ThdBlacklist>()
                .orderByDesc(ThdBlacklist::getId));
    }

    // ---------- 考核评价（FR-THD：四维均分定级） ----------

    @RequirePermission("thd:portal")
    @Transactional
    public ThdEvaluation evaluate(ThdEvaluation evaluation) {
        if (evaluation.getTargetType() == null
                || !evaluation.getTargetType().matches("SUPPLIER|CREW")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "考核对象必须为 SUPPLIER/CREW");
        }
        if (evaluation.getPeriod() == null || evaluation.getPeriod().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "考核期必填");
        }
        BigDecimal total = average(evaluation.getQualityScore(), evaluation.getDeliveryScore(),
                evaluation.getSafetyScore(), evaluation.getServiceScore());
        if (total == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "四维评分至少填一项");
        }
        evaluation.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
        evaluation.setGrade(total.doubleValue() >= 90 ? "A"
                : total.doubleValue() >= 75 ? "B"
                : total.doubleValue() >= 60 ? "C" : "D");
        // 同期唯一，重复评价视为修正（UK 兜底转更新）
        ThdEvaluation existing = evaluationMapper.selectOne(new LambdaQueryWrapper<ThdEvaluation>()
                .eq(ThdEvaluation::getTargetType, evaluation.getTargetType())
                .eq(ThdEvaluation::getTargetId, evaluation.getTargetId())
                .eq(ThdEvaluation::getPeriod, evaluation.getPeriod()).last("LIMIT 1"));
        if (existing != null) {
            evaluation.setId(existing.getId());
            evaluationMapper.updateById(evaluation);
            return evaluation;
        }
        evaluationMapper.insert(evaluation);
        return evaluation;
    }

    // ---------- 黑名单（FR-THD：R02 审批，生效即冻结授权） ----------

    @RequirePermission("thd:portal")
    @Transactional
    public ThdBlacklist blacklistAdd(ThdBlacklist blacklist) {
        if (blacklist.getTargetType() == null
                || !blacklist.getTargetType().matches("SUPPLIER|CREW|WORKER")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "黑名单对象必须为 SUPPLIER/CREW/WORKER");
        }
        if (blacklist.getReason() == null || blacklist.getReason().isEmpty()) {
            throw BizException.blocked("THD-BL-REASON",
                    "拉黑必须填写原因", "请记录事实依据与证据索引后提交");
        }
        if (blacklist.getApprovedBy() == null) {
            blacklist.setApprovedBy(UserContextHolder.currentUserId()); // R02 审批随流程批次
        }
        if (blacklist.getEffectiveDate() == null) {
            blacklist.setEffectiveDate(LocalDate.now());
        }
        blacklist.setStatus("ACTIVE");
        blacklistMapper.insert(blacklist);
        // 生效即冻结该对象在场授权（WORKER 直接撤；CREW 批量撤其成员授权）
        freezeAuths(blacklist.getTargetType(), blacklist.getTargetId());
        return blacklist;
    }

    /** 解除黑名单 */
    @RequirePermission("thd:portal")
    @Transactional
    public ThdBlacklist blacklistRelease(Long id) {
        ThdBlacklist blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) {
            throw BizException.notFound("黑名单记录不存在: " + id);
        }
        blacklist.setStatus("RELEASED");
        blacklist.setReleaseDate(LocalDate.now());
        blacklistMapper.updateById(blacklist);
        return blacklist;
    }

    public boolean isBlacklisted(String targetType, Long targetId) {
        Long count = blacklistMapper.selectCount(new LambdaQueryWrapper<ThdBlacklist>()
                .eq(ThdBlacklist::getTargetType, targetType)
                .eq(ThdBlacklist::getTargetId, targetId)
                .eq(ThdBlacklist::getStatus, "ACTIVE")
                .eq(ThdBlacklist::getDeleted, 0));
        return count != null && count > 0;
    }

    private void freezeAuths(String targetType, Long targetId) {
        if (!"WORKER".equals(targetType)) {
            return; // CREW/SUPPLIER 成员批量冻结随班组档案关联批次
        }
        List<ThdWorkerProject> grants = workerProjectMapper.selectList(
                new LambdaQueryWrapper<ThdWorkerProject>()
                        .eq(ThdWorkerProject::getWorkerId, targetId)
                        .eq(ThdWorkerProject::getStatus, "GRANTED"));
        for (ThdWorkerProject grant : grants) {
            grant.setStatus("REVOKED");
            grant.setFrozenReason("黑名单生效自动冻结");
            workerProjectMapper.updateById(grant);
        }
    }

    private BigDecimal average(BigDecimal... scores) {
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (BigDecimal score : scores) {
            if (score != null) {
                sum = sum.add(score);
                n++;
            }
        }
        return n == 0 ? null : sum.divide(new BigDecimal(n), 4, RoundingMode.HALF_UP);
    }

    private ThdDeliveryNote mustNote(Long id) {
        ThdDeliveryNote note = noteMapper.selectById(id);
        if (note == null) {
            throw BizException.notFound("送货单不存在: " + id);
        }
        return note;
    }
}
