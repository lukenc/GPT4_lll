package com.wmsay.gpt4_lll.fc.memory;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.model.Message;

import java.util.*;
import java.util.function.Function;

/**
 * 摘要压缩记忆策略（装饰器模式 + 双轨存储）。
 * <p>
 * 包装另一个 {@link ConversationMemory} 实例作为底层存储。
 * 原始消息完整保留供 UI 展示和持久化，{@link #getMessages()} 构建独立的 LLM 视图：
 * system prompt + 摘要消息 + 摘要范围之后的新消息。
 * <p>
 * 摘要触发逻辑：
 * <ul>
 *   <li>{@code lastKnownPromptTokens > 0} 且超过 {@code tokenThreshold} → 触发摘要</li>
 *   <li>{@code lastKnownPromptTokens == -1} 且 {@code isLoadedFromHistory == true} → 直接触发摘要（旧版本数据）</li>
 *   <li>{@code lastKnownPromptTokens == -1} 且 {@code isLoadedFromHistory == false} → 跳过预检查（全新对话）</li>
 * </ul>
 * <p>
 * 保留最近至少 3 轮对话（6 条 user/assistant 消息）不参与摘要。
 * 摘要失败时回退到底层策略默认行为，通过 ProgressCallback 通知。
 * <p>
 * 线程安全：所有公开方法使用 synchronized 保护。
 */
public class SummarizingMemory implements ConversationMemory {

    private static final Logger LOG = Logger.getLogger(SummarizingMemory.class.getName());

    /** 保留最近至少 3 轮对话不参与摘要 */
    private static final int MIN_PRESERVED_ROUNDS = 3;

    private final ConversationMemory delegate;
    private final int tokenThreshold;
    private final Function<String, String> summarizer;
    private final FunctionCallOrchestrator.ProgressCallback progressCallback;

    // 双轨状态
    private final List<Message> originalMessages = new ArrayList<>();
    private final List<SummaryMetadata> summaries = new ArrayList<>();
    private volatile int lastKnownPromptTokens = -1;
    private boolean isLoadedFromHistory = false;
    private int summarizeCount = 0;

    /**
     * @param delegate          底层 ConversationMemory 实例
     * @param tokenThreshold    触发摘要的 token 阈值
     * @param summarizer        LLM 摘要函数，输入待摘要文本，返回摘要结果
     * @param progressCallback  可选的进度回调，为 null 时使用空实现
     */
    public SummarizingMemory(ConversationMemory delegate,
                             int tokenThreshold,
                             Function<String, String> summarizer,
                             FunctionCallOrchestrator.ProgressCallback progressCallback) {
        this.delegate = delegate;
        this.tokenThreshold = tokenThreshold;
        this.summarizer = summarizer;
        this.progressCallback = progressCallback != null ? progressCallback : new FunctionCallOrchestrator.ProgressCallback() {};
    }

    @Override
    public synchronized void add(Message message) {
        if (message == null) {
            return;
        }
        originalMessages.add(message);
        delegate.add(message);
    }

    @Override
    public synchronized void addAll(List<Message> messages) {
        if (messages == null) {
            return;
        }
        for (Message msg : messages) {
            if (msg != null) {
                originalMessages.add(msg);
                delegate.add(msg);
            }
        }
    }

    @Override
    public synchronized List<Message> getMessages() {
        // 判断是否需要触发摘要
        boolean shouldSummarize = false;

        if (lastKnownPromptTokens > 0 && lastKnownPromptTokens > tokenThreshold) {
            shouldSummarize = true;
        } else if (lastKnownPromptTokens == -1 && isLoadedFromHistory) {
            // 旧版本数据，不论多长，直接触发摘要
            shouldSummarize = true;
        }
        // lastKnownPromptTokens == -1 且 isLoadedFromHistory == false → 全新对话，跳过

        if (shouldSummarize) {
            trySummarize();
        }

        return buildLlmView();
    }

