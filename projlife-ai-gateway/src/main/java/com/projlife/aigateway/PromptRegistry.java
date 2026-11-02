package com.projlife.aigateway;

import org.springframework.stereotype.Component;

/**
 * Prompt 注册表（13 册 §2 契约）：按 AI 接口编号给出系统提示词与输出 JSON 契约说明。
 * 真实模型（OpenAI 兼容 chat/completions）与 mock 返回同一套数据结构，业务侧无感切换。
 */
@Component
public class PromptRegistry {

    /** 是否为已定义契约的编号 */
    public boolean defined(String aiCode) {
        return systemPrompt(aiCode) != null;
    }

    /** 按编号返回系统提示词；未定义编号返回 null（走通用提示词） */
    public String systemPrompt(String aiCode) {
        switch (aiCode) {
            case "AI-001":
                // 合同风险分析（13 册 §2.1）
                return "你是风电工程合同评审专家。分析用户提供的合同文本或要素，识别合同风险。"
                        + "只输出一个 JSON 对象，不要输出任何其他文字或 markdown 代码块标记，结构如下："
                        + "{\"summary\":\"整体风险概述(50字内)\",\"risks\":["
                        + "{\"level\":\"RED|YELLOW|GREEN\",\"clause\":\"条款名\",\"description\":\"风险描述\",\"suggestion\":\"整改建议或null\"}"
                        + "]}。risks 必须恰好 3 条（红/黄/绿各至少覆盖一条，重大失衡为 RED，需关注为 YELLOW，权责对等为 GREEN）。"
                        + "另在 JSON 顶层附 \"confidence\":0到1之间的小数表示你对该分析的确信度。";
            case "AI-002":
                // 合同 12 要素信息提取（S1-9）
                return "你是合同信息抽取引擎。从用户提供的合同文本中抽取 12 个要素。"
                        + "只输出一个 JSON 对象，不要输出任何其他文字或 markdown 代码块标记，结构如下："
                        + "{\"fields\":{\"contract_name\":\"合同名称\",\"customer\":\"客户/业主\",\"amount\":\"金额(纯数字字符串)\","
                        + "\"sign_date\":\"签订日期 yyyy-MM-dd\",\"duration\":\"工期/期限\",\"payment_terms\":\"付款条件\","
                        + "\"quality_standard\":\"质量标准\",\"penalty\":\"违约条款\",\"warranty_months\":\"质保期(月,数字字符串)\","
                        + "\"dispute_resolution\":\"争议解决\",\"party_a\":\"甲方\",\"party_b\":\"乙方\"}}。"
                        + "文本中不存在的要素填空字符串。"
                        + "另在 JSON 顶层附 \"confidence\":0到1之间的小数表示抽取确信度。";
            default:
                return null;
        }
    }

    /** 未定义编号的通用提示词（保持 mock 的回显语义：返回结构化 JSON） */
    public String genericPrompt(String aiCode) {
        return "你是 ProjLife 企业业务管理系统的 AI 分析组件，接口编号 " + aiCode + "。"
                + "基于用户提供的业务数据进行分析。"
                + "只输出一个 JSON 对象，不要输出任何其他文字或 markdown 代码块标记，结构如下："
                + "{\"analysis\":\"分析结论\",\"points\":[\"要点1\",\"要点2\"],"
                + "\"confidence\":0到1之间的小数}。";
    }
}
