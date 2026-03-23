package com.wmsay.gpt4_lll.fc.memory;

import com.alibaba.fastjson.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.wmsay.gpt4_lll.model.Message;

import java.util.*;

/**
 * 滑动窗口记忆策略。
 * <p>
 * 保留 system prompt 和最近的消息，当 {@code lastKnownPromptTokens} 超过
 * {@code maxTokens} 时从最早非 system 消息开始移除。
 * 遵守 Tool_Call_Group 完整性约束：assistant 工具调用消息和所有对应的
 * tool 结果消息作为整体被保留或移除。
 * <p>
 * 裁剪决策完全依赖 {@code lastKnownPromptTokens}（真实值）。
 * {@code lastKnownPromptTokens == -1}（全新对话）时跳过预检查直接返回所有消息。
 * <p>
 * 线程安全：所有公开方法使用 synchronized 保护。
 */
public class SlidingWindowMemory implements ConversationMemory {

    private static final Logger LOG = Logger.getLogger(SlidingWindowMemory.class.getName());

    private final int maxTokens;
    private final int hardLimitTokens;
    private final List<Message> messages = new ArrayList<>();
    private volatile int lastKnownPromptTokens = -1;
    private int trimCount = 0;

    public SlidingWindowMemory(int maxTokens, int hardLimitTokens) {
        this.maxTokens = maxTokens;
        this.hardLimitTokens = hardLimitTokens;
    }

    @Override
    public synchronized void add(Message message) {
        if (message == null) {
            return;
        }
        messages.add(message);
    }

    @Override
    public synchronized void addAll(List<Message> messages) {
        if (messages == null) {
            return;
        }
        for (Message msg : messages) {
            if (msg != null) {
                this.messages.add(msg);
            }
        }
    }

    @Override
    public synchronized List<Message> getMessages() {
        if (messages.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>());
        }

