package com.projlife.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.projlife.inventory.entity.InvTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis-Plus Mapper：InvTransaction */
@Mapper
public interface InvTransactionMapper extends BaseMapper<InvTransaction> {

    /** SPRINT-OPS1 F15：台账求和聚合（DB 端 SUM，替代全量加载实体） */
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(qty), 0) FROM inv_transaction "
            + "WHERE legal_entity_id = #{legalEntityId} AND warehouse_code = #{warehouseCode} "
            + "AND material_id = #{materialId} AND deleted = 0")
    java.math.BigDecimal sumQtyBy(@Param("legalEntityId") Long legalEntityId,
                                  @Param("warehouseCode") String warehouseCode,
                                  @Param("materialId") Long materialId);
}