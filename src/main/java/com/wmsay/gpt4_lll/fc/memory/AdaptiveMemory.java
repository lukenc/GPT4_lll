package com.wmsay.gpt4_lll.fc.memory;

import java.util.logging.Logger;
import com.wmsay.gpt4_lll.model.Message;

import java.util.*;

/**
 * 自适应记忆策略。
 * <p>
 * 根据内容相似度动态选择最合适的记忆管理策略：
 * <ul>
 *   <li>高相似度（同一话题）→ {@link SummarizingMemory} 摘要压缩，保留语义信息</li>
 *   <li>低相似度（话题转换）→ {@link SlidingWindowMemory} 滑动窗口，直接丢弃旧内容</li>
 * </ul>
 * <p>
 * 消息通过 {@link #add(Message)} 和 {@link #addAll(List)} 同时添加到两个候选策略中，
 * 确保无论选择哪个策略都拥有完整的消息历史。
 * <p>
 * 策略决策使用真实 prompt_tokens（通过 {@link #updateRealTokenUsage(TokenUsageInfo)} 更新）。
 * 线程安全：所有公开方法使用 synchronized 保护。
 */
public class AdaptiveMemory implements ConversationMemory {

    private static final Logger LOG = Logger.getLogger(AdaptiveMemory.class.getName());

    private final SlidingWindowMemory slidingWindow;
    private final SummarizingMemory summarizing;
    private final SimilarityAnalyzer similarityAnalyzer;
    private final double similarityThreshold;
    private volatile int lastKnownPromptTokens = -1;

    /**
     * @param slidingWindow       滑动窗口策略实例
     * @param summarizing         摘要压缩策略实例
     * @param similarityAnalyzer  相似度分析器
     * @param similarityThreshold 相似度阈值，默认 0.6
     */
    public AdaptiveMemory(SlidingWindowMemory slidingWindow,
                          SummarizingMemory summarizing,
                          SimilarityAnalyzer similarityAnalyzer,
                          double similarityThreshold) {
        this.slidingWindow = slidingWindow;
        this.summarizing = summarizing;
        this.similarityAnalyzer = similarityAnalyzer;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public synchronized void add(Message message) {
        if (message == null) {
            return;
        }
        slidingWindow.add(message);
        summarizing.add(message);
    }

    @Override
    public synchronized void addAll(List<Message> messages) {
        if (messages == null) {
            return;
        }
        slidingWindow.addAll(messages);
        summarizing.addAll(messages);
    }

    @Override
    public synchronized List<Message> getMessages() {
        // Check if trimming is needed based on real token usage
        boolean needsTrimming = lastKnownPromptTokens > 0;

        if (!needsTrimming) {
            // No real token data yet or tokens within limit — delegate to summarizing
            // (which holds the complete message list and handles -1 / history cases)
            return summarizing.getMessages();
        }

        // Use SimilarityAnalyzer to decide which strategy to use
        List<Message> allOriginal = summarizing.getAllOriginalMessages();

        // Split into recent and earlier messages for similarity analysis
        List<Message> nonSystemMessages = new ArrayList<>();
        for (Message msg : allOriginal) {
            if (!"system".equals(msg.getRole())) {
                nonSystemMessages.add(msg);
            }
        }

        // Need at least some messages to analyze
        if (nonSystemMessages.size() < 4) {
            // Too few messages to meaningfully analyze similarity, use summarizing
            LOG.info("AdaptiveMemory: too few messages (" + nonSystemMessages.size()
                    + ") for similarity analysis, defaulting to summarizing strategy");
            return summarizing.getMessages();
        }

        // Split: recent 6 messages (3 rounds) vs earlier messages
        int splitPoint = Math.max(0, nonSystemMessages.size() - 6);
        List<Message> earlierMessages = nonSystemMessages.subList(0, splitPoint);
        List<Message> recentMessages = nonSystemMessages.subList(splitPoint, nonSystemMessages.size());

        if (earlierMessages.isEmpty()) {
            // All messages are "recent", use summarizing
            return summarizing.getMessages();
        }

        double similarity = similarityAnalyzer.analyzeSimilarity(recentMessages, earlierMessages);

        if (similarity > similarityThreshold) {
            LOG.info("AdaptiveMemory: similarity=" + String.format("%.2f", similarity)
                    + " > threshold=" + similarityThreshold
                    + ", selecting SummarizingMemory strategy");
            return summarizing.getMessages();
        } else {
            LOG.info("AdaptiveMemory: similarity=" + String.format("%.2f", similarity)
                    + " <= threshold=" + similarityThreshold
                    + ", selecting SlidingWindowMemory strategy");
            return slidingWindow.getMessages();
        }
    }

    @Override
    public synchronized List<Message> getAllOriginalMessages() {
        // Delegate to SummarizingMemory which maintains the complete original list
        return summarizing.getAllOriginalMessages();
    }

    @Override
    public synchronized void clear() {
        slidingWindow.clear();
        summarizing.clear();
        lastKnownPromptTokens = -1;
    }

    @Override
    public synchronized int size() {
        // Delegate to SummarizingMemory which maintains the complete original list
        return summarizing.size();
    }

    @Override
    public synchronized MemoryStats getStats() {
        MemoryStats slidingStats = slidingWindow.getStats();
        MemoryStats summarizingStats = summarizing.getStats();
        return new MemoryStats(
                summarizingStats.getMessageCount(),
                lastKnownPromptTokens,
                slidingStats.getTrimCount(),
                summarizingStats.getSummarizeCount()
        );
    }

    @Override
    public synchronized void loadWithSummary(List<Message> originalMessages, List<SummaryMetadata> metadata) {
        slidingWindow.loadWithSummary(originalMessages, metadata);
        summarizing.loadWithSummary(originalMessages, metadata);
    }

    @Override
    public void updateRealTokenUsage(TokenUsageInfo usageInfo) {
        if (usageInfo == null) {
            return;
        }
        lastKnownPromptTokens = usageInfo.getPromptTokens();
        slidingWindow.updateRealTokenUsage(usageInfo);
        summarizing.updateRealTokenUsage(usageInfo);
    }

    @Override
    public int getLastKnownPromptTokens() {
        return lastKnownPromptTokens;
    }
}
