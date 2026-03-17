package com.wmsay.gpt4_lll.fc.model;

import java.util.Collections;
import java.util.Map;

/**
 * 工具调用请求。
 * 表示从 LLM 响应中解析出的单个工具调用。
 */
public class ToolCall {

    private final String callId;
    private final String toolName;
    private final Map<String, Object> parameters;
    private final String parentCallId;

    private ToolCall(Builder builder) {
        this.callId = builder.callId;
        this.toolName = builder.toolName;
        this.parameters = builder.parameters == null ? 
            Collections.emptyMap() : Collections.unmodifiableMap(builder.parameters);
        this.parentCallId = builder.parentCallId;
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getParentCallId() {
        return parentCallId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String callId;
        private String toolName;
        private Map<String, Object> parameters;
        private String parentCallId;

        public Builder callId(String callId) {
            this.callId = callId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder parentCallId(String parentCallId) {
            this.parentCallId = parentCallId;
            return this;
        }

        public ToolCall build() {
            if (callId == null || callId.isEmpty()) {
                throw new IllegalArgumentException("callId is required");
            }
            if (toolName == null || toolName.isEmpty()) {
                throw new IllegalArgumentException("toolName is required");
            }
            return new ToolCall(this);
        }
    }
}
