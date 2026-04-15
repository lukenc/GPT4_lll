package com.wmsay.gpt4_lll.fc.model;

import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用追踪。
 * 记录单个工具调用的详细信息,包括输入、输出和执行时间。
 */
public class ToolCallTrace {

    private final String callId;
    private final String toolName;
    private final Map<String, Object> parameters;
    private final ToolResult result;
    private final long startTime;
    private final long endTime;
    private final String parentCallId;

    private ToolCallTrace(Builder builder) {
        this.callId = builder.callId;
        this.toolName = builder.toolName;
        this.parameters = builder.parameters == null ? 
            Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(builder.parameters));
        this.result = builder.result;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
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

    public ToolResult getResult() {
        return result;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getParentCallId() {
        return parentCallId;
    }

    public long getDuration() {
        return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String callId;
        private String toolName;
        private Map<String, Object> parameters;
        private ToolResult result;
        private long startTime;
        private long endTime;
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

        public Builder result(ToolResult result) {
            this.result = result;
            return this;
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder parentCallId(String parentCallId) {
            this.parentCallId = parentCallId;
            return this;
        }

        public ToolCallTrace build() {
            if (callId == null || callId.isEmpty()) {
                throw new IllegalArgumentException("callId is required");
            }
            if (toolName == null || toolName.isEmpty()) {
                throw new IllegalArgumentException("toolName is required");
            }
            if (startTime <= 0) {
                throw new IllegalArgumentException("startTime must be positive");
            }
            return new ToolCallTrace(this);
        }
    }
}
