package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.org.dto.DictItemReq;
import com.projlife.org.dto.DictTypeReq;
import com.projlife.org.entity.SysDictItem;
import com.projlife.org.entity.SysDictType;
import com.projlife.org.mapper.SysDictItemMapper;
import com.projlife.org.mapper.SysDictTypeMapper;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典管理（S0-8）。完成标准：系统枚举（is_system=1，E-01~E-42）删除被拒、item_code 禁改。
 */
@Service
public class DictService {

    private final SysDictTypeMapper typeMapper;
    private final SysDictItemMapper itemMapper;

    public DictService(SysDictTypeMapper typeMapper, SysDictItemMapper itemMapper) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    public List<Map<String, Object>> listTypes() {
        return typeMapper.selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getTypeCode))
                .stream().map(this::typeToMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> itemsOf(Long typeId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeId, typeId)
                .orderByAsc(SysDictItem::getSortNo))
                .stream().map(this::itemToMap).collect(Collectors.toList());
    }

    /** 按类型编码取启用项（其他模块下拉数据源） */
    public Map<String, String> itemsByCode(String typeCode) {
        SysDictType type = typeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTypeCode, typeCode));
        Map<String, String> result = new HashMap<>();
        if (type == null) {
            return result;
        }
        for (SysDictItem item : itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeId, type.getId())
                .eq(SysDictItem::getStatus, "ACTIVE")
                .orderByAsc(SysDictItem::getSortNo))) {
            result.put(item.getItemCode(), item.getItemLabel());
        }
        return result;
    }

    @Audited(module = "system", action = "create_dict_type", targetType = "sys_dict_type", targetId = "{0.typeCode}")
    @Transactional
    public Long createType(DictTypeReq req) {
        if (typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTypeCode, req.typeCode)) > 0) {
            throw BizException.badRequest("字典类型已存在：" + req.typeCode);
        }
        SysDictType type = new SysDictType();
        type.setTypeCode(req.typeCode);
        type.setTypeName(req.typeName);
        type.setIsSystem(0);
        type.setRemark(req.remark);
        type.setStatus(req.status == null ? "ACTIVE" : req.status);
        typeMapper.insert(type);
        return type.getId();
    }

    @Audited(module = "system", action = "update_dict_type", targetType = "sys_dict_type", targetId = "{0}")
    @Transactional
    public void updateType(Long id, DictTypeReq req) {
        SysDictType type = mustGet(id);
        type.setTypeName(req.typeName);
        if (req.remark != null) {
            type.setRemark(req.remark);
        }
        if (req.status != null) {
            type.setStatus(req.status);
        }
        typeMapper.updateById(type);
    }

    @Audited(module = "system", action = "delete_dict_type", targetType = "sys_dict_type", targetId = "{0}")
    @Transactional
    public void deleteType(Long id) {
        SysDictType type = mustGet(id);
        if (type.getIsSystem() != null && type.getIsSystem() == 1) {
            throw BizException.of(ErrorCode.BLOCKED, "系统内置枚举不可删除").with("blockCode", "SYS-DICT-FROZEN");
        }
        itemMapper.delete(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getTypeId, id));
        typeMapper.deleteById(id);
    }

    @Audited(module = "system", action = "create_dict_item", targetType = "sys_dict_item", targetId = "{0.itemCode}")
    @Transactional
    public Long createItem(DictItemReq req) {
        SysDictType type = mustGet(req.typeId);
        if (itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeId, req.typeId)
                .eq(SysDictItem::getItemCode, req.itemCode)) > 0) {
            throw BizException.badRequest("项编码已存在：" + req.itemCode);
        }
        SysDictItem item = new SysDictItem();
        item.setTypeId(req.typeId);
        item.setItemCode(req.itemCode);
        item.setItemLabel(req.itemLabel);
        item.setItemValue(req.itemValue);
        item.setSortNo(req.sortNo == null ? 0 : req.sortNo);
        item.setIsDefault(req.isDefault == null ? 0 : req.isDefault);
        item.setStatus(req.status == null ? "ACTIVE" : req.status);
        itemMapper.insert(item);
        return item.getId();
    }

    @Audited(module = "system", action = "update_dict_item", targetType = "sys_dict_item", targetId = "{0}")
    @Transactional
    public void updateItem(Long id, DictItemReq req) {
        SysDictItem item = itemMapper.selectById(id);
        if (item == null) {
            throw BizException.notFound("字典项不存在");
        }
        SysDictType type = mustGet(item.getTypeId());
        if (type.getIsSystem() != null && type.getIsSystem() == 1
                && req.itemCode != null && !req.itemCode.equals(item.getItemCode())) {
            throw BizException.of(ErrorCode.BLOCKED, "系统内置枚举项编码不可修改").with("blockCode", "SYS-DICT-FROZEN");
        }
        if (req.itemLabel != null) {
            item.setItemLabel(req.itemLabel);
        }
        if (req.itemValue != null) {
            item.setItemValue(req.itemValue);
        }
        if (req.sortNo != null) {
            item.setSortNo(req.sortNo);
        }
        if (req.isDefault != null) {
            item.setIsDefault(req.isDefault);
        }
        if (req.status != null) {
            item.setStatus(req.status);
        }
        itemMapper.updateById(item);
    }

    private SysDictType mustGet(Long id) {
        SysDictType type = typeMapper.selectById(id);
        if (type == null) {
            throw BizException.notFound("字典类型不存在");
        }
        return type;
    }

    private Map<String, Object> typeToMap(SysDictType type) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", type.getId());
        map.put("typeCode", type.getTypeCode());
        map.put("typeName", type.getTypeName());
        map.put("isSystem", type.getIsSystem());
        map.put("remark", type.getRemark());
        map.put("status", type.getStatus());
        return map;
    }

    private Map<String, Object> itemToMap(SysDictItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("typeId", item.getTypeId());
        map.put("itemCode", item.getItemCode());
        map.put("itemLabel", item.getItemLabel());
        map.put("itemValue", item.getItemValue());
        map.put("sortNo", item.getSortNo());
        map.put("isDefault", item.getIsDefault());
        map.put("status", item.getStatus());
        return map;
    }
}
