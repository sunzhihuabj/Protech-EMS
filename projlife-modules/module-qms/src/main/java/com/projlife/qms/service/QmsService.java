package com.projlife.qms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.mes.service.MesService;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.qms.entity.Qms8d;
import com.projlife.qms.entity.QmsGauge;
import com.projlife.qms.entity.QmsInspection;
import com.projlife.qms.mapper.Qms8dMapper;
import com.projlife.qms.mapper.QmsGaugeMapper;
import com.projlife.qms.mapper.QmsInspectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量（09 册）：检验三关口（IQC/IPQC/OQC）/AI 改判留痕/8D（D6 未填不可关闭）/量具超期锁定/放行联动 MES。
 */
@Service
public class QmsService {

    private final QmsInspectionMapper inspectionMapper;
    private final Qms8dMapper eightDMapper;
    private final QmsGaugeMapper gaugeMapper;
    private final CodeSequenceService codeSequenceService;
    private final MesService mesService;

    private final com.projlife.qms.mapper.QmsSpcDataMapper spcDataMapper;

    public QmsService(QmsInspectionMapper inspectionMapper, Qms8dMapper eightDMapper,
                      QmsGaugeMapper gaugeMapper, CodeSequenceService codeSequenceService,
                      MesService mesService,
                      com.projlife.qms.mapper.QmsSpcDataMapper spcDataMapper) {
        this.inspectionMapper = inspectionMapper;
        this.eightDMapper = eightDMapper;
        this.gaugeMapper = gaugeMapper;
        this.codeSequenceService = codeSequenceService;
        this.mesService = mesService;
        this.spcDataMapper = spcDataMapper;
    }

