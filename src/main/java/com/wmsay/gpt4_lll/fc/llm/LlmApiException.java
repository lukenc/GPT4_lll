package com.wmsay.gpt4_lll.fc.llm;

/**
 * LLM API 调用异常。携带结构化的错误信息。
 * <p>
 * 当 LlmClient 检测到 HTTP 错误状态码或错误响应体时抛出此异常，
 * 调用方通过 {@link #getErrorInfo()} 获取结构化的 {@link LlmErrorInfo}，
 * 用于在 ChatView 中展示中英双语的错误消息和修复建议。
 */
public class LlmApiException extends RuntimeException {

    private final LlmErrorInfo errorInfo;

    public LlmApiException(LlmErrorInfo errorInfo) {
        super(errorInfo.getMessage());
        this.errorInfo = errorInfo;
    }

    /** 获取结构化的错误信息。 */
    public LlmErrorInfo getErrorInfo() {
        return errorInfo;
    }
}
