package com.projlife.aigateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt 注册表单测：已定义编号出契约提示词，未知编号走通用提示词（保持 mock 回显语义）。
 */
class PromptRegistryTest {

    private final PromptRegistry registry = new PromptRegistry();

    @Test
    @DisplayName("AI-001/AI-002 有契约提示词且含 JSON 结构约束")
    void definedPrompts() {
        for (String code : new String[]{"AI-001", "AI-002"}) {
            String prompt = registry.systemPrompt(code);
            assertNotNull(prompt, code + " 应有契约提示词");
            assertTrue(prompt.contains("JSON"), code + " 提示词应约束 JSON 输出");
            assertTrue(prompt.contains("confidence"), code + " 提示词应要求置信度");
        }
        assertTrue(registry.defined("AI-001"));
    }

    @Test
    @DisplayName("未知编号 systemPrompt 为 null，走通用提示词")
    void unknownCodeGeneric() {
        assertNull(registry.systemPrompt("AI-999"));
        assertFalse(registry.defined("AI-999"));
        String generic = registry.genericPrompt("AI-999");
        assertNotNull(generic);
        assertTrue(generic.contains("AI-999"));
        assertTrue(generic.contains("JSON"));
    }
}
