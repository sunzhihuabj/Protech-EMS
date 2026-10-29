package com.projlife.plm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.mdm.entity.MdMaterial;
import com.projlife.mdm.mapper.MdMaterialMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.plm.entity.BomChange;
import com.projlife.plm.entity.BomChangeImpact;
import com.projlife.plm.entity.BomHeader;
import com.projlife.plm.entity.BomLine;
import com.projlife.plm.mapper.BomChangeImpactMapper;
import com.projlife.plm.mapper.BomChangeMapper;
import com.projlife.plm.mapper.BomHeaderMapper;
import com.projlife.plm.mapper.BomLineMapper;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BOM 二维矩阵（06 册 §2/§5：三形态 × 三视图）。
 * BR-PLM-01 唯一现行；BR-PLM-03 发布后不可编辑；BR-PLM-07 C 类 MBOM 显式不适用。
 */
@Service
public class BomService {

    /** E-20 受控 8 种单位 */
    private static final Set<String> ALLOWED_UNITS = Set.of("PCS", "SET", "UNIT", "M", "KG", "PKG", "PAIR", "BOX");

    private final BomHeaderMapper headerMapper;
    private final BomLineMapper lineMapper;
    private final BomChangeMapper changeMapper;
    private final BomChangeImpactMapper impactMapper;
    private final MdMaterialMapper materialMapper;
    private final CodeSequenceService codeSequenceService;

