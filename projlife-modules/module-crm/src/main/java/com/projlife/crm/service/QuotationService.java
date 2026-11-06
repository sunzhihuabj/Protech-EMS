package com.projlife.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.crm.entity.CrmOpportunity;
import com.projlife.crm.entity.CrmQuotation;
import com.projlife.crm.entity.CrmQuotationLine;
import com.projlife.crm.mapper.CrmOpportunityMapper;
import com.projlife.crm.mapper.CrmQuotationLineMapper;
import com.projlife.crm.mapper.CrmQuotationMapper;
import com.projlife.mdm.api.MdmApi;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报价单（V4-03 M01：FR-CRM-17/18/19/20，D-04）。
 * R2 编号 QT-{YYYYMM}-{4位}；R3 一商机多版报价（旧版只读）；R4 状态 DRAFT→SUBMITTED→WON/LOST/EXPIRED。
 * 金额 = 数量×单价×(1+税率)，服务端计算。转合同（FR-CRM-19）中标后一键生成合同草稿并回写 converted_contract_id。
 */
@Service
public class QuotationService {

    private final CrmQuotationMapper quotationMapper;
    private final CrmQuotationLineMapper lineMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CodeSequenceService codeSequenceService;
    private final MdmApi mdmApi;
    private final com.projlife.crm.service.contractbridge.ContractDraftBridge contractDraftBridge;
    private final com.projlife.security.auth.DataScopeGuard dataScopeGuard;

    public QuotationService(CrmQuotationMapper quotationMapper, CrmQuotationLineMapper lineMapper,
                            CrmOpportunityMapper opportunityMapper, CodeSequenceService codeSequenceService,
                            MdmApi mdmApi,
                            com.projlife.crm.service.contractbridge.ContractDraftBridge contractDraftBridge,
                            com.projlife.security.auth.DataScopeGuard dataScopeGuard) {
        this.quotationMapper = quotationMapper;
        this.lineMapper = lineMapper;
        this.opportunityMapper = opportunityMapper;
        this.codeSequenceService = codeSequenceService;
        this.mdmApi = mdmApi;
        this.contractDraftBridge = contractDraftBridge;
        this.dataScopeGuard = dataScopeGuard;
    }

