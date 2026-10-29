package com.projlife.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.crm.entity.CrmCustomerProfile;
import com.projlife.crm.mapper.CrmCustomerProfileMapper;
import com.projlife.mdm.api.MdmApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户 360°（FR-CRM-10/11/12/14）：聚合合同额、回款、应收、RFM 分档。
 * BR-CRM-04：金额字段由 Controller 层按 contract:view_price 权限脱敏。
 */
@Service
public class CustomerProfileService {

    private final CrmCustomerProfileMapper profileMapper;
    private final MdmApi mdmApi;
    private final JdbcTemplate jdbcTemplate;

    public CustomerProfileService(CrmCustomerProfileMapper profileMapper, MdmApi mdmApi, JdbcTemplate jdbcTemplate) {
        this.profileMapper = profileMapper;
        this.mdmApi = mdmApi;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** FR-CRM-10：一屏聚合（历史合同数/合同额/回款/应收/最近交易 + 主体信息） */
    public Map<String, Object> profile360(Long customerId) {
        Map<String, Object> result = new HashMap<>();
        result.put("profile", recompute(customerId));
        Long contractCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ct_contract WHERE counterparty_id = ? AND deleted = 0", Long.class, customerId);
        BigDecimal totalInvoiced = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM ct_invoice WHERE contract_id IN "
                        + "(SELECT id FROM ct_contract WHERE counterparty_id = ? AND deleted = 0) AND deleted = 0",
                BigDecimal.class, customerId);
        result.put("contractCount", contractCount);
        result.put("totalInvoiced", totalInvoiced);
        return result;
    }

    /** 重算画像：合同额/回款/应收/最近交易 + RFM 三分位（FR-CRM-14 简化实现，样本小按分档计） */
    @Transactional
    public CrmCustomerProfile recompute(Long customerId) {
        CrmCustomerProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CrmCustomerProfile>()
                .eq(CrmCustomerProfile::getCustomerId, customerId));
        if (profile == null) {
            profile = new CrmCustomerProfile();
            profile.setCustomerId(customerId);
        }
        BigDecimal totalAmount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM ct_contract WHERE counterparty_id = ? AND deleted = 0"
                        + " AND status IN ('SIGNED','PERFORMING','ACCEPTED','WARRANTY','CLOSED')",
                BigDecimal.class, customerId);
        BigDecimal received = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(r.amount),0) FROM ct_receipt r JOIN ct_contract c ON r.contract_id = c.id"
                        + " WHERE c.counterparty_id = ? AND r.status = 'CONFIRMED' AND r.deleted = 0",
                BigDecimal.class, customerId);
        BigDecimal invoiced = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(i.amount),0) FROM ct_invoice i JOIN ct_contract c ON i.contract_id = c.id"
                        + " WHERE c.counterparty_id = ? AND i.status = 'CONFIRMED' AND i.deleted = 0",
                BigDecimal.class, customerId);
        String lastTxn = jdbcTemplate.queryForObject(
                "SELECT MAX(r.receipt_date) FROM ct_receipt r JOIN ct_contract c ON r.contract_id = c.id"
                        + " WHERE c.counterparty_id = ? AND r.status = 'CONFIRMED' AND r.deleted = 0",
                String.class, customerId);

        profile.setTotalContractAmount(totalAmount == null ? BigDecimal.ZERO : totalAmount);
        profile.setTotalReceived(received == null ? BigDecimal.ZERO : received);
        profile.setTotalReceivable(invoiced == null ? BigDecimal.ZERO : invoiced.subtract(received == null ? BigDecimal.ZERO : received));
        profile.setLastTxnDate(lastTxn == null ? null : LocalDate.parse(lastTxn));
        applyRfm(profile);
        profile.setSnapshotDate(LocalDate.now());
        if (profile.getId() == null) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
        return profile;
    }

    /** FR-CRM-14 RFM：R/F/M 按阈值分 1-3 档（样本量小，用业务阈值而非三分位） */
    private void applyRfm(CrmCustomerProfile profile) {
        if (profile.getLastTxnDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(profile.getLastTxnDate(), LocalDate.now());
            profile.setRfmR(days <= 30 ? 3 : days <= 180 ? 2 : 1);
        } else {
            profile.setRfmR(1);
        }
        profile.setRfmF(profile.getTotalReceived().signum() > 0 ? 2 : 1);
        profile.setRfmM(profile.getTotalContractAmount().compareTo(new BigDecimal("1000000")) >= 0 ? 3
                : profile.getTotalContractAmount().signum() > 0 ? 2 : 1);
    }
}
