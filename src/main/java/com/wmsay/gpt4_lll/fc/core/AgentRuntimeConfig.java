package com.wmsay.gpt4_lll.fc.core;

import com.wmsay.gpt4_lll.fc.skill.SkillMatchingMode;

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
    private final SkillMatchingMode skillMatchingMode;
    private final int inContextSkillThreshold;

    private AgentRuntimeConfig(Builder builder) {
        this.maxConcurrentSessions = builder.maxConcurrentSessions;
        this.maxDelegationDepth = builder.maxDelegationDepth;
        this.delegationTimeoutSeconds = builder.delegationTimeoutSeconds;
        this.sessionIdleTimeoutSeconds = builder.sessionIdleTimeoutSeconds;
        this.recruitMode = builder.recruitMode;
        this.subAgentContextFallbackMessageCount = builder.subAgentContextFallbackMessageCount;
        this.subAgentTimeoutSeconds = builder.subAgentTimeoutSeconds;
        this.skillMatchingMode = builder.skillMatchingMode;
        this.inContextSkillThreshold = builder.inContextSkillThreshold;
    }

    public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public int getMaxDelegationDepth() { return maxDelegationDepth; }
    public int getDelegationTimeoutSeconds() { return delegationTimeoutSeconds; }
    public int getSessionIdleTimeoutSeconds() { return sessionIdleTimeoutSeconds; }
    public boolean isRecruitMode() { return recruitMode; }
    public int getSubAgentContextFallbackMessageCount() { return subAgentContextFallbackMessageCount; }
    public int getSubAgentTimeoutSeconds() { return subAgentTimeoutSeconds; }
    public SkillMatchingMode getSkillMatchingMode() { return skillMatchingMode; }
    public int getInContextSkillThreshold() { return inContextSkillThreshold; }

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
        private SkillMatchingMode skillMatchingMode = SkillMatchingMode.AUTO;
        private int inContextSkillThreshold = 30;

        public Builder maxConcurrentSessions(int v) { this.maxConcurrentSessions = v; return this; }
        public Builder maxDelegationDepth(int v) { this.maxDelegationDepth = v; return this; }
        public Builder delegationTimeoutSeconds(int v) { this.delegationTimeoutSeconds = v; return this; }
        public Builder sessionIdleTimeoutSeconds(int v) { this.sessionIdleTimeoutSeconds = v; return this; }
        public Builder recruitMode(boolean v) { this.recruitMode = v; return this; }
        public Builder subAgentContextFallbackMessageCount(int v) { this.subAgentContextFallbackMessageCount = v; return this; }
        public Builder subAgentTimeoutSeconds(int v) { this.subAgentTimeoutSeconds = v; return this; }
        public Builder skillMatchingMode(SkillMatchingMode v) { this.skillMatchingMode = v; return this; }
        public Builder inContextSkillThreshold(int v) { this.inContextSkillThreshold = v; return this; }

        public AgentRuntimeConfig build() {
            if (maxConcurrentSessions < 1)
                throw new IllegalArgumentException("maxConcurrentSessions must be >= 1");
            if (maxDelegationDepth < 0)
                throw new IllegalArgumentException("maxDelegationDepth must be >= 0");
            if (skillMatchingMode == null)
                throw new IllegalArgumentException("skillMatchingMode must not be null");
            if (inContextSkillThreshold < 0)
                throw new IllegalArgumentException("inContextSkillThreshold must be >= 0");
            return new AgentRuntimeConfig(this);
        }
    }
}
