package com.wmsay.gpt4_lll.fc.events;

import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.planning.PlanStep;

import java.util.List;
import java.util.Map;

/**
 * 执行进度回调接口 — 框架层顶层接口。
 * <p>
 * 在 FC 执行的关键节点通知调用方，使 UI 能实时展示执行状态。
 * 所有方法使用 default 空实现，调用方仅需覆盖关心的回调。
 * <p>
 * 从 {@code FunctionCallOrchestrator.ProgressCallback} 提取为独立顶层接口，
 * 属于 events 层，不依赖任何 IntelliJ Platform API。
 *
 * @see com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator
 */
public interface ProgressCallback {

    /** 开始调用 LLM（第 round 轮，从 0 开始） */
    default void onLlmCallStarting(int round) {}

    /** LLM 返回，解析出 toolCallCount 个工具调用 */
    default void onLlmCallCompleted(int round, int toolCallCount) {}

    /** LLM 响应中包含的思考过程内容（reasoning_content）——非流式路径使用 */
    default void onReasoningContent(int round, String reasoningContent) {}

    /** 流式路径：思考过程开始（用于创建 ThinkingBlock） */
    default void onReasoningStarted(int round) {}

    /** 流式路径：思考过程增量内容 */
    default void onReasoningDelta(int round, String delta) {}

    /** 流式路径：思考过程结束（用于折叠 ThinkingBlock） */
    default void onReasoningComplete(int round) {}

    /** LLM 响应中包含的文本内容——非流式路径使用 */
    default void onTextContent(int round, String content) {}

    /** 流式路径：文本内容增量 */
    default void onTextDelta(int round, String delta) {}

    /** 即将执行某个工具 */
    default void onToolExecutionStarting(String toolName, Map<String, Object> params) {}

    /** 工具执行完成（成功或失败） */
    default void onToolExecutionCompleted(ToolCallResult result) {}

    /** 摘要操作开始 */
    default void onMemorySummarizingStarted() {}

    /** 摘要操作完成 */
    default void onMemorySummarizingCompleted(int originalTokens, int compressedTokens) {}

    /** 摘要操作失败 */
    default void onMemorySummarizingFailed(String reason) {}

    // ---- Plan-and-Execute 策略相关回调 ----

    /** 规划阶段流式内容增量（仅用于 UI 展示，不加入主 Agent 上下文） */
    default void onPlanningContentDelta(String delta) {}

    /** 规划阶段流式思考过程增量（仅用于 UI 展示，不加入主 Agent 上下文） */
    default void onPlanningReasoningDelta(String delta) {}

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
