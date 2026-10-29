package com.projlife.inventory.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * T+ MOCK 适配器（S4-5）：恒成功，生成 mock 凭证号；业务链路（幂等/状态机/对账）与真实适配完全一致。
 */
@Component
public class MockTPlusAdapter implements TPlusAdapter {

    @Value("${projlife.tplus.mode:MOCK}")
    private String mode;

    @Override
    public Map<String, Object> pushTransaction(Map<String, Object> transaction) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("voucherNo", "TP-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        result.put("mode", mode);
        return result;
    }

    @Override
    public String mode() {
        return mode;
    }
}
