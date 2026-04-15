package com.wmsay.gpt4_lll.fc.planning;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.runtime.AgentRuntime;
import com.wmsay.gpt4_lll.fc.skill.ContextDistiller;
import com.wmsay.gpt4_lll.fc.skill.SkillComplexity;
import com.wmsay.gpt4_lll.fc.skill.SkillDefinition;
import com.wmsay.gpt4_lll.fc.skill.SkillMatchResult;
import com.wmsay.gpt4_lll.fc.skill.SkillMatcher;
import com.wmsay.gpt4_lll.fc.skill.SkillRegistry;
import com.wmsay.gpt4_lll.fc.skill.SubAgentFactory;
import com.wmsay.gpt4_lll.fc.state.SubAgentProgressProvider;
import com.wmsay.gpt4_lll.fc.state.SubAgentResult;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.ErrorMessage;
import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Plan-and-Execute 执行策略 — 先规划完整计划，再逐步执行。
 * <p>
 * 适用于复杂多步骤任务。相比 ReAct 的"边思考边行动"，PlanAndExecute
 * 先让 LLM 站在全局视角制定完整计划，然后逐步执行每个步骤。
 * <p>
 * 三阶段流程：
 * <ol>
 *   <li><b>Planning 阶段</b>：调用 LLM 生成结构化执行计划（JSON 步骤列表）</li>
 *   <li><b>Execution 阶段</b>：对每个步骤，使用内部 ReAct 循环执行（支持工具调用）</li>
 *   <li><b>Synthesis 阶段</b>：汇总所有步骤结果，生成最终回答</li>
 * </ol>
 * <p>
 * 支持自适应重规划：当某个步骤执行失败时，可根据已完成步骤的上下文重新规划剩余步骤。
 * <p>
 * 已集成 {@link ExecutionHook}：在步骤执行前后调用 beforeStep/afterStep 钩子，
 * 在完成后调用 afterCompletion 钩子。
 */
public class PlanAndExecuteStrategy implements ExecutionStrategy {

    private static final Logger LOG = Logger.getLogger(PlanAndExecuteStrategy.class.getName());

    /** 单个步骤内 ReAct 循环的最大轮次 */
    private static final int STEP_MAX_ROUNDS = 35;

    /** 计划步骤上限 */
    private static final int MAX_PLAN_STEPS = 35;

    /** 最大重规划次数 */
    private static final int MAX_REPLAN_ATTEMPTS = 2;

    /** 合成阶段 LLM 调用超时（秒） */
    private static final int SYNTHESIS_TIMEOUT_SECONDS = 180;

    /** 规划阶段 LLM 调用超时（秒） */
    private static final int PLANNING_TIMEOUT_SECONDS = 180;

    /** 内部 ReAct 策略实例，复用 ReAct 循环逻辑 */
    private final ReActStrategy reActStrategy = new ReActStrategy();

    /** 当前执行的 PlanProgressProvider，供 Bridge 层获取 */
    private volatile PlanProgressProvider planProgressProvider;

    @Override
    public String getName() {
        return "plan_and_execute";
    }

    @Override
    public String getDisplayName() {
        return "Plan & Execute";
    }

    @Override
    public String getDescription() {
        return "先规划完整计划，再逐步执行，适用于复杂多步骤任务";
    }

    /**
     * 获取当前执行的 PlanProgressProvider。
     * Bridge 层通过此方法获取 Provider 以转发进度事件给 UI。
     *
     * @return 当前 PlanProgressProvider，若未在执行中则返回 null
     */
    PlanProgressProvider getPlanProgressProvider() {
        return planProgressProvider;
    }

