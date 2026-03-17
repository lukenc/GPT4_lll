package com.wmsay.gpt4_lll.fc.model;

/**
 * Function Calling 配置。
 * 定义 function calling 框架的运行时配置参数。
 */
public class FunctionCallConfig {

    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private final int defaultTimeout;
    private final int maxRetries;
    private final int maxRounds;
    private final boolean enableApproval;
    private final boolean enableFunctionCalling;
    private final LogLevel logLevel;
    private final boolean traceExportEnabled;
    private final String memoryStrategy;
    private final int memoryMaxTokens;
    private final int memorySummarizeThreshold;
    private final double memorySimilarityThreshold;
    private final int memoryHardLimitTokens;

    private FunctionCallConfig(Builder builder) {
        this.defaultTimeout = builder.defaultTimeout;
        this.maxRetries = builder.maxRetries;
        this.maxRounds = builder.maxRounds;
        this.enableApproval = builder.enableApproval;
        this.enableFunctionCalling = builder.enableFunctionCalling;
        this.logLevel = builder.logLevel;
        this.traceExportEnabled = builder.traceExportEnabled;
        this.memoryStrategy = builder.memoryStrategy;
        this.memoryMaxTokens = builder.memoryMaxTokens;
        this.memorySummarizeThreshold = builder.memorySummarizeThreshold;
        this.memorySimilarityThreshold = builder.memorySimilarityThreshold;
        this.memoryHardLimitTokens = builder.memoryHardLimitTokens;
    }

    public int getDefaultTimeout() {
        return defaultTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public boolean isEnableApproval() {
        return enableApproval;
    }

    public boolean isEnableFunctionCalling() {
        return enableFunctionCalling;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public boolean isTraceExportEnabled() {
        return traceExportEnabled;
    }

    public String getMemoryStrategy() {
        return memoryStrategy;
    }

    public int getMemoryMaxTokens() {
        return memoryMaxTokens;
    }

    public int getMemorySummarizeThreshold() {
        return memorySummarizeThreshold;
    }

    public double getMemorySimilarityThreshold() {
        return memorySimilarityThreshold;
    }

    public int getMemoryHardLimitTokens() {
        return memoryHardLimitTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FunctionCallConfig defaultConfig() {
        return builder().build();
    }

    public static class Builder {
        private int defaultTimeout = 30;
        private int maxRetries = 3;
        private int maxRounds = 20;
        private boolean enableApproval = true;
        private boolean enableFunctionCalling = true;
        private LogLevel logLevel = LogLevel.INFO;
        private boolean traceExportEnabled = false;
        private String memoryStrategy = "sliding_window";
        private int memoryMaxTokens = 120000;
        private int memorySummarizeThreshold = 100000;
        private double memorySimilarityThreshold = 0.6;
        private int memoryHardLimitTokens = -1;

        public Builder defaultTimeout(int defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder enableApproval(boolean enableApproval) {
            this.enableApproval = enableApproval;
            return this;
        }

        public Builder enableFunctionCalling(boolean enableFunctionCalling) {
            this.enableFunctionCalling = enableFunctionCalling;
            return this;
        }

        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder traceExportEnabled(boolean traceExportEnabled) {
            this.traceExportEnabled = traceExportEnabled;
            return this;
        }

        public Builder memoryStrategy(String memoryStrategy) {
            this.memoryStrategy = memoryStrategy;
            return this;
        }

        public Builder memoryMaxTokens(int memoryMaxTokens) {
            this.memoryMaxTokens = memoryMaxTokens;
            return this;
        }

        public Builder memorySummarizeThreshold(int memorySummarizeThreshold) {
            this.memorySummarizeThreshold = memorySummarizeThreshold;
            return this;
        }

        public Builder memorySimilarityThreshold(double memorySimilarityThreshold) {
            this.memorySimilarityThreshold = memorySimilarityThreshold;
            return this;
        }

        public Builder memoryHardLimitTokens(int memoryHardLimitTokens) {
            this.memoryHardLimitTokens = memoryHardLimitTokens;
            return this;
        }

        public FunctionCallConfig build() {
            if (defaultTimeout <= 0) {
                throw new IllegalArgumentException("defaultTimeout must be positive");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be non-negative");
            }
            if (maxRounds <= 0) {
                throw new IllegalArgumentException("maxRounds must be positive");
            }
            if (logLevel == null) {
                throw new IllegalArgumentException("logLevel is required");
            }
            return new FunctionCallConfig(this);
        }
    }
}
