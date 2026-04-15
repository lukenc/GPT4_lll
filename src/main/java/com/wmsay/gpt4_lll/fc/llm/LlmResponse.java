package com.wmsay.gpt4_lll.fc.llm;

/**
 * LLM 响应封装。
 * 为调用方提供结构化的响应结果，包括内容、成功状态和错误信息。
 * <p>
 * 使用方式：
 * <pre>
 * LlmResponse response = LlmClient.syncChat(request);
 * if (response.isSuccess()) {
 *     String content = response.getContent();
 * } else {
 *     Exception error = response.getError();
 * }
 * </pre>
 */
public class LlmResponse {

    private final String content;
    private final boolean success;
    private final Exception error;

    private LlmResponse(String content, boolean success, Exception error) {
        this.content = content;
        this.success = success;
        this.error = error;
    }

    /**
     * 创建成功响应。
     *
     * @param content AI 回复的完整文本
     */
    public static LlmResponse success(String content) {
        return new LlmResponse(content, true, null);
    }

    /**
     * 创建失败响应。
     *
     * @param error 异常信息
     */
    public static LlmResponse failure(Exception error) {
        return new LlmResponse("", false, error);
    }

    /**
     * 获取 AI 回复的完整文本。
     * 仅包含通过 SSE data 行解析出的正式回复内容（不含非 data 行）。
     */
    public String getContent() {
        return content;
    }

    /**
     * 请求是否成功。
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取错误信息（仅在 isSuccess() 返回 false 时有值）。
     */
    public Exception getError() {
        return error;
    }
}