    @Override
    public FunctionCallResult execute(FunctionCallRequest request,
                                      ToolContext toolContext,
                                      LlmCaller llmCaller,
                                      ProgressCallback callback,
                                      ExecutionStrategyContext ctx) {
        String sessionId = ObservabilityManager.generateSessionId();
        ctx.getObservability().startSession(sessionId);
        List<ToolCallResult> allToolCallHistory = new ArrayList<>();
        ExecutionHook hook = ctx.getExecutionHook();

        // 创建 PlanProgressProvider 并集成 TaskPersistence
        PlanProgressProvider provider = new PlanProgressProvider();
        this.planProgressProvider = provider;

        // 重置钩子状态（计时器、计数器等），确保基于本次执行判断
        if (hook != null) {
            hook.beforeExecution();
        }

        try {
            // ============ Phase 1: Planning ============
            callback.onStrategyPhase("planning", "正在分析任务并制定执行计划...");

            String userMessage = extractUserMessage(request);
            if (userMessage == null || userMessage.isEmpty()) {
                return FunctionCallResult.error("No user message found for planning", sessionId);
            }

            List<PlanStep> plan = generatePlan(request, llmCaller, callback, ctx);
            if (plan == null || plan.isEmpty()) {
                LOG.info("Plan generation returned empty plan, falling back to ReAct");
                callback.onStrategyPhase("fallback", "计划生成失败，切换到 ReAct 模式...");
                return reActStrategy.execute(request, toolContext, llmCaller, callback, ctx);
            }

            callback.onPlanGenerated(plan);
            provider.setPlan(plan);
            LOG.info("Plan generated with " + plan.size() + " steps");

            // ============ Phase 2: Execution ============
            callback.onStrategyPhase("executing", "开始执行计划...");

            StringBuilder stepsContext = new StringBuilder();
            int replanCount = 0;

            for (int i = 0; i < plan.size(); i++) {
                PlanStep step = plan.get(i);

                // ExecutionHook: beforeStep
                if (hook != null) {
                    ExecutionHook.HookResult hr = hook.beforeStep(step.getIndex(), step.getDescription());
                    if (hr.getAction() == ExecutionHook.HookAction.ABORT) {
                        step.markSkipped(hr.getReason() != null ? hr.getReason() : "Skipped by hook");
                        LOG.info("Step " + (step.getIndex() + 1) + " skipped by hook: " + hr.getReason());
                        callback.onPlanStepCompleted(step.getIndex(), false, "Skipped: " + hr.getReason());
                        provider.updateStepStatus(step.getIndex(), PlanStep.Status.SKIPPED,
                                hr.getReason() != null ? hr.getReason() : "Skipped by hook");
                        continue;
                    }
                }

                step.markInProgress();
                provider.updateStepStatus(step.getIndex(), PlanStep.Status.IN_PROGRESS, null);
                callback.onPlanStepStarting(step.getIndex(), step.getDescription());

                long stepStart = System.currentTimeMillis();
                try {
                    // 步骤级 Skill 匹配与路由（需求 7.1, 7.2, 7.3, 7.4, 7.5, 7.6）
                    FunctionCallResult stepResult = executeStepWithSkillRouting(
                            step, request, toolContext, llmCaller, callback, ctx, stepsContext.toString());

                    long duration = System.currentTimeMillis() - stepStart;

                    if (stepResult.isSuccess()) {
                        String resultContent = stepResult.getContent() != null
                                ? stepResult.getContent() : "(completed)";
                        step.markCompleted(resultContent, duration);
                        stepsContext.append("\n[Step ").append(step.getIndex() + 1).append(" completed]: ")
                                .append(truncate(resultContent, 500));
                        allToolCallHistory.addAll(stepResult.getToolCallHistory());
                        callback.onPlanStepCompleted(step.getIndex(), true, resultContent);
                        provider.updateStepStatus(step.getIndex(), PlanStep.Status.COMPLETED, resultContent);
                    } else {
                        String errorContent = stepResult.getContent() != null
                                ? stepResult.getContent() : "Step execution failed";
                        step.markFailed(errorContent, duration);
                        stepsContext.append("\n[Step ").append(step.getIndex() + 1).append(" FAILED]: ")
                                .append(truncate(errorContent, 300));
                        allToolCallHistory.addAll(stepResult.getToolCallHistory());
                        callback.onPlanStepCompleted(step.getIndex(), false, errorContent);
                        provider.updateStepStatus(step.getIndex(), PlanStep.Status.FAILED, errorContent);

                        // 尝试重规划
                        if (replanCount < MAX_REPLAN_ATTEMPTS && i < plan.size() - 1) {
                            List<PlanStep> revisedPlan = attemptReplan(
                                    request, llmCaller, ctx, plan, i, stepsContext.toString());
                            if (revisedPlan != null && !revisedPlan.isEmpty()) {
                                replanCount++;
                                plan = mergeRevisedPlan(plan, i, revisedPlan);
                                callback.onPlanRevised(plan);
                                provider.revisePlan(plan);
                                LOG.info("Plan revised after step " + (i + 1)
                                        + " failure, " + revisedPlan.size() + " new steps");
                            }
                        }
                    }

                    // ExecutionHook: afterStep
                    if (hook != null) {
                        ExecutionHook.HookResult hr = hook.afterStep(step.getIndex(), stepResult);
                        if (hr.getAction() == ExecutionHook.HookAction.ABORT) {
                            LOG.info("ExecutionHook aborted plan after step " + (step.getIndex() + 1)
                                    + ": " + hr.getReason());
                            callback.onStrategyPhase("aborted",
                                    "执行被中止: " + (hr.getReason() != null ? hr.getReason() : ""));
                            break;
                        }
                    }

                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - stepStart;
                    step.markFailed(e.getMessage(), duration);
                    callback.onPlanStepCompleted(step.getIndex(), false, e.getMessage());
                    provider.updateStepStatus(step.getIndex(), PlanStep.Status.FAILED, e.getMessage());
                    LOG.log(Level.WARNING, "Step " + (step.getIndex() + 1) + " threw exception: " + e.getMessage());

                    // ExecutionHook: onError
                    if (hook != null) {
                        ExecutionHook.HookResult hr = hook.onError(
                                "step_" + (step.getIndex() + 1), e);
                        if (hr.getAction() == ExecutionHook.HookAction.ABORT) {
                            LOG.info("ExecutionHook aborted plan on error: " + hr.getReason());
                            break;
                        }
                    }
                }
            }

            // ============ Phase 3: Synthesis ============
            callback.onStrategyPhase("synthesizing", "正在汇总执行结果...");

            String synthesis = synthesizeResults(
                    request, llmCaller, callback, ctx, plan, stepsContext.toString());

            // 通过 callback 将合成结果推送到 UI，解决 UI 卡在 "正在汇总执行结果..." 的问题
            if (synthesis != null && !synthesis.isEmpty()) {
                callback.onStrategyPhase("completed", "执行完成");
                callback.onTextContent(0, synthesis);
            }

            FunctionCallResult finalResult = FunctionCallResult.success(
                    synthesis, sessionId, allToolCallHistory);

            // 标记 PlanProgressProvider 完成
            provider.markCompleted();

            // ExecutionHook: afterCompletion
            if (hook != null) {
                ExecutionHook.HookResult hr = hook.afterCompletion(finalResult);
                if (hr.getAction() == ExecutionHook.HookAction.ABORT) {
                    return FunctionCallResult.error(
                            hr.getReason() != null ? hr.getReason() : "Rejected by hook",
                            sessionId);
                }
            }

            return finalResult;

        } catch (Exception e) {
            ctx.getObservability().recordError(sessionId, e);
            ErrorMessage errorMsg = ctx.getErrorHandler().handle(e);
            callback.onStrategyPhase("error", "执行出错: " + errorMsg.getMessage());
            return FunctionCallResult.error(errorMsg.getMessage(), sessionId);
        } finally {
            ctx.getObservability().endSession(sessionId);
        }
    }

