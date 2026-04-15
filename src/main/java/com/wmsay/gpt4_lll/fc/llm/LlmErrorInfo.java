package com.wmsay.gpt4_lll.fc.llm;

/**
 * LLM API 错误的结构化信息。不可变值对象。
 * <p>
 * 封装错误类型、HTTP 状态码、用户可读消息、修复建议和原始错误详情，
 * 供调用路径在 ChatView 中展示中英双语的错误反馈。
 * <p>
 * 使用方式：
 * <pre>
 * LlmErrorInfo info = LlmErrorInfo.builder()
 *         .errorType("authentication_error")
 *         .httpStatusCode(401)
 *         .message("认证错误 / Authentication Error")
 *         .suggestion("请在 Settings 中检查 API Key 配置")
 *         .rawDetail("Invalid API key provided")
 *         .build();
 * </pre>
 */
public class LlmErrorInfo {

    private final String errorType;
    private final int httpStatusCode;
    private final String message;
    private final String suggestion;
    private final String rawDetail;

    private LlmErrorInfo(String errorType, int httpStatusCode,
                         String message, String suggestion, String rawDetail) {
        this.errorType = errorType;
        this.httpStatusCode = httpStatusCode;
        this.message = message;
        this.suggestion = suggestion;
        this.rawDetail = rawDetail;
    }

    /** 错误类型标识（如 "authentication_error"、"rate_limit_error"）。 */
    public String getErrorType() {
        return errorType;
    }

    /** HTTP 状态码。非 HTTP 错误时为 0。 */
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /** 中英双语用户可读消息。 */
    public String getMessage() {
        return message;
    }

    /** 中英双语修复建议。 */
    public String getSuggestion() {
        return suggestion;
    }

    /** 原始错误详情（API 返回的 error.message 或截取的响应体前 500 字符）。 */
    public String getRawDetail() {
        return rawDetail;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String errorType;
        private int httpStatusCode;
        private String message;
        private String suggestion;
        private String rawDetail;

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder httpStatusCode(int httpStatusCode) {
            this.httpStatusCode = httpStatusCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder rawDetail(String rawDetail) {
            this.rawDetail = rawDetail;
            return this;
        }

        public LlmErrorInfo build() {
            if (errorType == null || errorType.isBlank()) {
                throw new IllegalArgumentException("errorType is required / errorType 不能为空");
            }
            return new LlmErrorInfo(
                    errorType,
                    httpStatusCode,
                    message != null ? message : "",
                    suggestion != null ? suggestion : "",
                    rawDetail != null ? rawDetail : ""
            );
        }
    }
}
