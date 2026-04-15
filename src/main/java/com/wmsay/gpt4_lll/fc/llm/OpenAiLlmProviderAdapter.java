package com.wmsay.gpt4_lll.fc.llm;

import java.util.logging.Logger;

/**
 * 框架层 OpenAI 标准格式供应商适配器。
 * <p>
 * 覆盖所有使用 OpenAI 兼容接口的供应商：OpenAI / DeepSeek / 通义千问 / Grok 等。
 * 这些供应商的消息格式和 SSE 解析相同，差异仅在于 URL 和 API Key，
 * 均通过 {@link LlmProviderConfig} 预配置。
 * <p>
 * 通过构造参数区分不同供应商实例：
 * <pre>
 * LlmProviderAdapterRegistry.register(new OpenAiLlmProviderAdapter("OpenAI"));
 * LlmProviderAdapterRegistry.register(new OpenAiLlmProviderAdapter("DeepSeek"));
 * LlmProviderAdapterRegistry.register(new OpenAiLlmProviderAdapter("Alibaba"));
 * </pre>
 * <p>
 * 设计约束：
 * <ul>
 *   <li>不引用 {@code com.intellij.*} 或 {@code MyPluginSettings}</li>
 *   <li>所有配置通过 {@link LlmProviderConfig} 获取</li>
 * </ul>
 *
 * @see LlmProviderAdapter
 * @see LlmProviderConfig
 * @see LlmProviderAdapterRegistry
 */
public class OpenAiLlmProviderAdapter implements LlmProviderAdapter {

    private static final Logger LOG = Logger.getLogger(OpenAiLlmProviderAdapter.class.getName());

    private final String providerName;

    /**
     * 创建指定供应商名称的 OpenAI 兼容适配器。
     *
     * @param providerName 供应商名称（如 "OpenAI"、"DeepSeek"、"Alibaba"、"X-GROK"）
     * @throws IllegalArgumentException 如果 providerName 为 null 或空
     */
    public OpenAiLlmProviderAdapter(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            throw new IllegalArgumentException("providerName must not be null or empty");
        }
        this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    /**
     * 获取 API URL。
     * URL 已在 {@link LlmProviderConfig} 中预配置（由宿主层从 MyPluginSettings 或 ModelUtils 读取后传入）。
     *
     * @param config 供应商配置
     * @return 完整的 API URL
     */
    @Override
    public String getApiUrl(LlmProviderConfig config) {
        return config.getApiUrl();
    }

    /**
     * 获取 API Key。
     * API Key 已在 {@link LlmProviderConfig} 中预配置。
     *
     * @param config 供应商配置
     * @return API Key 字符串
     */
    @Override
    public String getApiKey(LlmProviderConfig config) {
        return config.getApiKey();
    }

    /**
     * OpenAI 兼容供应商均支持 system role。
     *
     * @return true
     */
    @Override
    public boolean supportsSystemRole() {
        return true;
    }

    // adaptMessages()  → 默认实现（不变，OpenAI 标准消息格式）
    // parseSseLine()   → 默认实现（OpenAI 标准 SSE 解析，SseStreamProcessor.processOpenAiLine）
}