    // ==================== Planning ====================

    /**
     * 调用 LLM 生成执行计划（使用流式调用 + 超时保护）。
     * <p>
     * 流式调用的好处：
     * <ol>
     *   <li>规划过程实时展示在对话框中（通过 callback.onPlanningContentDelta），用户可看到 LLM 的思考过程</li>
     *   <li>首 token 到达即重置超时感知，避免因排队等待导致的误判超时</li>
     *   <li>流式内容仅用于 UI 展示，不加入主 Agent 上下文</li>
     * </ol>
     */
    private List<PlanStep> generatePlan(FunctionCallRequest request,
                                        LlmCaller llmCaller,
                                        ProgressCallback callback,
                                        ExecutionStrategyContext ctx) {
        String userMessage = extractUserMessage(request);
        String toolDescriptions = buildToolSummary(request.getAvailableTools());

        String planPrompt = buildPlanningPrompt(userMessage, toolDescriptions);

        ChatContent planChatContent = new ChatContent();
        List<Message> planMessages = new ArrayList<>();

        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent(planPrompt);
        planMessages.add(systemMsg);

        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        planMessages.add(userMsg);

        planChatContent.setDirectMessages(planMessages);
        if (request.getChatContent() != null) {
            planChatContent.setModel(request.getChatContent().getModel());
        }

        FunctionCallRequest planRequest = FunctionCallRequest.builder()
                .chatContent(planChatContent)
                .availableTools(Collections.emptyList())
                .maxRounds(1)
                .config(request.getConfig())
                .build();

        // 优先使用流式调用：实时展示规划过程，同时收集完整响应用于解析
        com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller streamingCaller = ctx.getStreamingLlmCaller();
        if (streamingCaller != null) {
            return generatePlanStreaming(planRequest, streamingCaller, callback);
        }

        // 回退到非流式调用
        planChatContent.setStream(false);
        return generatePlanSync(planRequest, llmCaller, callback);
    }