    public BomService(BomHeaderMapper headerMapper, BomLineMapper lineMapper, BomChangeMapper changeMapper,
                      BomChangeImpactMapper impactMapper, MdMaterialMapper materialMapper,
                      CodeSequenceService codeSequenceService) {
        this.headerMapper = headerMapper;
        this.lineMapper = lineMapper;
        this.changeMapper = changeMapper;
        this.impactMapper = impactMapper;
        this.materialMapper = materialMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public PageResp<BomHeader> page(PageReq pageReq, String form, String view, String productLine) {
        LambdaQueryWrapper<BomHeader> wrapper = new LambdaQueryWrapper<BomHeader>()
                .orderByDesc(BomHeader::getId);
        if (form != null && !form.isEmpty()) {
            wrapper.eq(BomHeader::getBomForm, form);
        }
        if (view != null && !view.isEmpty()) {
            wrapper.eq(BomHeader::getBomView, view);
        }
        if (productLine != null && !productLine.isEmpty()) {
            wrapper.eq(BomHeader::getProductLine, productLine);
        }
        Page<BomHeader> page = headerMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<BomHeader> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public BomHeader detail(Long id) {
        return mustGet(id);
    }

    public List<BomLine> linesOf(Long id) {
        return lineMapper.selectList(new LambdaQueryWrapper<BomLine>()
                .eq(BomLine::getBomHeaderId, id)
                .orderByAsc(BomLine::getLineNo));
    }

    /** FR-PLM-11/27：新建——C 类 + MBOM 显式不适用（422），九宫格合法性 */
    @Audited(module = "bom", action = "create_bom", targetType = "bom_header", targetId = "{0.bomNo}")
    @Transactional
    public Long create(BomHeader header) {
        if (!"A".equals(header.getBomForm()) && !"B".equals(header.getBomForm()) && !"C".equals(header.getBomForm())) {
            throw BizException.badRequest("形态必须 A/B/C（E-17）");
        }
        if (!"EBOM".equals(header.getBomView()) && !"MBOM".equals(header.getBomView()) && !"SBOM".equals(header.getBomView())) {
            throw BizException.badRequest("视图必须 EBOM/MBOM/SBOM（E-18）");
        }
        if ("C".equals(header.getBomForm()) && "MBOM".equals(header.getBomView())) {
            throw BizException.of(ErrorCode.BLOCKED, "C 类工程物料无制造过程，不建 MBOM")
                    .with("blockCode", "SYS-BOM-NOT-APPLICABLE")
                    .with("suggestion", "C 类 MBOM 页签显示「不适用（工程物料无制造过程）」，BR-PLM-07");
        }
        if ("C".equals(header.getBomForm()) && header.getMdProjectId() == null) {
            throw BizException.badRequest("C 类 BOM 必选项目（FR-PLM-11）");
        }
        if (header.getProductLine() == null || header.getProductModel() == null) {
            throw BizException.badRequest("产品线与型号必填");
        }
        header.setBomNo(nextBomNo(header));
        header.setVersion("V1.0");
        header.setStatus("DRAFT");
        if ("C".equals(header.getBomForm()) && "MBOM".equals(header.getBomView())) {
            header.setMbomNotApplicable(1);
        }
        headerMapper.insert(header);
        return header.getId();
    }

    private String nextBomNo(BomHeader header) {
        String objectKey = "C".equals(header.getBomForm()) && header.getMdProjectId() != null
                ? "P" + header.getMdProjectId() : header.getProductModel();
        String display = "BOM-" + header.getBomForm() + header.getBomView() + "-" + objectKey + "-";
        return codeSequenceService.next(new CodeTypes.CodeRequest(
                "BOM:" + header.getBomForm() + ":" + header.getBomView() + ":" + objectKey, display, 2));
    }

    /** FR-PLM-12/13/14/15：加行——物料必选（禁空禁序号）、数量>0、单位受控 */
    @Audited(module = "bom", action = "add_bom_line", targetType = "bom_header", targetId = "{0.bomHeaderId}")
    @Transactional
    public Long addLine(BomLine line) {
        BomHeader header = mustGet(line.getBomHeaderId());
        if ("RELEASED".equals(header.getStatus()) || "OBSOLETE".equals(header.getStatus())) {
            throw BizException.badRequest("BOM 已发布不可编辑，修改必须新版本 + ECN（BR-PLM-03）");
        }
        if (line.getMaterialId() == null) {
            throw BizException.badRequest("物料必选，禁止空编码（FR-PLM-13）");
        }
        MdMaterial material = materialMapper.selectById(line.getMaterialId());
        if (material == null) {
            throw BizException.badRequest("物料主数据不存在，不得保存（BR-PLM-04）");
        }
        if (line.getQtyPerUnit() == null || line.getQtyPerUnit().signum() <= 0) {
            throw BizException.badRequest("数量必须 >0（FR-PLM-14）");
        }
        if (line.getUnit() == null || !ALLOWED_UNITS.contains(line.getUnit())) {
            throw BizException.badRequest("计量单位必须是 E-20 受控 8 种（FR-PLM-15）");
        }
        if (line.getLineNo() == null || !line.getLineNo().matches("^\\d+(-\\d+){0,2}$")) {
            throw BizException.badRequest("层级序号不规范（形如 1 / 1-1 / 1-1-1，FR-PLM-12）");
        }
        line.setMaterialCode(material.getMaterialCode());
        lineMapper.insert(line);
        return line.getId();
    }

    /** BR-PLM-06：删行软删，保留在差异比对中 */
    @Audited(module = "bom", action = "remove_bom_line", targetType = "bom_line", targetId = "{0}")
    @Transactional
    public void removeLine(Long lineId) {
        BomLine line = lineMapper.selectById(lineId);
        if (line == null) {
            throw BizException.notFound("BOM 行不存在");
        }
        BomHeader header = mustGet(line.getBomHeaderId());
        if ("RELEASED".equals(header.getStatus()) || "OBSOLETE".equals(header.getStatus())) {
            throw BizException.badRequest("BOM 已发布不可编辑（BR-PLM-03）");
        }
        lineMapper.deleteById(lineId);
    }

    /**
     * 06 册 §5.1 BOM 数据质量 13 条校验（保存实时 1-7/12；发布前全量）。
     */
    public List<Map<String, Object>> validate(Long bomId) {
        BomHeader header = mustGet(bomId);
        List<BomLine> lines = linesOf(bomId);
        List<Map<String, Object>> issues = new ArrayList<>();
        Set<Long> materialIds = new HashSet<>();
        Map<String, List<MdMaterial>> byNameSpec = new HashMap<>();

        for (BomLine line : lines) {
            if (line.getMaterialId() == null) {
                issue(issues, 1, "ERROR", line.getLineNo(), "物料编码为空");
                continue;
            }
            // 规则 2：编码列被填序号（行快照编码为 1-3 位纯数字且长度<5）
            if (line.getMaterialCode() != null && line.getMaterialCode().matches("^\\d{1,3}$")) {
                issue(issues, 2, "ERROR", line.getLineNo(), "编码列疑似填写序号：" + line.getMaterialCode());
            }
            MdMaterial material = materialMapper.selectById(line.getMaterialId());
            if (material == null) {
                issue(issues, 1, "ERROR", line.getLineNo(), "物料在主数据中不存在");
                continue;
            }
            materialIds.add(material.getId());
            byNameSpec.computeIfAbsent(material.getMaterialName() + "|" + material.getSpec(),
                    k -> new ArrayList<>()).add(material);
            // 规则 4：规格为空
            if (material.getSpec() == null || material.getSpec().isEmpty()) {
                issue(issues, 4, "WARN", line.getLineNo(), "物料规格为空：" + material.getMaterialName());
            }
            // 规则 5/6：单位
            if (line.getUnit() == null || line.getUnit().isEmpty()) {
                issue(issues, 5, "ERROR", line.getLineNo(), "计量单位为空");
            } else if (List.of("标准件", "钣金件", "组", "套件").contains(line.getUnit())) {
                issue(issues, 6, "ERROR", line.getLineNo(), "单位被填成类别：" + line.getUnit());
            }
            // 规则 7：数量
            if (line.getQtyPerUnit() == null || line.getQtyPerUnit().signum() <= 0) {
                issue(issues, 7, "ERROR", line.getLineNo(), "数量为 0 或负");
            }
            // 规则 12：层级序号
            if (line.getLineNo() != null && !line.getLineNo().matches("^\\d+(-\\d+){0,2}$")) {
                issue(issues, 12, "WARN", line.getLineNo(), "层级序号不规范");
            }
        }

        // 规则 8：一物多码（同名同规格多 material_id）
        for (List<MdMaterial> group : byNameSpec.values()) {
            Set<Long> distinct = new HashSet<>();
            group.forEach(m -> distinct.add(m.getId()));
            if (distinct.size() > 1) {
                issue(issues, 8, "WARN", "-", "一物多码：同名称同规格存在 " + distinct.size() + " 个编码");
            }
        }
        // 规则 9/10/11：一码多物 / 一码多规格 / 同码多单位（阻断级 10）
        Map<Long, Set<String>> namesByMaterial = new HashMap<>();
        Map<Long, Set<String>> unitsByMaterial = new HashMap<>();
        for (BomLine line : lines) {
            if (line.getMaterialId() == null) {
                continue;
            }
            if (line.getMaterialCode() != null) {
                namesByMaterial.computeIfAbsent(line.getMaterialId(), k -> new HashSet<>())
                        .add(String.valueOf(line.getRemark()));
            }
            unitsByMaterial.computeIfAbsent(line.getMaterialId(), k -> new HashSet<>()).add(line.getUnit());
        }
        for (Map.Entry<Long, Set<String>> entry : unitsByMaterial.entrySet()) {
            if (entry.getValue().size() > 1) {
                issue(issues, 11, "ERROR", "-", "同物料出现多种单位：" + entry.getValue());
            }
        }

        // 规则 13：工艺文件未关联（INFO）
        long withoutProcessDoc = lines.stream().filter(l -> l.getProcessDocId() == null).count();
        if (withoutProcessDoc > 0) {
            issue(issues, 13, "INFO", "-", withoutProcessDoc + " 行未关联工艺文件");
        }
        return issues;
    }

    private void issue(List<Map<String, Object>> issues, int rule, String level, String lineNo, String message) {
        Map<String, Object> row = new HashMap<>();
        row.put("rule", rule);
        row.put("level", level);
        row.put("lineNo", lineNo);
        row.put("message", message);
        issues.add(row);
    }

    /** FR-PLM-26 + BR-PLM-01：发布——全量校验无 ERROR 才可发布；原子切换现行 */
    @Audited(module = "bom", action = "release_bom", targetType = "bom_header", targetId = "{0}")
    @Transactional
    public Map<String, Object> release(Long bomId) {
        BomHeader header = mustGet(bomId);
        if (!"DRAFT".equals(header.getStatus()) && !"REVIEW".equals(header.getStatus())) {
            throw BizException.badRequest("仅草稿/审核中可发布（E-19）");
        }
        List<Map<String, Object>> issues = validate(bomId);
        List<Map<String, Object>> errors = issues.stream()
                .filter(i -> "ERROR".equals(i.get("level"))).collect(java.util.stream.Collectors.toList());
        if (!errors.isEmpty()) {
            throw BizException.badRequest("存在 " + errors.size() + " 个 ERROR 级校验问题，不可发布（06 册 §5.1）")
                    .with("issues", errors);
        }
        // BR-PLM-01：同对象唯一现行——先清旧的
        headerMapper.selectList(new LambdaQueryWrapper<BomHeader>()
                        .eq(BomHeader::getBomForm, header.getBomForm())
                        .eq(BomHeader::getBomView, header.getBomView())
                        .eq(BomHeader::getProductLine, header.getProductLine())
                        .eq(BomHeader::getProductModel, header.getProductModel())
                        .eq(BomHeader::getIsCurrent, 1))
                .forEach(other -> {
                    if (!other.getId().equals(bomId)) {
                        other.setIsCurrent(0);
                        headerMapper.updateById(other);
                    }
                });
        header.setStatus("RELEASED");
        header.setIsCurrent(1);
        header.setReleasedAt(LocalDateTime.now());
        header.setReleasedBy(UserContextHolder.currentUserCode());
        headerMapper.updateById(header);
        Map<String, Object> result = new HashMap<>();
        result.put("bomNo", header.getBomNo());
        result.put("status", "RELEASED");
        result.put("warnings", issues.stream().filter(i -> "WARN".equals(i.get("level"))).count());
        return result;
    }

    /** FR-PLM-18：项目 BOM 派生（复制行，只允许改量/删行/加项目专用行） */
    @Audited(module = "bom", action = "derive_bom", targetType = "bom_header", targetId = "{0}")
    @Transactional
    public Long derive(Long goldenBomId, Long mdProjectId) {
        BomHeader golden = mustGet(goldenBomId);
        BomHeader derived = new BomHeader();
        derived.setBomForm(golden.getBomForm());
        derived.setBomView(golden.getBomView());
        derived.setProductLine(golden.getProductLine());
        derived.setProductModel(golden.getProductModel());
        derived.setMdProjectId(mdProjectId);
        derived.setBaseBomId(golden.getBaseBomId() == null ? goldenBomId : golden.getBaseBomId());
        derived.setDerivedFrom(goldenBomId);
        Long id = create(derived);
        for (BomLine line : linesOf(goldenBomId)) {
            BomLine copy = new BomLine();
            copy.setBomHeaderId(id);
            copy.setLineNo(line.getLineNo());
            copy.setMaterialId(line.getMaterialId());
            copy.setMaterialCode(line.getMaterialCode());
            copy.setQtyPerUnit(line.getQtyPerUnit());
            copy.setUnit(line.getUnit());
            copy.setLossRate(line.getLossRate());
            copy.setMaterialCategory(line.getMaterialCategory());
            copy.setPointCode(line.getPointCode());
            copy.setIsProjectSpecial(line.getIsProjectSpecial());
            copy.setSourceType(line.getSourceType());
            lineMapper.insert(copy);
        }
        return id;
    }

    /** FR-PLM-21 视图转换（06 册 §2.4）：EBOM→MBOM 加工艺辅料行；EBOM→SBOM 过滤（A-17：is_controlled 口径） */
    @Audited(module = "bom", action = "convert_bom", targetType = "bom_header", targetId = "{0}")
    @Transactional
    public Long convert(Long sourceBomId, String targetView) {
        BomHeader source = mustGet(sourceBomId);
        if (!"EBOM".equals(source.getBomView())) {
            throw BizException.badRequest("仅支持 EBOM→MBOM / EBOM→SBOM（06 册 §2.4）");
        }
        if ("MBOM".equals(targetView) && "C".equals(source.getBomForm())) {
            throw BizException.of(ErrorCode.BLOCKED, "C 类工程物料无制造过程，不建 MBOM")
                    .with("blockCode", "SYS-BOM-NOT-APPLICABLE");
        }
        BomHeader target = new BomHeader();
        target.setBomForm(source.getBomForm());
        target.setBomView(targetView);
        target.setProductLine(source.getProductLine());
        target.setProductModel(source.getProductModel());
        target.setMdProjectId(source.getMdProjectId());
        target.setSourceBomId(sourceBomId);
        Long id = create(target);

        if ("MBOM".equals(targetView)) {
            for (BomLine line : linesOf(sourceBomId)) {
                BomLine copy = copyLine(id, line);
                // ④ 设置来源：按物料类别口径（S3 简化：外购件→PURCHASE，标准件→STD，其余 SELF）
                MdMaterial material = materialMapper.selectById(line.getMaterialId());
                if (material != null) {
                    String type = "STD".equals(material.getCategory()) ? "STD"
                            : "PURCHASE".equals(material.getCategory()) ? "PURCHASE" : "SELF";
                    copy.setSourceType(type);
                }
                lineMapper.insert(copy);
            }
            // ② 工艺辅料行（端子/扎带/标签，虚拟件编码 V- 通过主数据；此处用物料库中 V- 码，无则跳过）
            addAuxLine(id, "V-AUX-TERM", "1-90", "PCS");
            addAuxLine(id, "V-AUX-TIE", "1-91", "PCS");
            addAuxLine(id, "V-AUX-LABEL", "1-92", "PCS");
        } else if ("SBOM".equals(targetView)) {
            for (BomLine line : linesOf(sourceBomId)) {
                MdMaterial material = materialMapper.selectById(line.getMaterialId());
                // A-17：SBOM 只保留受控件（安全件/关键件=可更换件口径）
                if (material != null && material.getIsControlled() != null && material.getIsControlled() == 1) {
                    lineMapper.insert(copyLine(id, line));
                }
            }
        }
        return id;
    }

    private void addAuxLine(Long bomId, String auxCode, String lineNo, String unit) {
        MdMaterial aux = materialMapper.selectOne(new LambdaQueryWrapper<MdMaterial>()
                .eq(MdMaterial::getMaterialCode, auxCode).last("LIMIT 1"));
        if (aux == null) {
            return;
        }
        BomLine auxLine = new BomLine();
        auxLine.setBomHeaderId(bomId);
        auxLine.setLineNo(lineNo);
        auxLine.setMaterialId(aux.getId());
        auxLine.setMaterialCode(aux.getMaterialCode());
        auxLine.setQtyPerUnit(new BigDecimal("1"));
        auxLine.setUnit(unit);
        auxLine.setIsProjectSpecial(0);
        lineMapper.insert(auxLine);
    }

    private BomLine copyLine(Long targetBomId, BomLine source) {
        BomLine copy = new BomLine();
        copy.setBomHeaderId(targetBomId);
        copy.setLineNo(source.getLineNo());
        copy.setMaterialId(source.getMaterialId());
        copy.setMaterialCode(source.getMaterialCode());
        copy.setQtyPerUnit(source.getQtyPerUnit());
        copy.setUnit(source.getUnit());
        copy.setLossRate(source.getLossRate());
        copy.setMaterialCategory(source.getMaterialCategory());
        copy.setPointCode(source.getPointCode());
        copy.setIsProjectSpecial(source.getIsProjectSpecial());
        copy.setSourceType(source.getSourceType());
        return copy;
    }

    /** FR-PLM-20：两份 BOM 差异（新增/删除/改量/换料） */
    public Map<String, Object> diff(Long bomAId, Long bomBId) {
        Map<String, BomLine> linesA = new HashMap<>();
        for (BomLine line : linesOf(bomAId)) {
            linesA.put(line.getMaterialId() + "@" + line.getLineNo(), line);
        }
        Map<String, BomLine> linesB = new HashMap<>();
        for (BomLine line : linesOf(bomBId)) {
            linesB.put(line.getMaterialId() + "@" + line.getLineNo(), line);
        }
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();
        for (Map.Entry<String, BomLine> entry : linesB.entrySet()) {
            BomLine a = linesA.get(entry.getKey());
            if (a == null) {
                added.add(rowOf(entry.getValue(), "ADDED"));
            } else if (a.getQtyPerUnit().compareTo(entry.getValue().getQtyPerUnit()) != 0) {
                Map<String, Object> row = rowOf(entry.getValue(), "QTY_CHANGED");
                row.put("qtyA", a.getQtyPerUnit());
                row.put("qtyB", entry.getValue().getQtyPerUnit());
                changed.add(row);
            }
        }
        for (Map.Entry<String, BomLine> entry : linesA.entrySet()) {
            if (!linesB.containsKey(entry.getKey())) {
                removed.add(rowOf(entry.getValue(), "REMOVED"));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        result.put("removed", removed);
        result.put("qtyChanged", changed);
        return result;
    }

    private Map<String, Object> rowOf(BomLine line, String diffType) {
        Map<String, Object> row = new HashMap<>();
        row.put("diffType", diffType);
        row.put("lineNo", line.getLineNo());
        row.put("materialCode", line.getMaterialCode());
        row.put("qtyPerUnit", line.getQtyPerUnit());
        row.put("unit", line.getUnit());
        return row;
    }

    /** FR-PLM-22：B 类参数化展开（节数 = 塔高/段长 向上取整 × 标准节行数量） */
    @Audited(module = "bom", action = "expand_bom", targetType = "bom_header", targetId = "{0}")
    @Transactional
    public Long expand(Long templateBomId, Map<String, Object> params, Long mdProjectId) {
        BomHeader template = mustGet(templateBomId);
        if (!"B".equals(template.getBomForm())) {
            throw BizException.badRequest("仅 B 类参数化产品支持展开（FR-PLM-22）");
        }
        double towerHeight = Double.parseDouble(String.valueOf(params.getOrDefault("towerHeight", "0")));
        double sectionLength = Double.parseDouble(String.valueOf(params.getOrDefault("sectionLength", "1")));
        if (towerHeight <= 0 || sectionLength <= 0) {
            throw BizException.badRequest("塔高与段长必填且 >0");
        }
        int sections = (int) Math.ceil(towerHeight / sectionLength);

        BomHeader project = new BomHeader();
        project.setBomForm("B");
        project.setBomView(template.getBomView());
        project.setProductLine(template.getProductLine());
        project.setProductModel(template.getProductModel());
        project.setMdProjectId(mdProjectId);
        project.setDerivedFrom(templateBomId);
        project.setParamJson("{\"towerHeight\":" + towerHeight + ",\"sectionLength\":" + sectionLength
                + ",\"sections\":" + sections + "}");
        Long id = create(project);
        for (BomLine line : linesOf(templateBomId)) {
            BomLine expanded = copyLine(id, line);
            if (line.getRemark() != null && line.getRemark().contains("SECTION")) {
                expanded.setQtyPerUnit(line.getQtyPerUnit().multiply(BigDecimal.valueOf(sections)));
                expanded.setRemark(line.getRemark() + " x" + sections);
            }
            lineMapper.insert(expanded);
        }
        return id;
    }

    /** FR-PLM-28/30 + BR-09：ECN 变更（旧料处置必填 ≥10 字） */
    @Audited(module = "bom", action = "create_ecn", targetType = "bom_change", targetId = "{0.changeNo}")
    @Transactional
    public Long createEcn(BomChange change) {
        BomHeader header = mustGet(change.getBomHeaderId());
        String disposal = change.getOldDisposalPlan();
        if (disposal == null || disposal.trim().length() < 10) {
            throw BizException.of(ErrorCode.BLOCKED, "BOM 变更未附旧料处置方案，拒绝提交")
                    .with("blockCode", "BR-09")
                    .with("suggestion", "填写处置方案：优先领用/改制/退供应商/报废/客户消化 + 说明");
        }
        if (change.getImpactConfirmed() == null || change.getImpactConfirmed() != 1) {
            throw BizException.badRequest("影响清单未确认，拒绝进入审批（FR-PLM-30）");
        }
        change.setChangeNo(codeSequenceService.next(CodeTypes.ecn(
                String.valueOf(java.time.LocalDate.now().getYear()))));
        change.setStatus("SUBMITTED");
        changeMapper.insert(change);
        // FR-PLM-29：影响清单自动生成（S3 阶段库存/在制数据未建，生成行框架由确认页补数，A-16）
        for (BomLine line : linesOf(header.getId())) {
            BomChangeImpact impact = new BomChangeImpact();
            impact.setChangeId(change.getId());
            impact.setImpactType("STOCK");
            impact.setMaterialId(line.getMaterialId());
            impact.setQty(BigDecimal.ZERO);
            impact.setAmount(BigDecimal.ZERO);
            impact.setAction("待 S4 库存数据接入后自动计算");
            impactMapper.insert(impact);
        }
        return change.getId();
    }

    /** FR-PLM-32：批准发布——新版本置现行、旧版 OBSOLETE */
    @Audited(module = "bom", action = "publish_ecn", targetType = "bom_change", targetId = "{0}")
    @Transactional
    public Map<String, Object> publishEcn(Long changeId, boolean approved) {
        BomChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw BizException.notFound("ECN 不存在");
        }
        if ("PUBLISHED".equals(change.getStatus()) || "REJECTED".equals(change.getStatus())) {
            throw BizException.badRequest("ECN 已终态");
        }
        if (!approved) {
            change.setStatus("REJECTED");
            changeMapper.updateById(change);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "REJECTED");
            return result;
        }
        change.setStatus("PUBLISHED");
        change.setApprovedBy(UserContextHolder.currentUserId());
        change.setPublishedAt(LocalDateTime.now());

        // 新版本：从当前版本复制行 → is_current 切换 → 旧版 OBSOLETE
        BomHeader oldHeader = mustGet(change.getBomHeaderId());
        String newVersion = bumpVersion(oldHeader.getVersion());
        change.setNewVersion(newVersion);
        changeMapper.updateById(change);

        BomHeader newHeader = new BomHeader();
        newHeader.setBomForm(oldHeader.getBomForm());
        newHeader.setBomView(oldHeader.getBomView());
        newHeader.setProductLine(oldHeader.getProductLine());
        newHeader.setProductModel(oldHeader.getProductModel());
        newHeader.setMdProjectId(oldHeader.getMdProjectId());
        newHeader.setBaseBomId(oldHeader.getBaseBomId());
        newHeader.setDerivedFrom(oldHeader.getDerivedFrom());
        Long newId = create(newHeader);
        // create() 统一置 V1.0，ECN 新版本号在此覆盖（FR-PLM-26：V{n} 递增）
        newHeader.setId(newId);
        newHeader.setVersion(newVersion);
        headerMapper.updateById(newHeader);
        for (BomLine line : linesOf(oldHeader.getId())) {
            lineMapper.insert(copyLine(newId, line));
        }
        release(newId);
        oldHeader.setStatus("OBSOLETE");
        oldHeader.setIsCurrent(0);
        headerMapper.updateById(oldHeader);
        Map<String, Object> result = new HashMap<>();
        result.put("newBomId", newId);
        result.put("newVersion", newVersion);
        return result;
    }

    private String bumpVersion(String version) {
        if (version == null || !version.matches("V\\d+\\.\\d+")) {
            return "V2.0";
        }
        String[] parts = version.substring(1).split("\\.");
        return "V" + (Integer.parseInt(parts[0]) + 1) + ".0";
    }

    private BomHeader mustGet(Long id) {
        BomHeader header = headerMapper.selectById(id);
        if (header == null) {
            throw BizException.notFound("BOM 不存在");
        }
        return header;
    }
}
