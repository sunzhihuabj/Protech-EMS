package com.projlife.mdm.api;

/**
 * 主数据跨模块 API（模块间只经 api 包调用，禁止注入对方 Mapper——16 册 §三）。
 */
public interface MdmApi {

    /** 客户主体是否存在且启用 */
    boolean customerActive(Long customerId);

    /** 项目档案是否存在且未关闭 */
    boolean projectActive(Long mdProjectId);

    /** 合同已关联的项目档案 id（FR-MDM-18 多对多，BR-01 判定依据） */
    java.util.List<Long> linkedProjectIds(Long contractId);

    /** 合同与项目建立/解除关联（md_project_product 承载，FR-MDM-18） */
    void linkContract(Long mdProjectId, Long contractId, String productLine);
}
