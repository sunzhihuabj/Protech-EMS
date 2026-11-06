package com.projlife.eng.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.eng.entity.EngCrew;
import com.projlife.eng.entity.EngDispatch;
import com.projlife.eng.entity.EngRectification;
import com.projlife.eng.entity.EngUnit;
import com.projlife.eng.entity.EngWorker;
import com.projlife.eng.mapper.EngCrewMapper;
import com.projlife.eng.mapper.EngDispatchMapper;
import com.projlife.eng.mapper.EngRectificationMapper;
import com.projlife.eng.mapper.EngUnitMapper;
import com.projlife.eng.mapper.EngWorkerMapper;
import com.projlife.mdm.api.MdmApi;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工程交付核心服务（V4-05 M10）：班组/人员准入、派工（BR-03/04）、
 * 机组状态机 E-29、整改闭环（UC-ENG-08）、售后（BR-ENG-08）。
 */
@Service
public class EngService {

    private final EngDispatchMapper dispatchMapper;
    private final EngWorkerMapper workerMapper;
    private final EngUnitMapper unitMapper;
    private final EngRectificationMapper rectificationMapper;
    private final EngCrewMapper crewMapper;
    private final MdmApi mdmApi;
    private final CodeSequenceService codeSequenceService;

    public EngService(EngDispatchMapper dispatchMapper, EngWorkerMapper workerMapper,
                      EngUnitMapper unitMapper, EngRectificationMapper rectificationMapper,
                      EngCrewMapper crewMapper, MdmApi mdmApi, CodeSequenceService codeSequenceService) {
        this.dispatchMapper = dispatchMapper;
        this.workerMapper = workerMapper;
        this.unitMapper = unitMapper;
        this.rectificationMapper = rectificationMapper;
        this.crewMapper = crewMapper;
        this.mdmApi = mdmApi;
        this.codeSequenceService = codeSequenceService;
    }

    // ---------- 班组（SPRINT-A 派工联动选择器数据源） ----------

    public List<EngCrew> crews() {
        return crewMapper.selectList(new LambdaQueryWrapper<EngCrew>()
                .orderByAsc(EngCrew::getId));
    }

    // ---------- 人员准入（FR-ENG-01/02，UC-ENG-10） ----------

