package com.wmsay.gpt4_lll.fc.skill;

/**
 * Skill 复杂度枚举 — 决定执行路径。
 * <ul>
 *   <li>SIMPLE: 单步工具调用、简单查询，不需要独立上下文 → Inline_Execution</li>
 *   <li>MODERATE: 多步骤协调、特定工具集、专业化 systemPrompt → Sub_Agent</li>
 *   <li>COMPLEX: 独立上下文隔离、深度推理链、大量工具协调 → Sub_Agent</li>
 * </ul>
 */
public enum SkillComplexity {
    SIMPLE,
    MODERATE,
    COMPLEX;

    /**
     * 从字符串解析复杂度级别。
     * null 或空字符串默认返回 MODERATE，大小写不敏感。
     */
    public static SkillComplexity fromString(String value) {
        if (value == null || value.isBlank()) {
            return MODERATE;
        }
        return valueOf(value.toUpperCase());
    }

    /**
     * 是否需要创建子 Agent 执行。
     * MODERATE 和 COMPLEX 返回 true，SIMPLE 返回 false。
     */
    public boolean requiresSubAgent() {
        return this == MODERATE || this == COMPLEX;
    }
}
