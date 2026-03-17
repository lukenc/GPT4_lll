package com.wmsay.gpt4_lll.fc.memory;

/**
 * 从 LLM API 响应 usage 字段提取的真实 token 使用量数据。
 * 不可变数据类。
 */
public class TokenUsageInfo {

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public TokenUsageInfo(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
}