    /** FR-ENG-02：授权前置——保险过期拒绝（UC-ENG-10）。PENDING 可激活，FROZEN/EXPIRED 不可。 */
    public void assertWorkerAuthorizable(Long workerId) {
        EngWorker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            throw BizException.notFound("施工人员不存在");
        }
        if ("FROZEN".equals(worker.getStatus()) || "EXPIRED".equals(worker.getStatus())) {
            throw BizException.badRequest("人员状态 " + worker.getStatus() + "，未授权（FR-ENG-02）");
        }
        if (worker.getInsuranceExpire() != null && worker.getInsuranceExpire().isBefore(LocalDate.now())) {
            throw BizException.badRequest("保险已于 " + worker.getInsuranceExpire() + " 过期，拒绝授权（UC-ENG-10）");
        }
    }

    /** FR-ENG-01/02：建人员档案（身份证 AES 密文占位）+ 激活（UC-ENG-10 保险校验） */
    @Audited(module = "eng", action = "create_worker", targetType = "eng_worker", targetId = "{0.workerCode}")
    @Transactional
    public Long assertWorkerAndCreate(EngWorker worker) {
        if (worker.getName() == null || worker.getCrewId() == null) {
            throw BizException.badRequest("姓名与班组必填");
        }
        worker.setWorkerCode(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "WORKER:GLOBAL", "W-", 4)));
        worker.setStatus("PENDING");
        // 身份证 AES 加密在 S9 安全批次接入，此处暂存原文到密文字段并标记（A-25）
        workerMapper.insert(worker);
        return worker.getId();
    }

    @Audited(module = "eng", action = "activate_worker", targetType = "eng_worker", targetId = "{0}")
    @Transactional
    public void activateWorker(Long workerId) {
        assertWorkerAuthorizable(workerId);
        EngWorker worker = workerMapper.selectById(workerId);
        worker.setStatus("ACTIVE");
        workerMapper.updateById(worker);
    }

    // ---------- 派工（FR-ENG-03，BR-03/04） ----------

    public PageResp<EngDispatch> dispatchPage(PageReq pageReq, String status) {
        LambdaQueryWrapper<EngDispatch> wrapper = new LambdaQueryWrapper<EngDispatch>()
                .orderByDesc(EngDispatch::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EngDispatch::getStatus, status);
        }
        Page<EngDispatch> page = dispatchMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<EngDispatch> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    // ---------- 台账列表（SPRINT-UID-R20：人员/机组/整改此前仅有写入端点无列表） ----------

    /** 人员台账（FR-ENG-01/02 准入状态可视） */
    public PageResp<EngWorker> workers(PageReq pageReq, String status, Long crewId) {
        LambdaQueryWrapper<EngWorker> wrapper = new LambdaQueryWrapper<EngWorker>()
                .orderByDesc(EngWorker::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EngWorker::getStatus, status);
        }
        if (crewId != null) {
            wrapper.eq(EngWorker::getCrewId, crewId);
        }
        Page<EngWorker> page = workerMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<EngWorker> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** 机组台账（FR-ENG-21 一机一档；按项目过滤） */
    public PageResp<EngUnit> units(PageReq pageReq, Long projectId, String status) {
        LambdaQueryWrapper<EngUnit> wrapper = new LambdaQueryWrapper<EngUnit>()
                .orderByDesc(EngUnit::getId);
        if (projectId != null) {
            wrapper.eq(EngUnit::getMdProjectId, projectId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EngUnit::getStatus, status);
        }
        Page<EngUnit> page = unitMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<EngUnit> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** 整改台账（FR-ENG-14 闭环可视；按项目过滤） */
    public PageResp<EngRectification> rectifications(PageReq pageReq, Long projectId, String status) {
        LambdaQueryWrapper<EngRectification> wrapper = new LambdaQueryWrapper<EngRectification>()
                .orderByDesc(EngRectification::getId);
        if (projectId != null) {
            wrapper.eq(EngRectification::getProjectId, projectId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EngRectification::getStatus, status);
        }
        Page<EngRectification> page = rectificationMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<EngRectification> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** FR-ENG-03：派工单（BR-03 必挂项目；BR-04 必选风场） */
    @Audited(module = "eng", action = "create_dispatch", targetType = "eng_dispatch", targetId = "{0.dispatchNo}")
    @Transactional
    public Long createDispatch(EngDispatch dispatch) {
        if (dispatch.getMdProjectId() == null) {
            throw BizException.of(ErrorCode.BLOCKED, "派工单必须关联项目编码")
                    .with("blockCode", "BR-03")
                    .with("suggestion", "请先建档取码");
        }
        if (dispatch.getFarmId() == null) {
            throw BizException.of(ErrorCode.BLOCKED, "派工单必须选择风场")
                    .with("blockCode", "BR-04")
                    .with("suggestion", "请补选风场（UC-ENG-02 弹窗二选一）");
        }
        if (!mdmApi.projectActive(dispatch.getMdProjectId())) {
            throw BizException.badRequest("项目档案不存在或已关闭");
        }
        if (dispatch.getPlanStart() == null || dispatch.getPlanEnd() == null) {
            throw BizException.badRequest("计划起止必填");
        }
        dispatch.setDispatchNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "AS:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "AS-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-", 4)));
        dispatch.setStatus("TO_DISPATCH");
        dispatchMapper.insert(dispatch);
        return dispatch.getId();
    }

    /** E-32：TO_DISPATCH → DISPATCHED（下发） */
    @Audited(module = "eng", action = "dispatch_send", targetType = "eng_dispatch", targetId = "{0}")
    @Transactional
    public void sendDispatch(Long dispatchId) {
        EngDispatch dispatch = mustGetDispatch(dispatchId);
        if (!"TO_DISPATCH".equals(dispatch.getStatus())) {
            throw BizException.badRequest("仅待派状态可下发");
        }
        dispatch.setStatus("DISPATCHED");
        dispatchMapper.updateById(dispatch);
    }

    /** E-32：DISPATCHED → DOING（开工） */
    @Transactional
    public void startDispatch(Long dispatchId) {
        EngDispatch dispatch = mustGetDispatch(dispatchId);
        if (!"DISPATCHED".equals(dispatch.getStatus())) {
            throw BizException.badRequest("仅已派状态可开工");
        }
        dispatch.setStatus("DOING");
        dispatch.setActualStart(LocalDateTime.now());
        dispatchMapper.updateById(dispatch);
    }

    /** E-32：DOING → TO_ACCEPT → DONE */
    @Transactional
    public void acceptDispatch(Long dispatchId) {
        EngDispatch dispatch = mustGetDispatch(dispatchId);
        if (!"DOING".equals(dispatch.getStatus())) {
            throw BizException.badRequest("仅进行中可提请验收");
        }
        dispatch.setStatus("TO_ACCEPT");
        dispatchMapper.updateById(dispatch);
    }

    @Transactional
    public void closeDispatch(Long dispatchId) {
        EngDispatch dispatch = mustGetDispatch(dispatchId);
        if (!"TO_ACCEPT".equals(dispatch.getStatus())) {
            throw BizException.badRequest("仅待验收可关闭");
        }
        dispatch.setStatus("DONE");
        dispatch.setActualEnd(LocalDateTime.now());
        dispatchMapper.updateById(dispatch);
    }

    // ---------- 机组（FR-ENG-21 一机一档，E-29） ----------

    /** FR-ENG-21：机组建档（U-{project_code}-{3位}） */
    @Audited(module = "eng", action = "create_unit", targetType = "eng_unit", targetId = "{0.unitNo}")
    @Transactional
    public Long createUnit(EngUnit unit) {
        if (unit.getMdProjectId() == null) {
            throw BizException.badRequest("机组必挂项目档案");
        }
        if (unit.getProductLines() == null || unit.getProductLines().isEmpty()) {
            throw BizException.badRequest("产品线必填（一机一档追溯依据）");
        }
        unit.setUnitNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "UNIT:" + unit.getMdProjectId(), "U-P" + unit.getMdProjectId() + "-", 3)));
        unit.setStatus("TO_INSTALL");
        unitMapper.insert(unit);
        return unit.getId();
    }

    /** E-29：TO_INSTALL → INSTALLING → TO_DEBUG → ACCEPTED（安装/调试/验收推进） */
    @Audited(module = "eng", action = "advance_unit", targetType = "eng_unit", targetId = "{0}")
    @Transactional
    public void advanceUnit(Long unitId, String targetStatus) {
        EngUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw BizException.notFound("机组不存在");
        }
        // E-29 合法迁移表
        boolean legal;
        if ("INSTALLING".equals(targetStatus)) {
            legal = "TO_INSTALL".equals(unit.getStatus());
        } else if ("TO_DEBUG".equals(targetStatus)) {
            legal = "INSTALLING".equals(unit.getStatus());
        } else if ("ACCEPTED".equals(targetStatus)) {
            legal = "TO_DEBUG".equals(unit.getStatus()) || "DEBUGGING".equals(unit.getStatus());
        } else {
            legal = false;
        }
        if (!legal) {
            throw BizException.badRequest("机组状态 " + unit.getStatus() + " 不可迁移到 " + targetStatus + "（E-29）");
        }
        unit.setStatus(targetStatus);
        if ("INSTALLING".equals(targetStatus)) {
            unit.setInstallDate(LocalDate.now());
        }
        if ("TO_DEBUG".equals(targetStatus)) {
            unit.setDebugDate(LocalDate.now());
        }
        if ("ACCEPTED".equals(targetStatus)) {
            unit.setAcceptDate(LocalDate.now());
            unit.setWarrantyStart(LocalDate.now());
            unit.setWarrantyEnd(LocalDate.now().plusMonths(24));
        }
        unitMapper.updateById(unit);
    }

    // ---------- 整改闭环（FR-ENG-14，UC-ENG-08） ----------

    /** 三来源整改（AI/MANUAL/SUPERVISOR） */
    @Audited(module = "eng", action = "create_rectification", targetType = "eng_rectification", targetId = "{0.rectNo}")
    @Transactional
    public Long createRectification(EngRectification rect) {
        if (rect.getProjectId() == null || rect.getProblemDesc() == null) {
            throw BizException.badRequest("项目与问题描述必填（FR-ENG-14）");
        }
        rect.setRectNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "RECT:" + LocalDate.now().getYear(), "RECT-", 4)));
        rect.setStatus("PENDING");
        rectificationMapper.insert(rect);
        return rect.getId();
    }

    /** 整改状态机：PENDING → DOING → TO_REVIEW → PASSED → CLOSED（不通过回 DOING） */
    @Transactional
    public void advanceRectification(Long rectId, String targetStatus, String rejectReason) {
        EngRectification rect = rectificationMapper.selectById(rectId);
        if (rect == null) {
            throw BizException.notFound("整改单不存在");
        }
        boolean legal;
        if ("DOING".equals(targetStatus)) {
            legal = "PENDING".equals(rect.getStatus())
                    || ("TO_REVIEW".equals(rect.getStatus()) && rejectReason != null);
        } else if ("TO_REVIEW".equals(targetStatus)) {
            legal = "DOING".equals(rect.getStatus());
        } else if ("PASSED".equals(targetStatus)) {
            legal = "TO_REVIEW".equals(rect.getStatus());
        } else if ("CLOSED".equals(targetStatus)) {
            legal = "PASSED".equals(rect.getStatus());
        } else {
            legal = false;
        }
        if (!legal) {
            throw BizException.badRequest("整改状态 " + rect.getStatus() + " 不可迁移到 " + targetStatus);
        }
        rect.setStatus(targetStatus);
        if ("CLOSED".equals(targetStatus)) {
            rect.setClosedAt(LocalDateTime.now());
        }
        if ("DOING".equals(targetStatus) && rejectReason != null) {
            rect.setRequirement("复核不通过回退：" + rejectReason);
        }
        rectificationMapper.updateById(rect);
    }

    /** FR-ENG-08：整体验收前置——无未闭环整改 */
    public void assertNoOpenRectification(Long projectId) {
        long open = rectificationMapper.selectCount(new LambdaQueryWrapper<EngRectification>()
                .eq(EngRectification::getProjectId, projectId)
                .in(EngRectification::getStatus, "PENDING", "DOING", "TO_REVIEW"));
        if (open > 0) {
            throw BizException.badRequest("存在 " + open + " 个未闭环整改单，不可提交整体验收（FR-ENG-08）");
        }
    }

    private EngDispatch mustGetDispatch(Long id) {
        EngDispatch dispatch = dispatchMapper.selectById(id);
        if (dispatch == null) {
            throw BizException.notFound("派工单不存在");
        }
        return dispatch;
    }
}
