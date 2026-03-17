package com.wmsay.gpt4_lll.fc.memory;

/**
 * 记忆统计快照。不可变对象。
 * <p>
 * 包含当前消息数、真实 prompt_tokens（-1 表示未知）、历史裁剪次数和历史摘要次数。
 */
public class MemoryStats {

    private final int messageCount;
    private final int realPromptTokens;   // 真实 prompt_tokens，-1 表示未知
    private final int trimCount;
    private final int summarizeCount;

    public MemoryStats(int messageCount, int realPromptTokens, int trimCount, int summarizeCount) {
        this.messageCount = messageCount;
        this.realPromptTokens = realPromptTokens;
        this.trimCount = trimCount;
        this.summarizeCount = summarizeCount;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public int getRealPromptTokens() {
        return realPromptTokens;
    }

    public int getTrimCount() {
        return trimCount;
    }

    public int getSummarizeCount() {
        return summarizeCount;
    }
}