        // Separate system and non-system messages
        List<Message> systemMessages = new ArrayList<>();
        List<Message> nonSystemMessages = new ArrayList<>();
        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else {
                nonSystemMessages.add(msg);
            }
        }

        // lastKnownPromptTokens == -1: skip pre-check, return all messages
        if (lastKnownPromptTokens == -1) {
            List<Message> result = new ArrayList<>(systemMessages);
            result.addAll(nonSystemMessages);
            return Collections.unmodifiableList(new ArrayList<>(result));
        }

        // lastKnownPromptTokens > 0 but within maxTokens: no trimming needed
        if (lastKnownPromptTokens <= maxTokens) {
            List<Message> result = new ArrayList<>(systemMessages);
            result.addAll(nonSystemMessages);
            return Collections.unmodifiableList(new ArrayList<>(result));
        }

        // Need to trim: lastKnownPromptTokens > maxTokens
        List<Message> trimmed = trimMessages(nonSystemMessages);
        trimCount++;
        int beforeCount = nonSystemMessages.size();
        int afterCount = trimmed.size();
        LOG.info("SlidingWindow trimmed: messages " + beforeCount + " -> " + afterCount
                + ", lastKnownPromptTokens=" + lastKnownPromptTokens + ", maxTokens=" + maxTokens);

        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(trimmed);

        // Hard limit check using heuristic estimation
        result = applyHardLimit(result, systemMessages);

        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    /**
     * Trim non-system messages from the oldest, respecting Tool_Call_Group integrity.
     * Uses heuristic token estimation (content.length() / 4) per message.
     * At least one non-system message is always preserved.
     */
    private List<Message> trimMessages(List<Message> nonSystemMessages) {
        if (nonSystemMessages.isEmpty()) {
            return new ArrayList<>(nonSystemMessages);
        }

        // Build groups: identify Tool_Call_Groups and standalone messages
        List<MessageGroup> groups = buildMessageGroups(nonSystemMessages);

        // Calculate how many tokens we need to remove
        int tokensToRemove = lastKnownPromptTokens - maxTokens;

        // Remove groups from the oldest until we've removed enough estimated tokens
        int removedTokens = 0;
        int removeUpToGroup = 0;

        for (int i = 0; i < groups.size(); i++) {
            // Always keep at least the last group
            if (i >= groups.size() - 1) {
                break;
            }
            if (removedTokens >= tokensToRemove) {
                break;
            }
            removedTokens += groups.get(i).estimatedTokens;
            removeUpToGroup = i + 1;
        }

        // Collect remaining messages
        List<Message> result = new ArrayList<>();
        for (int i = removeUpToGroup; i < groups.size(); i++) {
            result.addAll(groups.get(i).messages);
        }

        // Ensure at least one non-system message is kept
        if (result.isEmpty() && !nonSystemMessages.isEmpty()) {
            // Keep the last group
            result.addAll(groups.get(groups.size() - 1).messages);
        }

        return result;
    }

    /**
     * Apply hard limit check. If estimated total tokens still exceed hardLimitTokens,
     * force-remove oldest non-system messages.
     */
    private List<Message> applyHardLimit(List<Message> allMessages, List<Message> systemMessages) {
        int estimatedTotal = estimateTokens(allMessages);
        if (estimatedTotal <= hardLimitTokens) {
            return allMessages;
        }

        LOG.log(Level.WARNING, "Hard limit triggered: estimated " + estimatedTotal
                + " tokens exceeds hardLimit=" + hardLimitTokens);

        // Extract non-system messages from the combined list
        List<Message> nonSystem = new ArrayList<>();
        for (Message msg : allMessages) {
            if (!"system".equals(msg.getRole())) {
                nonSystem.add(msg);
            }
        }

        List<MessageGroup> groups = buildMessageGroups(nonSystem);
        int tokensToRemove = estimatedTotal - hardLimitTokens;
        int removedTokens = 0;
        int removeUpToGroup = 0;

        for (int i = 0; i < groups.size(); i++) {
            if (i >= groups.size() - 1) {
                break;
            }
            if (removedTokens >= tokensToRemove) {
                break;
            }
            removedTokens += groups.get(i).estimatedTokens;
            removeUpToGroup = i + 1;
        }

        List<Message> result = new ArrayList<>(systemMessages);
        for (int i = removeUpToGroup; i < groups.size(); i++) {
            result.addAll(groups.get(i).messages);
        }

        // Ensure at least one non-system message
        if (result.size() == systemMessages.size() && !groups.isEmpty()) {
            result.addAll(groups.get(groups.size() - 1).messages);
        }

        int afterEstimate = estimateTokens(result);
        LOG.log(Level.WARNING, "Hard limit applied: " + estimatedTotal + " -> " + afterEstimate + " estimated tokens");

        return result;
    }

    /**
     * Build message groups respecting Tool_Call_Group integrity.
     * A Tool_Call_Group consists of an assistant message with toolCalls
     * and all corresponding tool messages (matching tool_call_id).
     * Standalone messages form their own single-message group.
     */
    private List<MessageGroup> buildMessageGroups(List<Message> nonSystemMessages) {
        List<MessageGroup> groups = new ArrayList<>();

        // Collect tool_call_ids for each assistant message that has toolCalls
        // and track which tool messages belong to which assistant message
        Set<Integer> consumed = new HashSet<>();

        for (int i = 0; i < nonSystemMessages.size(); i++) {
            if (consumed.contains(i)) {
                continue;
            }

            Message msg = nonSystemMessages.get(i);

            if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                // This is the start of a Tool_Call_Group
                List<Message> groupMessages = new ArrayList<>();
                groupMessages.add(msg);
                consumed.add(i);

                // Extract tool_call IDs from the assistant's toolCalls
                Set<String> toolCallIds = extractToolCallIds(msg);

                // Find all corresponding tool messages after this assistant message
                for (int j = i + 1; j < nonSystemMessages.size(); j++) {
                    if (consumed.contains(j)) {
                        continue;
                    }
                    Message candidate = nonSystemMessages.get(j);
                    if ("tool".equals(candidate.getRole())
                            && candidate.getToolCallId() != null
                            && toolCallIds.contains(candidate.getToolCallId())) {
                        groupMessages.add(candidate);
                        consumed.add(j);
                    }
                }

                groups.add(new MessageGroup(groupMessages));
            } else {
                // Standalone message
                consumed.add(i);
                groups.add(new MessageGroup(Collections.singletonList(msg)));
            }
        }

        return groups;
    }

    /**
     * Extract tool_call IDs from an assistant message's toolCalls list.
     * The toolCalls list contains Objects that are actually JSONObjects with an "id" field.
     */
    private Set<String> extractToolCallIds(Message assistantMessage) {
        Set<String> ids = new HashSet<>();
        if (assistantMessage.getToolCalls() == null) {
            return ids;
        }
        for (Object toolCall : assistantMessage.getToolCalls()) {
            if (toolCall instanceof JSONObject) {
                String id = ((JSONObject) toolCall).getString("id");
                if (id != null) {
                    ids.add(id);
                }
            } else if (toolCall instanceof Map) {
                Object id = ((Map<?, ?>) toolCall).get("id");
                if (id != null) {
                    ids.add(id.toString());
                }
            }
        }
        return ids;
    }

    /**
     * Estimate tokens for a list of messages using simple heuristic: content.length() / 4.
     */
    private int estimateTokens(List<Message> msgs) {
        int total = 0;
        for (Message msg : msgs) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }

    /**
     * Estimate tokens for a single message: content.length() / 4.
     */
    private int estimateMessageTokens(Message msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) {
            // Even empty messages have some overhead (role, etc.)
            return 4;
        }
        return Math.max(1, content.length() / 4);
    }

    @Override
    public synchronized List<Message> getAllOriginalMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    @Override
    public synchronized void clear() {
        messages.clear();
        lastKnownPromptTokens = -1;
        trimCount = 0;
    }

    @Override
    public synchronized int size() {
        return messages.size();
    }

    @Override
    public synchronized MemoryStats getStats() {
        return new MemoryStats(messages.size(), lastKnownPromptTokens, trimCount, 0);
    }

    @Override
    public synchronized void loadWithSummary(List<Message> originalMessages, List<SummaryMetadata> metadata) {
        // SlidingWindowMemory doesn't use summaries, just load the messages
        if (originalMessages == null) {
            return;
        }
        messages.clear();
        for (Message msg : originalMessages) {
            if (msg != null) {
                messages.add(msg);
            }
        }
    }

    @Override
    public void updateRealTokenUsage(TokenUsageInfo usageInfo) {
        if (usageInfo == null) {
            return;
        }
        lastKnownPromptTokens = usageInfo.getPromptTokens();
    }

    @Override
    public int getLastKnownPromptTokens() {
        return lastKnownPromptTokens;
    }

    /**
     * Internal helper: a group of messages that must be kept or removed as a unit.
     */
    private static class MessageGroup {
        final List<Message> messages;
        final int estimatedTokens;

        MessageGroup(List<Message> messages) {
            this.messages = messages;
            int tokens = 0;
            for (Message msg : messages) {
                String content = msg.getContent();
                if (content == null || content.isEmpty()) {
                    tokens += 4;
                } else {
                    tokens += Math.max(1, content.length() / 4);
                }
            }
            this.estimatedTokens = tokens;
        }
    }
}
