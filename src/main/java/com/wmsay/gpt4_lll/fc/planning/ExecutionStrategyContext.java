package com.wmsay.gpt4_lll.fc.planning;

import com.wmsay.gpt4_lll.fc.core.AgentRuntimeConfig;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller;
import com.wmsay.gpt4_lll.fc.llm.DegradationManager;
import com.wmsay.gpt4_lll.fc.llm.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.runtime.AgentRuntime;
import com.wmsay.gpt4_lll.fc.skill.SkillMatcher;
import com.wmsay.gpt4_lll.fc.skill.SkillRegistry;
import com.wmsay.gpt4_lll.fc.state.SubAgentProgressProvider;
import com.wmsay.gpt4_lll.fc.tools.ErrorHandler;
import com.wmsay.gpt4_lll.fc.tools.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.tools.ValidationEngine;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.UsageTracker;

/**
 * 执行策略共享上下文 — 打包所有策略执行所需的依赖组件。
 * <p>
 * 策略实现通过此上下文访问共享基础设施，无需直接持有各组件引用。
 * 由 {@link com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator} 在调度时创建。
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
    private final StreamingLlmCaller streamingLlmCaller;
    private final ExecutionHook executionHook;

    public ExecutionStrategyContext(ProtocolAdapter protocolAdapter,
                                   ValidationEngine validationEngine,
                                   ExecutionEngine executionEngine,
                                   ErrorHandler errorHandler,
                                   ObservabilityManager observability,
                                   DegradationManager degradationManager,
                                   ConversationMemory memory,
                                   UsageTracker usageTracker,
                                   StreamingLlmCaller streamingLlmCaller) {
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
                                   StreamingLlmCaller streamingLlmCaller,
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
    public StreamingLlmCaller getStreamingLlmCaller() { return streamingLlmCaller; }

    /**
     * 获取执行钩子。若未配置则返回 null，策略应检查 null 并跳过钩子调用。
     */
    public ExecutionHook getExecutionHook() { return executionHook; }

    // ── 可选的 Skill 相关组件（PlanAndExecuteStrategy 步骤级 Skill 匹配使用） ──

    private SkillMatcher skillMatcher;
    private SkillRegistry skillRegistry;
    private AgentRuntime agentRuntime;
    private SubAgentProgressProvider subAgentProgressProvider;
    private AgentRuntimeConfig agentRuntimeConfig;
    private com.wmsay.gpt4_lll.fc.state.AgentSession agentSession;

    public SkillMatcher getSkillMatcher() { return skillMatcher; }
    public void setSkillMatcher(SkillMatcher skillMatcher) { this.skillMatcher = skillMatcher; }

    public SkillRegistry getSkillRegistry() { return skillRegistry; }
    public void setSkillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; }

    public AgentRuntime getAgentRuntime() { return agentRuntime; }
    public void setAgentRuntime(AgentRuntime agentRuntime) { this.agentRuntime = agentRuntime; }

    public SubAgentProgressProvider getSubAgentProgressProvider() { return subAgentProgressProvider; }
    public void setSubAgentProgressProvider(SubAgentProgressProvider provider) { this.subAgentProgressProvider = provider; }

    public AgentRuntimeConfig getAgentRuntimeConfig() { return agentRuntimeConfig; }
    public void setAgentRuntimeConfig(AgentRuntimeConfig config) { this.agentRuntimeConfig = config; }

    public com.wmsay.gpt4_lll.fc.state.AgentSession getAgentSession() { return agentSession; }
    public void setAgentSession(com.wmsay.gpt4_lll.fc.state.AgentSession session) { this.agentSession = session; }
}
