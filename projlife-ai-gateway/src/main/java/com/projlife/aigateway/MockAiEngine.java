package com.projlife.aigateway;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 引擎（S0-13 完成标准：关掉模型不阻塞业务，mock 跑通全流程）。
 * 按 AI 接口编号返回符合 13 册契约结构的模拟结果；真实模型接入仅替换 route() 内部实现（S9）。
 */
@Component
public class MockAiEngine {

    private static final String MOCK_VERSION = "mock-1.0.0";

    public boolean supports(String aiCode) {
        return mockOf(aiCode) != null;
    }

    public MockResult invoke(String aiCode, String bizType, Object payload) {
        Object data = mockOf(aiCode);
        if (data == null) {
            data = defaultEcho(payload);
        }
        return new MockResult(data, mockConfidence(aiCode), MOCK_VERSION, "p-" + aiCode.toLowerCase() + "-1.0");
    }

    private double mockConfidence(String aiCode) {
        switch (aiCode) {
            case "AI-001":
                return 0.86;
            case "AI-002":
                return 0.92;
            default:
                return 0.80;
        }
    }

    /** 按接口编号构造契约结构 mock */
    private Object mockOf(String aiCode) {
        switch (aiCode) {
            case "AI-001": {
                // 合同风险分析：返回 3 条红黄绿（13 册 §2.1）
                List<Map<String, Object>> risks = new ArrayList<>();
                risks.add(risk("RED", "付款条款", "回款比例与验收节点绑定不足，存在垫资风险", "建议增加验收后付款比例下限"));
                risks.add(risk("YELLOW", "工期条款", "未约定风场进场条件顺延机制", "建议补充不可抗力顺延条款"));
                risks.add(risk("GREEN", "违约责任", "双方权责对等，未见明显失衡", null));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("summary", "合同整体风险中等，重点关注付款条款");
                result.put("risks", risks);
                return result;
            }
            case "AI-002": {
                // 合同信息提取：12 要素 mock（S1-9 使用）
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("contract_name", "demo 风场安装服务合同");
                fields.put("customer", "demo 业主公司");
                fields.put("amount", "1000000.00");
                fields.put("sign_date", "2026-10-29");
                fields.put("duration", "12 个月");
                fields.put("payment_terms", "3-4-3");
                fields.put("quality_standard", "国标 GB");
                fields.put("penalty", "日千分之一");
                fields.put("warranty_months", "24");
                fields.put("dispute_resolution", "原告所在地法院");
                fields.put("party_a", "demo 业主公司");
                fields.put("party_b", "demo 承包公司");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("fields", fields);
                return result;
            }
            default:
                return null;
        }
    }

    private Map<String, Object> risk(String level, String clause, String desc, String suggestion) {
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("level", level);
        risk.put("clause", clause);
        risk.put("description", desc);
        risk.put("suggestion", suggestion);
        return risk;
    }

    private Object defaultEcho(Object payload) {
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("echo", payload);
        echo.put("note", "默认 mock 回显：该 AI 编号尚未定义契约");
        return echo;
    }

    /** mock 结果载体 */
    public static class MockResult {

        public final Object data;
        public final double confidence;
        public final String modelVersion;
        public final String promptVersion;

        public MockResult(Object data, double confidence, String modelVersion, String promptVersion) {
            this.data = data;
            this.confidence = confidence;
            this.modelVersion = modelVersion;
            this.promptVersion = promptVersion;
        }
    }
}
