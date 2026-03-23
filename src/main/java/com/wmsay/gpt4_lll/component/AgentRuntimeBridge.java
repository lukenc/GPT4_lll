package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.agent.*;
import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.strategy.PlanStep;
import com.wmsay.gpt4_lll.model.AgentPhase;
import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;

import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AgentRuntime 桥接层 + 集成管理器。
 * 位于 component 包，允许依赖 IntelliJ API。
 * 每个 WindowTool 实例持有一个 AgentRuntimeBridge 实例。
 *
 * 职责：
 * 1. 桥接 IntelliJ Project 与 AgentRuntime（Project → projectId → AgentRuntime）
 * 2. 初始化 AgentRuntime 并注册默认 Agent
 * 3. 管理 AgentSession 生命周期（创建/获取/重置/销毁）
 * 4. 封装消息发送流程（委托 AgentRuntime.send()）
 * 5. 注册/移除 AgentFileChangeHook
 * 6. 项目关闭时清理资源
 */
public class AgentRuntimeBridge {

    private static final Logger LOG = Logger.getLogger(AgentRuntimeBridge.class.getName());
    static final String DEFAULT_AGENT_ID = "default-chat-agent";

    private final Project project;
    private AgentRuntime runtime;
    private AgentSession currentSession;
    private boolean initialized = false;
    private FunctionCallOrchestrator orchestrator;
    private FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller;
    private volatile Thread currentRequestThread;

    public AgentRuntimeBridge(Project project) {
        this.project = project;
    }

    /**
     * 从 IntelliJ Project 获取 AgentRuntime 实例（内部桥接方法）。
     * 使用 Project.getBasePath() 作为平台无关的 projectId。
     */
    private AgentRuntime getRuntime() {
        String projectId = project.getBasePath();
        return AgentRuntime.getInstance(projectId);
    }

