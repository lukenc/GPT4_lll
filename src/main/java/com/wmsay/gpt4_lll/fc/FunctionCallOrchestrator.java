package com.wmsay.gpt4_lll.fc;

import java.util.logging.Logger;
import java.util.logging.Level;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.UsageTracker;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.DegradationManager;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.strategy.CompoundExecutionHook;
import com.wmsay.gpt4_lll.fc.strategy.ExecutionHook;
import com.wmsay.gpt4_lll.fc.strategy.ExecutionStrategy;
import com.wmsay.gpt4_lll.fc.strategy.ExecutionStrategyContext;
import com.wmsay.gpt4_lll.fc.strategy.ExecutionStrategyFactory;
import com.wmsay.gpt4_lll.fc.strategy.PlanStep;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import com.wmsay.gpt4_lll.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Function Calling 编排器。
 * 管理 function calling 的完整生命周期，协调各组件协同工作。
 *
 * <p>核心流程：
 * <ol>
 *   <li>生成会话 ID，启动可观测性追踪</li>
 *   <li>通过 {@link ProtocolAdapter#formatToolDescriptions} 准备工具描述</li>
 *   <li>进入对话循环（最多 {@code maxRounds} 轮，默认 20）：
 *     <ul>
 *       <li>通过 {@link LlmCaller} 调用 LLM</li>
 *       <li>通过 {@link ProtocolAdapter#parseToolCalls} 解析工具调用</li>
 *       <li>若无工具调用，返回最终结果</li>
 *       <li>对每个工具调用：验证参数、执行工具、收集结果</li>
 *       <li>通过 {@link ProtocolAdapter#formatToolResult} 格式化结果</li>
 *       <li>将结果添加到对话历史，继续循环</li>
 *     </ul>
 *   </li>
 *   <li>超过最大轮次时返回 MAX_ROUNDS_EXCEEDED</li>
 * </ol>
 *
 * <p>LLM 调用通过 {@link LlmCaller} 函数式接口抽象，避免与具体 LlmClient 耦合。
 *
 * @see ProtocolAdapter
 * @see ValidationEngine
 * @see ExecutionEngine
 * @see ErrorHandler
 * @see ObservabilityManager
 */
public class FunctionCallOrchestrator {

    private static final Logger LOG = Logger.getLogger(FunctionCallOrchestrator.class.getName());

    /** 默认最大对话轮次 */
    static final int DEFAULT_MAX_ROUNDS = 20;

    /**
     * LLM 调用的函数式接口（非流式）。
     * 将实际的 LLM 调用抽象为回调，使编排器不依赖具体的 LlmClient 实现。
     */
    @FunctionalInterface
    public interface LlmCaller {
        /**
         * 调用 LLM 并返回响应文本。
         *
         * @param request 包含对话内容和工具描述的请求
         * @return LLM 响应文本
         */
        String call(FunctionCallRequest request);
    }

    /**
     * 流式 LLM 调用的函数式接口。
     * 在调用过程中通过 {@link com.wmsay.gpt4_lll.llm.StreamingFcCollector.DisplayCallback}
     * 实时推送 reasoning / content 内容，同时收集完整数据用于工具调用解析。
     * <p>
     * 返回的字符串必须是一个与 {@code protocolAdapter.parseToolCalls()} 兼容的
     * 非流式 JSON 响应（由 {@link com.wmsay.gpt4_lll.llm.StreamingFcCollector#reconstructResponse()} 生成）。
     */
    @FunctionalInterface
    public interface StreamingLlmCaller {
        String call(FunctionCallRequest request,
                    com.wmsay.gpt4_lll.llm.StreamingFcCollector.DisplayCallback displayCallback);
    }

    /**
     * 执行进度回调接口。
     * 在 FC 执行的关键节点通知调用方，使 UI 能实时展示执行状态。
     */
    public interface ProgressCallback {
        /** 开始调用 LLM（第 round 轮，从 0 开始） */
        default void onLlmCallStarting(int round) {}

        /** LLM 返回，解析出 toolCalls.size() 个工具调用 */
        default void onLlmCallCompleted(int round, int toolCallCount) {}

        /** LLM 响应中包含的思考过程内容（reasoning_content）——非流式路径使用 */
        default void onReasoningContent(int round, String reasoningContent) {}

        /** 流式路径：思考过程开始（用于创建 ThinkingBlock） */
        default void onReasoningStarted(int round) {}

        /** 流式路径：思考过程增量内容 */
        default void onReasoningDelta(int round, String delta) {}

        /** 流式路径：思考过程结束（用于折叠 ThinkingBlock） */
        default void onReasoningComplete(int round) {}

        /** LLM 响应中包含的文本内容——非流式路径使用（每轮都会通知，包括有工具调用的中间轮次） */
        default void onTextContent(int round, String content) {}

        /** 流式路径：文本内容增量 */
        default void onTextDelta(int round, String delta) {}

        /** 即将执行某个工具 */
        default void onToolExecutionStarting(String toolName, java.util.Map<String, Object> params) {}

        /** 工具执行完成（成功或失败） */
        default void onToolExecutionCompleted(ToolCallResult result) {}

        /** 摘要操作开始 */
        default void onMemorySummarizingStarted() {}

        /** 摘要操作完成 */
        default void onMemorySummarizingCompleted(int originalTokens, int compressedTokens) {}

        /** 摘要操作失败 */
        default void onMemorySummarizingFailed(String reason) {}

        // ---- Plan-and-Execute 策略相关回调 ----

        /** 策略执行阶段变化（planning / executing / synthesizing / fallback） */
        default void onStrategyPhase(String phase, String description) {}

        /** 计划已生成 */
        default void onPlanGenerated(List<PlanStep> steps) {}

        /** 计划步骤开始执行 */
        default void onPlanStepStarting(int stepIndex, String stepDescription) {}

        /** 计划步骤执行完成 */
        default void onPlanStepCompleted(int stepIndex, boolean success, String resultSummary) {}

        /** 计划已修订（步骤失败后重新规划） */
        default void onPlanRevised(List<PlanStep> revisedSteps) {}
    }

    /** 空实现，用于无回调场景 */
    private static final ProgressCallback NOOP_CALLBACK = new ProgressCallback() {};

    private final ProtocolAdapter protocolAdapter;
    private final ValidationEngine validationEngine;
    private final ExecutionEngine executionEngine;
    private final ErrorHandler errorHandler;
    private final ObservabilityManager observability;
    private final DegradationManager degradationManager;

    /** 可选的对话记忆管理器，为 null 时保持现有行为不变 */
    private ConversationMemory memory;
    /** 可选的 token 使用量追踪器，仅当 memory 非 null 时创建 */
    private UsageTracker usageTracker;
    /** 可选的流式 LLM 调用器，非 null 时 FC 使用流式调用（实时展示 reasoning） */
    private StreamingLlmCaller streamingLlmCaller;
    /** 当前执行策略名称，支持运行时切换（默认 "react"） */
    private volatile String executionStrategyName = ExecutionStrategyFactory.DEFAULT_STRATEGY;
    /** 执行钩子（组合钩子，支持多个钩子链式调用） */
    private final CompoundExecutionHook executionHooks = new CompoundExecutionHook();

    /**
     * 创建编排器实例。
     *
     * @param protocolAdapter  协议适配器
     * @param validationEngine 验证引擎
     * @param executionEngine  执行引擎
     * @param errorHandler     错误处理器
     * @param observability    可观测性管理器
     */
    public FunctionCallOrchestrator(ProtocolAdapter protocolAdapter,
                                    ValidationEngine validationEngine,
                                    ExecutionEngine executionEngine,
                                    ErrorHandler errorHandler,
                                    ObservabilityManager observability) {
        this(protocolAdapter, validationEngine, executionEngine, errorHandler, observability, new DegradationManager());
    }

    /**
     * 创建编排器实例（带自定义降级管理器）。
     *
     * @param protocolAdapter    协议适配器
     * @param validationEngine   验证引擎
     * @param executionEngine    执行引擎
     * @param errorHandler       错误处理器
     * @param observability      可观测性管理器
     * @param degradationManager 降级管理器
     */
    public FunctionCallOrchestrator(ProtocolAdapter protocolAdapter,
                                    ValidationEngine validationEngine,
                                    ExecutionEngine executionEngine,
                                    ErrorHandler errorHandler,
                                    ObservabilityManager observability,
                                    DegradationManager degradationManager) {
        this.protocolAdapter = protocolAdapter;
        this.validationEngine = validationEngine;
        this.executionEngine = executionEngine;
        this.errorHandler = errorHandler;
        this.observability = observability;
        this.degradationManager = degradationManager;
    }

    /**
     * 创建编排器实例（带对话记忆管理器）。
     * <p>
     * 当 memory 非 null 时，内部自动创建 UsageTracker 实例，
     * 用于从 LLM 响应中提取真实 token 使用量。
     *
     * @param protocolAdapter  协议适配器
     * @param validationEngine 验证引擎
     * @param executionEngine  执行引擎
     * @param errorHandler     错误处理器
     * @param observability    可观测性管理器
     * @param memory           对话记忆管理器（可为 null）
     */
    public FunctionCallOrchestrator(ProtocolAdapter protocolAdapter,
                                    ValidationEngine validationEngine,
                                    ExecutionEngine executionEngine,
                                    ErrorHandler errorHandler,
                                    ObservabilityManager observability,
                                    ConversationMemory memory) {
        this(protocolAdapter, validationEngine, executionEngine, errorHandler, observability, new DegradationManager());
        this.memory = memory;
        if (memory != null) {
            this.usageTracker = new UsageTracker();
        }
    }

    /**
     * 获取降级管理器（用于外部查询降级状态）。
     *
     * @return 降级管理器实例
     */
    public DegradationManager getDegradationManager() {
        return degradationManager;
    }

    /**
     * 获取对话记忆管理器（可为 null）。
     *
     * @return 对话记忆管理器实例，或 null
     */
    public ConversationMemory getMemory() {
        return memory;
    }

    /**
     * 获取 token 使用量追踪器（可为 null）。
     *
     * @return UsageTracker 实例，或 null
     */
    public UsageTracker getUsageTracker() {
        return usageTracker;
    }

    /**
     * 设置流式 LLM 调用器。设置后 FC 循环使用流式调用，reasoning/content 实时展示。
     */
    public void setStreamingLlmCaller(StreamingLlmCaller streamingLlmCaller) {
        this.streamingLlmCaller = streamingLlmCaller;
    }

    /**
     * 获取当前执行策略名称。
     */
    public String getExecutionStrategyName() {
        return executionStrategyName;
    }

    /**
     * 设置执行策略名称（支持运行时切换）。
     *
     * @param strategyName 策略名称（"react" 或 "plan_and_execute"）
     */
    public void setExecutionStrategyName(String strategyName) {
        if (strategyName != null && ExecutionStrategyFactory.isRegistered(strategyName)) {
            this.executionStrategyName = strategyName;
            LOG.info("Execution strategy switched to: " + strategyName);
        } else {
            LOG.log(Level.WARNING, "Unknown strategy '" + strategyName + "', keeping current: " + executionStrategyName);
        }
    }

    /**
     * 获取当前执行策略实例。
     */
    public ExecutionStrategy getExecutionStrategy() {
        return ExecutionStrategyFactory.get(executionStrategyName);
    }

    /**
     * 添加执行钩子。钩子按添加顺序依次调用。
     *
     * @param hook 执行钩子
     */
    public void addExecutionHook(ExecutionHook hook) {
        executionHooks.addHook(hook);
    }

    /**
     * 移除执行钩子。
     *
     * @param hook 要移除的钩子
     */
    public void removeExecutionHook(ExecutionHook hook) {
        executionHooks.removeHook(hook);
    }

    /**
     * 获取当前所有已注册的执行钩子。
     */
    public List<ExecutionHook> getExecutionHooks() {
        return executionHooks.getHooks();
    }

    /**
     * 执行 function calling 对话流程。
     *
     * <ol>
     *   <li>生成会话 ID 并启动追踪</li>
     *   <li>准备工具描述</li>
     *   <li>进入对话循环（最多 maxRounds 轮）</li>
     *   <li>每轮：调用 LLM → 解析工具调用 → 验证 → 执行 → 格式化结果</li>
     *   <li>无工具调用时返回 SUCCESS；超过轮次返回 MAX_ROUNDS_EXCEEDED</li>
     * </ol>
     *
     * @param request   初始请求（包含对话内容、可用工具、配置）
     * @param context   MCP 执行上下文
     * @param llmCaller LLM 调用回调
     * @return 对话最终结果
     */
    public FunctionCallResult execute(FunctionCallRequest request,
                                      McpContext context,
                                      LlmCaller llmCaller) {
        return execute(request, context, llmCaller, NOOP_CALLBACK);
    }

    /**
     * 执行 function calling 对话流程（带进度回调）。
     * <p>
     * 根据当前 {@link #executionStrategyName} 选择执行策略：
     * <ul>
     *   <li>"react" — ReAct 循环（默认，与重构前行为一致）</li>
     *   <li>"plan_and_execute" — 先规划后执行，适用于复杂多步骤任务</li>
     * </ul>
     *
     * @param request          初始请求
     * @param context          MCP 执行上下文
     * @param llmCaller        LLM 调用回调
     * @param progressCallback 进度回调，在关键节点通知调用方
     * @return 对话最终结果
     */
    public FunctionCallResult execute(FunctionCallRequest request,
                                      McpContext context,
                                      LlmCaller llmCaller,
                                      ProgressCallback progressCallback) {
        ProgressCallback callback = progressCallback != null ? progressCallback : NOOP_CALLBACK;

        // 构建策略共享上下文（传入执行钩子）
        ExecutionHook effectiveHook = executionHooks.isEmpty() ? null : executionHooks;
        ExecutionStrategyContext strategyContext = new ExecutionStrategyContext(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability, degradationManager,
                memory, usageTracker, streamingLlmCaller, effectiveHook
        );

        // 解析并调度到执行策略
        ExecutionStrategy strategy = ExecutionStrategyFactory.get(executionStrategyName);
        LOG.info("Dispatching to execution strategy: " + strategy.getName()
                + " (" + strategy.getDisplayName() + ")");

        return strategy.execute(request, context, llmCaller, callback, strategyContext);
    }

    // ==================== 响应内容有序处理 ====================

    /**
     * 单轮 LLM 响应中提取的内容汇总。
     */
    static class ResponseContentResult {
        final StringBuilder allText = new StringBuilder();
    }

    /**
     * 按原始顺序处理 LLM 响应中的内容块，保持 thinking / text / tool_use 的交错顺序。
     * <p>
     * 支持三种响应格式：
     * <ul>
     *   <li>Anthropic 格式：顶层 content 数组，块类型为 thinking / text / tool_use</li>
     *   <li>OpenAI 格式：choices[0].message，reasoning_content + content + tool_calls</li>
     *   <li>纯文本：直接作为 text 处理</li>
     * </ul>
     *
     * @param rawResponse LLM 原始响应
     * @param round       当前轮次
     * @param callback    进度回调
     * @return 提取的内容汇总
     */
    ResponseContentResult processResponseContentInOrder(String rawResponse, int round,
                                                         ProgressCallback callback) {
        ResponseContentResult result = new ResponseContentResult();
        if (rawResponse == null || rawResponse.isEmpty()) {
            return result;
        }

        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(rawResponse);
            if (json == null) {
                // 纯文本
                handleTextBlock(stripThinkTags(rawResponse), round, callback, result);
                return result;
            }

            // 尝试 Anthropic 格式：顶层 content 数组
            com.alibaba.fastjson.JSONArray contentArray = json.getJSONArray("content");
            if (contentArray != null && !contentArray.isEmpty()
                    && !json.containsKey("choices")) {
                processAnthropicContentArray(contentArray, round, callback, result);
                return result;
            }

            // OpenAI 格式：choices[0].message
            com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                com.alibaba.fastjson.JSONObject choice = choices.getJSONObject(0);
                if (choice != null) {
                    com.alibaba.fastjson.JSONObject message = choice.getJSONObject("message");
                    if (message == null) {
                        message = choice.getJSONObject("delta");
                    }
                    if (message != null) {
                        processOpenAIMessage(message, round, callback, result);
                        return result;
                    }
                }
            }

            // Fallback：纯文本
            handleTextBlock(stripThinkTags(rawResponse), round, callback, result);
        } catch (Exception e) {
            // JSON 解析失败，当作纯文本
            handleTextBlock(stripThinkTags(rawResponse), round, callback, result);
        }

        return result;
    }

    /**
     * 按顺序处理 Anthropic content 数组中的块。
     * 块类型：thinking、text、tool_use（tool_use 仅记录，不在此处执行）。
     */
    private void processAnthropicContentArray(com.alibaba.fastjson.JSONArray contentArray,
                                               int round, ProgressCallback callback,
                                               ResponseContentResult result) {
        for (int i = 0; i < contentArray.size(); i++) {
            com.alibaba.fastjson.JSONObject block = contentArray.getJSONObject(i);
            if (block == null) continue;

            String type = block.getString("type");
            if (type == null) continue;

            switch (type) {
                case "thinking":
                    String thinking = block.getString("thinking");
                    if (thinking != null && !thinking.isEmpty()) {
                        callback.onReasoningContent(round, thinking);
                    }
                    break;
                case "text":
                    String text = block.getString("text");
                    handleTextBlock(stripThinkTags(text), round, callback, result);
                    break;
                case "tool_use":
                    // tool_use 块不在此处执行，由 protocolAdapter.parseToolCalls 统一解析后执行。
                    // 但如果 tool_use 之前有 text 块，它们已经按正确顺序通知了回调。
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 处理 OpenAI 格式的 message 对象。
     * 按顺序：reasoning_content → content → (tool_calls 由外部处理)。
     */
    private void processOpenAIMessage(com.alibaba.fastjson.JSONObject message,
                                       int round, ProgressCallback callback,
                                       ResponseContentResult result) {
        // 1. reasoning_content（思考过程）
        String reasoning = message.getString("reasoning_content");
        if (reasoning != null && !reasoning.isEmpty()) {
            callback.onReasoningContent(round, reasoning);
        }

        // 2. content（文本内容）
        String content = message.getString("content");
        if (content != null && !content.isEmpty()) {
            // 如果没有独立的 reasoning_content，检查 <think> 标签
            if (reasoning == null || reasoning.isEmpty()) {
                String fromTags = extractThinkTagContent(content);
                if (fromTags != null && !fromTags.isEmpty()) {
                    callback.onReasoningContent(round, fromTags);
                }
            }
            handleTextBlock(stripThinkTags(content), round, callback, result);
        }
    }

    /**
     * 处理单个文本块：通知回调并累积到 allText。
     */
    private void handleTextBlock(String text, int round,
                                  ProgressCallback callback, ResponseContentResult result) {
        if (text != null && !text.isEmpty()) {
            callback.onTextContent(round, text);
            if (result.allText.length() > 0) {
                result.allText.append("\n\n");
            }
            result.allText.append(text);
        }
    }

    /**
     * 准备工具描述并注入到请求中。
     * <ul>
     *   <li>原生协议（OpenAI/Anthropic）：解析为 JSON 数组设置到 ChatContent.tools</li>
     *   <li>Prompt Engineering 模式（Markdown）：将工具描述作为 system 消息注入到对话历史</li>
     * </ul>
     *
     * @param request 请求（包含可用工具列表和 ChatContent）
     */
    void injectToolDescriptions(FunctionCallRequest request) {
        Object formatted = protocolAdapter.formatToolDescriptions(request.getAvailableTools());
        if (formatted == null || request.getChatContent() == null) {
            return;
        }

        String formattedStr = formatted.toString();

        if (protocolAdapter.supportsNativeFunctionCalling()) {
            // 原生协议：解析为 List<Object> 设置到 ChatContent.tools
            try {
                List<Object> toolsList = com.alibaba.fastjson.JSON.parseArray(formattedStr, Object.class);
                if (toolsList != null && !toolsList.isEmpty()) {
                    request.getChatContent().setTools(toolsList);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to parse tool descriptions as JSON array: " + e.getMessage());
            }
        } else {
            // Prompt Engineering 模式：将工具描述作为 system 消息注入到对话历史开头
            List<Message> messages = request.getChatContent().getMessages();
            if (messages != null) {
                Message toolSystemMsg = new Message();
                toolSystemMsg.setRole("system");
                toolSystemMsg.setContent(formattedStr);
                messages.add(0, toolSystemMsg);
            }
            // messages 为 null 时跳过注入（测试场景或无对话历史时）
        }
    }

    /**
     * 调用 LLM。
     * 通过 {@link LlmCaller} 回调执行实际的 LLM 调用。
     *
     * @param request   当前请求
     * @param llmCaller LLM 调用回调
     * @return LLM 响应文本
     */
    String callLlm(FunctionCallRequest request, LlmCaller llmCaller) {
        return llmCaller.call(request);
    }

    /**
     * 从 LLM 原始 JSON 响应中提取文本内容。
     * 支持 OpenAI 格式 (choices[0].message.content) 和纯文本响应。
     *
     * @param rawResponse LLM 原始响应（可能是 JSON 或纯文本）
     * @return 提取的文本内容
     */
    String extractContentFromResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return rawResponse;
        }

        // 尝试解析为 JSON（非流式响应格式）
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(rawResponse);
            if (json != null) {
                // OpenAI 格式: choices[0].message.content
                com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    com.alibaba.fastjson.JSONObject choice = choices.getJSONObject(0);
                    if (choice != null) {
                        com.alibaba.fastjson.JSONObject message = choice.getJSONObject("message");
                        if (message != null) {
                            String content = message.getString("content");
                            return stripThinkTags(content != null ? content : "");
                        }
                        com.alibaba.fastjson.JSONObject delta = choice.getJSONObject("delta");
                        if (delta != null) {
                            String content = delta.getString("content");
                            return stripThinkTags(content != null ? content : "");
                        }
                    }
                }
                // Anthropic 格式: 收集所有 type="text" 块的文本
                com.alibaba.fastjson.JSONArray contentArray = json.getJSONArray("content");
                if (contentArray != null && !contentArray.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < contentArray.size(); i++) {
                        com.alibaba.fastjson.JSONObject block = contentArray.getJSONObject(i);
                        if (block != null && "text".equals(block.getString("type"))) {
                            String text = block.getString("text");
                            if (text != null && !text.isEmpty()) {
                                if (sb.length() > 0) sb.append("\n\n");
                                sb.append(text);
                            }
                        }
                    }
                    if (sb.length() > 0) {
                        return stripThinkTags(sb.toString());
                    }
                }
            }
        } catch (Exception e) {
            // 不是 JSON，当作纯文本返回
        }

        return stripThinkTags(rawResponse);
    }

    /**
     * 从 LLM 原始 JSON 响应中提取思考过程内容。
     * 1. 检查 choices[0].message.reasoning_content（OpenAI / DeepSeek 格式）
     * 2. 若为空，则检查 content 中的 &lt;think&gt;...&lt;/think&gt; 标签（开源模型常用格式）
     */
    String extractReasoningContent(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return null;
        }
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(rawResponse);
            if (json != null) {
                com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    com.alibaba.fastjson.JSONObject choice = choices.getJSONObject(0);
                    if (choice != null) {
                        com.alibaba.fastjson.JSONObject message = choice.getJSONObject("message");
                        if (message != null) {
                            String reasoning = message.getString("reasoning_content");
                            if (reasoning != null && !reasoning.isEmpty()) {
                                return reasoning;
                            }
                            String content = message.getString("content");
                            if (content != null) {
                                String fromTags = extractThinkTagContent(content);
                                if (fromTags != null) {
                                    return fromTags;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，忽略
        }
        return null;
    }

    /**
     * 从 LLM 原始 JSON 响应中提取 tool_calls 数组。
     * 用于构建 assistant 消息的 tool_calls 字段，满足 OpenAI API 要求。
     * 如果无法从 JSON 中提取，则根据已解析的 ToolCall 列表构建。
     *
     * @param rawResponse LLM 原始响应
     * @param parsedCalls 已解析的工具调用列表（作为 fallback）
     * @return tool_calls 数组（List of JSONObject），或 null
     */
    List<Object> extractToolCallsFromResponse(String rawResponse, List<ToolCall> parsedCalls) {
        // 尝试从 JSON 响应中提取原始 tool_calls
        if (rawResponse != null && !rawResponse.isEmpty()) {
            try {
                com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(rawResponse);
                if (json != null) {
                    com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        com.alibaba.fastjson.JSONObject choice = choices.getJSONObject(0);
                        if (choice != null) {
                            com.alibaba.fastjson.JSONObject message = choice.getJSONObject("message");
                            if (message != null) {
                                com.alibaba.fastjson.JSONArray tc = message.getJSONArray("tool_calls");
                                if (tc != null && !tc.isEmpty()) {
                                    return normalizeToolCalls(tc);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 不是 JSON，使用 fallback
            }
        }

        // Fallback: 根据已解析的 ToolCall 构建 OpenAI 格式的 tool_calls
        if (parsedCalls != null && !parsedCalls.isEmpty()) {
            List<Object> toolCallsList = new ArrayList<>();
            for (ToolCall tc : parsedCalls) {
                com.alibaba.fastjson.JSONObject tcObj = new com.alibaba.fastjson.JSONObject(true);
                tcObj.put("id", tc.getCallId());
                tcObj.put("type", "function");
                com.alibaba.fastjson.JSONObject funcObj = new com.alibaba.fastjson.JSONObject(true);
                funcObj.put("name", tc.getToolName());
                funcObj.put("arguments", com.alibaba.fastjson.JSON.toJSONString(tc.getParameters()));
                tcObj.put("function", funcObj);
                toolCallsList.add(tcObj);
            }
            return toolCallsList;
        }

        return null;
    }

    /**
     * 规范化 tool_calls 数组中每个元素的 function.arguments 字段。
     * <p>
     * 部分模型（如 Qwen 代码模型）可能返回 arguments 为 JSON 对象而非 JSON 字符串，
     * 或返回空字符串/null。API 在接收回传时严格要求 arguments 为合法 JSON 字符串，
     * 此方法确保 arguments 始终为有效的 JSON 格式字符串。
     *
     * @param rawToolCalls 从 API 响应中提取的原始 tool_calls JSONArray
     * @return 规范化后的 tool_calls 列表
     */
    private List<Object> normalizeToolCalls(com.alibaba.fastjson.JSONArray rawToolCalls) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < rawToolCalls.size(); i++) {
            com.alibaba.fastjson.JSONObject tcObj = rawToolCalls.getJSONObject(i);
            if (tcObj == null) continue;

            com.alibaba.fastjson.JSONObject funcObj = tcObj.getJSONObject("function");
            if (funcObj == null) {
                result.add(tcObj);
                continue;
            }

            Object arguments = funcObj.get("arguments");
            if (arguments == null) {
                funcObj.put("arguments", "{}");
            } else if (arguments instanceof String) {
                String argsStr = (String) arguments;
                if (argsStr.isEmpty()) {
                    funcObj.put("arguments", "{}");
                } else {
                    // 验证是否为合法 JSON，不合法则包装为 JSON 对象
                    try {
                        com.alibaba.fastjson.JSON.parse(argsStr);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "tool_call arguments is not valid JSON, wrapping: " + argsStr);
                        com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
                        wrapper.put("raw_arguments", argsStr);
                        funcObj.put("arguments", wrapper.toJSONString());
                    }
                }
            } else {
                // arguments 是 JSON 对象/数组而非字符串，序列化为 JSON 字符串
                funcObj.put("arguments", com.alibaba.fastjson.JSON.toJSONString(arguments));
            }

            result.add(tcObj);
        }
        return result;
    }

    /**
     * 执行一轮中的所有工具调用。
     * 对每个工具调用：验证参数 → 执行工具 → 收集结果。
     * 单个工具调用的失败不会中断其他工具调用的执行。
     *
     * @param toolCalls 本轮解析出的工具调用列表
     * @param context   MCP 执行上下文
     * @param sessionId 会话 ID
     * @return 所有工具调用的结果列表
     */
    List<ToolCallResult> executeToolCalls(List<ToolCall> toolCalls,
                                          McpContext context,
                                          String sessionId) {
        return executeToolCalls(toolCalls, context, sessionId, NOOP_CALLBACK);
    }

    /**
     * 执行一轮中的所有工具调用（带进度回调）。
     */
    List<ToolCallResult> executeToolCalls(List<ToolCall> toolCalls,
                                          McpContext context,
                                          String sessionId,
                                          ProgressCallback callback) {
        List<ToolCallResult> results = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            // 使用原始 callId（来自 API 响应），确保 tool_call_id 匹配
            String callId = toolCall.getCallId() != null && !toolCall.getCallId().isEmpty()
                    ? toolCall.getCallId()
                    : ObservabilityManager.generateCallId();
            observability.startToolCall(sessionId, callId, toolCall);
            callback.onToolExecutionStarting(toolCall.getToolName(), toolCall.getParameters());
            long startTime = System.currentTimeMillis();

            ToolCallResult tcResult = null;
            try {
                // 验证参数
                ValidationResult validation = validationEngine.validate(toolCall);
                if (!validation.isValid()) {
                    ErrorMessage errorMsg = ErrorMessage.builder()
                            .type("validation_error")
                            .message("Parameter validation failed for tool: " + toolCall.getToolName())
                            .suggestion("Please check the parameters and try again.")
                            .build();
                    tcResult = ToolCallResult.validationError(callId, toolCall.getToolName(), errorMsg);
                    results.add(tcResult);
                    continue;
                }

                // 执行工具
                McpToolResult mcpResult = executionEngine.execute(toolCall, context);
                long duration = System.currentTimeMillis() - startTime;
                tcResult = ToolCallResult.success(callId, toolCall.getToolName(), mcpResult, duration);
                results.add(tcResult);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                observability.recordError(sessionId, e);
                ErrorMessage errorMsg = errorHandler.handle(e);
                tcResult = ToolCallResult.executionError(callId, toolCall.getToolName(), errorMsg, duration);
                results.add(tcResult);
            } finally {
                observability.endToolCall(sessionId, callId);
                if (tcResult != null) {
                    callback.onToolExecutionCompleted(tcResult);
                }
            }
        }

        return results;
    }

    // ==================== <think> 标签工具方法 ====================

    private static final Pattern THINK_PATTERN = Pattern.compile(
            "<think>(.*?)</think>", Pattern.DOTALL);

    /**
     * 从文本中提取 &lt;think&gt;...&lt;/think&gt; 标签内的内容。
     * 返回所有 think 块拼接后的文本，若不存在则返回 null。
     */
    static String extractThinkTagContent(String text) {
        if (text == null) return null;
        Matcher m = THINK_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(m.group(1).trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 移除文本中的 &lt;think&gt;...&lt;/think&gt; 标签及其内容，返回剩余文本。
     */
    static String stripThinkTags(String text) {
        if (text == null) return null;
        return THINK_PATTERN.matcher(text).replaceAll("").trim();
    }
}
