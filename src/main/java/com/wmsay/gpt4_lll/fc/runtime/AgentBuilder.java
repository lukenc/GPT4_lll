package com.wmsay.gpt4_lll.fc.runtime;

import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.llm.LlmClient;
import com.wmsay.gpt4_lll.fc.llm.LlmProviderAdapter;
import com.wmsay.gpt4_lll.fc.llm.LlmProviderAdapterRegistry;
import com.wmsay.gpt4_lll.fc.llm.LlmProviderConfig;
import com.wmsay.gpt4_lll.fc.llm.LlmRequest;
import com.wmsay.gpt4_lll.fc.llm.ProtocolAdapterRegistry;
import com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.tools.DefaultApprovalProvider;
import com.wmsay.gpt4_lll.fc.tools.ErrorHandler;
import com.wmsay.gpt4_lll.fc.tools.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.tools.RetryStrategy;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolRegistry;
import com.wmsay.gpt4_lll.fc.tools.ValidationEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Agent 高层 Builder API。
 * <p>
 * 提供流畅的构建器接口，最简场景下 10 行代码即可创建并运行一个完整 Agent：
 * <pre>{@code
 * Agent agent = Agent.builder()
 *     .provider("OpenAI")
 *     .apiKey("sk-xxx")
 *     .model("gpt-4")
 *     .systemPrompt("你是一个编程助手")
 *     .build();
 * String reply = agent.chat("帮我写一个快速排序");
 * agent.close();
 * }</pre>
 * <p>
 * 默认值：
 * <ul>
 *   <li>执行策略：react</li>
 *   <li>记忆策略：sliding_window</li>
 *   <li>最大轮次：60</li>
 *   <li>超时：30 秒</li>
 * </ul>
 *
 * @see Agent
 * @see AgentRuntime
 */
public class AgentBuilder {

    private static final Logger LOG = Logger.getLogger(AgentBuilder.class.getName());

    private String provider;
    private String apiKey;
    private String apiUrl;
    private String model;
    private String systemPrompt;
    private String executionStrategy = "react";
    private String memoryStrategy = "sliding_window";
    private int maxRounds = 60;
    private Duration timeout = Duration.ofSeconds(30);
    private String proxy;
    private final List<Tool> tools = new ArrayList<>();

    AgentBuilder() {
        // package-private, use Agent.builder()
    }

