package com.projlife.contract.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同关联单据聚合（SPRINT-PRJ1，审计 F9）：合同 → 项目 → 开票 → 收款计划 全链追溯。
 * 只读聚合走 JdbcTemplate 直查（跨模块只读，不引实体依赖）；空链返回空数组（P5 无假数据口径）。
 */
@Service
public class ContractRelatedService {

    private final JdbcTemplate jdbc;

    public ContractRelatedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> related(Long contractId) {
        Map<String, Object> result = new HashMap<>();
        result.put("projects", jdbc.queryForList(
                "SELECT id, project_no AS projectNo, project_name AS projectName, status, progress_pct AS progressPct "
                        + "FROM prj_project WHERE contract_id = ? AND deleted = 0 ORDER BY id", contractId));
        result.put("invoices", jdbc.queryForList(
                "SELECT id, invoice_no AS invoiceNo, invoice_type AS invoiceType, amount, tax_amount AS taxAmount, "
                        + "invoice_date AS invoiceDate, status FROM ct_invoice WHERE contract_id = ? AND deleted = 0 ORDER BY id", contractId));
        result.put("paymentPlans", jdbc.queryForList(
                "SELECT id, phase_name AS phaseName, percentage, amount, plan_date AS planDate, "
                        + "actual_amount AS actualAmount, actual_date AS actualDate, status "
                        + "FROM ct_contract_payment_plan WHERE contract_id = ? AND deleted = 0 ORDER BY id", contractId));
        return result;
    }
}
