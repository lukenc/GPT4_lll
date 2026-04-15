package com.wmsay.gpt4_lll.component;

import com.wmsay.gpt4_lll.JsonStorage;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.memory.ConversationData;
import com.wmsay.gpt4_lll.fc.state.ConversationStore;
import com.wmsay.gpt4_lll.fc.state.ConversationStoreFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 宿主层适配器：将框架层 {@link ConversationStore} SPI 桥接到
 * {@link ChatHistoryManager} UI 组件。
 * <p>
 * 提供与原 {@link JsonStorage} 兼容的便捷 API（如 {@link #loadAllAsMap()} 返回
 * {@code LinkedHashMap<String, List<Message>>}），使 ChatHistoryManager
 * 可以在不修改自身代码的前提下逐步迁移到框架层存储。
 * <p>
 * 当前阶段 {@link #loadAllAsMap()} 和 {@link #saveAll(LinkedHashMap)} 仍委托给
 * {@link JsonStorage}（保持向后兼容），其余操作委托给 {@link ConversationStore}。
 * 待 Phase 8 清理时，ChatHistoryManager 将直接使用本适配器替代 JsonStorage。
 * <p>
 * 本类位于宿主层（{@code component/} 包），可使用 {@code com.intellij.*} API。
 *
 * @see ConversationStore
 * @see ConversationStoreFactory
 * @see ChatHistoryManager
 */
public class IntelliJConversationStoreAdapter {

    private final ConversationStore store;

    /**
     * 使用默认 {@link ConversationStore}（通过 SPI 发现或 JsonConversationStore）创建适配器。
     */
    public IntelliJConversationStoreAdapter() {
        this(ConversationStoreFactory.getDefault());
    }

    /**
     * 使用指定的 {@link ConversationStore} 创建适配器。
     *
     * @param store ConversationStore 实例（非 null）
     */
    public IntelliJConversationStoreAdapter(ConversationStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    /**
     * 加载所有对话数据，返回与 {@link JsonStorage#loadData()} 兼容的格式。
     * <p>
     * 键为对话主题（topic），值为消息列表，按主题名称倒序排列。
     * 当前阶段委托给 {@link JsonStorage#loadData()} 以保持完全向后兼容。
     *
     * @return 所有对话数据的有序映射（不为 null）
     * @throws IOException 加载失败时抛出
     */
    public LinkedHashMap<String, List<Message>> loadAllAsMap() throws IOException {
        return JsonStorage.loadData();
    }

    /**
     * 保存所有对话数据（与 {@link JsonStorage#saveData(LinkedHashMap)} 兼容的格式）。
     * <p>
     * 当前阶段委托给 {@link JsonStorage#saveData(LinkedHashMap)} 以保持完全向后兼容。
     *
     * @param data 对话数据映射（非 null）
     * @throws IOException 保存失败时抛出
     */
    public void saveAll(LinkedHashMap<String, List<Message>> data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        JsonStorage.saveData(data);
    }

    /**
     * 搜索包含关键词的对话（匹配主题名或消息内容）。
     *
     * @param keyword 搜索关键词（非 null）
     * @return 匹配的对话数据列表（不可变）
     */
    public List<ConversationData> search(String keyword) {
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        return store.search(keyword);
    }

    /**
     * 按预定义日期范围过滤对话。
     * <p>
     * 支持的范围值：{@code "today"}、{@code "yesterday"}、{@code "last7days"}、
     * {@code "last30days"}、{@code "thismonth"}、{@code "lastmonth"}。
     * 不识别的范围值返回所有对话。
     *
     * @param range 日期范围字符串（非 null）
     * @return 在日期范围内的对话数据列表（不可变）
     */
    public List<ConversationData> filterByDateRange(String range) {
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from;
        LocalDateTime to = null;

        switch (range.toLowerCase()) {
            case "today":
                from = now.toLocalDate().atStartOfDay();
                break;
            case "yesterday":
                from = now.toLocalDate().minusDays(1).atStartOfDay();
                to = now.toLocalDate().atStartOfDay();
                break;
            case "last7days":
                from = now.minusDays(7);
                break;
            case "last30days":
                from = now.minusDays(30);
                break;
            case "thismonth":
                from = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
                break;
            case "lastmonth":
                from = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
                to = now.withDayOfMonth(1).toLocalDate().atStartOfDay().minusNanos(1);
                break;
            default:
                // Unrecognized range — return all
                from = null;
                break;
        }
        return store.filterByDateRange(from, to);
    }

    /**
     * 导出指定主题的对话到文件（TEXT 格式）。
     *
     * @param topic      对话主题（非 null）
     * @param outputFile 输出文件（非 null）
     */
    public void export(String topic, File outputFile) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }
        store.export(topic, outputFile.toPath(), ConversationStore.ExportFormat.TEXT);
    }

    /**
     * 导出指定主题的对话到文件（指定格式）。
     *
     * @param topic      对话主题（非 null）
     * @param outputPath 输出路径（非 null）
     * @param format     导出格式（非 null）
     */
    public void export(String topic, Path outputPath, ConversationStore.ExportFormat format) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        store.export(topic, outputPath, format);
    }

    /**
     * 重命名对话主题。
     *
     * @param oldTopic 原主题名称（非 null）
     * @param newTopic 新主题名称（非 null）
     */
    public void rename(String oldTopic, String newTopic) {
        if (oldTopic == null) {
            throw new IllegalArgumentException("oldTopic must not be null");
        }
        if (newTopic == null) {
            throw new IllegalArgumentException("newTopic must not be null");
        }
        store.rename(oldTopic, newTopic);
    }

    /**
     * 删除指定主题的对话。
     *
     * @param topic 对话主题（非 null）
     */
    public void delete(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        store.delete(topic);
    }

    /**
     * 获取底层 {@link ConversationStore} 实例。
     * <p>
     * 供需要直接访问框架层 SPI 的调用方使用。
     *
     * @return ConversationStore 实例（非 null）
     */
    public ConversationStore getStore() {
        return store;
    }
}
