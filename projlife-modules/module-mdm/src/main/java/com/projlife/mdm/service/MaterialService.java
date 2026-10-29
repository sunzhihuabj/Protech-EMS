package com.projlife.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.mdm.entity.MdMaterial;
import com.projlife.mdm.mapper.MdMaterialMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 物料主数据（12 册 §2.4）：FR-MDM-25 编码规范、FR-MDM-26 双码检测、FR-MDM-29 单位受控。
 */
@Service
public class MaterialService {

    private static final Set<String> ALLOWED_UNITS = Set.of("PCS", "SET", "UNIT", "M", "KG", "PKG", "PAIR", "BOX");

    private final MdMaterialMapper materialMapper;
    private final CodeSequenceService codeSequenceService;

    public MaterialService(MdMaterialMapper materialMapper, CodeSequenceService codeSequenceService) {
        this.materialMapper = materialMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public PageResp<MdMaterial> page(PageReq pageReq, String keyword) {
        LambdaQueryWrapper<MdMaterial> wrapper = new LambdaQueryWrapper<MdMaterial>()
                .orderByDesc(MdMaterial::getId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MdMaterial::getMaterialName, keyword);
        }
        Page<MdMaterial> page = materialMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<MdMaterial> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    @Audited(module = "mdm", action = "create_material", targetType = "md_material", targetId = "{0.materialCode}")
    @Transactional
    public Long create(MdMaterial material) {
        if (material.getMaterialName() == null || material.getUnit() == null) {
            throw BizException.badRequest("名称与单位必填");
        }
        if (!ALLOWED_UNITS.contains(material.getUnit())) {
            throw BizException.badRequest("单位必须是 E-20 受控 8 种（FR-MDM-29）");
        }
        // FR-MDM-26 双码检测
        Long dupNameSpec = materialMapper.selectCount(new LambdaQueryWrapper<MdMaterial>()
                .eq(MdMaterial::getMaterialName, material.getMaterialName())
                .eq(MdMaterial::getSpec, material.getSpec() == null ? "" : material.getSpec()));
        if (dupNameSpec > 0) {
            throw BizException.badRequest("同名称同规格已存在编码，疑似一物多码（FR-MDM-26 阻断）");
        }
        if (material.getMaterialCode() == null || material.getMaterialCode().isEmpty()) {
            material.setMaterialCode(codeSequenceService.next(CodeTypes.material()));
        }
        if (material.getStatus() == null) {
            material.setStatus("ACTIVE");
        }
        materialMapper.insert(material);
        return material.getId();
    }
}
