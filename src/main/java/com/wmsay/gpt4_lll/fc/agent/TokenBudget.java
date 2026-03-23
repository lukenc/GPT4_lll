package com.wmsay.gpt4_lll.fc.agent;

/**
 * Token 预算分配 — 不可变对象，Builder 模式。
 * 各区段比例之和须为 1.0±0.01。
 */
public class TokenBudget {

    private final int totalTokens;
    private final double systemPromptRatio;
    private final double knowledgeRatio;
    private final double historyRatio;
    private final double toolsRatio;

    private TokenBudget(Builder builder) {
        this.totalTokens = builder.totalTokens;
        this.systemPromptRatio = builder.systemPromptRatio;
        this.knowledgeRatio = builder.knowledgeRatio;
        this.historyRatio = builder.historyRatio;
        this.toolsRatio = builder.toolsRatio;
    }

    public int getTotalTokens() { return totalTokens; }
    public double getSystemPromptRatio() { return systemPromptRatio; }
    public double getKnowledgeRatio() { return knowledgeRatio; }
    public double getHistoryRatio() { return historyRatio; }
    public double getToolsRatio() { return toolsRatio; }

    public int getBudgetFor(PromptSection section) {
        double ratio;
        switch (section) {
            case SYSTEM_PROMPT: ratio = systemPromptRatio; break;
            case KNOWLEDGE: ratio = knowledgeRatio; break;
            case HISTORY: ratio = historyRatio; break;
            case TOOLS: ratio = toolsRatio; break;
            default: ratio = 0;
        }
        return (int) (totalTokens * ratio);
    }

    public static TokenBudget defaultBudget() { return builder().build(); }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int totalTokens = 120000;
        private double systemPromptRatio = 0.15;
        private double knowledgeRatio = 0.20;
        private double historyRatio = 0.45;
        private double toolsRatio = 0.20;

        public Builder totalTokens(int v) { this.totalTokens = v; return this; }
        public Builder systemPromptRatio(double v) { this.systemPromptRatio = v; return this; }
        public Builder knowledgeRatio(double v) { this.knowledgeRatio = v; return this; }
        public Builder historyRatio(double v) { this.historyRatio = v; return this; }
        public Builder toolsRatio(double v) { this.toolsRatio = v; return this; }

        public TokenBudget build() {
            double sum = systemPromptRatio + knowledgeRatio + historyRatio + toolsRatio;
            if (Math.abs(sum - 1.0) > 0.01) {
                throw new IllegalArgumentException(
                    "Token budget ratios must sum to 1.0 (±0.01), got " + sum);
            }
            return new TokenBudget(this);
        }
    }
}
