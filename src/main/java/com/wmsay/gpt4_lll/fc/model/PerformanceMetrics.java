package com.wmsay.gpt4_lll.fc.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 性能指标。
 * 记录 function calling 框架的性能统计信息。
 */
public class PerformanceMetrics {

    private final double averageSessionDuration;
    private final double averageToolCallDuration;
    private final long totalToolCalls;
    private final double successRate;
    private final double errorRate;
    private final Map<String, Long> errorsByType;

    private PerformanceMetrics(Builder builder) {
        this.averageSessionDuration = builder.averageSessionDuration;
        this.averageToolCallDuration = builder.averageToolCallDuration;
        this.totalToolCalls = builder.totalToolCalls;
        this.successRate = builder.successRate;
        this.errorRate = builder.errorRate;
        this.errorsByType = builder.errorsByType == null ? 
            Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(builder.errorsByType));
    }

    public double getAverageSessionDuration() {
        return averageSessionDuration;
    }

    public double getAverageToolCallDuration() {
        return averageToolCallDuration;
    }

    public long getTotalToolCalls() {
        return totalToolCalls;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public Map<String, Long> getErrorsByType() {
        return errorsByType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double averageSessionDuration;
        private double averageToolCallDuration;
        private long totalToolCalls;
        private double successRate;
        private double errorRate;
        private Map<String, Long> errorsByType;

        public Builder averageSessionDuration(double averageSessionDuration) {
            this.averageSessionDuration = averageSessionDuration;
            return this;
        }

        public Builder averageToolCallDuration(double averageToolCallDuration) {
            this.averageToolCallDuration = averageToolCallDuration;
            return this;
        }

        public Builder totalToolCalls(long totalToolCalls) {
            this.totalToolCalls = totalToolCalls;
            return this;
        }

        public Builder successRate(double successRate) {
            this.successRate = successRate;
            return this;
        }

        public Builder errorRate(double errorRate) {
            this.errorRate = errorRate;
            return this;
        }

        public Builder errorsByType(Map<String, Long> errorsByType) {
            this.errorsByType = errorsByType;
            return this;
        }

        public PerformanceMetrics build() {
            if (successRate < 0 || successRate > 1) {
                throw new IllegalArgumentException("successRate must be between 0 and 1");
            }
            if (errorRate < 0 || errorRate > 1) {
                throw new IllegalArgumentException("errorRate must be between 0 and 1");
            }
            return new PerformanceMetrics(this);
        }
    }
}