    /**
     * 流式规划：通过 StreamingLlmCaller 调用 LLM，实时推送规划内容到 UI。
     * 规划内容通过 onPlanningContentDelta / onPlanningReasoningDelta 推送，
     * 不加入主 Agent 上下文。
     */
    private List<PlanStep> generatePlanStreaming(FunctionCallRequest planRequest,
                                                 com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller streamingCaller,
                                                 ProgressCallback callback) {
        planRequest.getChatContent().setStream(true);

        try {
            String planResponse = callStreamingLlmWithTimeout(streamingCaller, planRequest, callback, PLANNING_TIMEOUT_SECONDS);
            if (planResponse == null || planResponse.isEmpty()) {
                LOG.log(Level.WARNING, "Streaming planning LLM call returned empty response");
                return Collections.emptyList();
            }
            return parsePlan(planResponse);
        } catch (TimeoutException e) {
            LOG.log(Level.WARNING, "Streaming planning LLM call timed out after " + PLANNING_TIMEOUT_SECONDS + "s");
            callback.onStrategyPhase("planning_timeout", "计划生成超时，切换到 ReAct 模式...");
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Streaming planning LLM call failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 非流式规划（回退路径）：当 StreamingLlmCaller 不可用时使用。
     */
    private List<PlanStep> generatePlanSync(FunctionCallRequest planRequest,
                                            LlmCaller llmCaller,
                                            ProgressCallback callback) {
        try {
            String planResponse = callLlmWithTimeout(llmCaller, planRequest, PLANNING_TIMEOUT_SECONDS);
            if (planResponse == null || planResponse.isEmpty()) {
                LOG.log(Level.WARNING, "Planning LLM call returned empty response");
                return Collections.emptyList();
            }
            return parsePlan(planResponse);
        } catch (TimeoutException e) {
            LOG.log(Level.WARNING, "Planning LLM call timed out after " + PLANNING_TIMEOUT_SECONDS + "s");
            callback.onStrategyPhase("planning_timeout", "计划生成超时，切换到 ReAct 模式...");
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Planning LLM call failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildPlanningPrompt(String userMessage, String toolDescriptions) {
        return "You are a planning agent. Your job is to analyze the user's request and create a clear, " +
                "step-by-step execution plan.\n\n" +
                "Available tools:\n" + toolDescriptions + "\n\n" +
                "Instructions:\n" +
                "1. Break down the task into concrete, actionable steps\n" +
                "2. Each step should be independently executable\n" +
                "3. Consider dependencies between steps\n" +
                "4. Keep the plan concise (max " + MAX_PLAN_STEPS + " steps)\n" +
                "5. Each step description should be specific enough for an executor to understand\n\n" +
                "Output your plan as a JSON array. Example format:\n" +
                "```json\n" +
                "[\n" +
                "  {\"step\": 1, \"description\": \"Read the main configuration file to understand current settings\"},\n" +
                "  {\"step\": 2, \"description\": \"Search for all usages of the deprecated API\"},\n" +
                "  {\"step\": 3, \"description\": \"Modify each file to use the new API\"}\n" +
                "]\n" +
                "```\n\n" +
                "Output ONLY the JSON array, no other text.";
    }

    private String buildToolSummary(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "(no tools available)";
        }
        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的计划 JSON。
     * 支持从 JSON 响应和纯 JSON 文本中提取。
     */
    List<PlanStep> parsePlan(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        String jsonContent = extractJsonContent(response);
        if (jsonContent == null) {
            LOG.log(Level.WARNING, "Failed to extract JSON from plan response");
            return parsePlanFromText(response);
        }

        try {
            JSONArray stepsArray = JSON.parseArray(jsonContent);
            if (stepsArray == null || stepsArray.isEmpty()) {
                return Collections.emptyList();
            }

            List<PlanStep> steps = new ArrayList<>();
            for (int i = 0; i < Math.min(stepsArray.size(), MAX_PLAN_STEPS); i++) {
                JSONObject stepObj = stepsArray.getJSONObject(i);
                if (stepObj != null) {
                    String description = stepObj.getString("description");
                    if (description != null && !description.isEmpty()) {
                        steps.add(new PlanStep(i, description));
                    }
                }
            }
            return steps;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse plan JSON: " + e.getMessage());
            return parsePlanFromText(response);
        }
    }

    private String extractJsonContent(String response) {
        String textContent = extractContentFromLlmResponse(response);

        // 尝试 markdown code block
        int jsonStart = textContent.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = textContent.indexOf('\n', jsonStart) + 1;
            int contentEnd = textContent.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                String blockContent = textContent.substring(contentStart, contentEnd).trim();
                // code block 内也可能有重复 JSON，提取第一个完整数组
                String firstArray = extractFirstJsonArray(blockContent);
                return firstArray != null ? firstArray : blockContent;
            }
        }

        // 尝试提取第一个完整的 JSON 数组（处理 LLM 返回重复 JSON 的情况）
        String firstArray = extractFirstJsonArray(textContent);
        if (firstArray != null) {
            return firstArray;
        }

        return null;
    }

    /**
     * 从文本中提取第一个完整的 JSON 数组。
     * 通过括号匹配找到第一个 '[' 对应的 ']'，而不是用 lastIndexOf。
     * 这样可以正确处理 LLM 返回重复 JSON 数组的情况（如 "[...] [...]"）。
     */
    private String extractFirstJsonArray(String text) {
        int arrayStart = text.indexOf('[');
        if (arrayStart < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = arrayStart; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(arrayStart, i + 1);
                }
            }
        }

        // 没有找到匹配的 ']'，返回 null（截断的 JSON）
        return null;
    }

    private String extractContentFromLlmResponse(String response) {
        try {
            JSONObject json = JSON.parseObject(response);
            if (json != null) {
                JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject choice = choices.getJSONObject(0);
                    if (choice != null) {
                        JSONObject message = choice.getJSONObject("message");
                        if (message != null) {
                            String content = message.getString("content");
                            if (content != null) return content;
                        }
                    }
                }
                // Anthropic 格式
                JSONArray contentArray = json.getJSONArray("content");
                if (contentArray != null) {
                    for (int i = 0; i < contentArray.size(); i++) {
                        JSONObject block = contentArray.getJSONObject(i);
                        if (block != null && "text".equals(block.getString("type"))) {
                            String text = block.getString("text");
                            if (text != null) return text;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // not JSON, return as-is
        }
        return response;
    }

    private List<PlanStep> parsePlanFromText(String text) {
        String content = extractContentFromLlmResponse(text);
        List<PlanStep> steps = new ArrayList<>();
        String[] lines = content.split("\n");
        int index = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+[.):]\\s+.*") ||
                    trimmed.matches("^-\\s+.*") ||
                    trimmed.matches("(?i)^step\\s+\\d+[.:]?\\s+.*")) {
                String description = trimmed.replaceFirst("^(\\d+[.):]|\\s*-|(?i)step\\s+\\d+[.:]?)\\s*", "").trim();
                if (!description.isEmpty() && index < MAX_PLAN_STEPS) {
                    steps.add(new PlanStep(index++, description));
                }
            }
        }
        return steps;
    }

    // ==================== Step Execution with Skill Routing ====================

