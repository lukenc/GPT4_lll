package com.wmsay.gpt4_lll.fc.skill;

/**
 * Skill 匹配结果 — 不可变对象。
 * 使用静态工厂方法构建。
 */
public class SkillMatchResult {
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;

    private final boolean matched;
    private final String skillName;
    private final double confidence;
    private final String reasoning;

    private SkillMatchResult(boolean matched, String skillName, double confidence, String reasoning) {
        this.matched = matched;
        this.skillName = skillName;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }

    /**
     * 创建匹配成功的结果。
     */
    public static SkillMatchResult matched(String skillName, double confidence, String reasoning) {
        return new SkillMatchResult(true, skillName, confidence, reasoning);
    }

    /**
     * 创建未匹配的结果。
     */
    public static SkillMatchResult unmatched(String reasoning) {
        return new SkillMatchResult(false, null, 0.0, reasoning);
    }

    /**
     * 等价于 matched == true && confidence >= DEFAULT_CONFIDENCE_THRESHOLD。
     */
    public boolean isMatched() {
        return matched && confidence >= DEFAULT_CONFIDENCE_THRESHOLD;
    }

    /**
     * 返回当前配置的置信度阈值。
     */
    public static double getConfidenceThreshold() {
        return DEFAULT_CONFIDENCE_THRESHOLD;
    }

    public String getSkillName() { return skillName; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
}
