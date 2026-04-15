package com.wmsay.gpt4_lll.fc.state;

import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.runtime.KnowledgeBase;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 上下文管理器 — 认知/模型层。
 * 负责组装最终发送给 LLM 的 prompt，控制 token 用量。
 * <p>
 * 线程安全：assemblePrompt 无共享可变状态，可并发调用。
 * 通过 ObservabilityManager 记录组装统计（需求 19.12）。
 */
public class ContextManager {

    private static final int CHARS_PER_TOKEN = 4;

    /** 基础工具集合 — 裁剪时优先保留 */
    private static final Set<String> BASE_TOOLS = Set.of(
            "keyword_search", "read_file", "write_file", "shell_exec", "project_tree");

    private ObservabilityManager observability;
    private KnowledgeBase knowledgeBase;

    public ContextManager() {}

    public ContextManager(ObservabilityManager observability) {
        this.observability = observability;
    }

    public void setObservability(ObservabilityManager obs) {
        this.observability = obs;
    }

    public void setKnowledgeBase(KnowledgeBase kb) {
        this.knowledgeBase = kb;
    }

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    /**
     * 组装最终发送给 LLM 的 prompt（需求 19.1）。
     * <p>
     * 流程：收集各区段内容 → 估算 token → 超预算时按优先级裁剪 → 组装结果。
     * 裁剪顺序：TOOLS → HISTORY → KNOWLEDGE，SYSTEM_PROMPT 不裁剪（需求 19.3）。
     */
    public AssembleResult assemblePrompt(AssembleRequest request) {
        TokenBudget budget = request.getTokenBudget() != null
                ? request.getTokenBudget() : TokenBudget.defaultBudget();

        // 1. 收集各区段原始内容
        String systemPrompt = request.getSystemPrompt() != null ? request.getSystemPrompt() : "";
        KnowledgeBase kb = request.getKnowledgeBase() != null
                ? request.getKnowledgeBase() : this.knowledgeBase;
        String knowledgeContent = kb != null ? kb.buildKnowledgePrompt() : "";
        String historyContent = buildHistoryContent(request.getMemory());
        String toolDescriptions = buildToolDescriptions(request.getFilteredTools());

        // 2. 估算各区段 token
        int sysTokens = estimateTokenCount(systemPrompt);
        int knowledgeTokens = estimateTokenCount(knowledgeContent);
        int historyTokens = estimateTokenCount(historyContent);
        int toolsTokens = estimateTokenCount(toolDescriptions);
        boolean trimmed = false;

        int totalEstimate = sysTokens + knowledgeTokens + historyTokens + toolsTokens;

        // 3. 超预算时按优先级裁剪：TOOLS → HISTORY → KNOWLEDGE
        if (totalEstimate > budget.getTotalTokens()) {
            trimmed = true;

            // 3a. 先裁剪工具描述
            int toolsBudget = budget.getBudgetFor(PromptSection.TOOLS);
            if (toolsTokens > toolsBudget) {
                toolDescriptions = trimToolDescriptions(
                        request.getFilteredTools(), toolsBudget);
                toolsTokens = estimateTokenCount(toolDescriptions);
            }

            // 3b. 再裁剪对话历史
            int remaining = budget.getTotalTokens() - sysTokens - knowledgeTokens - toolsTokens;
            if (historyTokens > remaining && remaining > 0) {
                historyContent = trimText(historyContent, remaining);
                historyTokens = estimateTokenCount(historyContent);
            } else if (remaining <= 0) {
                historyContent = "";
                historyTokens = 0;
            }

            // 3c. 最后裁剪知识注入
            int remainingAfterHistory = budget.getTotalTokens() - sysTokens - historyTokens - toolsTokens;
            if (knowledgeTokens > remainingAfterHistory && remainingAfterHistory > 0) {
                knowledgeContent = trimText(knowledgeContent, remainingAfterHistory);
                knowledgeTokens = estimateTokenCount(knowledgeContent);
            } else if (remainingAfterHistory <= 0) {
                knowledgeContent = "";
                knowledgeTokens = 0;
            }
        }

        // 4. 组装最终 prompt 字符串
        StringBuilder assembled = new StringBuilder();
        assembled.append(systemPrompt);
        if (!knowledgeContent.isEmpty()) {
            assembled.append("\n\n").append(knowledgeContent);
        }
        if (!historyContent.isEmpty()) {
            assembled.append("\n\n").append(historyContent);
        }
        if (!toolDescriptions.isEmpty()) {
            assembled.append("\n\n").append(toolDescriptions);
        }

        // 添加用户消息
        String userMessage = request.getUserMessage();
        if (userMessage != null && !userMessage.isEmpty()) {
            assembled.append("\n\n").append(userMessage);
        }

        String chatContent = assembled.toString();
        int totalTokens = sysTokens + knowledgeTokens + historyTokens + toolsTokens;

        return new AssembleResult(chatContent, sysTokens, knowledgeTokens,
                historyTokens, toolsTokens, totalTokens, trimmed);
    }

    /**
     * 估算文本的 token 数量（需求 19.9）。
     * 使用 chars/4 启发式估算。
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / CHARS_PER_TOKEN;
    }

    // ---- 内部辅助方法 ----

    /**
     * 从 ConversationMemory 获取对话历史并格式化为文本（需求 19.4）。
     */
    private String buildHistoryContent(ConversationMemory memory) {
        if (memory == null) {
            return "";
        }
        List<Message> messages = memory.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg.getRole() != null && msg.getContent() != null) {
                sb.append("[").append(msg.getRole()).append("]: ")
                  .append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 构建工具描述文本（需求 19.6）。
     */
    private String buildToolDescriptions(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools) {
            sb.append("### ").append(tool.name()).append("\n");
            sb.append(tool.description()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 裁剪工具描述：优先保留 BASE_TOOLS，再按顺序添加其他工具直到预算用尽。
     */
    private String trimToolDescriptions(List<Tool> tools, int budgetTokens) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        List<Tool> base = tools.stream()
                .filter(t -> BASE_TOOLS.contains(t.name()))
                .collect(Collectors.toList());
        List<Tool> others = tools.stream()
                .filter(t -> !BASE_TOOLS.contains(t.name()))
                .collect(Collectors.toList());

        List<Tool> kept = new ArrayList<>(base);
        int currentTokens = estimateTokenCount(buildToolDescriptions(kept));

        for (Tool tool : others) {
            String desc = "### " + tool.name() + "\n" + tool.description() + "\n\n";
            int toolTokens = estimateTokenCount(desc);
            if (currentTokens + toolTokens <= budgetTokens) {
                kept.add(tool);
                currentTokens += toolTokens;
            }
        }
        return buildToolDescriptions(kept);
    }

    /**
     * 按 token 预算裁剪文本。
     */
    private String trimText(String text, int budgetTokens) {
        int maxChars = budgetTokens * CHARS_PER_TOKEN;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}
