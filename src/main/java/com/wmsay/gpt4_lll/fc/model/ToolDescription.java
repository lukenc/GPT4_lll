package com.wmsay.gpt4_lll.fc.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具描述。
 * 用于生成 OpenAI Function Calling 格式的工具描述。
 */
public class ToolDescription {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    private ToolDescription(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters == null ? 
            Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(builder.parameters));
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private Map<String, Object> parameters;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public ToolDescription build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is required");
            }
            if (description == null || description.isEmpty()) {
                throw new IllegalArgumentException("description is required");
            }
            return new ToolDescription(this);
        }
    }
}
