package com.wmsay.gpt4_lll.fc.agent;

/**
 * ContextManager 组装结果。
 * 包含组装后的 chatContent 和各区段 token 统计。
 */
public class AssembleResult {

    private final String chatContent;
    private final int systemPromptTokens;
    private final int knowledgeTokens;
    private final int historyTokens;
    private final int toolsTokens;
    private final int totalTokens;
    private final boolean trimmed;

    public AssembleResult(String chatContent, int systemPromptTokens, int knowledgeTokens,
                          int historyTokens, int toolsTokens, int totalTokens, boolean trimmed) {
        this.chatContent = chatContent;
        this.systemPromptTokens = systemPromptTokens;
        this.knowledgeTokens = knowledgeTokens;
        this.historyTokens = historyTokens;
        this.toolsTokens = toolsTokens;
        this.totalTokens = totalTokens;
        this.trimmed = trimmed;
    }

    public String getChatContent() { return chatContent; }
    public int getSystemPromptTokens() { return systemPromptTokens; }
    public int getKnowledgeTokens() { return knowledgeTokens; }
    public int getHistoryTokens() { return historyTokens; }
    public int getToolsTokens() { return toolsTokens; }
    public int getTotalTokens() { return totalTokens; }
    public boolean isTrimmed() { return trimmed; }
}
