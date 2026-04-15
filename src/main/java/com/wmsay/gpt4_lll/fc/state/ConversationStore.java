package com.wmsay.gpt4_lll.fc.state;

import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.memory.ConversationData;
import com.wmsay.gpt4_lll.fc.memory.SummaryMetadata;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话历史持久化 SPI 接口。
 * <p>
 * 定义会话历史的完整 CRUD 操作，支持通过 {@link java.util.ServiceLoader}
 * 机制发现和注册不同的存储后端实现（JSON 文件、XML、数据库等）。
 * <p>
 * 框架内置 {@link JsonConversationStore} 作为默认实现。
 *
 * @see JsonConversationStore
 * @see ConversationStoreFactory
 * @see ConversationData
 */
public interface ConversationStore {

    /**
     * 保存对话消息。
     *
     * @param topic    对话主题（非 null）
     * @param messages 消息列表（非 null）
     * @throws ConversationStoreException 保存失败时抛出
     */
    void save(String topic, List<Message> messages);

    /**
     * 加载指定主题的对话数据。
     *
     * @param topic 对话主题
     * @return 对话数据，主题不存在时返回 null
     * @throws ConversationStoreException 加载失败时抛出
     */
    ConversationData load(String topic);

    /**
     * 加载所有对话数据，按主题名称倒序排列。
     *
     * @return 所有对话数据列表（不可变）
     * @throws ConversationStoreException 加载失败时抛出
     */
    List<ConversationData> loadAll();

    /**
     * 删除指定主题的对话。
     *
     * @param topic 对话主题
     * @throws ConversationStoreException 删除失败时抛出
     */
    void delete(String topic);

    /**
     * 重命名对话主题。
     *
     * @param oldTopic 原主题名称
     * @param newTopic 新主题名称
     * @throws ConversationStoreException 重命名失败或原主题不存在时抛出
     */
    void rename(String oldTopic, String newTopic);

    /**
     * 搜索包含关键词的对话（匹配主题名或消息内容）。
     *
     * @param keyword 搜索关键词
     * @return 匹配的对话数据列表
     * @throws ConversationStoreException 搜索失败时抛出
     */
    List<ConversationData> search(String keyword);

    /**
     * 按日期范围过滤对话。
     *
     * @param from 起始时间（含），null 表示不限
     * @param to   结束时间（含），null 表示不限
     * @return 在日期范围内的对话数据列表
     * @throws ConversationStoreException 过滤失败时抛出
     */
    List<ConversationData> filterByDateRange(LocalDateTime from, LocalDateTime to);

    /**
     * 导出指定主题的对话到文件。
     *
     * @param topic      对话主题
     * @param outputPath 输出文件路径
     * @param format     导出格式
     * @throws ConversationStoreException 导出失败或主题不存在时抛出
     */
    void export(String topic, Path outputPath, ExportFormat format);

    /**
     * 保存对话消息（含元数据）。
     * <p>
     * 使用 {@link ConversationData} 结构存储消息、摘要元数据和 token 状态。
     *
     * @param topic    对话主题
     * @param messages 消息列表
     * @param metadata 摘要元数据列表（可为 null）
     * @param lastKnownPromptTokens 最近真实 prompt_tokens（-1 表示未知）
     * @throws ConversationStoreException 保存失败时抛出
     */
    void saveWithMetadata(String topic, List<Message> messages,
                          List<SummaryMetadata> metadata, int lastKnownPromptTokens);

    /**
     * 导出格式枚举。
     */
    enum ExportFormat {
        TEXT, JSON, MARKDOWN
    }
}
