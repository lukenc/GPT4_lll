package com.wmsay.gpt4_lll.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;

/**
 * LLM 请求封装。
 * 统一各调用方（Action、MCP Tool、Agent）的请求参数，
 * 使调用方无需关心 HTTP 构建细节。
 * <p>
 * 使用方式：
 * <pre>
 * LlmRequest request = LlmRequest.builder()
 *         .url(url)
 *         .chatContent(chatContent)
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

        /**
         * 传入 ChatContent 对象，内部自动序列化为 JSON。
         * 便于业务层直接使用，无需手动序列化。
         * 序列化时过滤掉 Message 中仅用于 UI/持久化的内部字段
         * （thinking_content、tool_call_summaries、content_blocks），
         * 防止非标准字段发送到 AI API 导致拒绝请求。
         */
        public Builder chatContent(ChatContent content) {
            SimplePropertyPreFilter filter = new SimplePropertyPreFilter(Message.class);
            filter.getExcludes().add("thinking_content");
            filter.getExcludes().add("tool_call_summaries");
            filter.getExcludes().add("content_blocks");
            this.requestBody = JSON.toJSONString(content, filter);
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
