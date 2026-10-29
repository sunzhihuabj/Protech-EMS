package com.projlife.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.mdm.api.MdmApi;
import com.projlife.mdm.entity.MdProject;
import com.projlife.mdm.entity.MdProjectAlias;
import com.projlife.mdm.entity.MdProjectProduct;
import com.projlife.mdm.mapper.MdProjectAliasMapper;
import com.projlife.mdm.mapper.MdProjectMapper;
import com.projlife.mdm.mapper.MdProjectProductMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目档案（核心锚点，02 册 §4.3 + 12 册 §2.3）。
 * FR-MDM-12 三重查重（提交强制）；FR-MDM-15 关键 12 项校验；FR-MDM-16 一风场一档案；
 * FR-MDM-21 farm_pending 待关联；BR-MDM-01 R18 唯一写入入口（权限在 Controller 层强制）。
 */
@Service
public class MdProjectService implements MdmApi {

    private final MdProjectMapper projectMapper;
    private final MdProjectAliasMapper aliasMapper;
    private final MdProjectProductMapper productMapper;
    private final com.projlife.mdm.mapper.MdCustomerMapper customerMapper;
    private final CodeSequenceService codeSequenceService;
    private final NameRuleService nameRuleService;

    public MdProjectService(MdProjectMapper projectMapper, MdProjectAliasMapper aliasMapper,
                            MdProjectProductMapper productMapper,
                            com.projlife.mdm.mapper.MdCustomerMapper customerMapper,
                            CodeSequenceService codeSequenceService, NameRuleService nameRuleService) {
        this.projectMapper = projectMapper;
        this.aliasMapper = aliasMapper;
        this.productMapper = productMapper;
        this.customerMapper = customerMapper;
        this.codeSequenceService = codeSequenceService;
        this.nameRuleService = nameRuleService;
    }