    /**
     * 初始化 AgentRuntime 并注册默认 Agent。
     * 内部捕获所有异常并记录日志，不影响现有功能。
     * 重复调用时跳过已注册的 Agent，保证幂等性。
     *
     * @return true 初始化成功，false 初始化失败
     */
    public boolean initialize() {
        try {
            // 1. 通过内部桥接方法获取 AgentRuntime
            this.runtime = getRuntime();

            // 2. 初始化 KnowledgeBase 并注入到 runtime
            KnowledgeBase kb = new KnowledgeBase(Paths.get(project.getBasePath()));
            runtime.setKnowledgeBase(kb);

            // 3. 注册默认聊天 Agent（如果尚未注册，保证幂等性）
            if (!runtime.isRegistered(DEFAULT_AGENT_ID)) {
                List<String> toolNames = McpToolRegistry.getAllTools().stream()
                        .map(McpTool::name)
                        .collect(Collectors.toList());

                AgentDefinition definition = AgentDefinition.builder()
                        .id(DEFAULT_AGENT_ID)
                        .name("Chat Agent")
                        .systemPrompt("你是一个智能编程助手，可以帮助用户完成代码编写、调试和项目管理等任务。")
                        .availableToolNames(toolNames)
                        .strategyName("react")
                        .memoryStrategy("sliding_window")
                        .build();

                runtime.register(definition);
            }

            initialized = true;
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "AgentRuntime initialization failed", e);
            initialized = false;
            return false;
        }
    }

    /** 是否已成功初始化 */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取或创建当前会话。
     * 当 currentSession 为 null 或已 DESTROYED 时自动创建新会话，
     * 否则返回现有会话。
     * 处理并发数超限：捕获 IllegalStateException，先销毁旧会话再重试。
     *
     * @return 当前活跃的 AgentSession
     */
    public AgentSession getOrCreateSession() {
        if (currentSession != null && currentSession.getState() != SessionState.DESTROYED) {
            return currentSession;
        }

        // 构建包含 project 和 projectRoot 的 ExecutionContext
        McpContext mcpContext = McpContext.fromIdeState(project, null);
        ExecutionContext context = ExecutionContext.fromMcpContext(mcpContext);

        try {
            currentSession = runtime.createSession(DEFAULT_AGENT_ID, context);
        } catch (IllegalStateException e) {
            // 并发数超限，先销毁旧会话再重试
            if (currentSession != null) {
                runtime.destroySession(currentSession.getSessionId());
                currentSession = null;
            }
            currentSession = runtime.createSession(DEFAULT_AGENT_ID, context);
        }
        return currentSession;
    }

    /**
     * 重置会话（新建对话时调用）。
     * 通过 AgentRuntime.destroySession() 销毁旧会话，将 currentSession 置为 null。
     */
    public void resetSession() {
        if (currentSession != null && runtime != null) {
            runtime.destroySession(currentSession.getSessionId());
            currentSession = null;
        }
    }

    /**
     * 关闭并清理所有资源。
     * 销毁当前会话并调用 AgentRuntime.shutdown() 释放所有资源。
     */
    public void shutdown() {
        if (currentSession != null && runtime != null) {
            runtime.destroySession(currentSession.getSessionId());
            currentSession = null;
        }
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
        }
        initialized = false;
    }

    /**
     * 获取当前会话的 FileChangeTracker。
     *
     * @return FileChangeTracker，当前无会话时返回 null
     */
    public FileChangeTracker getFileChangeTracker() {
        return currentSession != null ? currentSession.getFileChangeTracker() : null;
    }

    /**
     * 获取当前会话的 TaskManager。
     *
     * @return TaskManager，当前无会话时返回 null
     */
    public TaskManager getTaskManager() {
        return currentSession != null ? currentSession.getTaskManager() : null;
    }

    /**
     * 注入 FunctionCallOrchestrator 实例，供 sendMessage() 使用。
     * 由 WindowTool 在初始化后调用。
     *
     * @param orchestrator FC 编排器实例
     */
    public void setOrchestrator(FunctionCallOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 注入流式 LLM 调用器，供 sendMessage() 使用。
     * 由 WindowTool 在初始化后调用。
     *
     * @param caller 流式 LLM 调用器
     */
    public void setStreamingLlmCaller(FunctionCallOrchestrator.StreamingLlmCaller caller) {
        this.streamingLlmCaller = caller;
    }

    /**
     * 设置当前请求线程引用（由 WindowTool 在启动线程时调用）。
     */
    public void setCurrentRequestThread(Thread thread) {
        this.currentRequestThread = thread;
    }

    /**
     * 请求停止当前执行。
     * 中断当前请求线程并设置 AgentPhase 为 STOPPED。
     * 即使 interrupt 抛出异常，仍保证 phase 被设置为 STOPPED。
     */
    public void requestStop(Project project) {
        try {
            Thread thread = currentRequestThread;
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        } catch (SecurityException e) {
            LOG.log(Level.WARNING, "SecurityException during thread interrupt", e);
        } finally {
            RuntimeStatusManager.setAgentPhase(project,
                    AgentStatusContext.of(AgentPhase.STOPPED, "用户主动停止"));
        }
    }

    /**
     * 包装 ProgressCallback，将执行进度映射为 AgentPhase 变化。
     */
    FunctionCallOrchestrator.ProgressCallback wrapCallback(
            Project project,
            FunctionCallOrchestrator.ProgressCallback original) {
        return new FunctionCallOrchestrator.ProgressCallback() {
            @Override
            public void onLlmCallStarting(int round) {
                RuntimeStatusManager.setAgentPhase(project,
                        AgentStatusContext.of(AgentPhase.RUNNING, "LLM 思考中"));
                if (original != null) original.onLlmCallStarting(round);
            }

            @Override
            public void onToolExecutionStarting(String toolName, java.util.Map<String, Object> params) {
                RuntimeStatusManager.setAgentPhase(project,
                        AgentStatusContext.of(AgentPhase.RUNNING, "正在执行 " + toolName));
                if (original != null) original.onToolExecutionStarting(toolName, params);
            }

            @Override
            public void onTextDelta(int round, String delta) {
                RuntimeStatusManager.setAgentPhase(project,
                        AgentStatusContext.of(AgentPhase.RUNNING, "流式输出中"));
                if (original != null) original.onTextDelta(round, delta);
            }

            @Override
            public void onLlmCallCompleted(int round, int toolCallCount) {
                if (original != null) original.onLlmCallCompleted(round, toolCallCount);
            }

            @Override
            public void onReasoningContent(int round, String reasoningContent) {
                if (original != null) original.onReasoningContent(round, reasoningContent);
            }

            @Override
            public void onReasoningStarted(int round) {
                if (original != null) original.onReasoningStarted(round);
            }

            @Override
            public void onReasoningDelta(int round, String delta) {
                if (original != null) original.onReasoningDelta(round, delta);
            }

            @Override
            public void onReasoningComplete(int round) {
                if (original != null) original.onReasoningComplete(round);
            }

            @Override
            public void onTextContent(int round, String content) {
                if (original != null) original.onTextContent(round, content);
            }

            @Override
            public void onToolExecutionCompleted(ToolCallResult result) {
                if (original != null) original.onToolExecutionCompleted(result);
            }

            @Override
            public void onMemorySummarizingStarted() {
                if (original != null) original.onMemorySummarizingStarted();
            }

            @Override
            public void onMemorySummarizingCompleted(int originalTokens, int compressedTokens) {
                if (original != null) original.onMemorySummarizingCompleted(originalTokens, compressedTokens);
            }

            @Override
            public void onMemorySummarizingFailed(String reason) {
                if (original != null) original.onMemorySummarizingFailed(reason);
            }

            @Override
            public void onStrategyPhase(String phase, String description) {
                if (original != null) original.onStrategyPhase(phase, description);
            }

            @Override
            public void onPlanGenerated(java.util.List<PlanStep> steps) {
                if (original != null) original.onPlanGenerated(steps);
            }

            @Override
            public void onPlanStepStarting(int stepIndex, String stepDescription) {
                if (original != null) original.onPlanStepStarting(stepIndex, stepDescription);
            }

            @Override
            public void onPlanStepCompleted(int stepIndex, boolean success, String resultSummary) {
                if (original != null) original.onPlanStepCompleted(stepIndex, success, resultSummary);
            }

            @Override
            public void onPlanRevised(java.util.List<PlanStep> revisedSteps) {
                if (original != null) original.onPlanRevised(revisedSteps);
            }
        };
    }

    /**
     * 通过 AgentRuntime 发送消息，执行完整的 Agent 流程。
     * <p>
     * 流程：
     * 1. 调用 getOrCreateSession() 确保会话存在
     * 2. 注入 orchestrator 和 streamingLlmCaller 到 runtime
     * 3. 创建 AgentFileChangeHook 并注册到 orchestrator
     * 4. 委托 runtime.send() 执行完整流程（透传原始 ChatContent）
     * 5. finally 块中移除 AgentFileChangeHook，保证钩子生命周期对称
     *
     * @param message             用户消息文本
     * @param originalChatContent 调用方构建的原始 ChatContent（含完整对话历史和 model 设置）
     * @param llmCaller           LLM 调用回调
     * @param callback            进度回调
     * @return FunctionCallResult 执行结果
     */
    public FunctionCallResult sendMessage(
            String message,
            com.wmsay.gpt4_lll.model.ChatContent originalChatContent,
            FunctionCallOrchestrator.LlmCaller llmCaller,
            FunctionCallOrchestrator.ProgressCallback callback) {

        // Set RUNNING phase at start
        RuntimeStatusManager.setAgentPhase(project,
                AgentStatusContext.of(AgentPhase.RUNNING, "意图识别中"));

        // 1. 获取或创建会话
        AgentSession session = getOrCreateSession();

        // 2. 注入 orchestrator 和 streamingLlmCaller 到 runtime
        if (orchestrator != null) {
            runtime.setOrchestrator(orchestrator);
            runtime.setStreamingLlmCaller(streamingLlmCaller);
        }

        // 3. 创建 AgentFileChangeHook 并注册到 orchestrator
        AgentFileChangeHook hook = new AgentFileChangeHook(
                session.getFileChangeTracker(),
                project.getBasePath());

        if (orchestrator != null) {
            orchestrator.addExecutionHook(hook);
        }

        // 4. Wrap callback to map progress events to AgentPhase changes
        FunctionCallOrchestrator.ProgressCallback wrappedCallback = wrapCallback(project, callback);

        try {
            // 5. 委托 runtime.send() 执行完整流程（透传原始 ChatContent）
            FunctionCallResult result = runtime.send(session.getSessionId(), message,
                    originalChatContent, llmCaller, wrappedCallback);

            // Set COMPLETED phase on success
            RuntimeStatusManager.setAgentPhase(project,
                    AgentStatusContext.of(AgentPhase.COMPLETED));

            return result;
        } catch (Exception e) {
            // Set ERROR phase with error summary
            String errorDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            RuntimeStatusManager.setAgentPhase(project,
                    AgentStatusContext.of(AgentPhase.ERROR, errorDetail));
            throw e;
        } finally {
            // 6. 移除 AgentFileChangeHook，保证钩子生命周期对称
            if (orchestrator != null) {
                orchestrator.removeExecutionHook(hook);
            }
        }
    }
}
