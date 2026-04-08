package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.events.PlanProgressListener;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller;
import com.wmsay.gpt4_lll.fc.planning.PlanProgressProvider;
import com.wmsay.gpt4_lll.fc.state.PlanProgressSnapshot;
import com.wmsay.gpt4_lll.fc.runtime.AgentFileChangeHook;
import com.wmsay.gpt4_lll.fc.runtime.AgentRuntime;
import com.wmsay.gpt4_lll.fc.runtime.KnowledgeBase;
import com.wmsay.gpt4_lll.fc.state.AgentSession;
import com.wmsay.gpt4_lll.fc.state.ExecutionContext;
import com.wmsay.gpt4_lll.fc.state.FileChangeTracker;
import com.wmsay.gpt4_lll.fc.state.TaskManager;
import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.core.SessionState;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.planning.PlanStep;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.model.AgentPhase;
import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;

import com.wmsay.gpt4_lll.fc.skill.SkillCommandHandler;
import com.wmsay.gpt4_lll.fc.state.PlanStepInfo;

import javax.swing.SwingUtilities;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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
    private StreamingLlmCaller streamingLlmCaller;
    private volatile Thread currentRequestThread;
    private PlanProgressProvider planProgressProvider;
    private AgentChatView chatView;

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
                        .map(Tool::name)
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

        // 使用 IntelliJToolContext 构建 ToolContext，再创建 ExecutionContext
        ToolContext toolContext = new IntelliJToolContext(project, null);
        ExecutionContext context = ExecutionContext.fromToolContext(toolContext);

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

    // ── 计划进度转发（UI 层通过这些 public 方法获取进度，不直接接触 PlanProgressProvider） ──

    /**
     * 获取当前计划进度快照（pull 模式）。
     * 委托 PlanProgressProvider 返回 PlanProgressSnapshot DTO。
     * Provider 为 null 时（非 PlanAndExecute 策略或无活跃计划）返回空快照。
     *
     * @return 当前计划进度快照，永不为 null
     */
    public PlanProgressSnapshot getPlanProgress() {
        PlanProgressProvider provider = this.planProgressProvider;
        return provider != null ? provider.getProgressSnapshot() : PlanProgressSnapshot.empty();
    }

    /**
     * 注册计划进度监听器（push 模式）。
     * UI 层通过此方法注册监听器，不直接接触 PlanProgressProvider。
     *
     * @param listener 要注册的监听器
     */
    public void addPlanProgressListener(PlanProgressListener listener) {
        PlanProgressProvider provider = this.planProgressProvider;
        if (provider != null) {
            provider.addListener(listener);
        }
    }

    /**
     * 移除计划进度监听器。
     *
     * @param listener 要移除的监听器
     */
    public void removePlanProgressListener(PlanProgressListener listener) {
        PlanProgressProvider provider = this.planProgressProvider;
        if (provider != null) {
            provider.removeListener(listener);
        }
    }

    /**
     * 设置 PlanProgressProvider（package-private）。
     * 仅由 wrapCallback 内部调用，不暴露给 UI 层。
     *
     * @param provider 计划进度提供者
     */
    void setPlanProgressProvider(PlanProgressProvider provider) {
        this.planProgressProvider = provider;
    }

    /**
     * 注入 AgentChatView 引用，供 wrapCallback 在 onPlanGenerated 时创建 PlanProgressPanel。
     *
     * @param chatView AgentChatView 实例
     */
    public void setChatView(AgentChatView chatView) {
        this.chatView = chatView;
    }

    /**
     * 重置会话（新建对话时调用）。
     * 通过 AgentRuntime.destroySession() 销毁旧会话，将 currentSession 置为 null。
     * 同时清空 PlanProgressProvider 并通知监听器计划已清空。
     */
    public void resetSession() {
        // 清空 PlanProgressProvider
        if (planProgressProvider != null) {
            planProgressProvider.clear();
            planProgressProvider = null;
        }

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
    public void setStreamingLlmCaller(StreamingLlmCaller caller) {
        this.streamingLlmCaller = caller;
    }

    /**
     * 设置当前请求线程引用（由 WindowTool 在启动线程时调用）。
     */
    public void setCurrentRequestThread(Thread thread) {
        this.currentRequestThread = thread;
    }

    /**
     * 获取当前请求线程引用（供 WindowTool 检查是否有请求正在进行）。
     */
    public Thread getCurrentRequestThread() {
        return currentRequestThread;
    }

    /**
     * 请求停止当前执行。
     * 先将 AgentSession 转换到 PAUSED 状态（作为结构化取消信号），
     * 然后中断当前请求线程，最后设置 AgentPhase 为 STOPPED。
     * 即使 interrupt 抛出异常，仍保证 phase 被设置为 STOPPED。
     */
    public void requestStop(Project project) {
        // 1. Transition session to PAUSED (best-effort, don't let failure block interrupt)
        try {
            if (currentSession != null && currentSession.getState() == SessionState.RUNNING) {
                currentSession.transitionTo(SessionState.PAUSED);
            }
        } catch (IllegalStateException e) {
            LOG.log(Level.FINE, "Session state transition to PAUSED failed (non-critical)", e);
        }

        // 2. Interrupt the request thread — MUST always execute regardless of session state
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
    ProgressCallback wrapCallback(
            Project project,
            ProgressCallback original) {
        return new ProgressCallback() {
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
                // 创建 PlanProgressProvider 并设置到 Bridge
                PlanProgressProvider provider = new PlanProgressProvider();
                setPlanProgressProvider(provider);
                provider.setPlan(steps);

                // 将 PlanStep 转换为 PlanStepInfo DTO，通过 chatView 创建 UI 面板
                if (chatView != null) {
                    List<PlanStepInfo> stepInfos = new ArrayList<>(steps.size());
                    for (PlanStep step : steps) {
                        stepInfos.add(new PlanStepInfo(
                                step.getIndex(),
                                step.getDescription(),
                                PlanStepInfo.Status.PENDING,
                                null));
                    }
                    SwingUtilities.invokeLater(() -> chatView.addPlanProgressBlock(stepInfos));
                }

                if (original != null) original.onPlanGenerated(steps);
            }

            @Override
            public void onPlanStepStarting(int stepIndex, String stepDescription) {
                if (planProgressProvider != null) {
                    planProgressProvider.updateStepStatus(stepIndex, PlanStep.Status.IN_PROGRESS, null);
                }
                if (original != null) original.onPlanStepStarting(stepIndex, stepDescription);
            }

            @Override
            public void onPlanStepCompleted(int stepIndex, boolean success, String resultSummary) {
                if (planProgressProvider != null) {
                    PlanStep.Status status = success ? PlanStep.Status.COMPLETED : PlanStep.Status.FAILED;
                    planProgressProvider.updateStepStatus(stepIndex, status, resultSummary);
                }
                if (original != null) original.onPlanStepCompleted(stepIndex, success, resultSummary);
            }

            @Override
            public void onPlanRevised(java.util.List<PlanStep> revisedSteps) {
                if (planProgressProvider != null) {
                    planProgressProvider.revisePlan(revisedSteps);
                }
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
            com.wmsay.gpt4_lll.fc.core.ChatContent originalChatContent,
            LlmCaller llmCaller,
            ProgressCallback callback) {

        // /skill 命令拦截：从 AgentRuntime 获取 Skill 组件
        if (runtime != null && runtime.getSkillRegistry() != null && runtime.getSkillLoader() != null) {
            SkillCommandHandler cmdHandler = new SkillCommandHandler(
                    runtime.getSkillRegistry(), runtime.getSkillLoader());
            if (cmdHandler.isSkillCommand(message)) {
                String response = cmdHandler.handleCommand(message);
                return FunctionCallResult.success(response,
                        currentSession != null ? currentSession.getSessionId() : "skill-command",
                        Collections.emptyList());
            }
        }

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
        ProgressCallback wrappedCallback = wrapCallback(project, callback);

        try {
            // 5. 委托 runtime.send() 执行完整流程（透传原始 ChatContent）
            FunctionCallResult result = runtime.send(session.getSessionId(), message,
                    originalChatContent, llmCaller, wrappedCallback);

            // Check if user stopped during execution (session transitioned to PAUSED)
            if (currentSession != null && currentSession.getState() == SessionState.PAUSED) {
                // requestStop() already set AgentPhase.STOPPED — don't overwrite
                currentSession.transitionTo(SessionState.RUNNING); // PAUSED → RUNNING
                currentSession.transitionTo(SessionState.COMPLETED); // RUNNING → COMPLETED
                return result;
            }

            // Set COMPLETED phase on success
            RuntimeStatusManager.setAgentPhase(project,
                    AgentStatusContext.of(AgentPhase.COMPLETED));

            return result;
        } catch (Exception e) {
            // Check if user stopped during execution (session transitioned to PAUSED)
            if (currentSession != null && currentSession.getState() == SessionState.PAUSED) {
                // User-initiated stop — don't set ERROR phase
                currentSession.transitionTo(SessionState.RUNNING);
                currentSession.transitionTo(SessionState.COMPLETED);
                return FunctionCallResult.success(null, currentSession.getSessionId(), Collections.emptyList());
            }
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