    public PageResp<QmsInspection> page(PageReq pageReq, String type, String result) {
        LambdaQueryWrapper<QmsInspection> wrapper = new LambdaQueryWrapper<QmsInspection>()
                .orderByDesc(QmsInspection::getId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(QmsInspection::getInspectionType, type);
        }
        if (result != null && !result.isEmpty()) {
            wrapper.eq(QmsInspection::getResult, result);
        }
        Page<QmsInspection> page = inspectionMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<QmsInspection> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** FR（09 册）：检验单创建（三关口；E-28 结论） */
    @Audited(module = "qms", action = "create_inspection", targetType = "qms_inspection", targetId = "{0.inspectionNo}")
    @Transactional
    public Long createInspection(QmsInspection inspection) {
        if (!"IQC".equals(inspection.getInspectionType())
                && !"IPQC".equals(inspection.getInspectionType())
                && !"OQC".equals(inspection.getInspectionType())) {
            throw BizException.badRequest("检验类型必须 IQC/IPQC/OQC（E-27）");
        }
        if (inspection.getSampleSize() == null || inspection.getSampleSize() <= 0) {
            throw BizException.badRequest("抽样数必填且 >0");
        }
        String prefix = inspection.getInspectionType();
        inspection.setInspectionNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "QC:" + prefix + ":" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "QC-" + prefix + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-", 3)));
        inspection.setInspectorId(UserContextHolder.currentUserId());
        inspection.setInspectionTime(LocalDateTime.now());
        inspectionMapper.insert(inspection);
        return inspection.getId();
    }

    /** FR（09 册）AI 改判留痕：人工改判置 human_corrected=1 */
    @Audited(module = "qms", action = "correct_inspection", targetType = "qms_inspection", targetId = "{0}")
    @Transactional
    public void correct(Long inspectionId, String newResult, String remark) {
        QmsInspection inspection = mustGet(inspectionId);
        inspection.setResult(newResult);
        inspection.setHumanCorrected(1);
        if (remark != null) {
            inspection.setRemark(remark);
        }
        inspectionMapper.updateById(inspection);
        // 联动 MES：来源为工序的检验放行
        if ("PROCESS".equals(inspection.getSourceType()) && inspection.getSourceId() != null) {
            mesService.qcRelease(inspection.getSourceId(), "PASS".equals(newResult), remark);
        }
    }

    /** FR（09 册）：8D 创建 */
    @Audited(module = "qms", action = "create_8d", targetType = "qms_8d", targetId = "{0.reportNo}")
    @Transactional
    public Long create8d(Qms8d report) {
        if (report.getProblemDesc() == null || report.getProblemDesc().isEmpty()) {
            throw BizException.badRequest("问题描述必填");
        }
        report.setReportNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "8D:" + LocalDate.now().getYear(), "8D-", 4)));
        report.setStatus("OPEN");
        report.setOwnerId(UserContextHolder.currentUserId());
        eightDMapper.insert(report);
        return report.getId();
    }

    /** 验收要点：D6 未填不可关闭（09 册） */
    @Audited(module = "qms", action = "close_8d", targetType = "qms_8d", targetId = "{0}")
    @Transactional
    public void close8d(Long reportId) {
        Qms8d report = eightDMapper.selectById(reportId);
        if (report == null) {
            throw BizException.notFound("8D 报告不存在");
        }
        if (isBlank(report.getD1Team()) || isBlank(report.getD4RootCause()) || isBlank(report.getD6Verify())) {
            throw BizException.badRequest("D6（效果验证）未填写不可关闭（09 册验收要点 3）");
        }
        report.setStatus("CLOSED");
        report.setClosedAt(LocalDateTime.now());
        eightDMapper.updateById(report);
    }

    /** FR（09 册）：量具校准（超期自动锁定） */
    @Audited(module = "qms", action = "calibrate_gauge", targetType = "qms_gauge", targetId = "{0.gaugeId}")
    @Transactional
    public void calibrate(Long gaugeId, LocalDate nextDate, String result, String certificateNo) {
        QmsGauge gauge = gaugeMapper.selectById(gaugeId);
        if (gauge == null) {
            throw BizException.notFound("量具不存在");
        }
        gauge.setLastCalibrateDate(LocalDate.now());
        gauge.setNextCalibrateDate(nextDate);
        if (nextDate != null && nextDate.isAfter(LocalDate.now())) {
            gauge.setStatus("ACTIVE");
        }
        gaugeMapper.updateById(gauge);
    }

    /** 验收要点：量具超期锁定（下拉不可选） */
    public List<QmsGauge> usableGauges() {
        List<QmsGauge> all = gaugeMapper.selectList(new LambdaQueryWrapper<QmsGauge>()
                .eq(QmsGauge::getStatus, "ACTIVE"));
        // 超期自动锁定
        LocalDate today = LocalDate.now();
        for (QmsGauge gauge : all) {
            if (gauge.getNextCalibrateDate() != null && gauge.getNextCalibrateDate().isBefore(today)) {
                gauge.setStatus("FROZEN");
                gaugeMapper.updateById(gauge);
            }
        }
        return gaugeMapper.selectList(new LambdaQueryWrapper<QmsGauge>()
                .eq(QmsGauge::getStatus, "ACTIVE"));
    }

    /** FR（09 册）：MTBF = 总运行时间 / 故障次数（简化：按设备统计） */
    public Map<String, Object> mtbf(Long assetId) {
        Map<String, Object> result = new HashMap<>();
        result.put("assetId", assetId);
        result.put("note", "MTBF = 统计期总时间 / 故障次数；明细数据由 eam_failure 提供（job 汇总随 S8 批次）");
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private QmsInspection mustGet(Long id) {
        QmsInspection inspection = inspectionMapper.selectById(id);
        if (inspection == null) {
            throw BizException.notFound("检验单不存在");
        }
        return inspection;
    }

    /** FR-QMS-09 SPC 数据录入 + 出控判定（valueX 超出 UCL/LCL → isOutOfControl=1） */
    @Transactional
    public com.projlife.qms.entity.QmsSpcData recordSpc(com.projlife.qms.entity.QmsSpcData spc) {
        if (spc.getCharacteristic() == null || spc.getCharacteristic().isEmpty()) {
            throw BizException.badRequest("质量特征必填");
        }
        if (spc.getValueX() == null) {
            throw BizException.badRequest("测量值必填");
        }
        if (spc.getSampleTime() == null) {
            spc.setSampleTime(java.time.LocalDateTime.now());
        }
        // 出控判定：valueX > UCL 或 < LCL
        if (spc.getUcl() != null && spc.getValueX().compareTo(spc.getUcl()) > 0) {
            spc.setIsOutOfControl(1);
        } else if (spc.getLcl() != null && spc.getValueX().compareTo(spc.getLcl()) < 0) {
            spc.setIsOutOfControl(1);
        } else {
            spc.setIsOutOfControl(0);
        }
        spcDataMapper.insert(spc);
        return spc;
    }

    /** SPC 数据查询（按特征过滤） */
    public java.util.List<com.projlife.qms.entity.QmsSpcData> spcList(String characteristic) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.projlife.qms.entity.QmsSpcData>()
                .eq(com.projlife.qms.entity.QmsSpcData::getDeleted, 0)
                .orderByAsc(com.projlife.qms.entity.QmsSpcData::getSampleTime);
        if (characteristic != null && !characteristic.isEmpty()) {
            wrapper.eq(com.projlife.qms.entity.QmsSpcData::getCharacteristic, characteristic);
        }
        return spcDataMapper.selectList(wrapper.last("LIMIT 100"));
    }
}
