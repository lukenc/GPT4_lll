package com.wmsay.gpt4_lll.fc.llm;

/**
 * LLM 请求封装（框架层）。
 * 统一各调用方（Action、MCP Tool、Agent）的请求参数，
 * 使调用方无需关心 HTTP 构建细节。
 * <p>
 * 使用方式：
 * <pre>
 * LlmRequest request = LlmRequest.builder()
 *         .url(url)
 *         .requestBody(jsonBody)
 *         .apiKey(apiKey)
 *         .proxy(proxy)
 *         .provider(provider)
 *         .build();
 *
 * String result = LlmClient.syncChat(request);
 * </pre>
 */
public class LlmRequest {

    private final String url;
    private final String requestBody;
    private final String apiKey;
    private final String proxy;
    private final String provider;

    private LlmRequest(String url, String requestBody, String apiKey, String proxy, String provider) {
        this.url = url;
        this.requestBody = requestBody;
        this.apiKey = apiKey;
        this.proxy = proxy;
        this.provider = provider;
    }

    public String getUrl() {
        return url;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getProxy() {
        return proxy;
    }

    public String getProvider() {
        return provider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private String requestBody;
        private String apiKey;
        private String proxy;
        private String provider;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * 直接设置已序列化的 JSON 请求体。
         */
        public Builder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 代理地址，格式为 ip:port。可选，为 null 或空字符串表示不使用代理。
         */
        public Builder proxy(String proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public LlmRequest build() {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("URL is required / 请提供 API URL");
            }
            if (requestBody == null) {
                throw new IllegalArgumentException("requestBody is required / 请提供请求体");
            }
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("provider is required / 请提供供应商名称");
            }
            return new LlmRequest(url, requestBody, apiKey, proxy, provider);
        }
    }
}