    /**
     * 执行单个计划步骤，带步骤级 Skill 匹配路由。
     * <p>
     * 路由逻辑（需求 7.1-7.6）：
     * <ol>
     *   <li>对步骤描述进行 Skill 匹配</li>
     *   <li>匹配成功 + MODERATE/COMPLEX → 创建子 Agent 执行步骤</li>
     *   <li>匹配成功 + SIMPLE → Inline_Execution（注入 promptTemplate 到 ReAct 循环）</li>
     *   <li>未匹配或匹配/执行失败 → 使用现有 ReAct 循环</li>
     * </ol>
     * 所有错误优雅降级到 ReAct 循环，不阻塞计划执行流程。
     */
    private FunctionCallResult executeStepWithSkillRouting(
            PlanStep step,
            FunctionCallRequest originalRequest,
            ToolContext toolContext,
            LlmCaller llmCaller,
            ProgressCallback callback,
            ExecutionStrategyContext ctx,
            String previousStepsContext) {

        SkillMatcher matcher = ctx.getSkillMatcher();
        SkillRegistry registry = ctx.getSkillRegistry();

        // 如果 Skill 组件不可用，直接走 ReAct
        if (matcher == null || registry == null || registry.getSkillCount() == 0) {
            return executeStep(step, originalRequest, toolContext, llmCaller, callback, ctx, previousStepsContext);
        }

        // 1. 对步骤描述进行 Skill 匹配（需求 7.1）
        SkillDefinition matchedSkill = null;
        try {
            String modelName = originalRequest.getChatContent() != null
                    ? originalRequest.getChatContent().getModel() : null;
            SkillMatchResult matchResult = matcher.match(
                    step.getDescription(), registry.getAllSkills(), llmCaller, modelName);

            if (matchResult.isMatched()) {
                matchedSkill = registry.getSkill(matchResult.getSkillName());
            }

            if (matchedSkill != null) {
                SkillComplexity complexity = matchedSkill.getComplexity();
                String skillName = matchedSkill.getName();

                if (complexity.requiresSubAgent()) {
                    // 2. MODERATE/COMPLEX → 创建子 Agent 执行步骤（需求 7.2）
                    LOG.info("[PlanStep] Skill routing: step=" + (step.getIndex() + 1)
                            + ", skill='" + skillName + "', complexity=" + complexity
                            + ", decision=CREATE_SUB_AGENT");

                    FunctionCallResult subAgentResult = executeStepViaSubAgent(
                            step, matchedSkill, originalRequest, llmCaller, callback, ctx, previousStepsContext);
                    if (subAgentResult != null) {
                        return subAgentResult;
                    }
                    // 子 Agent 失败 → 降级到 ReAct
                    LOG.warning("[PlanStep] Sub-agent failed for step " + (step.getIndex() + 1)
                            + ", falling back to ReAct");
                } else {
                    // 3. SIMPLE → Inline_Execution（需求 7.3）
                    LOG.info("[PlanStep] Skill routing: step=" + (step.getIndex() + 1)
                            + ", skill='" + skillName + "', complexity=" + complexity
                            + ", decision=INLINE_EXECUTION");

                    return executeStepInline(
                            step, matchedSkill, originalRequest, toolContext, llmCaller, callback, ctx, previousStepsContext);
                }
            } else {
                LOG.fine("[PlanStep] No skill matched for step " + (step.getIndex() + 1)
                        + ", using ReAct loop");
            }
        } catch (Exception e) {
            // Skill 匹配失败 → 优雅降级到 ReAct（需求 10.1）
            LOG.log(Level.WARNING, "[PlanStep] Skill matching failed for step " + (step.getIndex() + 1)
                    + ", falling back to ReAct: " + e.getMessage(), e);
        }

        // 4. 未匹配或降级 → 使用现有 ReAct 循环（需求 7.4）
        return executeStep(step, originalRequest, toolContext, llmCaller, callback, ctx, previousStepsContext);
    }

    /**
     * 通过子 Agent 执行步骤（MODERATE/COMPLEX Skill）。
     * 子 Agent 执行结果直接返回，失败时返回 null 表示需要降级。
     */
    private FunctionCallResult executeStepViaSubAgent(
            PlanStep step,
            SkillDefinition skill,
            FunctionCallRequest originalRequest,
            LlmCaller llmCaller,
            ProgressCallback callback,
            ExecutionStrategyContext ctx,
            String previousStepsContext) {

        AgentRuntime runtime = ctx.getAgentRuntime();
        SubAgentProgressProvider progressProvider = ctx.getSubAgentProgressProvider();
        com.wmsay.gpt4_lll.fc.state.AgentSession mainSession = ctx.getAgentSession();

        if (runtime == null || mainSession == null) {
            LOG.warning("[PlanStep] AgentRuntime or session not available for sub-agent creation, falling back");
            return null;
        }

        try {
            ContextDistiller distiller = new ContextDistiller();
            SubAgentFactory factory = new SubAgentFactory(runtime, distiller);

            // 使用主 Agent 的会话创建子 Agent，传入 stepsContext 供 ContextDistiller 使用
            SubAgentResult subResult = factory.createAndExecute(
                    skill, step.getDescription(), mainSession,
                    llmCaller, callback, progressProvider, previousStepsContext);

            if (subResult.isSuccess()) {
                // 需求 7.5: 子 Agent 执行结果写入 stepsContext（由调用方处理）
                return FunctionCallResult.success(
                        subResult.getContent(),
                        "plan-step-" + step.getIndex(),
                        Collections.emptyList());
            } else {
                LOG.warning("[PlanStep] Sub-agent execution failed for step " + (step.getIndex() + 1)
                        + ": " + subResult.getContent());
                return null; // 降级信号
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[PlanStep] Sub-agent creation/execution failed for step "
                    + (step.getIndex() + 1) + ": " + e.getMessage(), e);
            return null; // 降级信号
        }
    }

