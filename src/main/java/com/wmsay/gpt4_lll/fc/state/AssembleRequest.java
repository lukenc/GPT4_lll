package com.wmsay.gpt4_lll.fc.state;

import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.runtime.KnowledgeBase;
import com.wmsay.gpt4_lll.fc.tools.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ContextManager 组装请求 — Builder 模式。
 */
public class AssembleRequest {

    private final String systemPrompt;
    private final String userMessage;
    private final ConversationMemory memory;
    private final KnowledgeBase knowledgeBase;
    private final List<Tool> filteredTools;
    private final TokenBudget tokenBudget;

    private AssembleRequest(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.userMessage = builder.userMessage;
        this.memory = builder.memory;
        this.knowledgeBase = builder.knowledgeBase;
        this.filteredTools = builder.filteredTools == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(builder.filteredTools));
        this.tokenBudget = builder.tokenBudget;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserMessage() { return userMessage; }
    public ConversationMemory getMemory() { return memory; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public List<Tool> getFilteredTools() { return filteredTools; }
    public TokenBudget getTokenBudget() { return tokenBudget; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String systemPrompt;
        private String userMessage;
        private ConversationMemory memory;
        private KnowledgeBase knowledgeBase;
        private List<Tool> filteredTools;
        private TokenBudget tokenBudget;

        public Builder systemPrompt(String v) { this.systemPrompt = v; return this; }
        public Builder userMessage(String v) { this.userMessage = v; return this; }
        public Builder memory(ConversationMemory v) { this.memory = v; return this; }
        public Builder knowledgeBase(KnowledgeBase v) { this.knowledgeBase = v; return this; }
        public Builder filteredTools(List<Tool> v) { this.filteredTools = v; return this; }
        public Builder tokenBudget(TokenBudget v) { this.tokenBudget = v; return this; }

        public AssembleRequest build() { return new AssembleRequest(this); }
    }
}
