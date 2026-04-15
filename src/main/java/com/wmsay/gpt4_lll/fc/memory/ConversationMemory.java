package com.wmsay.gpt4_lll.fc.memory;

import com.wmsay.gpt4_lll.fc.core.Message;
import java.util.List;

/**
 * 对话记忆管理核心接口。
 * <p>
 * 通过策略模式支持多种记忆管理策略（滑动窗口、摘要压缩、自适应、组合）。
 * 采用双轨存储模型：原始消息完整保留供 UI 展示和持久化，
 * {@link #getMessages()} 返回经过策略处理后的 LLM 视图。
 * <p>
 * Token 追踪完全依赖 LLM API 真实 usage 数据（无启发式估算）。
 * 通过 {@link #updateRealTokenUsage(TokenUsageInfo)} 接收真实 token 使用量，
 * 通过 {@link #getLastKnownPromptTokens()} 查询最近的真实 prompt_tokens。
 */
public interface ConversationMemory {

    /**
     * 添加单条消息到记忆中。
     * <p>
     * 若 {@code message} 为 null，静默忽略不抛异常。
     *
     * @param message 要添加的消息
     */
    void add(Message message);

    /**
     * 批量添加消息到记忆中。
     * <p>
     * 若 {@code messages} 为 null，静默忽略不抛异常。
     * 列表中的 null 元素也会被忽略。
     *
     * @param messages 要添加的消息列表
     */
    void addAll(List<Message> messages);

    /**
     * 返回经过策略处理后应发送给 LLM 的消息列表。
     * <p>
     * 返回不可变副本，调用方对返回列表的修改不影响内部状态。
     * 策略决策基于 {@link #getLastKnownPromptTokens()} 的真实值。
     *
     * @return 策略处理后的消息列表（不可变副本）
     */
    List<Message> getMessages();

    /**
     * 返回所有原始消息的完整列表，供 UI 展示和持久化使用。
     * <p>
     * 不受任何裁剪或摘要策略影响。
     *
     * @return 完整的原始消息列表
     */
    List<Message> getAllOriginalMessages();

    /**
     * 清空所有记忆内容。
     * <p>
     * 同时将 {@code lastKnownPromptTokens} 重置为 -1。
     */
    void clear();

    /**
     * 返回当前记忆中的原始消息总数（未经裁剪）。
     *
     * @return 原始消息总数
     */
    int size();

    /**
     * 返回记忆统计快照，包含消息数、真实 prompt_tokens、裁剪次数和摘要次数。
     *
     * @return 统计快照
     */
    MemoryStats getStats();

    /**
     * 从历史数据恢复双轨状态。
     * <p>
     * 加载后 {@link #getMessages()} 立即使用已有摘要，无需重新调用 LLM。
     *
     * @param originalMessages 原始消息列表
     * @param metadata         摘要元数据列表
     */
    void loadWithSummary(List<Message> originalMessages, List<SummaryMetadata> metadata);

    /**
     * 接收来自 LLM API 响应的真实 token 使用量数据，更新内部的 lastKnownPromptTokens。
     * <p>
     * 若 {@code usageInfo} 为 null，静默忽略不更新。
     *
     * @param usageInfo 从 LLM API 响应中提取的 token 使用量
     */
    void updateRealTokenUsage(TokenUsageInfo usageInfo);

    /**
     * 返回最近一次 LLM 调用的真实 prompt_tokens 值。
     * <p>
     * 若尚无真实数据（全新对话），返回 -1 表示未知。
     *
     * @return 最近真实 prompt_tokens，或 -1
     */
    int getLastKnownPromptTokens();
}