    public PageResp<CrmQuotation> page(PageReq pageReq, Long opportunityId, String status) {
        LambdaQueryWrapper<CrmQuotation> wrapper = new LambdaQueryWrapper<CrmQuotation>()
                .orderByDesc(CrmQuotation::getId);
        if (opportunityId != null) {
            wrapper.eq(CrmQuotation::getOpportunityId, opportunityId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(CrmQuotation::getStatus, status);
        }
        Page<CrmQuotation> page = quotationMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<CrmQuotation> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** SPRINT-C：按报价 ID 直查行表此前绕过数据范围，补 owner 口径校验 */
    public List<CrmQuotationLine> linesOf(Long quotationId) {
        dataScopeGuard.checkByOwner(mustGet(quotationId).getCreatedBy());
        return lineMapper.selectList(new LambdaQueryWrapper<CrmQuotationLine>()
                .eq(CrmQuotationLine::getQuotationId, quotationId));
    }

    /** FR-CRM-17 R1/R2/R3：建档（必填校验 + 版本递增 + 金额服务端算） */
    @Audited(module = "crm", action = "create_quotation", targetType = "crm_quotation", targetId = "{0.quotationNo}")
    @Transactional
    public Long create(CrmQuotation quotation, List<CrmQuotationLine> lines) {
        if (quotation.getOpportunityId() == null || quotation.getCustomerId() == null
                || quotation.getProductLine() == null || quotation.getQty() == null
                || quotation.getUnitPrice() == null) {
            throw BizException.badRequest("客户/商机/产品线/数量/单价必填（FR-CRM-17 R1）");
        }
        if (!mdmApi.customerActive(quotation.getCustomerId())) {
            throw BizException.badRequest("客户主体不存在或已停用");
        }
        CrmOpportunity opp = opportunityMapper.selectById(quotation.getOpportunityId());
        if (opp == null) {
            throw BizException.notFound("商机不存在");
        }
        // R3：一商机多版报价，版本号自动递增（V1/V2…）
        long nextVersion = quotationMapper.selectCount(new LambdaQueryWrapper<CrmQuotation>()
                .eq(CrmQuotation::getOpportunityId, quotation.getOpportunityId())) + 1;
        quotation.setVersion("V" + nextVersion);
        quotation.setQuotationNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "QT:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                "QT-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-", 4)));
        quotation.setTaxRate(quotation.getTaxRate() == null ? new BigDecimal("0.13") : quotation.getTaxRate());
        // 金额 = 数量×单价×(1+税率)，服务端计算（M01 §3）
        BigDecimal amount = quotation.getQty().multiply(quotation.getUnitPrice())
                .multiply(BigDecimal.ONE.add(quotation.getTaxRate()))
                .setScale(2, RoundingMode.HALF_UP);
        quotation.setAmount(amount);
        quotation.setStatus("DRAFT");
        quotationMapper.insert(quotation);
        // 行表：行金额 = qty×price
        BigDecimal lineTotal = BigDecimal.ZERO;
        if (lines != null) {
            for (CrmQuotationLine line : lines) {
                if (line.getMaterialId() == null || line.getQty() == null || line.getPrice() == null) {
                    throw BizException.badRequest("报价行物料/数量/单价必填");
                }
                line.setQuotationId(quotation.getId());
                line.setAmount(line.getQty().multiply(line.getPrice()).setScale(2, RoundingMode.HALF_UP));
                lineTotal = lineTotal.add(line.getAmount());
                lineMapper.insert(line);
            }
        }
        // 行合计一致性校验（有行时）：行金额为不含税口径，×(1+税率) 后须等于主表金额
        if (lines != null && !lines.isEmpty()) {
            BigDecimal taxedLineTotal = lineTotal.multiply(BigDecimal.ONE.add(quotation.getTaxRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            if (taxedLineTotal.compareTo(amount) != 0) {
                throw BizException.badRequest("报价行合计（含税）" + taxedLineTotal + " 与主表金额 " + amount + " 不一致");
            }
        }
        // 商机状态机：TRACKING → QUOTING（有报价，M01 §4）
        if ("TRACKING".equals(opp.getStatus())) {
            opp.setStatus("QUOTING");
            opportunityMapper.updateById(opp);
        }
        return quotation.getId();
    }

    /** FR-CRM-17 R4：提交（DRAFT→SUBMITTED） */
    @Audited(module = "crm", action = "submit_quotation", targetType = "crm_quotation", targetId = "{0}")
    @Transactional
    public void submit(Long quotationId) {
        CrmQuotation quotation = mustGet(quotationId);
        if (!"DRAFT".equals(quotation.getStatus())) {
            throw BizException.badRequest("仅草稿可提交（R4 状态机）");
        }
        quotation.setStatus("SUBMITTED");
        quotationMapper.updateById(quotation);
    }

    /** FR-CRM-18：价格基准提示（成本告警不阻断，红字 + R05 确认口径）（SPRINT-C：补数据范围校验） */
    public Map<String, Object> priceBasis(Long quotationId) {
        CrmQuotation quotation = mustGet(quotationId);
        dataScopeGuard.checkByOwner(quotation.getCreatedBy());
        Map<String, Object> basis = new HashMap<>();
        basis.put("amount", quotation.getAmount());
        basis.put("unitPrice", quotation.getUnitPrice());
        basis.put("warning", "报价低于成本下限 → 红字告警 + 需 R05 确认，不阻断（M01 §7）");
        basis.put("priceBasis", quotation.getPriceBasisJson());
        return basis;
    }

    /** FR-CRM-19：中标 → 一键生成合同草稿 + 回写 converted_contract_id；未中标须填原因 */
    @Audited(module = "crm", action = "decide_quotation", targetType = "crm_quotation", targetId = "{0}")
    @Transactional
    public Map<String, Object> decide(Long quotationId, boolean won, String lostReason) {
        CrmQuotation quotation = mustGet(quotationId);
        if (!"SUBMITTED".equals(quotation.getStatus())) {
            throw BizException.badRequest("仅已提交报价可定标（R4）");
        }
        Map<String, Object> result = new HashMap<>();
        if (won) {
            quotation.setStatus("WON");
            // FR-CRM-19 R1：一键生成合同草稿（客户/金额/产品线/付款条件带出）
            Long contractId = contractDraftBridge.createFromQuotation(quotation);
            quotation.setConvertedContractId(contractId);
            result.put("contractDraftId", contractId);
            // 商机 WON
            CrmOpportunity opp = opportunityMapper.selectById(quotation.getOpportunityId());
            if (opp != null && !"WON".equals(opp.getStatus())) {
                opp.setStatus("WON");
                opportunityMapper.updateById(opp);
            }
        } else {
            if (lostReason == null || lostReason.length() < 4) {
                throw BizException.badRequest("未中标必须填原因（回流报价库，FR-CRM-19 R3）");
            }
            quotation.setStatus("LOST");
            result.put("lostReason", lostReason);
        }
        quotationMapper.updateById(quotation);
        return result;
    }

    /** FR-CRM-20 R3：转化率按来源分组统计 */
    public Map<String, Object> conversionBySource() {
        Map<String, Object> result = new HashMap<>();
        List<CrmOpportunity> all = opportunityMapper.selectList(new LambdaQueryWrapper<CrmOpportunity>());
        Map<String, int[]> bySource = new HashMap<>();
        for (CrmOpportunity opp : all) {
            String key = opp.getSourceType() == null ? "MANUAL" : opp.getSourceType();
            int[] stat = bySource.computeIfAbsent(key, k -> new int[2]);
            stat[0]++;
            if ("WON".equals(opp.getStatus())) {
                stat[1]++;
            }
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Map.Entry<String, int[]> entry : bySource.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("sourceType", entry.getKey());
            row.put("total", entry.getValue()[0]);
            row.put("won", entry.getValue()[1]);
            row.put("conversionPct", entry.getValue()[0] == 0 ? 0
                    : Math.round(entry.getValue()[1] * 10000.0 / entry.getValue()[0]) / 100.0);
            rows.add(row);
        }
        result.put("bySource", rows);
        return result;
    }

    private CrmQuotation mustGet(Long id) {
        CrmQuotation quotation = quotationMapper.selectById(id);
        if (quotation == null) {
            throw BizException.notFound("报价单不存在");
        }
        return quotation;
    }
}
