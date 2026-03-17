package com.wmsay.gpt4_lll.fc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话追踪。
 * 记录完整的 function calling 会话信息,包括所有工具调用和错误。
 */
public class SessionTrace {

    private final String sessionId;
    private final long startTime;
    private final long endTime;
    private final List<ToolCallTrace> toolCalls;
    private final List<Throwable> errors;

    private SessionTrace(Builder builder) {
        this.sessionId = builder.sessionId;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.toolCalls = builder.toolCalls == null ? 
            Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(builder.toolCalls));
        this.errors = builder.errors == null ? 
            Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(builder.errors));
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<ToolCallTrace> getToolCalls() {
        return toolCalls;
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public long getDuration() {
        return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
    }

    public int getToolCallCount() {
        return toolCalls.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private long startTime;
        private long endTime;
        private List<ToolCallTrace> toolCalls;
        private List<Throwable> errors;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
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

        public Builder toolCalls(List<ToolCallTrace> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder errors(List<Throwable> errors) {
            this.errors = errors;
            return this;
        }

        public SessionTrace build() {
            if (sessionId == null || sessionId.isEmpty()) {
                throw new IllegalArgumentException("sessionId is required");
            }
            if (startTime <= 0) {
                throw new IllegalArgumentException("startTime must be positive");
            }
            return new SessionTrace(this);
        }
    }
}