    /**
     * 通过 Inline_Execution 模式执行步骤（SIMPLE Skill）。
     * 将 Skill 的 promptTemplate 注入到 ReAct 循环中执行。
     * 需求 7.3, 7.6: 结果格式与子 Agent 执行保持一致。
     */
    private FunctionCallResult executeStepInline(
            PlanStep step,
            SkillDefinition skill,
            FunctionCallRequest originalRequest,
            ToolContext toolContext,
            LlmCaller llmCaller,
            ProgressCallback callback,
            ExecutionStrategyContext ctx,
            String previousStepsContext) {

        // 构建增强的 systemPrompt（包含 Skill 的 systemPrompt + additionalNotes）
        StringBuilder enhancedSystemPrompt = new StringBuilder();
        enhancedSystemPrompt.append(skill.getSystemPrompt());
        if (skill.getAdditionalNotes() != null && !skill.getAdditionalNotes().isBlank()) {
            enhancedSystemPrompt.append("\n\n").append(skill.getAdditionalNotes());
        }
        enhancedSystemPrompt.append("\n\n");
        enhancedSystemPrompt.append(buildStepSystemPrompt(step, previousStepsContext));

        // 替换 promptTemplate 中的 {{user_input}} 占位符
        String effectiveUserMessage = skill.getPromptTemplate()
                .replace("{{user_input}}", step.getDescription());

        ChatContent stepChatContent = new ChatContent();
        List<Message> stepMessages = new ArrayList<>();

        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent(enhancedSystemPrompt.toString());
        stepMessages.add(systemMsg);

        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(effectiveUserMessage);
        stepMessages.add(userMsg);

        stepChatContent.setDirectMessages(stepMessages);
        if (originalRequest.getChatContent() != null) {
            stepChatContent.setModel(originalRequest.getChatContent().getModel());
        }

        // 工具集策略：Skill 声明了工具列表时仅使用声明的工具，否则使用全量工具
        List<Tool> stepTools = originalRequest.getAvailableTools();
        if (skill.getTools() != null && !skill.getTools().isEmpty()) {
            List<String> skillToolNames = skill.getTools();
            stepTools = originalRequest.getAvailableTools().stream()
                    .filter(t -> skillToolNames.contains(t.name()))
                    .collect(Collectors.toList());
        }

        FunctionCallRequest stepRequest = FunctionCallRequest.builder()
                .chatContent(stepChatContent)
                .availableTools(stepTools)
                .maxRounds(STEP_MAX_ROUNDS)
                .config(originalRequest.getConfig())
                .build();

        ExecutionStrategyContext stepCtx = new ExecutionStrategyContext(
                ctx.getProtocolAdapter(),
                ctx.getValidationEngine(),
                ctx.getExecutionEngine(),
                ctx.getErrorHandler(),
                ctx.getObservability(),
                ctx.getDegradationManager(),
                null,
                null,
                ctx.getStreamingLlmCaller(),
                ctx.getExecutionHook()
        );

        LOG.info("[PlanStep] Inline execution for step " + (step.getIndex() + 1)
                + " with skill '" + skill.getName() + "'");

        return reActStrategy.executeReActLoop(
                stepRequest, toolContext, llmCaller, callback, stepCtx, STEP_MAX_ROUNDS);
    }

    // ==================== Step Execution ====================

    /**
     * 执行单个计划步骤。
     * <p>
     * 为每个步骤构建独立的对话上下文（包含步骤指令和之前步骤的结果摘要），
     * 然后使用 ReAct 循环执行该步骤。
     */
    private FunctionCallResult executeStep(PlanStep step,
                                           FunctionCallRequest originalRequest,
                                           ToolContext toolContext,
                                           LlmCaller llmCaller,
                                           ProgressCallback callback,
                                           ExecutionStrategyContext ctx,
                                           String previousStepsContext) {
        String stepSystemPrompt = buildStepSystemPrompt(step, previousStepsContext);

        ChatContent stepChatContent = new ChatContent();
        List<Message> stepMessages = new ArrayList<>();

        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent(stepSystemPrompt);
        stepMessages.add(systemMsg);

        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent("Execute this step: " + step.getDescription());
        stepMessages.add(userMsg);

        stepChatContent.setDirectMessages(stepMessages);
        if (originalRequest.getChatContent() != null) {
            stepChatContent.setModel(originalRequest.getChatContent().getModel());
        }

        FunctionCallRequest stepRequest = FunctionCallRequest.builder()
                .chatContent(stepChatContent)
                .availableTools(originalRequest.getAvailableTools())
                .maxRounds(STEP_MAX_ROUNDS)
                .config(originalRequest.getConfig())
                .build();

        // 保留 hook 以便步骤内的 ReAct 循环也能受 hook 保护
        ExecutionStrategyContext stepCtx = new ExecutionStrategyContext(
                ctx.getProtocolAdapter(),
                ctx.getValidationEngine(),
                ctx.getExecutionEngine(),
                ctx.getErrorHandler(),
                ctx.getObservability(),
                ctx.getDegradationManager(),
                null,  // 步骤级别不使用全局 memory
                null,  // 步骤级别不使用 usage tracker
                ctx.getStreamingLlmCaller(),
                ctx.getExecutionHook()
        );

        return reActStrategy.executeReActLoop(
                stepRequest, toolContext, llmCaller, callback, stepCtx, STEP_MAX_ROUNDS);
    }

