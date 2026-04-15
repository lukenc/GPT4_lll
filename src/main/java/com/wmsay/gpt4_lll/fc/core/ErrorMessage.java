package com.wmsay.gpt4_lll.fc.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 错误消息。
 * 用于向 LLM 返回格式化的错误信息。
 */
public class ErrorMessage {

    private final String type;
    private final String message;
    private final Map<String, Object> details;
    private final String suggestion;

    private ErrorMessage(Builder builder) {
        this.type = builder.type;
        this.message = builder.message;
        this.details = builder.details == null ? 
            Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(builder.details));
        this.suggestion = builder.suggestion;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String type;
        private String message;
        private Map<String, Object> details;
        private String suggestion;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public ErrorMessage build() {
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("type is required");
            }
            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("message is required");
            }
            return new ErrorMessage(this);
        }
    }
}
