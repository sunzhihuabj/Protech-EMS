package com.projlife.inventory.api;

import java.util.Map;

/**
 * T+ 适配器接口（00 册 C-04：T+ 是库存唯一权威；14 册 FILE/MOCK/API 三模式）。
 * S4 交付 MOCK 模式（恒成功回写凭证号）；真实 T+ 接口在 S9/集成批次接入。
 */
public interface TPlusAdapter {

    /**
     * 推送库存事务到 T+（生成对应出入库单）。
     *
     * @param transaction 事务数据（txnType/materialId/qty/warehouseCode...）
     * @return {success: boolean, voucherNo: String, error: String}
     */
    Map<String, Object> pushTransaction(Map<String, Object> transaction);

    /** 适配器模式：MOCK/FILE/API */
    String mode();
}
