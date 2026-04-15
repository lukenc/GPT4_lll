package com.wmsay.gpt4_lll.fc.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig.LogLevel;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * 配置加载器，支持从 JSON 文件和环境变量加载 {@link FunctionCallConfig}。
 * <p>
 * 加载优先级：环境变量 &gt; 配置文件 &gt; 默认值。
 * 当配置文件不存在或无效时，使用默认配置并记录警告。
 *
 * <p>支持的环境变量：
 * <ul>
 *   <li>{@code LLL_FC_ENABLED} — 是否启用 function calling（true/false）</li>
 *   <li>{@code LLL_FC_LOG_LEVEL} — 日志级别（DEBUG/INFO/WARN/ERROR）</li>
 *   <li>{@code LLL_FC_MAX_ROUNDS} — 最大对话轮次（正整数）</li>
 *   <li>{@code LLL_FC_TRACE_EXPORT} — 是否启用追踪导出（true/false）</li>
 *   <li>{@code LLL_FC_MEMORY_STRATEGY} — 记忆管理策略名称</li>
 *   <li>{@code LLL_FC_MEMORY_MAX_TOKENS} — 记忆最大 token 数（正整数）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ConfigLoader loader = new ConfigLoader();
 *
 * // 完整加载：文件 + 环境变量覆盖
 * FunctionCallConfig config = loader.load(Path.of(".lll/function-calling-config.json"));
 *
 * // 仅从文件加载
 * FunctionCallConfig fileConfig = loader.loadFromFile(path);
 *
 * // 仅从环境变量加载
 * FunctionCallConfig envConfig = loader.loadFromEnv();
 * }</pre>
 *
 * @see FunctionCallConfig
 */
public class ConfigLoader {

    private static final Logger LOG = Logger.getLogger(ConfigLoader.class.getName());

    static final String ENV_ENABLED = "LLL_FC_ENABLED";
    static final String ENV_LOG_LEVEL = "LLL_FC_LOG_LEVEL";
    static final String ENV_MAX_ROUNDS = "LLL_FC_MAX_ROUNDS";
    static final String ENV_TRACE_EXPORT = "LLL_FC_TRACE_EXPORT";
    static final String ENV_MEMORY_STRATEGY = "LLL_FC_MEMORY_STRATEGY";
    static final String ENV_MEMORY_MAX_TOKENS = "LLL_FC_MEMORY_MAX_TOKENS";
    static final String ENV_EXECUTION_STRATEGY = "LLL_FC_EXECUTION_STRATEGY";

    private final Function<String, String> envReader;

    /** Default constructor reads from real system environment. */
    public ConfigLoader() {
        this(System::getenv);
    }

    /**
     * Test-friendly constructor accepting a custom env var reader.
     *
     * @param envReader 环境变量读取函数，接收变量名返回变量值
     */
    public ConfigLoader(Function<String, String> envReader) {
        this.envReader = envReader;
    }

    /**
     * 从 JSON 文件加载配置。
     * 如果文件不存在或解析失败，返回默认配置并记录警告。
     *
     * @param path JSON 配置文件路径，可以为 null
     * @return 解析后的配置，文件不存在或无效时返回默认配置
     */
    public FunctionCallConfig loadFromFile(Path path) {
        if (path == null || !Files.exists(path)) {
            LOG.log(Level.WARNING, "Config file not found: " + path + ", using default config");
            return FunctionCallConfig.defaultConfig();
        }

        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to read config file: " + path + ", using default config", e);
            return FunctionCallConfig.defaultConfig();
        }

