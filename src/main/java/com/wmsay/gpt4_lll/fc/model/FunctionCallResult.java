package com.wmsay.gpt4_lll.fc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Function Calling 结果。
 * 封装对话的最终结果和工具调用历史。
 */
public class FunctionCallResult {

    public enum ResultType {
        SUCCESS,
        ERROR,
        MAX_ROUNDS_EXCEEDED,
        DEGRADED
    }

    private final ResultType type;
    private final String content;
    private final List<ToolCallResult> toolCallHistory;
    private final String sessionId;

    private FunctionCallResult(Builder builder) {
        this.type = builder.type;
        this.content = builder.content;
        this.toolCallHistory = builder.toolCallHistory == null ? 
            Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(builder.toolCallHistory));
        this.sessionId = builder.sessionId;
    }

    public ResultType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCallResult> getToolCallHistory() {
        return toolCallHistory;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isSuccess() {
        return type == ResultType.SUCCESS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FunctionCallResult success(String content, String sessionId, List<ToolCallResult> history) {
        return builder()
            .type(ResultType.SUCCESS)
            .content(content)
            .sessionId(sessionId)
            .toolCallHistory(history)
            .build();
    }

    public static FunctionCallResult error(String errorMessage, String sessionId) {
        return builder()
            .type(ResultType.ERROR)
            .content(errorMessage)
            .sessionId(sessionId)
            .build();
    }

    public static FunctionCallResult maxRoundsExceeded(String sessionId, List<ToolCallResult> history) {
        return builder()
            .type(ResultType.MAX_ROUNDS_EXCEEDED)
            .content("Maximum conversation rounds exceeded")
            .sessionId(sessionId)
            .toolCallHistory(history)
            .build();
    }

    public static FunctionCallResult degraded(String reason, String sessionId) {
        return builder()
            .type(ResultType.DEGRADED)
            .content(reason)
            .sessionId(sessionId)
            .build();
    }

    public static class Builder {
        private ResultType type;
        private String content;
        private List<ToolCallResult> toolCallHistory;
        private String sessionId;

        public Builder type(ResultType type) {
            this.type = type;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCallHistory(List<ToolCallResult> toolCallHistory) {
            this.toolCallHistory = toolCallHistory;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public FunctionCallResult build() {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            return new FunctionCallResult(this);
        }
    }
}