    /**
     * 构建独立的 LLM 视图：system prompt + 摘要消息 + 摘要范围之后的新消息。
     */
    private List<Message> buildLlmView() {
        List<Message> systemMessages = new ArrayList<>();
        List<Message> nonSystemMessages = new ArrayList<>();

        for (Message msg : originalMessages) {
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else {
                nonSystemMessages.add(msg);
            }
        }

        List<Message> result = new ArrayList<>(systemMessages);

        if (summaries.isEmpty()) {
            // 无摘要，返回所有消息
            result.addAll(nonSystemMessages);
        } else {
            // 找到最新摘要的 endIndex，确定摘要覆盖范围
            SummaryMetadata latestSummary = summaries.get(summaries.size() - 1);
            int summaryEndIndex = latestSummary.getEndIndex();

            // 添加摘要消息
            for (SummaryMetadata meta : summaries) {
                Message summaryMsg = new Message();
                summaryMsg.setRole("system");
                summaryMsg.setContent("[对话摘要] " + meta.getSummaryText());
                result.add(summaryMsg);
            }

            // 添加摘要范围之后的新消息（基于原始消息列表中的索引）
            // summaryEndIndex 是基于 originalMessages 的索引（包含 system 消息）
            for (int i = summaryEndIndex + 1; i < originalMessages.size(); i++) {
                Message msg = originalMessages.get(i);
                if (!"system".equals(msg.getRole())) {
                    result.add(msg);
                }
            }
        }

        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    /**
     * 尝试执行摘要操作。
     * 保留最近至少 3 轮对话（6 条 user/assistant 消息）不参与摘要。
     * 摘要失败时回退到底层策略默认行为。
     */
    private void trySummarize() {
        // 收集非 system 消息及其在 originalMessages 中的索引
        List<IndexedMessage> nonSystemIndexed = new ArrayList<>();
        for (int i = 0; i < originalMessages.size(); i++) {
            Message msg = originalMessages.get(i);
            if (!"system".equals(msg.getRole())) {
                nonSystemIndexed.add(new IndexedMessage(i, msg));
            }
        }

        if (nonSystemIndexed.isEmpty()) {
            return;
        }

        // 计算需要保留的最近消息数量（至少 3 轮 = 6 条 user/assistant 消息）
        int preserveCount = countPreservedMessages(nonSystemIndexed);
        int summarizableCount = nonSystemIndexed.size() - preserveCount;

        if (summarizableCount <= 0) {
            LOG.info("SummarizingMemory: not enough messages to summarize, "
                    + "total non-system=" + nonSystemIndexed.size()
                    + ", preserved=" + preserveCount);
            return;
        }

        // 确定要摘要的消息范围
        List<IndexedMessage> toSummarize = nonSystemIndexed.subList(0, summarizableCount);

        // 检查是否已经被摘要过（避免重复摘要同一范围）
        int startIdx = toSummarize.get(0).index;
        int endIdx = toSummarize.get(toSummarize.size() - 1).index;

        if (!summaries.isEmpty()) {
            SummaryMetadata latest = summaries.get(summaries.size() - 1);
            if (latest.getEndIndex() >= endIdx) {
                // 已经摘要过这个范围
                return;
            }
            // 新摘要从上次摘要结束之后开始
            List<IndexedMessage> newToSummarize = new ArrayList<>();
            for (IndexedMessage im : toSummarize) {
                if (im.index > latest.getEndIndex()) {
                    newToSummarize.add(im);
                }
            }
            if (newToSummarize.isEmpty()) {
                return;
            }
            toSummarize = newToSummarize;
            startIdx = toSummarize.get(0).index;
            endIdx = toSummarize.get(toSummarize.size() - 1).index;
        }

        // 构建待摘要的文本
        StringBuilder sb = new StringBuilder();
        for (IndexedMessage im : toSummarize) {
            sb.append(im.message.getRole()).append(": ");
            String content = im.message.getContent();
            sb.append(content != null ? content : "").append("\n");
        }
        String textToSummarize = sb.toString();

        // 执行摘要
        try {
            progressCallback.onMemorySummarizingStarted();
            LOG.info("SummarizingMemory: summarizing messages index " + startIdx + " to " + endIdx
                    + " (" + toSummarize.size() + " messages)");

            String summaryText = summarizer.apply(textToSummarize);

            if (summaryText == null || summaryText.trim().isEmpty()) {
                LOG.log(Level.WARNING, "SummarizingMemory: summarizer returned empty result, skipping");
                progressCallback.onMemorySummarizingFailed("摘要结果为空");
                return;
            }

            int originalTokensEstimate = textToSummarize.length() / 4;
            int compressedTokensEstimate = summaryText.length() / 4;

            SummaryMetadata metadata = new SummaryMetadata(
                    summaryText, startIdx, endIdx,
                    System.currentTimeMillis(),
                    originalTokensEstimate, compressedTokensEstimate
            );
            summaries.add(metadata);
            summarizeCount++;

            LOG.info("SummarizingMemory: summary completed, original ~" + originalTokensEstimate
                    + " tokens -> compressed ~" + compressedTokensEstimate + " tokens");
            progressCallback.onMemorySummarizingCompleted(originalTokensEstimate, compressedTokensEstimate);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "SummarizingMemory: summarizer failed, falling back to delegate behavior", e);
            progressCallback.onMemorySummarizingFailed(e.getMessage());
        }
    }