    public PageResp<MdProject> page(PageReq pageReq, String keyword, String status, Long farmId) {
        LambdaQueryWrapper<MdProject> wrapper = new LambdaQueryWrapper<MdProject>()
                .orderByDesc(MdProject::getId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(MdProject::getStdName, keyword)
                    .or().like(MdProject::getShortName, keyword)
                    .or().like(MdProject::getProjectCode, keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MdProject::getStatus, status);
        }
        if (farmId != null) {
            wrapper.eq(MdProject::getFarmId, farmId);
        }
        Page<MdProject> page = projectMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<MdProject> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public MdProject detail(Long id) {
        return mustGet(id);
    }

    /** FR-MDM-15：关键 12 项校验，返回缺失项清单（前端据此置灰提交并高亮） */
    public List<String> validateRequired(MdProject p) {
        List<String> missing = new ArrayList<>();
        if (p.getCustomerId() == null) missing.add("签约主体");
        if (p.getLegalEntityId() == null) missing.add("我方法人");
        if (p.getOrderType() == null || p.getOrderType().isEmpty()) missing.add("订单类型");
        if (p.getFarmId() == null && (p.getFarmPending() == null || p.getFarmPending() != 1)) missing.add("风场（或标记 farm_pending）");
        if (p.getStdName() == null || p.getStdName().isEmpty()) missing.add("标准名");
        if (p.getShortName() == null || p.getShortName().isEmpty()) missing.add("常用简称");
        if (p.getProductLines() == null || p.getProductLines().isEmpty()) missing.add("产品线");
        if (p.getSetQty() == null || p.getSetQty().signum() <= 0) missing.add("台数/套数");
        if (p.getEngOwnerId() == null) missing.add("工程责任人");
        if (p.getBizOwnerId() == null) missing.add("商务责任人");
        if (p.getOwnerId() == null && (p.getOwnerPending() == null || p.getOwnerPending() != 1)) missing.add("业主（或标记 owner_pending）");
        if (p.getWarrantyStart() != null && p.getWarrantyEnd() != null
                && !p.getWarrantyEnd().isAfter(p.getWarrantyStart())) missing.add("质保止须晚于质保起");
        return missing;
    }

    /** 建档：三重查重强制 + 12 项校验 + FR-MDM-16 一风场一档案 + PJ 编码 */
    @Audited(module = "mdm", action = "create_project", targetType = "md_project", targetId = "{0.stdName}")
    @Transactional
    public Long create(MdProject p, List<String> productLineList, String forceNewReason) {
        List<String> missing = validateRequired(p);
        if (!missing.isEmpty()) {
            throw BizException.badRequest("关键 " + missing.size() + " 项未填全（FR-MDM-15）：" + String.join("、", missing));
        }
        // 三重查重：未命中放行；命中必须给"确认新建"理由（≥10 字）
        List<Map<String, Object>> hits = nameRuleService.tripleCheck(
                p.getFarmNameRaw(), p.getShortName(), p.getOwnerId());
        if (!hits.isEmpty() && (forceNewReason == null || forceNewReason.length() < 10)) {
            throw BizException.of(com.projlife.common.exception.ErrorCode.BLOCKED,
                            "三重查重命中 " + hits.size() + " 条疑似重复，请关联已有项目或填写新建理由")
                    .with("blockCode", "MDM-DUP-CHECK").with("candidates", hits)
                    .with("suggestion", "选择对比卡中的已有项目，或填写不少于 10 字的新建理由");
        }
        // FR-MDM-16：同 farm+phase 同产品线仅一条 ACTIVE 项目
        if (p.getFarmId() != null) {
            List<MdProject> sameFarm = projectMapper.selectList(new LambdaQueryWrapper<MdProject>()
                    .eq(MdProject::getFarmId, p.getFarmId())
                    .ne(MdProject::getStatus, "CLOSED"));
            for (MdProject other : sameFarm) {
                boolean samePhase = java.util.Objects.equals(
                        other.getPhase() == null ? "" : other.getPhase(),
                        p.getPhase() == null ? "" : p.getPhase());
                if (samePhase && overlaps(other.getProductLines(), p.getProductLines())) {
                    throw BizException.badRequest("同风场同期次下产品线已有 ACTIVE 项目，请在该项目下新增产品子项（FR-MDM-16）");
                }
            }
        }
        // FR-MDM-21：OEM 初装无风场 → farm_pending 必须=1
        if ("OEM_INSTALL".equals(p.getOrderType()) && p.getFarmId() == null) {
            p.setFarmPending(1);
        }
        p.setProjectCode(codeSequenceService.next(CodeTypes.projectArchive(
                String.valueOf(LocalDate.now().getYear()))));
        if (p.getStatus() == null) {
            p.setStatus("SIGNED_PENDING");
        }
        projectMapper.insert(p);
        // 产品子项
        if (productLineList != null) {
            for (String line : productLineList) {
                MdProjectProduct product = new MdProjectProduct();
                product.setMdProjectId(p.getId());
                product.setProductLine(line);
                product.setSetQty(p.getSetQty());
                productMapper.insert(product);
            }
        }
        return p.getId();
    }

    /** 产品子项增删（FR-MDM-17，子项不单独编码） */
    @Audited(module = "mdm", action = "add_project_product", targetType = "md_project_product", targetId = "{0.mdProjectId}")
    @Transactional
    public Long addProduct(MdProjectProduct product) {
        productMapper.insert(product);
        return product.getId();
    }

    /** 项目别名登记（FR-MDM-23：只增不删） */
    @Audited(module = "mdm", action = "add_project_alias", targetType = "md_project", targetId = "{0.mdProjectId}")
    @Transactional
    public Long addAlias(MdProjectAlias alias) {
        aliasMapper.insert(alias);
        return alias.getId();
    }

    /** 状态流转按 02 册 §7.2：首个任务开工/首采 → IN_PROGRESS（手工改状态不开放，由 S2 联动） */
    @Override
    @Transactional
    public void markMdProjectInProgress(Long id) {
        MdProject p = projectMapper.selectById(id);
        if (p == null) {
            // 自动链路韧性：档案缺失不阻断任务开工（正常建档流程档案必在）
            return;
        }
        if (!"SIGNED_PENDING".equals(p.getStatus())) {
            return;
        }
        p.setStatus("IN_PROGRESS");
        projectMapper.updateById(p);
    }

    @Audited(module = "mdm", action = "close_project", targetType = "md_project", targetId = "{0}")
    @Transactional
    public void close(Long id) {
        MdProject p = mustGet(id);
        p.setStatus("CLOSED");
        projectMapper.updateById(p);
    }

    /** MdmApi 实现 */
    @Override
    public boolean customerActive(Long customerId) {
        com.projlife.mdm.entity.MdCustomer c = customerMapper.selectById(customerId);
        return c != null && c.getDeleted() == 0 && "ACTIVE".equals(c.getStatus());
    }

    @Override
    public boolean projectActive(Long mdProjectId) {
        MdProject p = projectMapper.selectById(mdProjectId);
        return p != null && p.getDeleted() == 0 && !"CLOSED".equals(p.getStatus());
    }

    @Override
    public List<Long> linkedProjectIds(Long contractId) {
        List<Long> ids = new ArrayList<>();
        for (MdProjectProduct product : productMapper.selectList(new LambdaQueryWrapper<MdProjectProduct>()
                .eq(MdProjectProduct::getContractId, contractId))) {
            ids.add(product.getMdProjectId());
        }
        return ids;
    }

    @Override
    public void linkContract(Long mdProjectId, Long contractId, String productLine) {
        MdProjectProduct product = new MdProjectProduct();
        product.setMdProjectId(mdProjectId);
        product.setContractId(contractId);
        product.setProductLine(productLine == null ? "OTHER" : productLine);
        productMapper.insert(product);
    }

    private boolean overlaps(String jsonA, String jsonB) {
        if (jsonA == null || jsonB == null) {
            return false;
        }
        for (String token : jsonB.replaceAll("[\\[\\]\"]", "").split(",")) {
            if (!token.isBlank() && jsonA.contains(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private MdProject mustGet(Long id) {
        MdProject p = projectMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("项目档案不存在");
        }
        return p;
    }
}
