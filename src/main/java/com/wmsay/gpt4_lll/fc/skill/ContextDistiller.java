package com.wmsay.gpt4_lll.fc.skill;

import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 上下文蒸馏器。
 * 通过 Sidecar LLM 调用从主 Agent 的对话历史中提炼子 Agent 所需的关键上下文。
 * <p>
 * 所有 LLM 调用均为独立的 Sidecar 调用，不写入主 Agent 的 ConversationMemory。
 * 当 Sidecar LLM 调用失败时，降级为截取最近 N 条消息作为上下文。
 */
public class ContextDistiller {

    private static final Logger LOG = Logger.getLogger(ContextDistiller.class.getName());

    /** 默认降级时截取的消息数量 */
    private static final int DEFAULT_FALLBACK_MESSAGE_COUNT = 5;

    private static final String SYSTEM_PROMPT =
        "You are a context distiller. Your task is to analyze a conversation history and extract " +
        "the key context relevant to a specific skill that is about to be executed.\n\n" +
        "Skill purpose: %s\n" +
        "Skill trigger: %s\n\n" +
        "%s" +
        "From the conversation history below, extract and summarize ONLY the information that is " +
        "relevant to this skill's execution. Include:\n" +
        "- The user's core intent and requirements\n" +
        "- Any specific parameters, file paths, or technical details mentioned\n" +
        "- Relevant decisions or constraints established earlier in the conversation\n" +
        "- Key context from previous tool executions if relevant\n\n" +
        "Output a concise context summary (no more than 500 words). " +
        "Do NOT include irrelevant conversation turns or small talk.";

    /**
     * 从主 Agent 的对话历史中蒸馏出子 Agent 所需的关键上下文。
     * <p>
     * 通过 Sidecar LLM 调用完成蒸馏，不写入主 Agent 的 ConversationMemory。
     * 当 LLM 调用失败时，降级为截取最近 N 条消息（N 由 fallbackMessageCount 指定）。
     *
     * @param mainMemory           主 Agent 的 ConversationMemory（只读，不写入）
     * @param skillPurpose         匹配到的 Skill 的 purpose
     * @param skillTrigger         匹配到的 Skill 的 trigger
     * @param stepsContext          PlanAndExecute 场景下已完成步骤的上下文，可为 null
     * @param llmCaller            LLM 调用器
     * @param modelName            模型名称
     * @param fallbackMessageCount 降级时截取的消息数量
     * @return 蒸馏后的上下文摘要文本
     */
    public String distill(ConversationMemory mainMemory,
                          String skillPurpose,
                          String skillTrigger,
                          String stepsContext,
                          LlmCaller llmCaller,
                          String modelName,
                          int fallbackMessageCount) {
        if (mainMemory == null || mainMemory.size() == 0) {
            LOG.info("[ContextDistiller] Empty conversation memory, returning empty context");
            return "";
        }

        try {
            return distillViaLlm(mainMemory, skillPurpose, skillTrigger, stepsContext, llmCaller, modelName);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[ContextDistiller] Sidecar LLM call failed, falling back to recent messages: " + e.getMessage(), e);
            return fallbackExtract(mainMemory, fallbackMessageCount);
        }
    }

    /**
     * 使用默认降级消息数量（5）的便捷重载方法。
     */
    public String distill(ConversationMemory mainMemory,
                          String skillPurpose,
                          String skillTrigger,
                          String stepsContext,
                          LlmCaller llmCaller,
                          String modelName) {
        return distill(mainMemory, skillPurpose, skillTrigger, stepsContext,
                       llmCaller, modelName, DEFAULT_FALLBACK_MESSAGE_COUNT);
    }

    /**
     * 通过 Sidecar LLM 调用蒸馏上下文。
     * 构建独立的消息列表和请求，不写入主 Agent 的 ConversationMemory。
     */
    private String distillViaLlm(ConversationMemory mainMemory,
                                  String skillPurpose,
                                  String skillTrigger,
                                  String stepsContext,
                                  LlmCaller llmCaller,
                                  String modelName) {
        // 构建 stepsContext 段落（PlanAndExecute 场景）
        String stepsSection = "";
        if (stepsContext != null && !stepsContext.isBlank()) {
            stepsSection = "Previous execution steps context:\n" + stepsContext + "\n\n";
        }

        String systemPrompt = String.format(SYSTEM_PROMPT,
                skillPurpose != null ? skillPurpose : "",
                skillTrigger != null ? skillTrigger : "",
                stepsSection);

        // 构建对话历史文本
        String conversationText = buildConversationText(mainMemory);

        // 构建独立的 sidecar 消息列表（与 SkillMatcher 模式一致）
        List<Message> messages = new ArrayList<>();

        Message sysMsg = new Message();
        sysMsg.setRole("system");
        sysMsg.setContent(systemPrompt);
        messages.add(sysMsg);

        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent("Conversation history:\n\n" + conversationText);
        messages.add(userMsg);

        ChatContent chatContent = new ChatContent();
        chatContent.setDirectMessages(messages);
        chatContent.setStream(false);
        if (modelName != null && !modelName.isBlank()) {
            chatContent.setModel(modelName);
        }

        FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(1)
                .config(FunctionCallConfig.builder().build())
                .build();

        LOG.info("[ContextDistiller] Calling LLM for context distillation...");
        String response = llmCaller.call(request);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("Empty response from LLM during context distillation");
        }

        LOG.info("[ContextDistiller] Context distillation completed (" + response.length() + " chars)");
        return response.trim();
    }

    /**
     * 将 ConversationMemory 中的消息格式化为文本，供 LLM 分析。
     */
    private String buildConversationText(ConversationMemory memory) {
        List<Message> allMessages = memory.getMessages();
        StringBuilder sb = new StringBuilder();
        for (Message msg : allMessages) {
            String role = msg.getRole() != null ? msg.getRole() : "unknown";
            String content = msg.getContent() != null ? msg.getContent() : "";
            sb.append("[").append(role).append("]: ").append(content).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 降级策略：从 ConversationMemory 中提取最近 N 条消息作为上下文。
     * 提取数量为 min(fallbackMessageCount, memory.size())。
     *
     * @param memory               主 Agent 的 ConversationMemory
     * @param fallbackMessageCount 截取的消息数量上限
     * @return 格式化的最近消息文本
     */
    String fallbackExtract(ConversationMemory memory, int fallbackMessageCount) {
        int count = Math.max(fallbackMessageCount, 0);
        List<Message> allMessages = memory.getMessages();
        int total = allMessages.size();
        int take = Math.min(count, total);

        if (take == 0) {
            return "";
        }

        List<Message> recent = allMessages.subList(total - take, total);
        LOG.info("[ContextDistiller] Fallback: extracting " + take + " recent messages from " + total + " total");

        StringBuilder sb = new StringBuilder();
        sb.append("Recent conversation context:\n\n");
        for (Message msg : recent) {
            String role = msg.getRole() != null ? msg.getRole() : "unknown";
            String content = msg.getContent() != null ? msg.getContent() : "";
            sb.append("[").append(role).append("]: ").append(content).append("\n\n");
        }
        return sb.toString().trim();
    }
}
