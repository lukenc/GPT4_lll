package com.wmsay.gpt4_lll.fc.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentDefinition {
    private final String id;
    private final String name;
    private final String systemPrompt;
    private final List<String> availableToolNames;
    private final String strategyName;
    private final String memoryStrategy;

    private AgentDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.systemPrompt = builder.systemPrompt;
        this.availableToolNames = builder.availableToolNames == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(builder.availableToolNames));
        this.strategyName = builder.strategyName;
        this.memoryStrategy = builder.memoryStrategy;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSystemPrompt() { return systemPrompt; }
    public List<String> getAvailableToolNames() { return availableToolNames; }
    public String getStrategyName() { return strategyName; }
    public String getMemoryStrategy() { return memoryStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String name;
        private String systemPrompt;
        private List<String> availableToolNames;
        private String strategyName = "react";
        private String memoryStrategy = "sliding_window";

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder availableToolNames(List<String> names) { this.availableToolNames = names; return this; }
        public Builder strategyName(String strategyName) { this.strategyName = strategyName; return this; }
        public Builder memoryStrategy(String memoryStrategy) { this.memoryStrategy = memoryStrategy; return this; }

        public AgentDefinition build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("AgentDefinition id must not be null or empty");
            }
            if (systemPrompt == null) {
                throw new IllegalArgumentException("AgentDefinition systemPrompt must not be null");
            }
            return new AgentDefinition(this);
        }
    }
}