    private String buildStepSystemPrompt(PlanStep step, String previousStepsContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are executing step ").append(step.getIndex() + 1).append(" of a multi-step plan.\n\n");
        sb.append("Current step: ").append(step.getDescription()).append("\n\n");

        if (previousStepsContext != null && !previousStepsContext.isEmpty()) {
            sb.append("Context from previous steps:\n").append(previousStepsContext).append("\n\n");
        }

        sb.append("Instructions:\n");
        sb.append("- Focus on completing ONLY this step\n");
        sb.append("- Use the available tools as needed\n");
        sb.append("- Provide a clear summary of what was accomplished when done\n");
        sb.append("- If you cannot complete the step, explain why\n");

        return sb.toString();
    }

    // ==================== Replanning ====================

    private List<PlanStep> attemptReplan(FunctionCallRequest originalRequest,
                                         LlmCaller llmCaller,
                                         ExecutionStrategyContext ctx,
                                         List<PlanStep> currentPlan,
                                         int failedStepIndex,
                                         String stepsContext) {
        String userMessage = extractUserMessage(originalRequest);
        String completedSteps = currentPlan.stream()
                .filter(s -> s.getStatus() == PlanStep.Status.COMPLETED)
                .map(s -> "✅ Step " + (s.getIndex() + 1) + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
        String failedStep = "❌ Step " + (failedStepIndex + 1) + ": "
                + currentPlan.get(failedStepIndex).getDescription()
                + " — " + currentPlan.get(failedStepIndex).getResult();

        String replanPrompt = "The original task was: " + userMessage + "\n\n" +
                "Completed steps:\n" + completedSteps + "\n\n" +
                "Failed step:\n" + failedStep + "\n\n" +
                "Context:\n" + stepsContext + "\n\n" +
                "Please create a revised plan for the REMAINING work, taking into account " +
                "what has already been completed and the failure reason.\n\n" +
                "Output ONLY a JSON array of remaining steps. Example:\n" +
                "[{\"step\": 1, \"description\": \"...\"}]";

        ChatContent replanChat = new ChatContent();
        List<Message> messages = new ArrayList<>();
        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent("You are a planning agent. Revise the execution plan based on completed and failed steps.");
        messages.add(systemMsg);
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(replanPrompt);
        messages.add(userMsg);
        replanChat.setDirectMessages(messages);
        if (originalRequest.getChatContent() != null) {
            replanChat.setModel(originalRequest.getChatContent().getModel());
        }
        replanChat.setStream(false);

        FunctionCallRequest replanRequest = FunctionCallRequest.builder()
                .chatContent(replanChat)
                .availableTools(Collections.emptyList())
                .maxRounds(1)
                .config(originalRequest.getConfig())
                .build();

        try {
            String response = callLlmWithTimeout(llmCaller, replanRequest, PLANNING_TIMEOUT_SECONDS);
            List<PlanStep> revisedSteps = parsePlan(response);
            if (revisedSteps != null && !revisedSteps.isEmpty()) {
                int startIndex = failedStepIndex + 1;
                List<PlanStep> reindexed = new ArrayList<>();
                for (int i = 0; i < revisedSteps.size(); i++) {
                    reindexed.add(new PlanStep(startIndex + i, revisedSteps.get(i).getDescription()));
                }
                return reindexed;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Replan attempt failed: " + e.getMessage());
        }
        return null;
    }

    private List<PlanStep> mergeRevisedPlan(List<PlanStep> originalPlan,
                                            int failedStepIndex,
                                            List<PlanStep> revisedSteps) {
        List<PlanStep> merged = new ArrayList<>();
        for (int i = 0; i <= failedStepIndex; i++) {
            merged.add(originalPlan.get(i));
        }
        merged.addAll(revisedSteps);
        return merged;
    }

    // ==================== Synthesis ====================

    /**
     * 汇总所有步骤结果，生成最终回答（带超时保护）。
     * <p>
     * 修复了原来的 UI 卡死问题：当 LLM 调用超时或失败时，
     * 使用步骤结果的拼接作为 fallback，确保总能返回结果。
     */
    private String synthesizeResults(FunctionCallRequest originalRequest,
                                     LlmCaller llmCaller,
                                     ProgressCallback callback,
                                     ExecutionStrategyContext ctx,
                                     List<PlanStep> plan,
                                     String stepsContext) {
        String userMessage = extractUserMessage(originalRequest);

        // 构建 fallback 结果（即使 LLM 调用失败也有内容可返回）
        String fallbackResult = buildFallbackSynthesis(plan, stepsContext);

        // 如果只有一个步骤且成功完成，直接返回其结果
        long completedCount = plan.stream()
                .filter(s -> s.getStatus() == PlanStep.Status.COMPLETED)
                .count();
        if (plan.size() == 1 && completedCount == 1) {
            return plan.get(0).getResult();
        }

        // 如果没有任何步骤完成，直接返回 fallback
        if (completedCount == 0) {
            return fallbackResult;
        }

        // 多步骤：调用 LLM 综合结果（带超时保护）
        String synthesisPrompt = "You completed a multi-step task. Here is the summary:\n\n" +
                "Original request: " + userMessage + "\n\n" +
                "Steps and results:\n" + stepsContext + "\n\n" +
                "Please provide a comprehensive final answer to the original request, " +
                "incorporating the results from all completed steps. " +
                "Be concise but thorough.";

        ChatContent synthChat = new ChatContent();
        List<Message> messages = new ArrayList<>();
        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent("You are a helpful assistant. Synthesize the results of a multi-step execution plan into a final answer.");
        messages.add(systemMsg);
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(synthesisPrompt);
        messages.add(userMsg);
        synthChat.setDirectMessages(messages);
        if (originalRequest.getChatContent() != null) {
            synthChat.setModel(originalRequest.getChatContent().getModel());
        }
        synthChat.setStream(false);

        FunctionCallRequest synthRequest = FunctionCallRequest.builder()
                .chatContent(synthChat)
                .availableTools(Collections.emptyList())
                .maxRounds(1)
                .config(originalRequest.getConfig())
                .build();

        try {
            String response = callLlmWithTimeout(llmCaller, synthRequest, SYNTHESIS_TIMEOUT_SECONDS);
            String content = extractContentFromLlmResponse(response);
            if (content != null && !content.isEmpty()) {
                return content;
            }
            LOG.log(Level.WARNING, "Synthesis LLM returned empty content, using fallback");
            return fallbackResult;
        } catch (TimeoutException e) {
            LOG.log(Level.WARNING, "Synthesis LLM call timed out after " + SYNTHESIS_TIMEOUT_SECONDS + "s, using fallback");
            callback.onStrategyPhase("synthesis_timeout", "结果汇总超时，使用步骤结果...");
            return fallbackResult;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Synthesis LLM call failed: " + e.getMessage() + ", using fallback");
            return fallbackResult;
        }
    }

    /**
     * 构建 fallback 合成结果：将所有已完成步骤的结果拼接。
     */
    private String buildFallbackSynthesis(List<PlanStep> plan, String stepsContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Plan execution completed.\n\n");

        boolean hasCompleted = false;
        for (PlanStep step : plan) {
            String icon;
            switch (step.getStatus()) {
                case COMPLETED: icon = "✅"; hasCompleted = true; break;
                case FAILED: icon = "❌"; break;
                case SKIPPED: icon = "⏭"; break;
                default: icon = "⬜"; break;
            }
            sb.append(icon).append(" Step ").append(step.getIndex() + 1)
                    .append(": ").append(step.getDescription());
            if (step.getResult() != null) {
                sb.append("\n   ").append(truncate(step.getResult(), 300));
            }
            sb.append("\n\n");
        }

        if (!hasCompleted) {
            sb.append("No steps completed successfully.");
        }
        return sb.toString();
    }

    // ==================== Utilities ====================

    /**
     * 带超时的流式 LLM 调用（用于规划阶段）。
     * <p>
     * 通过 StreamingLlmCaller 调用 LLM，实时将 reasoning/content 增量推送到
     * ProgressCallback 的 onPlanningReasoningDelta / onPlanningContentDelta，
     * 使 UI 能实时展示规划过程。这些内容不会加入主 Agent 上下文。
     * <p>
     * 返回 StreamingFcCollector.reconstructResponse() 生成的等价非流式 JSON，
     * 供 parsePlan() 解析。
     */
    private String callStreamingLlmWithTimeout(
            com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller streamingCaller,
            FunctionCallRequest request,
            ProgressCallback callback,
            int timeoutSeconds) throws TimeoutException {

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PlanAndExecute-StreamingLLM-Timeout");
            t.setDaemon(true);
            return t;
        });

        // DisplayCallback：将规划阶段的流式内容推送到 ProgressCallback，仅用于 UI 展示
        com.wmsay.gpt4_lll.fc.llm.StreamingFcCollector.DisplayCallback displayCallback =
                new com.wmsay.gpt4_lll.fc.llm.StreamingFcCollector.DisplayCallback() {
                    @Override
                    public void onReasoningDelta(String delta) {
                        callback.onPlanningReasoningDelta(delta);
                    }

                    @Override
                    public void onContentDelta(String delta) {
                        callback.onPlanningContentDelta(delta);
                    }
                };

        Future<String> future = executor.submit(() -> streamingCaller.call(request, displayCallback));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Streaming LLM call failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Streaming LLM call interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 带超时的 LLM 调用。
     * 防止规划/合成阶段的 LLM 调用无限阻塞导致 UI 卡死。
     */
    private String callLlmWithTimeout(LlmCaller llmCaller,
                                      FunctionCallRequest request,
                                      int timeoutSeconds) throws TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PlanAndExecute-LLM-Timeout");
            t.setDaemon(true);
            return t;
        });

        Future<String> future = executor.submit(() -> llmCaller.call(request));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private String extractUserMessage(FunctionCallRequest request) {
        if (request.getChatContent() == null || request.getChatContent().getMessages() == null) {
            return null;
        }
        List<Message> messages = request.getChatContent().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                return msg.getContent();
            }
        }
        return null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
