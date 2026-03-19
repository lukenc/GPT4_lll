package com.wmsay.gpt4_lll.fc.strategy;

import com.intellij.openapi.diagnostic.Logger;
import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.MemoryStats;
import com.wmsay.gpt4_lll.fc.memory.TokenUsageInfo;
import com.wmsay.gpt4_lll.fc.memory.UsageTracker;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.DegradationManager;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.strategy.ExecutionHook.HookAction;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import com.wmsay.gpt4_lll.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 执行策略 — 观察→思考→行动循环。
 * <p>
 * 将原 {@link FunctionCallOrchestrator#execute} 中的核心 ReAct 循环提取为独立策略。
 * 这是默认策略，行为与重构前完全一致。
 * <p>
 * 流程：
 * <ol>
 *   <li>调用 LLM 获取响应</li>
 *   <li>解析是否包含工具调用</li>
 *   <li>无工具调用 → 返回最终结果</li>
 *   <li>有工具调用 → 执行工具，将结果添加到对话历史，继续循环</li>
 *   <li>超过最大轮次 → 返回 MAX_ROUNDS_EXCEEDED</li>
 * </ol>
 */
public class ReActStrategy implements ExecutionStrategy {

    private static final Logger LOG = Logger.getInstance(ReActStrategy.class);
    static final int DEFAULT_MAX_ROUNDS = 20;
    private static final Pattern THINK_PATTERN = Pattern.compile(
            "<think>(.*?)</think>", Pattern.DOTALL);

    @Override
    public String getName() {
        return "react";
    }

    @Override
    public String getDisplayName() {
        return "ReAct";
    }

    @Override
    public String getDescription() {
        return "观察→思考→行动循环，适用于通用任务";
    }

    @Override
    public FunctionCallResult execute(FunctionCallRequest request,
                                      McpContext mcpContext,
                                      FunctionCallOrchestrator.LlmCaller llmCaller,
                                      FunctionCallOrchestrator.ProgressCallback callback,
                                      ExecutionStrategyContext ctx) {
        ExecutionHook hook = ctx.getExecutionHook();
        if (hook != null) {
            hook.beforeExecution();
        }
        return executeReActLoop(request, mcpContext, llmCaller, callback, ctx,
                request.getMaxRounds() > 0 ? request.getMaxRounds() : DEFAULT_MAX_ROUNDS);
    }

    /**
     * 执行 ReAct 循环（核心方法，也供 PlanAndExecuteStrategy 的步骤执行使用）。
     *
     * @param request   请求
     * @param mcpContext MCP 上下文
     * @param llmCaller LLM 调用器
     * @param callback  进度回调
     * @param ctx       策略上下文
     * @param maxRounds 最大轮次
     * @return 执行结果
     */
    public FunctionCallResult executeReActLoop(FunctionCallRequest request,
                                               McpContext mcpContext,
                                               FunctionCallOrchestrator.LlmCaller llmCaller,
                                               FunctionCallOrchestrator.ProgressCallback callback,
                                               ExecutionStrategyContext ctx,
                                               int maxRounds) {
        ProtocolAdapter protocolAdapter = ctx.getProtocolAdapter();
        ValidationEngine validationEngine = ctx.getValidationEngine();
        ExecutionEngine executionEngine = ctx.getExecutionEngine();
        ErrorHandler errorHandler = ctx.getErrorHandler();
        ObservabilityManager observability = ctx.getObservability();
        DegradationManager degradationManager = ctx.getDegradationManager();
        ConversationMemory memory = ctx.getMemory();
        UsageTracker usageTracker = ctx.getUsageTracker();
        FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller = ctx.getStreamingLlmCaller();

        String sessionId = ObservabilityManager.generateSessionId();
        observability.startSession(sessionId);

        List<ToolCallResult> toolCallHistory = new ArrayList<>();
        List<Message> toolResultMessages = new ArrayList<>();

        try {
            // 0. 检查降级状态
            if (degradationManager.isDisabled()) {
                String reason = degradationManager.getCurrentModeDescription(false);
                return FunctionCallResult.degraded(reason, sessionId);
            }

            // 0b. 检测原生 FC 支持
            boolean isNative = protocolAdapter.supportsNativeFunctionCalling();
            if (!isNative) {
                degradationManager.recordDegradationToPromptEngineering(protocolAdapter.getName());
            }

            // 1. 准备工具描述
            injectToolDescriptions(request, protocolAdapter);

            // 1b. 流式/非流式决策
            boolean useStreaming = streamingLlmCaller != null;
            if (request.getChatContent() != null) {
                request.getChatContent().setStream(useStreaming);
            }

            // 1c. Memory 初始化
            if (memory != null && request.getChatContent() != null
                    && request.getChatContent().getMessages() != null) {
                memory.addAll(request.getChatContent().getMessages());
            }

            // 2. 对话循环
            for (int round = 0; round < maxRounds; round++) {
                if (degradationManager.isDisabled()) {
                    String reason = degradationManager.getCurrentModeDescription(false);
                    return FunctionCallResult.degraded(reason, sessionId);
                }

                // 3. 调用 LLM
                callback.onLlmCallStarting(round);

                List<Message> originalMessages = null;
                if (memory != null && request.getChatContent() != null
                        && request.getChatContent().getMessages() != null) {
                    try {
                        List<Message> llmMessages = memory.getMessages();
                        originalMessages = request.getChatContent().getMessages();
                        List<Message> mutableLlmMessages = new ArrayList<>(llmMessages);
                        request.getChatContent().setDirectMessages(mutableLlmMessages);
                    } catch (Exception e) {
                        LOG.warn("Memory getMessages() failed, falling back to original messages", e);
                    }
                }

                String llmResponse;
                ResponseContentResult contentResult;
                boolean streamedThisRound = false;

                if (useStreaming) {
                    request.getChatContent().setStream(true);
                    final int currentRound = round;
                    final boolean[] reasoningStarted = {false};
                    final StringBuilder streamedContent = new StringBuilder();

                    com.wmsay.gpt4_lll.llm.StreamingFcCollector.DisplayCallback displayCb =
                            new com.wmsay.gpt4_lll.llm.StreamingFcCollector.DisplayCallback() {
                                @Override
                                public void onReasoningDelta(String delta) {
                                    if (!reasoningStarted[0]) {
                                        reasoningStarted[0] = true;
                                        callback.onReasoningStarted(currentRound);
                                    }
                                    callback.onReasoningDelta(currentRound, delta);
                                }

                                @Override
                                public void onContentDelta(String delta) {
                                    if (reasoningStarted[0]) {
                                        reasoningStarted[0] = false;
                                        callback.onReasoningComplete(currentRound);
                                    }
                                    streamedContent.append(delta);
                                    callback.onTextDelta(currentRound, delta);
                                }
                            };

                    try {
                        llmResponse = streamingLlmCaller.call(request, displayCb);
                        streamedThisRound = true;
                    } catch (Exception e) {
                        LOG.warn("Streaming LLM call failed, falling back to sync: " + e.getMessage());
                        request.getChatContent().setStream(false);
                        llmResponse = llmCaller.call(request);
                    }

                    if (reasoningStarted[0]) {
                        callback.onReasoningComplete(currentRound);
                    }

                    if (streamedThisRound) {
                        contentResult = new ResponseContentResult();
                        if (streamedContent.length() > 0) {
                            contentResult.allText.append(streamedContent);
                        }
                    } else {
                        contentResult = processResponseContentInOrder(llmResponse, round, callback, protocolAdapter);
                    }
                } else {
                    llmResponse = llmCaller.call(request);
                    contentResult = null;
                }

                // 3c. 恢复原始消息
                if (originalMessages != null && request.getChatContent() != null) {
                    request.getChatContent().setDirectMessages(originalMessages);
                }

                // 3d. Memory token usage
                if (memory != null && usageTracker != null) {
                    TokenUsageInfo usageInfo = usageTracker.extractUsage(llmResponse);
                    if (usageInfo != null) {
                        memory.updateRealTokenUsage(usageInfo);
                    }
                }

                // 3e. 非流式路径内容处理
                if (contentResult == null) {
                    contentResult = processResponseContentInOrder(llmResponse, round, callback, protocolAdapter);
                }

                // 4. 解析工具调用
                List<ToolCall> toolCalls = protocolAdapter.parseToolCalls(llmResponse);
                if (!isNative) {
                    degradationManager.recordParseAttempt(!toolCalls.isEmpty());
                }
                callback.onLlmCallCompleted(round, toolCalls.size());

                // 5. 无工具调用 → 返回
                String textContent = contentResult.allText.toString();
                if (toolCalls.isEmpty()) {
                    FunctionCallResult finalResult = FunctionCallResult.success(
                            textContent.isEmpty() ? null : textContent, sessionId, toolCallHistory);
                    ExecutionHook hook = ctx.getExecutionHook();
                    if (hook != null) {
                        ExecutionHook.HookResult hr = hook.afterCompletion(finalResult);
                        if (hr.getAction() == HookAction.ABORT) {
                            return FunctionCallResult.error(
                                    hr.getReason() != null ? hr.getReason() : "Aborted by hook",
                                    sessionId);
                        }
                    }
                    return finalResult;
                }

                // 6. 执行工具
                List<ToolCallResult> roundResults = executeToolCalls(
                        toolCalls, mcpContext, sessionId, callback,
                        validationEngine, executionEngine, errorHandler, observability);
                toolCallHistory.addAll(roundResults);

                // 7. 更新对话历史
                updateConversationHistory(request, llmResponse, textContent, toolCalls,
                        roundResults, toolResultMessages, protocolAdapter, memory);

                // 8. Memory stats
                if (memory != null) {
                    MemoryStats stats = memory.getStats();
                    LOG.info("Memory stats after round " + round
                            + ": messages=" + stats.getMessageCount()
                            + ", realPromptTokens=" + stats.getRealPromptTokens());
                }

                // 9. ExecutionHook — afterRound
                ExecutionHook hook = ctx.getExecutionHook();
                if (hook != null) {
                    ExecutionHook.HookResult hookResult = hook.afterRound(round, roundResults);
                    if (hookResult.getAction() == ExecutionHook.HookAction.ABORT) {
                        LOG.info("ExecutionHook aborted after round " + round
                                + ": " + hookResult.getReason());
                        String abortContent = textContent.isEmpty()
                                ? hookResult.getReason() : textContent;
                        return FunctionCallResult.success(abortContent, sessionId, toolCallHistory);
                    }
                }
            }

            return FunctionCallResult.maxRoundsExceeded(sessionId, toolCallHistory);

        } catch (Exception e) {
            observability.recordError(sessionId, e);
            ErrorMessage errorMsg = errorHandler.handle(e);
            return FunctionCallResult.error(errorMsg.getMessage(), sessionId);
        } finally {
            observability.endSession(sessionId);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 准备工具描述并注入到请求中。
     */
    void injectToolDescriptions(FunctionCallRequest request, ProtocolAdapter protocolAdapter) {
        Object formatted = protocolAdapter.formatToolDescriptions(request.getAvailableTools());
        if (formatted == null || request.getChatContent() == null) {
            return;
        }

        String formattedStr = formatted.toString();

        if (protocolAdapter.supportsNativeFunctionCalling()) {
            try {
                List<Object> toolsList = com.alibaba.fastjson.JSON.parseArray(formattedStr, Object.class);
                if (toolsList != null && !toolsList.isEmpty()) {
                    request.getChatContent().setTools(toolsList);
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse tool descriptions as JSON array: " + e.getMessage());
            }
        } else {
            List<Message> messages = request.getChatContent().getMessages();
            if (messages != null) {
                Message toolSystemMsg = new Message();
                toolSystemMsg.setRole("system");
                toolSystemMsg.setContent(formattedStr);
                messages.add(0, toolSystemMsg);
            }
        }
    }

    /**
     * 执行一轮中的所有工具调用。
     */
    List<ToolCallResult> executeToolCalls(List<ToolCall> toolCalls,
                                          McpContext context,
                                          String sessionId,
                                          FunctionCallOrchestrator.ProgressCallback callback,
                                          ValidationEngine validationEngine,
                                          ExecutionEngine executionEngine,
                                          ErrorHandler errorHandler,
                                          ObservabilityManager observability) {
        List<ToolCallResult> results = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            String callId = toolCall.getCallId() != null && !toolCall.getCallId().isEmpty()
                    ? toolCall.getCallId()
                    : ObservabilityManager.generateCallId();
            observability.startToolCall(sessionId, callId, toolCall);
            callback.onToolExecutionStarting(toolCall.getToolName(), toolCall.getParameters());
            long startTime = System.currentTimeMillis();

            ToolCallResult tcResult = null;
            try {
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

    /**
     * 更新对话历史（assistant tool_calls 消息 + 工具结果消息）。
     */
    private void updateConversationHistory(FunctionCallRequest request,
                                           String llmResponse,
                                           String textContent,
                                           List<ToolCall> toolCalls,
                                           List<ToolCallResult> roundResults,
                                           List<Message> toolResultMessages,
                                           ProtocolAdapter protocolAdapter,
                                           ConversationMemory memory) {
        if (request.getChatContent() != null && request.getChatContent().getMessages() != null) {
            Message assistantToolCallMsg = new Message();
            assistantToolCallMsg.setRole("assistant");
            assistantToolCallMsg.setContent(textContent.isEmpty() ? null : textContent);

            List<Object> rawToolCalls = extractToolCallsFromResponse(llmResponse, toolCalls);
            if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
                assistantToolCallMsg.setToolCalls(rawToolCalls);
            }
            request.getChatContent().getMessages().add(assistantToolCallMsg);

            if (memory != null) {
                memory.add(assistantToolCallMsg);
            }

            for (ToolCallResult result : roundResults) {
                Message msg = protocolAdapter.formatToolResult(result);
                if (msg.getToolCallId() == null || msg.getToolCallId().isEmpty()) {
                    msg.setToolCallId(result.getCallId());
                }
                toolResultMessages.add(msg);
                request.getChatContent().getMessages().add(msg);

                if (memory != null) {
                    memory.add(msg);
                }
            }
        } else {
            for (ToolCallResult result : roundResults) {
                Message msg = protocolAdapter.formatToolResult(result);
                toolResultMessages.add(msg);
            }
        }
    }

    /**
     * 从 LLM 响应中提取 tool_calls。
     */
    List<Object> extractToolCallsFromResponse(String rawResponse, List<ToolCall> parsedCalls) {
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
                // fallback
            }
        }

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
                    try {
                        com.alibaba.fastjson.JSON.parse(argsStr);
                    } catch (Exception e) {
                        com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
                        wrapper.put("raw_arguments", argsStr);
                        funcObj.put("arguments", wrapper.toJSONString());
                    }
                }
            } else {
                funcObj.put("arguments", com.alibaba.fastjson.JSON.toJSONString(arguments));
            }

            result.add(tcObj);
        }
        return result;
    }

    // ==================== 响应内容处理 ====================

    static class ResponseContentResult {
        final StringBuilder allText = new StringBuilder();
    }

    ResponseContentResult processResponseContentInOrder(String rawResponse, int round,
                                                        FunctionCallOrchestrator.ProgressCallback callback,
                                                        ProtocolAdapter protocolAdapter) {
        ResponseContentResult result = new ResponseContentResult();
        if (rawResponse == null || rawResponse.isEmpty()) {
            return result;
        }

        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(rawResponse);
            if (json == null) {
                handleTextBlock(stripThinkTags(rawResponse), round, callback, result);
                return result;
            }

            com.alibaba.fastjson.JSONArray contentArray = json.getJSONArray("content");
            if (contentArray != null && !contentArray.isEmpty()
                    && !json.containsKey("choices")) {
                processAnthropicContentArray(contentArray, round, callback, result);
                return result;
            }

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

            handleTextBlock(stripThinkTags(rawResponse), round, callback, result);
        } catch (Exception e) {
            handleTextBlock(stripThinkTags(rawResponse), round, callback, result);
        }

        return result;
    }

    private void processAnthropicContentArray(com.alibaba.fastjson.JSONArray contentArray,
                                              int round,
                                              FunctionCallOrchestrator.ProgressCallback callback,
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
                default:
                    break;
            }
        }
    }

    private void processOpenAIMessage(com.alibaba.fastjson.JSONObject message,
                                      int round,
                                      FunctionCallOrchestrator.ProgressCallback callback,
                                      ResponseContentResult result) {
        String reasoning = message.getString("reasoning_content");
        if (reasoning != null && !reasoning.isEmpty()) {
            callback.onReasoningContent(round, reasoning);
        }

        String content = message.getString("content");
        if (content != null && !content.isEmpty()) {
            if (reasoning == null || reasoning.isEmpty()) {
                String fromTags = extractThinkTagContent(content);
                if (fromTags != null && !fromTags.isEmpty()) {
                    callback.onReasoningContent(round, fromTags);
                }
            }
            handleTextBlock(stripThinkTags(content), round, callback, result);
        }
    }

    private void handleTextBlock(String text, int round,
                                 FunctionCallOrchestrator.ProgressCallback callback,
                                 ResponseContentResult result) {
        if (text != null && !text.isEmpty()) {
            callback.onTextContent(round, text);
            if (result.allText.length() > 0) {
                result.allText.append("\n\n");
            }
            result.allText.append(text);
        }
    }

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

    static String stripThinkTags(String text) {
        if (text == null) return null;
        return THINK_PATTERN.matcher(text).replaceAll("").trim();
    }
}
