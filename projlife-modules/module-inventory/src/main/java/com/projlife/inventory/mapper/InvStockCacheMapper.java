package com.projlife.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.projlife.inventory.entity.InvStockCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 库存缓存 Mapper（SPRINT-SEC1 F4）：
 * 出/入库对 qty 的全部变更走原子 UPDATE（根除读-改-写竞态 TOCTOU）。
 */
@Mapper
public interface InvStockCacheMapper extends BaseMapper<InvStockCache> {

    /** 条件扣减（出库）：仅当可用量足够时原子扣减，返回 1=成功 / 0=不足或无行 */
    @Update("UPDATE inv_stock_cache SET qty_on_hand = qty_on_hand - #{qty}, "
            + "qty_available = qty_available - #{qty}, cached_at = NOW(), ttl_seconds = 300 "
            + "WHERE legal_entity_id = #{legalEntityId} AND warehouse_code = #{warehouseCode} "
            + "AND material_id = #{materialId} AND qty_available >= #{qty} AND deleted = 0")
    int deductIfAvailable(@Param("legalEntityId") Long legalEntityId,
                          @Param("warehouseCode") String warehouseCode,
                          @Param("materialId") Long materialId,
                          @Param("qty") BigDecimal qty);

    /** 原子增量（入库/调整）：对既有行安全并发（替换读-改-写） */
    @Update("UPDATE inv_stock_cache SET qty_on_hand = qty_on_hand + #{dQty}, "
            + "qty_available = qty_available + #{dQty}, cached_at = NOW(), ttl_seconds = 300 "
            + "WHERE id = #{id}")
    int addQty(@Param("id") Long id, @Param("dQty") BigDecimal dQty);
}
