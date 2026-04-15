package com.wmsay.gpt4_lll.fc.llm;

import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.List;

/**
 * LLM 供应商适配器接口（框架层）。
 * <p>
 * 替代宿主层的 {@code ProviderAdapter}，通过 {@link LlmProviderConfig}（而非
 * {@code MyPluginSettings}）获取供应商配置，实现框架层零 IntelliJ 依赖。
 * <p>
 * 新增供应商只需：
 * <ol>
 *   <li>创建一个 {@code LlmProviderAdapter} 实现类</li>
 *   <li>在 {@link LlmProviderAdapterRegistry} 中注册（手动或通过 SPI）</li>
 * </ol>
 * <p>
 * 设计约束：
 * <ul>
 *   <li>不引用 {@code com.intellij.*} 或 {@code MyPluginSettings}</li>
 *   <li>不修改 Message 的数据结构</li>
 *   <li>{@link #adaptMessages(List)} 不能修改原始列表，必须返回新列表</li>
 * </ul>
 *
 * @see LlmProviderConfig
 * @see LlmProviderAdapterRegistry
 */
public interface LlmProviderAdapter {

    /**
     * 供应商名称，用于注册表查找。
     *
     * @return 供应商名称（如 "OpenAI"、"Baidu"）
     */
    String getProviderName();

    /**
     * 获取 API URL。
     *
     * @param config 供应商配置
     * @return 完整的 API URL
     */
    String getApiUrl(LlmProviderConfig config);

    /**
     * 获取 API Key。
     *
     * @param config 供应商配置
     * @return API Key 字符串
     */
    String getApiKey(LlmProviderConfig config);

    /**
     * 是否支持 system 角色。
     * 百度等供应商不支持，需降级为 user。
     *
     * @return true 支持 system role，false 不支持
     */
    default boolean supportsSystemRole() {
        return true;
    }

    /**
     * 消息格式适配。
     * 默认实现不做任何修改（适用于 OpenAI 标准格式的供应商）。
     * <p>
     * ⚠️ 不能修改原始列表，必须返回新列表。
     *
     * @param messages 原始消息列表
     * @return 适配后的消息列表
     */
    default List<Message> adaptMessages(List<Message> messages) {
        return messages;
    }

    /**
     * 解析 SSE 行，提取内容。
     * 默认实现使用 OpenAI 标准格式。
     *
     * @param lineData 去掉 "data:" 前缀后的行内容
     * @param callback 回调接口
     */
    default void parseSseLine(String lineData, LlmStreamCallback callback) {
        SseStreamProcessor.processOpenAiLine(lineData, callback);
    }
}
