package com.wmsay.gpt4_lll.fc.core;

/**
 * AgentRuntime 运行时配置 — 不可变对象。
 * 使用 Builder 模式构建，提供运行时参数的集中配置。
 */
public class AgentRuntimeConfig {

    private final int maxConcurrentSessions;
    private final int maxDelegationDepth;
    private final int delegationTimeoutSeconds;
    private final int sessionIdleTimeoutSeconds;
    private final boolean recruitMode;
    private final int subAgentContextFallbackMessageCount;
    private final int subAgentTimeoutSeconds;

    private AgentRuntimeConfig(Builder builder) {
        this.maxConcurrentSessions = builder.maxConcurrentSessions;
        this.maxDelegationDepth = builder.maxDelegationDepth;
        this.delegationTimeoutSeconds = builder.delegationTimeoutSeconds;
        this.sessionIdleTimeoutSeconds = builder.sessionIdleTimeoutSeconds;
        this.recruitMode = builder.recruitMode;
        this.subAgentContextFallbackMessageCount = builder.subAgentContextFallbackMessageCount;
        this.subAgentTimeoutSeconds = builder.subAgentTimeoutSeconds;
    }

    public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public int getMaxDelegationDepth() { return maxDelegationDepth; }
    public int getDelegationTimeoutSeconds() { return delegationTimeoutSeconds; }
    public int getSessionIdleTimeoutSeconds() { return sessionIdleTimeoutSeconds; }
    public boolean isRecruitMode() { return recruitMode; }
    public int getSubAgentContextFallbackMessageCount() { return subAgentContextFallbackMessageCount; }
    public int getSubAgentTimeoutSeconds() { return subAgentTimeoutSeconds; }

    public static AgentRuntimeConfig defaultConfig() { return builder().build(); }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int maxConcurrentSessions = 5;
        private int maxDelegationDepth = 3;
        private int delegationTimeoutSeconds = 120;
        private int sessionIdleTimeoutSeconds = 600;
        private boolean recruitMode = false;
        private int subAgentContextFallbackMessageCount = 5;
        private int subAgentTimeoutSeconds = 180;

        public Builder maxConcurrentSessions(int v) { this.maxConcurrentSessions = v; return this; }
        public Builder maxDelegationDepth(int v) { this.maxDelegationDepth = v; return this; }
        public Builder delegationTimeoutSeconds(int v) { this.delegationTimeoutSeconds = v; return this; }
        public Builder sessionIdleTimeoutSeconds(int v) { this.sessionIdleTimeoutSeconds = v; return this; }
        public Builder recruitMode(boolean v) { this.recruitMode = v; return this; }
        public Builder subAgentContextFallbackMessageCount(int v) { this.subAgentContextFallbackMessageCount = v; return this; }
        public Builder subAgentTimeoutSeconds(int v) { this.subAgentTimeoutSeconds = v; return this; }

        public AgentRuntimeConfig build() {
            if (maxConcurrentSessions < 1)
                throw new IllegalArgumentException("maxConcurrentSessions must be >= 1");
            if (maxDelegationDepth < 0)
                throw new IllegalArgumentException("maxDelegationDepth must be >= 0");
            return new AgentRuntimeConfig(this);
        }
    }
}
