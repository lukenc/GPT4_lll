package com.wmsay.gpt4_lll.fc.memory;

import java.util.logging.Logger;
import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.*;

/**
 * 组合记忆策略。
 * <p>
 * 将工作记忆（当前任务状态）与短期记忆（对话历史）分离管理，
 * 在复杂 Agent 场景下提供更精细的上下文控制。
 * <p>
 * {@link #add(Message)} 添加到短期记忆，
 * {@link #addToWorkingMemory(Message)} 添加到工作记忆。
 * {@link #getMessages()} 返回合并列表：system 消息在最前，
 * 工作记忆非 system 消息在中间，短期记忆非 system 消息在最后。
 * <p>
 * 线程安全：所有公开方法使用 synchronized 保护。
 */
public class CompositeMemory implements ConversationMemory {

    private static final Logger LOG = Logger.getLogger(CompositeMemory.class.getName());

    private final ConversationMemory workingMemory;
    private final ConversationMemory shortTermMemory;
    private volatile int lastKnownPromptTokens = -1;

    /**
     * @param workingMemory   工作记忆实例（当前任务状态）
     * @param shortTermMemory 短期记忆实例（对话历史）
     */
    public CompositeMemory(ConversationMemory workingMemory,
                           ConversationMemory shortTermMemory) {
        this.workingMemory = workingMemory;
        this.shortTermMemory = shortTermMemory;
    }

    /**
     * 添加消息到工作记忆。
     *
     * @param message 要添加的消息
     */
    public synchronized void addToWorkingMemory(Message message) {
        if (message == null) {
            return;
        }
        workingMemory.add(message);
    }

    /**
     * 仅清空工作记忆，保留短期记忆。
     */
    public synchronized void clearWorkingMemory() {
        workingMemory.clear();
    }

    @Override
    public synchronized void add(Message message) {
        if (message == null) {
            return;
        }
        shortTermMemory.add(message);
    }

    @Override
    public synchronized void addAll(List<Message> messages) {
        if (messages == null) {
            return;
        }
        shortTermMemory.addAll(messages);
    }

    @Override
    public synchronized List<Message> getMessages() {
        List<Message> workingMessages = workingMemory.getMessages();
        List<Message> shortTermMessages = shortTermMemory.getMessages();

        // Collect system messages from both
        List<Message> systemMessages = new ArrayList<>();
        List<Message> workingNonSystem = new ArrayList<>();
        List<Message> shortTermNonSystem = new ArrayList<>();

        for (Message msg : workingMessages) {
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else {
                workingNonSystem.add(msg);
            }
        }

        for (Message msg : shortTermMessages) {
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else {
                shortTermNonSystem.add(msg);
            }
        }

        // Build result: system first, then working memory, then short-term memory
        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(workingNonSystem);
        result.addAll(shortTermNonSystem);

        LOG.info("CompositeMemory: merged view system=" + systemMessages.size()
                + ", working=" + workingNonSystem.size()
                + ", shortTerm=" + shortTermNonSystem.size()
                + ", total=" + result.size());

        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    @Override
    public synchronized List<Message> getAllOriginalMessages() {
        List<Message> workingOriginal = workingMemory.getAllOriginalMessages();
        List<Message> shortTermOriginal = shortTermMemory.getAllOriginalMessages();

        List<Message> result = new ArrayList<>(workingOriginal);
        result.addAll(shortTermOriginal);

        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    @Override
    public synchronized void clear() {
        workingMemory.clear();
        shortTermMemory.clear();
        lastKnownPromptTokens = -1;
    }

    @Override
    public synchronized int size() {
        return workingMemory.size() + shortTermMemory.size();
    }

    @Override
    public synchronized MemoryStats getStats() {
        MemoryStats workingStats = workingMemory.getStats();
        MemoryStats shortTermStats = shortTermMemory.getStats();
        return new MemoryStats(
                workingStats.getMessageCount() + shortTermStats.getMessageCount(),
                lastKnownPromptTokens,
                workingStats.getTrimCount() + shortTermStats.getTrimCount(),
                workingStats.getSummarizeCount() + shortTermStats.getSummarizeCount()
        );
    }

    @Override
    public synchronized void loadWithSummary(List<Message> originalMessages, List<SummaryMetadata> metadata) {
        // Delegate to short-term memory (conversation history)
        shortTermMemory.loadWithSummary(originalMessages, metadata);
    }

    @Override
    public void updateRealTokenUsage(TokenUsageInfo usageInfo) {
        if (usageInfo == null) {
            return;
        }
        lastKnownPromptTokens = usageInfo.getPromptTokens();
        workingMemory.updateRealTokenUsage(usageInfo);
        shortTermMemory.updateRealTokenUsage(usageInfo);
    }

    @Override
    public int getLastKnownPromptTokens() {
        return lastKnownPromptTokens;
    }
}