        return parseJson(content);
    }

    /**
     * 从环境变量加载配置。
     * 读取 LLL_FC_ENABLED, LLL_FC_LOG_LEVEL, LLL_FC_MAX_ROUNDS, LLL_FC_TRACE_EXPORT。
     * 未设置的环境变量使用默认值。
     *
     * @return 基于环境变量的配置
     */
    public FunctionCallConfig loadFromEnv() {
        FunctionCallConfig.Builder builder = FunctionCallConfig.builder();

        String enabled = envReader.apply(ENV_ENABLED);
        if (enabled != null && !enabled.isBlank()) {
            builder.enableFunctionCalling("true".equalsIgnoreCase(enabled.trim()));
        }

        String logLevel = envReader.apply(ENV_LOG_LEVEL);
        if (logLevel != null && !logLevel.isBlank()) {
            try {
                builder.logLevel(LogLevel.valueOf(logLevel.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Invalid env LLL_FC_LOG_LEVEL='" + logLevel + "', using default INFO");
            }
        }

        String maxRounds = envReader.apply(ENV_MAX_ROUNDS);
        if (maxRounds != null && !maxRounds.isBlank()) {
            try {
                int rounds = Integer.parseInt(maxRounds.trim());
                if (rounds > 0) {
                    builder.maxRounds(rounds);
                } else {
                    LOG.log(Level.WARNING, "Invalid env LLL_FC_MAX_ROUNDS='" + maxRounds + "', must be positive");
                }
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Invalid env LLL_FC_MAX_ROUNDS='" + maxRounds + "', not a number");
            }
        }

        String traceExport = envReader.apply(ENV_TRACE_EXPORT);
        if (traceExport != null && !traceExport.isBlank()) {
            builder.traceExportEnabled("true".equalsIgnoreCase(traceExport.trim()));
        }

        String memoryStrategy = envReader.apply(ENV_MEMORY_STRATEGY);
        if (memoryStrategy != null && !memoryStrategy.isBlank()) {
            builder.memoryStrategy(memoryStrategy.trim());
        }

        String memoryMaxTokens = envReader.apply(ENV_MEMORY_MAX_TOKENS);
        if (memoryMaxTokens != null && !memoryMaxTokens.isBlank()) {
            try {
                int tokens = Integer.parseInt(memoryMaxTokens.trim());
                if (tokens > 0) {
                    builder.memoryMaxTokens(tokens);
                } else {
                    LOG.log(Level.WARNING, "Invalid env LLL_FC_MEMORY_MAX_TOKENS='" + memoryMaxTokens + "', must be positive");
                }
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Invalid env LLL_FC_MEMORY_MAX_TOKENS='" + memoryMaxTokens + "', not a number");
            }
        }

        String executionStrategy = envReader.apply(ENV_EXECUTION_STRATEGY);
        if (executionStrategy != null && !executionStrategy.isBlank()) {
            builder.executionStrategy(executionStrategy.trim());
        }

        return builder.build();
    }

    /**
     * 完整加载流程：先从文件加载，再用环境变量覆盖。
     * 环境变量优先级高于配置文件。
     *
     * @param path JSON 配置文件路径
     * @return 最终合并后的配置
     */
    public FunctionCallConfig load(Path path) {
        FunctionCallConfig fileConfig = loadFromFile(path);
        return mergeWithEnv(fileConfig);
    }

    /**
     * 将文件配置与环境变量合并。环境变量中设置的值覆盖文件配置。
     */
    FunctionCallConfig mergeWithEnv(FunctionCallConfig fileConfig) {
        FunctionCallConfig.Builder builder = FunctionCallConfig.builder()
                .defaultTimeout(fileConfig.getDefaultTimeout())
                .maxRetries(fileConfig.getMaxRetries())
                .maxRounds(fileConfig.getMaxRounds())
                .enableApproval(fileConfig.isEnableApproval())
                .enableFunctionCalling(fileConfig.isEnableFunctionCalling())
                .logLevel(fileConfig.getLogLevel())
                .traceExportEnabled(fileConfig.isTraceExportEnabled())
                .memoryStrategy(fileConfig.getMemoryStrategy())
                .memoryMaxTokens(fileConfig.getMemoryMaxTokens())
                .memorySummarizeThreshold(fileConfig.getMemorySummarizeThreshold())
                .memorySimilarityThreshold(fileConfig.getMemorySimilarityThreshold())
                .memoryHardLimitTokens(fileConfig.getMemoryHardLimitTokens())
                .executionStrategy(fileConfig.getExecutionStrategy());

        String enabled = envReader.apply(ENV_ENABLED);
        if (enabled != null && !enabled.isBlank()) {
            builder.enableFunctionCalling("true".equalsIgnoreCase(enabled.trim()));
        }

        String logLevel = envReader.apply(ENV_LOG_LEVEL);
        if (logLevel != null && !logLevel.isBlank()) {
            try {
                builder.logLevel(LogLevel.valueOf(logLevel.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Invalid env LLL_FC_LOG_LEVEL='" + logLevel + "', keeping file value");
            }
        }

        String maxRounds = envReader.apply(ENV_MAX_ROUNDS);
        if (maxRounds != null && !maxRounds.isBlank()) {
            try {
                int rounds = Integer.parseInt(maxRounds.trim());
                if (rounds > 0) {
                    builder.maxRounds(rounds);
                } else {
                    LOG.log(Level.WARNING, "Invalid env LLL_FC_MAX_ROUNDS='" + maxRounds + "', keeping file value");
                }
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Invalid env LLL_FC_MAX_ROUNDS='" + maxRounds + "', keeping file value");
            }
        }

        String traceExport = envReader.apply(ENV_TRACE_EXPORT);
        if (traceExport != null && !traceExport.isBlank()) {
            builder.traceExportEnabled("true".equalsIgnoreCase(traceExport.trim()));
        }

        String memoryStrategy = envReader.apply(ENV_MEMORY_STRATEGY);
        if (memoryStrategy != null && !memoryStrategy.isBlank()) {
            builder.memoryStrategy(memoryStrategy.trim());
        }

        String memoryMaxTokens = envReader.apply(ENV_MEMORY_MAX_TOKENS);
        if (memoryMaxTokens != null && !memoryMaxTokens.isBlank()) {
            try {
                int tokens = Integer.parseInt(memoryMaxTokens.trim());
                if (tokens > 0) {
                    builder.memoryMaxTokens(tokens);
                } else {
                    LOG.log(Level.WARNING, "Invalid env LLL_FC_MEMORY_MAX_TOKENS='" + memoryMaxTokens + "', keeping file value");
                }
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Invalid env LLL_FC_MEMORY_MAX_TOKENS='" + memoryMaxTokens + "', keeping file value");
            }
        }

        String executionStrategy = envReader.apply(ENV_EXECUTION_STRATEGY);
        if (executionStrategy != null && !executionStrategy.isBlank()) {
            builder.executionStrategy(executionStrategy.trim());
        }

        return builder.build();
    }

    /**
     * 解析 JSON 字符串为 FunctionCallConfig。
     * 对每个字段进行单独解析，无效字段使用默认值。
     * 最终通过 Builder 验证整体配置有效性。
     */
    FunctionCallConfig parseJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            LOG.log(Level.WARNING, "Empty config content, using default config");
            return FunctionCallConfig.defaultConfig();
        }

        JSONObject json;
        try {
            json = JSON.parseObject(jsonContent);
        } catch (JSONException e) {
            LOG.log(Level.WARNING, "Invalid JSON in config: " + e.getMessage() + ", using default config");
            return FunctionCallConfig.defaultConfig();
        }

        if (json == null) {
            LOG.log(Level.WARNING, "Parsed config is null, using default config");
            return FunctionCallConfig.defaultConfig();
        }

        FunctionCallConfig.Builder builder = FunctionCallConfig.builder();

        if (json.containsKey("defaultTimeout")) {
            builder.defaultTimeout(json.getIntValue("defaultTimeout"));
        }
        if (json.containsKey("maxRetries")) {
            builder.maxRetries(json.getIntValue("maxRetries"));
        }
        if (json.containsKey("maxRounds")) {
            builder.maxRounds(json.getIntValue("maxRounds"));
        }
        if (json.containsKey("enableApproval")) {
            builder.enableApproval(json.getBooleanValue("enableApproval"));
        }
        if (json.containsKey("enableFunctionCalling")) {
            builder.enableFunctionCalling(json.getBooleanValue("enableFunctionCalling"));
        }
        if (json.containsKey("logLevel")) {
            String level = json.getString("logLevel");
            try {
                builder.logLevel(LogLevel.valueOf(level.toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Invalid logLevel '" + level + "', using default INFO");
            }
        }
        if (json.containsKey("traceExportEnabled")) {
            builder.traceExportEnabled(json.getBooleanValue("traceExportEnabled"));
        }
        if (json.containsKey("executionStrategy")) {
            String strategy = json.getString("executionStrategy");
            if (strategy != null && !strategy.isBlank()) {
                builder.executionStrategy(strategy.trim());
            }
        }

        JSONObject memory = json.getJSONObject("memory");
        if (memory != null) {
            if (memory.containsKey("strategy")) {
                String strategy = memory.getString("strategy");
                if (strategy != null && !strategy.isBlank()) {
                    builder.memoryStrategy(strategy.trim());
                } else {
                    LOG.log(Level.WARNING, "Invalid memory.strategy (empty), using default");
                }
            }
            if (memory.containsKey("maxTokens")) {
                int val = memory.getIntValue("maxTokens");
                if (val > 0) {
                    builder.memoryMaxTokens(val);
                } else {
                    LOG.log(Level.WARNING, "Invalid memory.maxTokens '" + val + "', using default");
                }
            }
            if (memory.containsKey("summarizeThreshold")) {
                int val = memory.getIntValue("summarizeThreshold");
                if (val > 0) {
                    builder.memorySummarizeThreshold(val);
                } else {
                    LOG.log(Level.WARNING, "Invalid memory.summarizeThreshold '" + val + "', using default");
                }
            }
            if (memory.containsKey("similarityThreshold")) {
                double val = memory.getDoubleValue("similarityThreshold");
                if (val >= 0.0 && val <= 1.0) {
                    builder.memorySimilarityThreshold(val);
                } else {
                    LOG.log(Level.WARNING, "Invalid memory.similarityThreshold '" + val + "', using default");
                }
            }
            if (memory.containsKey("hardLimitTokens")) {
                int val = memory.getIntValue("hardLimitTokens");
                if (val > 0) {
                    builder.memoryHardLimitTokens(val);
                } else {
                    LOG.log(Level.WARNING, "Invalid memory.hardLimitTokens '" + val + "', using default");
                }
            }
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Config validation failed: " + e.getMessage() + ", using default config");
            return FunctionCallConfig.defaultConfig();
        }
    }
}
