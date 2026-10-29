package com.projlife.plm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.plm.entity.BomHeader;
import com.projlife.plm.entity.PcsProcessDoc;
import com.projlife.plm.entity.PcsRoute;
import com.projlife.plm.entity.PcsRouteStep;
import com.projlife.plm.mapper.BomHeaderMapper;
import com.projlife.plm.mapper.PcsProcessDocMapper;
import com.projlife.plm.mapper.PcsRouteMapper;
import com.projlife.plm.mapper.PcsRouteStepMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工艺（06 册 §7：FR-PCS-01~13）。BR-PCS-01 未编号不得发布；BR-PCS-02 正式发布前对应 BOM 已发布。
 */
@Service
public class PcsService {

    private final PcsProcessDocMapper docMapper;
    private final PcsRouteMapper routeMapper;
    private final PcsRouteStepMapper stepMapper;
    private final BomHeaderMapper bomHeaderMapper;
    private final CodeSequenceService codeSequenceService;

    public PcsService(PcsProcessDocMapper docMapper, PcsRouteMapper routeMapper, PcsRouteStepMapper stepMapper,
                      BomHeaderMapper bomHeaderMapper, CodeSequenceService codeSequenceService) {
        this.docMapper = docMapper;
        this.routeMapper = routeMapper;
        this.stepMapper = stepMapper;
        this.bomHeaderMapper = bomHeaderMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public PageResp<PcsProcessDoc> docs(PageReq pageReq, String productLine, String status) {
        LambdaQueryWrapper<PcsProcessDoc> wrapper = new LambdaQueryWrapper<PcsProcessDoc>()
                .orderByDesc(PcsProcessDoc::getId);
        if (productLine != null && !productLine.isEmpty()) {
            wrapper.eq(PcsProcessDoc::getProductLine, productLine);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PcsProcessDoc::getStatus, status);
        }
        Page<PcsProcessDoc> page = docMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<PcsProcessDoc> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** FR-PCS-03：工艺文件编号 TLD-GY{类}-{3位}（A-18：非 18 类编码，走序列服务） */
    @Audited(module = "pcs", action = "create_process_doc", targetType = "pcs_process_doc", targetId = "{0.docName}")
    @Transactional
    public Long createDoc(PcsProcessDoc doc) {
        if (doc.getProductLine() == null || doc.getDocName() == null) {
            throw BizException.badRequest("产品线与文件名称必填");
        }
        String category = docCategory(doc.getDocType());
        doc.setDocNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "GY:" + category + ":" + java.time.LocalDate.now().getYear(),
                "TLD-GY" + category + "-", 3)));
        doc.setStatus("DRAFT");
        if (doc.getIsTemp() == null) {
            doc.setIsTemp(0);
        }
        docMapper.insert(doc);
        return doc.getId();
    }

    private String docCategory(String docType) {
        // 类别段：作业指导书 101 / 工艺流程 102 / PFMEA 103 / 检验规范 104 / 其他 199
        if (docType == null) {
            return "199";
        }
        switch (docType) {
            case "作业指导书": return "101";
            case "工艺流程": return "102";
            case "PFMEA": return "103";
            case "检验规范": return "104";
            default: return "199";
        }
    }

    /** FR-PCS-05 + BR-PCS-01/02：发布——编号必须 + 对应 BOM 已发布 */
    @Audited(module = "pcs", action = "publish_process_doc", targetType = "pcs_process_doc", targetId = "{0}")
    @Transactional
    public void publish(Long docId) {
        PcsProcessDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw BizException.notFound("工艺文件不存在");
        }
        if (doc.getDocNo() == null || doc.getDocNo().isEmpty()) {
            throw BizException.badRequest("工艺文件未编号不得发布（BR-PCS-01）");
        }
        if (doc.getBomHeaderId() != null) {
            BomHeader bom = bomHeaderMapper.selectById(doc.getBomHeaderId());
            if (bom == null || !"RELEASED".equals(bom.getStatus())) {
                throw BizException.badRequest("正式发布前必须有对应 BOM 已发布（BR-PCS-02）");
            }
        }
        doc.setStatus("PUBLISHED");
        doc.setPublishedAt(java.time.LocalDateTime.now());
        doc.setApprovedBy(UserContextHolder.currentUserId());
        docMapper.updateById(doc);
    }

    /** FR-PCS-13：暂停发布 */
    @Transactional
    public void pause(Long docId) {
        PcsProcessDoc doc = docMapper.selectById(docId);
        if (doc == null || !"PUBLISHED".equals(doc.getStatus())) {
            throw BizException.badRequest("仅已发布文件可暂停");
        }
        doc.setStatus("PAUSED");
        docMapper.updateById(doc);
    }

    /** FR-PCS-08/09：工艺路线 + 工序步骤 */
    @Audited(module = "pcs", action = "create_route", targetType = "pcs_route", targetId = "{0.routeName}")
    @Transactional
    public Long createRoute(PcsRoute route, List<PcsRouteStep> steps) {
        if (route.getBomHeaderId() == null || route.getRouteName() == null) {
            throw BizException.badRequest("适用 BOM 与路线名称必填");
        }
        route.setRouteNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "ROUTE:" + java.time.LocalDate.now().getYear(), "RT-", 4)));
        route.setStatus("ACTIVE");
        routeMapper.insert(route);
        int seq = 0;
        for (PcsRouteStep step : steps) {
            step.setRouteId(route.getId());
            step.setStepSeq(++seq);
            stepMapper.insert(step);
        }
        return route.getId();
    }

    public List<PcsRouteStep> stepsOf(Long routeId) {
        return stepMapper.selectList(new LambdaQueryWrapper<PcsRouteStep>()
                .eq(PcsRouteStep::getRouteId, routeId)
                .orderByAsc(PcsRouteStep::getStepSeq));
    }

    /** FR-PCS-11：覆盖度矩阵（产品线 × BOM 数/工艺文件数/覆盖率/判定） */
    public List<Map<String, Object>> coverageMatrix() {
        List<String> productLines = List.of("LADDER", "CLIMBER", "CLIMB", "RAIL", "CCTV", "FIRE", "COVER", "OTHER");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : productLines) {
            Long bomCount = bomHeaderMapper.selectCount(new LambdaQueryWrapper<BomHeader>()
                    .eq(BomHeader::getProductLine, line)
                    .eq(BomHeader::getStatus, "RELEASED"));
            Long docCount = docMapper.selectCount(new LambdaQueryWrapper<PcsProcessDoc>()
                    .eq(PcsProcessDoc::getProductLine, line)
                    .eq(PcsProcessDoc::getStatus, "PUBLISHED"));
            double bom = bomCount == null ? 0 : bomCount;
            double doc = docCount == null ? 0 : docCount;
            double coverage = bom == 0 ? 0 : doc / bom;
            String verdict;
            if (coverage == 0) {
                verdict = "零覆盖";
            } else if (coverage < 0.05) {
                verdict = "严重不足";
            } else if (coverage < 0.15) {
                verdict = "偏低";
            } else {
                verdict = "尚可";
            }
            Map<String, Object> row = new HashMap<>();
            row.put("productLine", line);
            row.put("bomCount", bomCount);
            row.put("docCount", docCount);
            row.put("coveragePct", Math.round(coverage * 10000) / 100.0);
            row.put("verdict", verdict);
            rows.add(row);
        }
        return rows;
    }
}
