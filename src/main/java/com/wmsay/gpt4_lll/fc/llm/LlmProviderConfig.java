package com.wmsay.gpt4_lll.fc.llm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 供应商不可变配置类（框架层）。
 * <p>
 * 替代宿主层的 {@code MyPluginSettings}，为 {@link LlmProviderAdapter} 提供
 * 平台无关的供应商配置。所有字段在构建后不可变。
 * <p>
 * 使用 Builder 模式构建：
 * <pre>
 * LlmProviderConfig config = LlmProviderConfig.builder()
 *         .apiKey("sk-xxx")
 *         .apiUrl("https://api.openai.com/v1/chat/completions")
 *         .modelName("gpt-4")
 *         .proxy("127.0.0.1:7890")
 *         .extraSetting("organization", "org-xxx")
 *         .build();
 * </pre>
 */
public final class LlmProviderConfig {

    private final String apiKey;
    private final String apiUrl;
    private final String modelName;
    private final String proxy;
    private final Map<String, String> extraSettings;

    private LlmProviderConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiUrl = builder.apiUrl;
        this.modelName = builder.modelName;
        this.proxy = builder.proxy;
        this.extraSettings = Collections.unmodifiableMap(new HashMap<>(builder.extraSettings));
    }

    /** API Key 字符串，可为 null（某些供应商使用 access token 拼接在 URL 中） */
    public String getApiKey() {
        return apiKey;
    }

    /** API URL，可为 null（由适配器根据供应商规则生成） */
    public String getApiUrl() {
        return apiUrl;
    }

    /** 模型名称，如 "gpt-4"、"ernie-speed-128k" */
    public String getModelName() {
        return modelName;
    }

    /** 代理地址，格式 ip:port，可为 null 表示不使用代理 */
    public String getProxy() {
        return proxy;
    }

    /**
     * 供应商特定的额外配置（不可变视图）。
     * 例如 organization、access_token 等供应商特有参数。
     *
     * @return 不可变的额外配置 Map
     */
    public Map<String, String> getExtraSettings() {
        return extraSettings;
    }

    /**
     * 获取指定的额外配置值。
     *
     * @param key 配置键
     * @return 配置值，不存在时返回 null
     */
    public String getExtraSetting(String key) {
        return extraSettings.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String apiKey;
        private String apiUrl;
        private String modelName;
        private String proxy;
        private final Map<String, String> extraSettings = new HashMap<>();

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder proxy(String proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder extraSetting(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("Extra setting key must not be null");
            }
            this.extraSettings.put(key, value);
            return this;
        }

        public Builder extraSettings(Map<String, String> settings) {
            if (settings == null) {
                throw new IllegalArgumentException("Extra settings map must not be null");
            }
            this.extraSettings.putAll(settings);
            return this;
        }

        public LlmProviderConfig build() {
            return new LlmProviderConfig(this);
        }
    }
}
