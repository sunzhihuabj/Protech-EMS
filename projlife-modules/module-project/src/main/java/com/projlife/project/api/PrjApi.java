package com.projlife.project.api;

import java.time.LocalDate;

/**
 * 项目模块跨模块 API（合同归档自动立项入口，FR-PRJ-01）。
 */
public interface PrjApi {

    /**
     * 合同建档（PERFORMING）后自动立项：生成执行项目 + 按默认模板实例化任务树。
     *
     * @param mdProjectId 项目档案 id
     * @param contractId  关联合同
     * @param projectName 项目名称（= 合同项目名）
     * @param planStart   计划开始（签约日）
     * @param defaultDays 默认工期（120 可配）
     * @return 执行项目 id
     */
    Long autoCreateFromContract(Long mdProjectId, Long contractId, String projectName,
                                LocalDate planStart, int defaultDays);
}