    /**
     * 设置 LLM 供应商名称（必需）。
     *
     * @param provider 供应商名称，如 "OpenAI"、"DeepSeek"、"Baidu"
     * @return 当前 Builder 实例
     */
    public AgentBuilder provider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * 设置 API Key（必需）。
     *
     * @param apiKey API 密钥
     * @return 当前 Builder 实例
     */
    public AgentBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * 设置自定义 API URL。
     * <p>
     * 若未设置，将由 {@link LlmProviderAdapter} 根据供应商配置自动生成。
     *
     * @param apiUrl 自定义 API URL
     * @return 当前 Builder 实例
     */
    public AgentBuilder apiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
        return this;
    }

    /**
     * 设置模型名称（必需）。
     *
     * @param model 模型名称，如 "gpt-4"、"deepseek-chat"
     * @return 当前 Builder 实例
     */
    public AgentBuilder model(String model) {
        this.model = model;
        return this;
    }

    /**
     * 设置系统提示词。
     *
     * @param systemPrompt 系统提示词
     * @return 当前 Builder 实例
     */
    public AgentBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /**
     * 注册工具（可变参数）。
     *
     * @param tools 要注册的工具
     * @return 当前 Builder 实例
     */
    public AgentBuilder tools(Tool... tools) {
        if (tools != null) {
            this.tools.addAll(Arrays.asList(tools));
        }
        return this;
    }

    /**
     * 注册工具（列表形式）。
     *
     * @param tools 要注册的工具列表
     * @return 当前 Builder 实例
     */
    public AgentBuilder tools(List<Tool> tools) {
        if (tools != null) {
            this.tools.addAll(tools);
        }
        return this;
    }

    /**
     * 设置执行策略名称。默认 "react"。
     *
     * @param executionStrategy 策略名称，如 "react"、"plan_and_execute"
     * @return 当前 Builder 实例
     */
    public AgentBuilder executionStrategy(String executionStrategy) {
        this.executionStrategy = executionStrategy;
        return this;
    }

    /**
     * 设置记忆策略名称。默认 "sliding_window"。
     *
     * @param memoryStrategy 策略名称，如 "sliding_window"、"summarizing"、"adaptive"
     * @return 当前 Builder 实例
     */
    public AgentBuilder memoryStrategy(String memoryStrategy) {
        this.memoryStrategy = memoryStrategy;
        return this;
    }

    /**
     * 设置最大对话轮次。默认 60。
     *
     * @param maxRounds 最大轮次，必须大于 0
     * @return 当前 Builder 实例
     */
    public AgentBuilder maxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
        return this;
    }

    /**
     * 设置工具执行超时时间。默认 30 秒。
     *
     * @param timeout 超时时间
     * @return 当前 Builder 实例
     */
    public AgentBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * 设置工具执行超时时间（秒）。默认 30 秒。
     *
     * @param seconds 超时秒数，必须大于 0
     * @return 当前 Builder 实例
     */
    public AgentBuilder timeout(int seconds) {
        this.timeout = Duration.ofSeconds(seconds);
        return this;
    }

    /**
     * 设置代理地址。格式 ip:port。
     *
     * @param proxy 代理地址
     * @return 当前 Builder 实例
     */
    public AgentBuilder proxy(String proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * 构建 Agent 实例。
     * <p>
     * 组装流程：LlmProviderConfig → LlmCaller → ToolRegistry → FunctionCallOrchestrator → AgentRuntime → Agent
     *
     * @return 完整配置的 Agent 实例
     * @throws IllegalArgumentException 如果必需参数（provider、apiKey、model）缺失
     */
    public Agent build() {
        validate();

        // 1. 构建 LlmProviderConfig
        LlmProviderConfig.Builder configBuilder = LlmProviderConfig.builder()
                .apiKey(apiKey)
                .modelName(model);
        if (apiUrl != null) {
            configBuilder.apiUrl(apiUrl);
        }
        if (proxy != null) {
            configBuilder.proxy(proxy);
        }
        LlmProviderConfig providerConfig = configBuilder.build();

        // 2. 获取供应商适配器并构建 LlmCaller
        LlmProviderAdapter adapter = LlmProviderAdapterRegistry.getAdapter(provider);
        String resolvedUrl = apiUrl != null ? apiUrl : adapter.getApiUrl(providerConfig);
        String resolvedApiKey = adapter.getApiKey(providerConfig);

        LlmCaller llmCaller = request -> {
            // 从 FunctionCallRequest 中提取 ChatContent 并序列化为 JSON
            String requestBody = com.alibaba.fastjson.JSON.toJSONString(request.getChatContent());
            LlmRequest llmRequest = LlmRequest.builder()
                    .url(resolvedUrl)
                    .requestBody(requestBody)
                    .apiKey(resolvedApiKey)
                    .proxy(proxy)
                    .provider(provider)
                    .build();
            return LlmClient.syncChatRaw(llmRequest);
        };

        // 3. 构建 ToolRegistry 并注册工具
        ToolRegistry toolRegistry = new ToolRegistry();
        List<String> toolNames = new ArrayList<>();
        for (Tool tool : tools) {
            toolRegistry.registerTool(tool);
            toolNames.add(tool.name());
        }

        // 4. 构建 FunctionCallOrchestrator 组件
        ObservabilityManager observability = new ObservabilityManager();
        ValidationEngine validationEngine = new ValidationEngine(toolRegistry);
        ErrorHandler errorHandler = new ErrorHandler(
                () -> toolRegistry.getAllTools().stream()
                        .map(Tool::name)
                        .collect(Collectors.toList()));
        ExecutionEngine executionEngine = new ExecutionEngine(
                toolRegistry, new DefaultApprovalProvider(), new RetryStrategy());

        var protocolAdapter = ProtocolAdapterRegistry.getAdapter(provider);
        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability);
        orchestrator.setExecutionStrategyName(executionStrategy);

        // 5. 构建 AgentDefinition
        String agentId = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        AgentDefinition definition = AgentDefinition.builder()
                .id(agentId)
                .name("Agent")
                .systemPrompt(systemPrompt != null ? systemPrompt : "You are a helpful assistant.")
                .availableToolNames(toolNames.isEmpty() ? null : toolNames)
                .strategyName(executionStrategy)
                .memoryStrategy(memoryStrategy)
                .build();

        // 6. 构建 AgentRuntime
        String runtimeId = "builder-runtime-" + UUID.randomUUID().toString().substring(0, 8);
        AgentRuntime runtime = AgentRuntime.getInstance(runtimeId);
        runtime.setToolRegistry(toolRegistry);
        runtime.setOrchestrator(orchestrator);

        // 7. 注册 Agent 定义
        runtime.register(definition);

        LOG.info("Agent built: provider=" + provider + ", model=" + model
                + ", strategy=" + executionStrategy + ", memory=" + memoryStrategy
                + ", maxRounds=" + maxRounds + ", tools=" + toolNames.size());

        return new Agent(runtime, runtimeId, definition, llmCaller, maxRounds);
    }

    private void validate() {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (maxRounds <= 0) {
            throw new IllegalArgumentException("maxRounds must be positive, got: " + maxRounds);
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
