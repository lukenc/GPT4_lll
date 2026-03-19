package com.wmsay.gpt4_lll.fc.strategy;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.UsageTracker;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.DegradationManager;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;

/**
 * 执行策略共享上下文 — 打包所有策略执行所需的依赖组件。
 * <p>
 * 策略实现通过此上下文访问共享基础设施，无需直接持有各组件引用。
 * 由 {@link com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator} 在调度时创建。
 */
public class ExecutionStrategyContext {

    private final ProtocolAdapter protocolAdapter;
    private final ValidationEngine validationEngine;
    private final ExecutionEngine executionEngine;
    private final ErrorHandler errorHandler;
    private final ObservabilityManager observability;
    private final DegradationManager degradationManager;
    private final ConversationMemory memory;
    private final UsageTracker usageTracker;
    private final FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller;
    private final ExecutionHook executionHook;

    public ExecutionStrategyContext(ProtocolAdapter protocolAdapter,
                                   ValidationEngine validationEngine,
                                   ExecutionEngine executionEngine,
                                   ErrorHandler errorHandler,
                                   ObservabilityManager observability,
                                   DegradationManager degradationManager,
                                   ConversationMemory memory,
                                   UsageTracker usageTracker,
                                   FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller) {
        this(protocolAdapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager, memory, usageTracker, streamingLlmCaller, null);
    }

    public ExecutionStrategyContext(ProtocolAdapter protocolAdapter,
                                   ValidationEngine validationEngine,
                                   ExecutionEngine executionEngine,
                                   ErrorHandler errorHandler,
                                   ObservabilityManager observability,
                                   DegradationManager degradationManager,
                                   ConversationMemory memory,
                                   UsageTracker usageTracker,
                                   FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller,
                                   ExecutionHook executionHook) {
        this.protocolAdapter = protocolAdapter;
        this.validationEngine = validationEngine;
        this.executionEngine = executionEngine;
        this.errorHandler = errorHandler;
        this.observability = observability;
        this.degradationManager = degradationManager;
        this.memory = memory;
        this.usageTracker = usageTracker;
        this.streamingLlmCaller = streamingLlmCaller;
        this.executionHook = executionHook;
    }

    public ProtocolAdapter getProtocolAdapter() { return protocolAdapter; }
    public ValidationEngine getValidationEngine() { return validationEngine; }
    public ExecutionEngine getExecutionEngine() { return executionEngine; }
    public ErrorHandler getErrorHandler() { return errorHandler; }
    public ObservabilityManager getObservability() { return observability; }
    public DegradationManager getDegradationManager() { return degradationManager; }
    public ConversationMemory getMemory() { return memory; }
    public UsageTracker getUsageTracker() { return usageTracker; }
    public FunctionCallOrchestrator.StreamingLlmCaller getStreamingLlmCaller() { return streamingLlmCaller; }

    /**
     * 获取执行钩子。若未配置则返回 null，策略应检查 null 并跳过钩子调用。
     */
    public ExecutionHook getExecutionHook() { return executionHook; }
}
