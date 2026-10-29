package com.projlife.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.inventory.api.TPlusAdapter;
import com.projlife.inventory.entity.InvStockCache;
import com.projlife.inventory.entity.InvTransaction;
import com.projlife.inventory.mapper.InvStockCacheMapper;
import com.projlife.inventory.mapper.InvTransactionMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存事务服务（00 册 C-04 + 07 册 §1：T+ 权威 + 本地流水 + 缓存 + 对账）。
 * BR-INV-01 不建权威数量表；BR-INV-02 出库校验可用量；BR-INV-03 幂等键唯一。
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InvTransactionMapper transactionMapper;
    private final InvStockCacheMapper stockCacheMapper;
    private final TPlusAdapter tPlusAdapter;
    private final CodeSequenceService codeSequenceService;

    public InventoryService(InvTransactionMapper transactionMapper, InvStockCacheMapper stockCacheMapper,
                            TPlusAdapter tPlusAdapter, CodeSequenceService codeSequenceService) {
        this.transactionMapper = transactionMapper;
        this.stockCacheMapper = stockCacheMapper;
        this.tPlusAdapter = tPlusAdapter;
        this.codeSequenceService = codeSequenceService;
    }

    public InvTransaction byIdempotencyKey(String idempotencyKey) {
        return transactionMapper.selectOne(new LambdaQueryWrapper<InvTransaction>()
                .eq(InvTransaction::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    /**
     * 提交库存事务（核心链路）：
     * ① 幂等检查（重复提交返回首次结果，BR-INV-03）
     * ② 出库校验可用量（BR-INV-02，缓存口径）
     * ③ 写本地事务（PENDING_SYNC，只增）
     * ④ 推 T+（MOCK 恒成功）→ 回写凭证号 SYNCED / 失败 EXCEPTION
     */
    @Audited(module = "inv", action = "submit_transaction", targetType = "inv_transaction", targetId = "{0.txnNo}")
    @Transactional
    public InvTransaction submit(InvTransaction txn) {
        if (txn.getIdempotencyKey() == null || txn.getIdempotencyKey().isEmpty()) {
            throw BizException.badRequest("幂等键必填（来源单号+行号+动作，BR-INV-03）");
        }
        InvTransaction existing = byIdempotencyKey(txn.getIdempotencyKey());
        if (existing != null) {
            log.info("幂等命中 key={} 返回首次事务 {}", txn.getIdempotencyKey(), existing.getTxnNo());
            return existing;
        }
        if (txn.getQty() == null || txn.getQty().signum() == 0) {
            throw BizException.badRequest("数量不能为 0");
        }
        if (txn.getWarehouseCode() == null || txn.getMaterialId() == null) {
            throw BizException.badRequest("仓库与物料必填");
        }
        // BR-INV-02：出库校验可用量（ISSUE/负数量）
        if ("ISSUE".equals(txn.getTxnType()) || (txn.getQty().signum() < 0
                && !"COUNT_ADJ".equals(txn.getTxnType()) && !"ADJUST".equals(txn.getTxnType()))) {
            BigDecimal available = availableQty(txn.getLegalEntityId(), txn.getWarehouseCode(), txn.getMaterialId());
            if (available.add(txn.getQty()).signum() < 0) {
                throw BizException.badRequest("可用量不足：当前可用 " + available + "，本次出库 " + txn.getQty().abs()
                        + "（BR-INV-02 不允许负库存）");
            }
        }

        txn.setTxnNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "TX:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "TX-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-", 6)));
        if (txn.getSyncStatus() == null) {
            txn.setSyncStatus("PENDING_SYNC");
        }
        if (txn.getOccurredAt() == null) {
            txn.setOccurredAt(LocalDateTime.now());
        }
        txn.setOperatorId(UserContextHolder.currentUserId() == null ? 0L : UserContextHolder.currentUserId());
        transactionMapper.insert(txn);

        // 推 T+（业务不中断：失败置 EXCEPTION 留待重试，07 册异常处理表）
        try {
            Map<String, Object> push = tPlusAdapter.pushTransaction(txnOf(txn));
            if (Boolean.TRUE.equals(push.get("success"))) {
                txn.setSyncStatus("SYNCED");
                txn.setTPlusVoucherNo(String.valueOf(push.get("voucherNo")));
            } else {
                txn.setSyncStatus("EXCEPTION");
                txn.setSyncError(String.valueOf(push.get("error")));
            }
        } catch (Exception ex) {
            log.warn("T+ 推送异常，事务转 EXCEPTION txnNo={}", txn.getTxnNo(), ex);
            txn.setSyncStatus("EXCEPTION");
            txn.setSyncError(ex.getMessage() == null ? "未知异常" : ex.getMessage());
        }
        transactionMapper.updateById(txn);
        refreshCache(txn);
        return txn;
    }

    /** 重试 EXCEPTION 事务（接口监控页批量重试入口） */
    @Transactional
    public int retryExceptions() {
        List<InvTransaction> exceptions = transactionMapper.selectList(new LambdaQueryWrapper<InvTransaction>()
                .eq(InvTransaction::getSyncStatus, "EXCEPTION"));
        int recovered = 0;
        for (InvTransaction txn : exceptions) {
            try {
                Map<String, Object> push = tPlusAdapter.pushTransaction(txnOf(txn));
                if (Boolean.TRUE.equals(push.get("success"))) {
                    txn.setSyncStatus("SYNCED");
                    txn.setTPlusVoucherNo(String.valueOf(push.get("voucherNo")));
                    transactionMapper.updateById(txn);
                    recovered++;
                }
            } catch (Exception ex) {
                // 仍失败，保留 EXCEPTION
            }
        }
        return recovered;
    }

    /** 可用量（缓存口径 BR-INV-02；缓存无记录按 0——T+ 拉取在 S9 集成批次） */
    public BigDecimal availableQty(Long legalEntityId, String warehouseCode, Long materialId) {
        InvStockCache cache = stockCacheMapper.selectOne(new LambdaQueryWrapper<InvStockCache>()
                .eq(InvStockCache::getLegalEntityId, legalEntityId)
                .eq(InvStockCache::getWarehouseCode, warehouseCode)
                .eq(InvStockCache::getMaterialId, materialId)
                .last("LIMIT 1"));
        if (cache == null) {
            // 缓存无记录：以本地事务流水推算（只读口径，不写权威表）
            List<InvTransaction> txns = transactionMapper.selectList(new LambdaQueryWrapper<InvTransaction>()
                    .eq(InvTransaction::getLegalEntityId, legalEntityId)
                    .eq(InvTransaction::getWarehouseCode, warehouseCode)
                    .eq(InvTransaction::getMaterialId, materialId));
            BigDecimal qty = BigDecimal.ZERO;
            for (InvTransaction txn : txns) {
                qty = qty.add(txn.getQty());
            }
            return qty;
        }
        return cache.getQtyAvailable();
    }

    /** 缓存刷新（事务驱动；TTL 5 分钟口径 00 册 C-04） */
    private void refreshCache(InvTransaction txn) {
        InvStockCache cache = stockCacheMapper.selectOne(new LambdaQueryWrapper<InvStockCache>()
                .eq(InvStockCache::getLegalEntityId, txn.getLegalEntityId())
                .eq(InvStockCache::getWarehouseCode, txn.getWarehouseCode())
                .eq(InvStockCache::getMaterialId, txn.getMaterialId())
                .last("LIMIT 1"));
        if (cache == null) {
            cache = new InvStockCache();
            cache.setLegalEntityId(txn.getLegalEntityId());
            cache.setWarehouseCode(txn.getWarehouseCode());
            cache.setMaterialId(txn.getMaterialId());
            cache.setQtyOnHand(BigDecimal.ZERO);
            cache.setQtyAllocated(BigDecimal.ZERO);
            cache.setQtyAvailable(BigDecimal.ZERO);
            cache.setBatchNo(txn.getBatchNo());
        }
        cache.setQtyOnHand(cache.getQtyOnHand().add(txn.getQty()));
        cache.setQtyAvailable(cache.getQtyAvailable().add(txn.getQty()));
        cache.setCachedAt(LocalDateTime.now());
        cache.setTtlSeconds(300);
        if (cache.getId() == null) {
            stockCacheMapper.insert(cache);
        } else {
            stockCacheMapper.updateById(cache);
        }
    }

    private Map<String, Object> txnOf(InvTransaction txn) {
        Map<String, Object> map = new HashMap<>();
        map.put("txnNo", txn.getTxnNo());
        map.put("txnType", txn.getTxnType());
        map.put("materialId", txn.getMaterialId());
        map.put("qty", txn.getQty());
        map.put("warehouseCode", txn.getWarehouseCode());
        return map;
    }

    /** FR-INV-41：每日对账（23:00 job 入口；MOCK 模式下 T+ 笔数=SYNCED 笔数） */
    @Transactional
    public com.projlife.inventory.entity.InvReconcile dailyReconcile(LocalDate date, Long legalEntityId) {
        com.projlife.inventory.entity.InvReconcile reconcile = new com.projlife.inventory.entity.InvReconcile();
        reconcile.setReconcileDate(date);
        reconcile.setLegalEntityId(legalEntityId);
        List<InvTransaction> dayTxns = transactionMapper.selectList(new LambdaQueryWrapper<InvTransaction>()
                .apply("DATE(occurred_at) = {0}", date)
                .eq(InvTransaction::getLegalEntityId, legalEntityId));
        reconcile.setLocalTxnCount(dayTxns.size());
        long synced = dayTxns.stream().filter(t -> "SYNCED".equals(t.getSyncStatus())).count();
        reconcile.setTPlusTxnCount((int) synced);
        long exceptions = dayTxns.size() - synced;
        reconcile.setDiffCount((int) exceptions);
        reconcile.setDiffAmount(dayTxns.stream()
                .filter(t -> !"SYNCED".equals(t.getSyncStatus()))
                .map(t -> t.getAmount() == null ? java.math.BigDecimal.ZERO : t.getAmount())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        reconcile.setStatus(exceptions == 0 ? "CLOSED" : "OPEN");
        return reconcile;
    }
}