    /**
     * 计算需要保留的最近消息数量。
     * 至少保留最近 3 轮对话（1 轮 = 1 user + 1 assistant = 2 条消息，3 轮 = 6 条）。
     */
    private int countPreservedMessages(List<IndexedMessage> nonSystemIndexed) {
        // 从后往前数，找到至少 3 轮（user + assistant 对）
        int rounds = 0;
        int count = 0;
        boolean expectAssistant = true; // 从后往前，先遇到 assistant 再遇到 user

        for (int i = nonSystemIndexed.size() - 1; i >= 0 && rounds < MIN_PRESERVED_ROUNDS; i--) {
            Message msg = nonSystemIndexed.get(i).message;
            String role = msg.getRole();

            count++;

            if (expectAssistant && "assistant".equals(role)) {
                expectAssistant = false;
            } else if (!expectAssistant && "user".equals(role)) {
                rounds++;
                expectAssistant = true;
            }
            // tool 消息也计入保留范围（它们属于对话轮次的一部分）
        }

        // 如果消息不足 3 轮，保留所有消息
        if (rounds < MIN_PRESERVED_ROUNDS) {
            return nonSystemIndexed.size();
        }

        return count;
    }

    @Override
    public synchronized List<Message> getAllOriginalMessages() {
        return Collections.unmodifiableList(new ArrayList<>(originalMessages));
    }

    @Override
    public synchronized void clear() {
        originalMessages.clear();
        summaries.clear();
        lastKnownPromptTokens = -1;
        isLoadedFromHistory = false;
        summarizeCount = 0;
        delegate.clear();
    }

    @Override
    public synchronized int size() {
        return originalMessages.size();
    }

    @Override
    public synchronized MemoryStats getStats() {
        return new MemoryStats(originalMessages.size(), lastKnownPromptTokens, 0, summarizeCount);
    }

    @Override
    public synchronized void loadWithSummary(List<Message> originalMessages, List<SummaryMetadata> metadata) {
        if (originalMessages == null) {
            return;
        }
        this.originalMessages.clear();
        this.summaries.clear();

        for (Message msg : originalMessages) {
            if (msg != null) {
                this.originalMessages.add(msg);
            }
        }

        if (metadata != null) {
            for (SummaryMetadata meta : metadata) {
                if (meta != null) {
                    this.summaries.add(meta);
                }
            }
        }

        this.isLoadedFromHistory = true;

        // Also load into delegate
        delegate.loadWithSummary(originalMessages, metadata);
    }

    @Override
    public void updateRealTokenUsage(TokenUsageInfo usageInfo) {
        if (usageInfo == null) {
            return;
        }
        lastKnownPromptTokens = usageInfo.getPromptTokens();
        delegate.updateRealTokenUsage(usageInfo);
    }

    @Override
    public int getLastKnownPromptTokens() {
        return lastKnownPromptTokens;
    }

    /**
     * 返回摘要元数据列表，供持久化使用。
     */
    public synchronized List<SummaryMetadata> getSummaries() {
        return Collections.unmodifiableList(new ArrayList<>(summaries));
    }

    /**
     * 返回是否从历史数据加载。
     */
    public synchronized boolean isLoadedFromHistory() {
        return isLoadedFromHistory;
    }

    /** 内部辅助类：带原始索引的消息 */
    private static class IndexedMessage {
        final int index;
        final Message message;

        IndexedMessage(int index, Message message) {
            this.index = index;
            this.message = message;
        }
    }
}
