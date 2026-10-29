package com.projlife.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.mdm.entity.MdCustomer;
import com.projlife.mdm.entity.MdCustomerAlias;
import com.projlife.mdm.mapper.MdCustomerAliasMapper;
import com.projlife.mdm.mapper.MdCustomerMapper;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户主体建档（12 册 §2.2：R18 唯一入口，BR-MDM-01 其余角色只能申请）。
 * FR-MDM-01 同名拒绝；FR-MDM-06 有引用只能停用；FR-MDM-05 别名库。
 */
@Service
public class CustomerService {

    private final MdCustomerMapper customerMapper;
    private final MdCustomerAliasMapper aliasMapper;
    private final CodeSequenceService codeSequenceService;

    public CustomerService(MdCustomerMapper customerMapper, MdCustomerAliasMapper aliasMapper,
                           CodeSequenceService codeSequenceService) {
        this.customerMapper = customerMapper;
        this.aliasMapper = aliasMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public PageResp<MdCustomer> page(PageReq pageReq, String keyword, String level, String groupName) {
        LambdaQueryWrapper<MdCustomer> wrapper = new LambdaQueryWrapper<MdCustomer>()
                .orderByDesc(MdCustomer::getId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(MdCustomer::getCustomerName, keyword)
                    .or().like(MdCustomer::getShortName, keyword)
                    .or().like(MdCustomer::getCustomerCode, keyword));
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(MdCustomer::getLevel, level);
        }
        if (groupName != null && !groupName.isEmpty()) {
            wrapper.eq(MdCustomer::getGroupName, groupName);
        }
        Page<MdCustomer> page = customerMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<MdCustomer> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** 集团归并视图（FR-MDM-03）：集团名 → 主体数 */
    public List<Map<String, Object>> groupSummary() {
        List<MdCustomer> all = customerMapper.selectList(new LambdaQueryWrapper<MdCustomer>()
                .eq(MdCustomer::getLevel, "GROUP"));
        return all.stream().map(g -> {
            long subCount = customerMapper.selectCount(new LambdaQueryWrapper<MdCustomer>()
                    .and(w -> w.eq(MdCustomer::getParentId, g.getId())
                            .or().eq(MdCustomer::getGroupName, g.getGroupName())));
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("groupId", g.getId());
            row.put("groupName", g.getGroupName());
            row.put("customerCount", subCount);
            row.put("isInternal", g.getIsInternal());
            return row;
        }).collect(Collectors.toList());
    }

    /** FR-MDM-01：建档（编码 C{4位} 全局连续；同名拒绝） */
    @Audited(module = "mdm", action = "create_customer", targetType = "md_customer", targetId = "{0.customerName}")
    @Transactional
    public Long create(MdCustomer customer) {
        if (customerMapper.selectCount(new LambdaQueryWrapper<MdCustomer>()
                .eq(MdCustomer::getCustomerName, customer.getCustomerName())) > 0) {
            throw BizException.badRequest("同名客户已存在：" + customer.getCustomerName() + "（FR-MDM-01 查重）");
        }
        customer.setCustomerCode(codeSequenceService.next(CodeTypes.customer()));
        if (customer.getStatus() == null) {
            customer.setStatus("ACTIVE");
        }
        if (customer.getIsInternal() == null) {
            customer.setIsInternal(0);
        }
        customerMapper.insert(customer);
        return customer.getId();
    }

    /** 别名登记（只增不删——FR-MDM-23，删除需 R03 + 审计，本接口不提供删除） */
    @Audited(module = "mdm", action = "add_customer_alias", targetType = "md_customer", targetId = "{0.customerId}")
    @Transactional
    public Long addAlias(MdCustomerAlias alias) {
        aliasMapper.insert(alias);
        return alias.getId();
    }

    /** 别名命中：任一别名/简称匹配即视为同一主体（FR-MDM-05） */
    public Long findByAnyName(String name) {
        MdCustomer direct = customerMapper.selectOne(new LambdaQueryWrapper<MdCustomer>()
                .eq(MdCustomer::getCustomerName, name).last("LIMIT 1"));
        if (direct != null) {
            return direct.getId();
        }
        MdCustomer byShort = customerMapper.selectOne(new LambdaQueryWrapper<MdCustomer>()
                .eq(MdCustomer::getShortName, name).last("LIMIT 1"));
        if (byShort != null) {
            return byShort.getId();
        }
        MdCustomerAlias alias = aliasMapper.selectOne(new LambdaQueryWrapper<MdCustomerAlias>()
                .eq(MdCustomerAlias::getAliasName, name).last("LIMIT 1"));
        return alias == null ? null : alias.getCustomerId();
    }

    /** FR-MDM-06：有引用（子级）只能停用 */
    @Audited(module = "mdm", action = "deactivate_customer", targetType = "md_customer", targetId = "{0}")
    @Transactional
    public void deactivate(Long id) {
        MdCustomer customer = mustGet(id);
        long children = customerMapper.selectCount(new LambdaQueryWrapper<MdCustomer>()
                .eq(MdCustomer::getParentId, id).eq(MdCustomer::getStatus, "ACTIVE"));
        if (children > 0) {
            throw BizException.badRequest("存在启用中的下级主体，只能停用（FR-MDM-06）");
        }
        customer.setStatus("FROZEN");
        customerMapper.updateById(customer);
    }

    private MdCustomer mustGet(Long id) {
        MdCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw BizException.notFound("客户主体不存在");
        }
        return customer;
    }
}
