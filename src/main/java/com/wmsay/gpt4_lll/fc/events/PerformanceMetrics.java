package com.wmsay.gpt4_lll.fc.events;

/**
 * 性能指标聚合数据类 — 不可变，使用 Builder 模式构建。
 * <p>
 * 包含 Agent 运行时的关键聚合指标，如总会话数、平均会话时长、
 * 工具调用次数、LLM 调用次数和错误计数等。
 * <p>
 * 纯框架层数据类，不依赖任何 IntelliJ Platform API。
 */
public final class PerformanceMetrics {

    private final long totalSessions;
    private final int activeSessions;
    private final double averageSessionDurationMs;
    private final long totalToolCalls;
    private final long totalLlmCalls;
    private final long totalErrors;

    private PerformanceMetrics(Builder builder) {
        this.totalSessions = builder.totalSessions;
        this.activeSessions = builder.activeSessions;
        this.averageSessionDurationMs = builder.averageSessionDurationMs;
        this.totalToolCalls = builder.totalToolCalls;
        this.totalLlmCalls = builder.totalLlmCalls;
        this.totalErrors = builder.totalErrors;
    }

    /** 总会话数（历史累计） */
    public long getTotalSessions() { return totalSessions; }

    /** 当前活跃会话数 */
    public int getActiveSessions() { return activeSessions; }

    /** 平均会话持续时间（毫秒） */
    public double getAverageSessionDurationMs() { return averageSessionDurationMs; }

    /** 总工具调用次数 */
    public long getTotalToolCalls() { return totalToolCalls; }

    /** 总 LLM 调用次数 */
    public long getTotalLlmCalls() { return totalLlmCalls; }

    /** 总错误次数 */
    public long getTotalErrors() { return totalErrors; }

    /**
     * 创建新的 Builder 实例。
     *
     * @return 新的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "PerformanceMetrics{" +
                "totalSessions=" + totalSessions +
                ", activeSessions=" + activeSessions +
                ", averageSessionDurationMs=" + averageSessionDurationMs +
                ", totalToolCalls=" + totalToolCalls +
                ", totalLlmCalls=" + totalLlmCalls +
                ", totalErrors=" + totalErrors +
                '}';
    }

    /**
     * PerformanceMetrics 的 Builder。
     */
    public static final class Builder {
        private long totalSessions;
        private int activeSessions;
        private double averageSessionDurationMs;
        private long totalToolCalls;
        private long totalLlmCalls;
        private long totalErrors;

        private Builder() {}

        public Builder totalSessions(long totalSessions) {
            this.totalSessions = totalSessions;
            return this;
        }

        public Builder activeSessions(int activeSessions) {
            this.activeSessions = activeSessions;
            return this;
        }

        public Builder averageSessionDurationMs(double averageSessionDurationMs) {
            this.averageSessionDurationMs = averageSessionDurationMs;
            return this;
        }

        public Builder totalToolCalls(long totalToolCalls) {
            this.totalToolCalls = totalToolCalls;
            return this;
        }

        public Builder totalLlmCalls(long totalLlmCalls) {
            this.totalLlmCalls = totalLlmCalls;
            return this;
        }

        public Builder totalErrors(long totalErrors) {
            this.totalErrors = totalErrors;
            return this;
        }

        /**
         * 构建不可变的 PerformanceMetrics 实例。
         *
         * @return 新的 PerformanceMetrics 实例
         */
        public PerformanceMetrics build() {
            return new PerformanceMetrics(this);
        }
    }
}
