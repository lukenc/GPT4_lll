package com.wmsay.gpt4_lll.fc.model;

import com.wmsay.gpt4_lll.fc.core.ErrorMessage;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

/**
 * 工具调用结果。
 * 封装工具执行的结果、状态和错误信息。
 */
public class ToolCallResult {

    public enum ResultStatus {
        SUCCESS,
        VALIDATION_ERROR,
        EXECUTION_ERROR,
        TIMEOUT,
        USER_REJECTED,
        TOOL_NOT_FOUND
    }

    private final String callId;
    private final String toolName;
    private final ResultStatus status;
    private final ToolResult result;
    private final ErrorMessage error;
    private final long durationMs;

    private ToolCallResult(Builder builder) {
        this.callId = builder.callId;
        this.toolName = builder.toolName;
        this.status = builder.status;
        this.result = builder.result;
        this.error = builder.error;
        this.durationMs = builder.durationMs;
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public ToolResult getResult() {
        return result;
    }

    public ErrorMessage getError() {
        return error;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSuccess() {
        return status == ResultStatus.SUCCESS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ToolCallResult success(String callId, String toolName, ToolResult result, long durationMs) {
        return builder()
            .callId(callId)
            .toolName(toolName)
            .status(ResultStatus.SUCCESS)
            .result(result)
            .durationMs(durationMs)
            .build();
    }

    public static ToolCallResult validationError(String callId, String toolName, ErrorMessage error) {
        return builder()
            .callId(callId)
            .toolName(toolName)
            .status(ResultStatus.VALIDATION_ERROR)
            .error(error)
            .build();
    }

    public static ToolCallResult executionError(String callId, String toolName, ErrorMessage error, long durationMs) {
        return builder()
            .callId(callId)
            .toolName(toolName)
            .status(ResultStatus.EXECUTION_ERROR)
            .error(error)
            .durationMs(durationMs)
            .build();
    }

    public static class Builder {
        private String callId;
        private String toolName;
        private ResultStatus status;
        private ToolResult result;
        private ErrorMessage error;
        private long durationMs;

        public Builder callId(String callId) {
            this.callId = callId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder status(ResultStatus status) {
            this.status = status;
            return this;
        }

        public Builder result(ToolResult result) {
            this.result = result;
            return this;
        }

        public Builder error(ErrorMessage error) {
            this.error = error;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public ToolCallResult build() {
            if (callId == null || callId.isEmpty()) {
                throw new IllegalArgumentException("callId is required");
            }
            if (toolName == null || toolName.isEmpty()) {
                throw new IllegalArgumentException("toolName is required");
            }
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
            return new ToolCallResult(this);
        }
    }
}
