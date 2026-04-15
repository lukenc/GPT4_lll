package com.wmsay.gpt4_lll.fc.llm;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM 供应商适配器注册表（框架层）。
 * <p>
 * 替代宿主层的 {@code ProviderAdapterRegistry}，通过 SPI 自动发现
 * {@link LlmProviderAdapter} 实现，并内置 OpenAI 标准格式适配器作为默认。
 * <p>
 * 使用方式：
 * <pre>
 * // 获取指定供应商的适配器
 * LlmProviderAdapter adapter = LlmProviderAdapterRegistry.getAdapter("OpenAI");
 *
 * // 手动注册自定义适配器
 * LlmProviderAdapterRegistry.register(new MyCustomAdapter());
 * </pre>
 * <p>
 * 当供应商未注册时，返回内置的 OpenAI 标准格式适配器作为 fallback。
 *
 * @see LlmProviderAdapter
 * @see LlmProviderConfig
 */
public class LlmProviderAdapterRegistry {

    private static final Logger LOG = Logger.getLogger(LlmProviderAdapterRegistry.class.getName());

    private static final Map<String, LlmProviderAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 内置默认适配器：OpenAI 标准格式。
     * 直接返回 config 中的 apiUrl 和 apiKey，使用 OpenAI 标准 SSE 解析。
     */
    private static final LlmProviderAdapter DEFAULT_ADAPTER = new LlmProviderAdapter() {
        @Override
        public String getProviderName() {
            return "OpenAI";
        }

        @Override
        public String getApiUrl(LlmProviderConfig config) {
            return config.getApiUrl();
        }

        @Override
        public String getApiKey(LlmProviderConfig config) {
            return config.getApiKey();
        }
        // supportsSystemRole() → true（默认）
        // adaptMessages()      → 不变（默认，OpenAI 标准消息格式）
        // parseSseLine()       → OpenAI 标准（默认，SseStreamProcessor.processOpenAiLine）
    };

    static {
        // 注册内置默认适配器
        register(DEFAULT_ADAPTER);

        // 注册内置供应商适配器
        register(new OpenAiLlmProviderAdapter("OpenAI"));
        register(new OpenAiLlmProviderAdapter("DeepSeek"));
        register(new OpenAiLlmProviderAdapter("Alibaba"));
        register(new OpenAiLlmProviderAdapter("X-GROK"));
        register(new BaiduLlmProviderAdapter());

        // SPI 自动发现
        loadFromServiceLoader();
    }

    /**
     * 注册供应商适配器。
     * 如果已存在同名适配器，将被覆盖。
     *
     * @param adapter 供应商适配器实例
     * @throws IllegalArgumentException 如果 adapter 为 null
     */
    public static void register(LlmProviderAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("LlmProviderAdapter must not be null");
        }
        adapters.put(adapter.getProviderName(), adapter);
    }

    /**
     * 获取指定供应商的适配器。
     * 如果供应商未注册，返回 OpenAI 标准适配器作为默认实现。
     *
     * @param providerName 供应商名称
     * @return 对应的 LlmProviderAdapter，未找到时返回默认 OpenAI 适配器
     */
    public static LlmProviderAdapter getAdapter(String providerName) {
        if (providerName == null) {
            return DEFAULT_ADAPTER;
        }
        return adapters.getOrDefault(providerName, DEFAULT_ADAPTER);
    }

    /**
     * 通过 SPI（ServiceLoader）自动发现并注册 LlmProviderAdapter 实现。
     * 加载异常时记录警告日志并继续，不中断框架初始化。
     */
    private static void loadFromServiceLoader() {
        try {
            ServiceLoader<LlmProviderAdapter> loader = ServiceLoader.load(LlmProviderAdapter.class);
            for (LlmProviderAdapter adapter : loader) {
                try {
                    register(adapter);
                    LOG.fine("SPI registered LlmProviderAdapter: " + adapter.getProviderName());
                } catch (Exception e) {
                    LOG.log(Level.WARNING,
                            "Failed to register SPI LlmProviderAdapter: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load LlmProviderAdapter via SPI: " + e.getMessage(), e);
        }
    }
}
