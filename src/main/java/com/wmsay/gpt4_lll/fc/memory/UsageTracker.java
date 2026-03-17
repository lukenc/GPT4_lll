package com.wmsay.gpt4_lll.fc.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.diagnostic.Logger;

/**
 * 从 LLM API 原始 JSON 响应中提取 usage 字段的工具类。
 * 支持 OpenAI 格式（prompt_tokens/completion_tokens/total_tokens）
 * 和 Anthropic 格式（input_tokens/output_tokens）。
 * 线程安全。
 */
public class UsageTracker {

    private static final Logger LOG = Logger.getInstance(UsageTracker.class);

    /** 最近一次成功提取的 prompt_tokens，-1 表示尚无数据 */
    private volatile int lastKnownPromptTokens = -1;

    /**
     * 从 LLM API 原始 JSON 响应中提取 usage 字段。
     *
     * @param rawJsonResponse LLM 返回的原始 JSON 字符串
     * @return TokenUsageInfo 对象，解析失败返回 null
     */
    public TokenUsageInfo extractUsage(String rawJsonResponse) {
        if (rawJsonResponse == null || rawJsonResponse.isEmpty()) {
            return null;
        }

        try {
            JSONObject root = JSON.parseObject(rawJsonResponse);
            if (root == null) {
                return null;
            }

            JSONObject usage = root.getJSONObject("usage");
            if (usage == null) {
                LOG.warn("No 'usage' field in LLM response");
                return null;
            }

            TokenUsageInfo info = tryOpenAIFormat(usage);
            if (info == null) {
                info = tryAnthropicFormat(usage);
            }

            if (info != null) {
                lastKnownPromptTokens = info.getPromptTokens();
                LOG.info("Extracted token usage: prompt=" + info.getPromptTokens()
                        + ", completion=" + info.getCompletionTokens()
                        + ", total=" + info.getTotalTokens());
            }

            return info;
        } catch (Exception e) {
            String snippet = rawJsonResponse.length() > 200
                    ? rawJsonResponse.substring(0, 200) + "..."
                    : rawJsonResponse;
            LOG.warn("Failed to parse usage from LLM response: " + e.getMessage()
                    + ", response snippet: " + snippet);
            return null;
        }
    }

    /**
     * 返回最近一次成功提取的 prompt_tokens，无数据返回 -1。
     */
    public int getLastKnownPromptTokens() {
        return lastKnownPromptTokens;
    }

    /**
     * 重置状态。
     */
    public void reset() {
        lastKnownPromptTokens = -1;
    }

    /**
     * 尝试按 OpenAI 格式解析 usage 对象。
     * 格式: {"prompt_tokens": N, "completion_tokens": M, "total_tokens": N+M}
     */
    private TokenUsageInfo tryOpenAIFormat(JSONObject usage) {
        Integer promptTokens = usage.getInteger("prompt_tokens");
        Integer completionTokens = usage.getInteger("completion_tokens");
        Integer totalTokens = usage.getInteger("total_tokens");

        if (promptTokens != null && completionTokens != null && totalTokens != null) {
            return new TokenUsageInfo(promptTokens, completionTokens, totalTokens);
        }
        return null;
    }

    /**
     * 尝试按 Anthropic 格式解析 usage 对象。
     * 格式: {"input_tokens": N, "output_tokens": M}
     * 映射: input_tokens → promptTokens, output_tokens → completionTokens
     * totalTokens = input_tokens + output_tokens
     */
    private TokenUsageInfo tryAnthropicFormat(JSONObject usage) {
        Integer inputTokens = usage.getInteger("input_tokens");
        Integer outputTokens = usage.getInteger("output_tokens");

        if (inputTokens != null && outputTokens != null) {
            return new TokenUsageInfo(inputTokens, outputTokens, inputTokens + outputTokens);
        }
        return null;
    }
}
