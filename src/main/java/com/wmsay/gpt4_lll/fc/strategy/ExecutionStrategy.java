package com.wmsay.gpt4_lll.fc.strategy;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.mcp.McpContext;

/**
 * 执行策略接口 — Agent 的核心执行逻辑抽象。
 * <p>
 * ReAct loop 是其中一种实现，PlanAndExecute 是另一种。
 * 未来可扩展 Tree of Thought、ReWOO 等策略。
 * <p>
 * 策略通过 {@link ExecutionStrategyContext} 访问所有共享组件
 * （ProtocolAdapter、ExecutionEngine、Memory 等），无需直接依赖 FunctionCallOrchestrator。
 */
public interface ExecutionStrategy {

    /** 策略的唯一标识名称（用于配置文件和注册表） */
    String getName();

    /** 策略的用户可读显示名称（用于 UI） */
    String getDisplayName();

    /** 策略的简要描述 */
    String getDescription();

    /**
     * 执行 Agent 任务。
     *
     * @param request          包含对话内容、可用工具和配置
     * @param mcpContext        MCP 执行上下文
     * @param llmCaller         非流式 LLM 调用器
     * @param progressCallback  进度回调
     * @param strategyContext   共享组件上下文
     * @return 执行结果
     */
    FunctionCallResult execute(
            FunctionCallRequest request,
            McpContext mcpContext,
            FunctionCallOrchestrator.LlmCaller llmCaller,
            FunctionCallOrchestrator.ProgressCallback progressCallback,
            ExecutionStrategyContext strategyContext
    );
}
