package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.MemoryFactory;
import com.wmsay.gpt4_lll.fc.model.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;

import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 多 Agent 运行时管理器 — Project 级别单例。
 * <p>
 * 使用 String projectId 作为平台无关标识，不依赖任何 com.intellij.* API。
 * 管理 Agent 定义注册、会话生命周期、并发控制和全局资源。
 * IntentRecognizer、ToolFilter、KnowledgeBase 作为可替换组件设计（需求 21.5）。
 */
public class AgentRuntime {

    private static final Logger LOG = Logger.getLogger(AgentRuntime.class.getName());
    private static final ConcurrentHashMap<String, AgentRuntime> INSTANCES = new ConcurrentHashMap<>();

    /**
     * 获取指定 projectId 对应的 AgentRuntime 实例（使用默认配置）。
     *
     * @param projectId 平台无关的项目标识，由桥接层从 Project 对象转换而来
     * @return AgentRuntime 实例
     */
    public static AgentRuntime getInstance(String projectId) {
        return INSTANCES.computeIfAbsent(projectId, id -> new AgentRuntime(AgentRuntimeConfig.defaultConfig()));
    }

    /**
     * 获取指定 projectId 对应的 AgentRuntime 实例（使用自定义配置）。
     *
     * @param projectId 平台无关的项目标识
     * @param config    运行时配置
     * @return AgentRuntime 实例
     */
    public static AgentRuntime getInstance(String projectId, AgentRuntimeConfig config) {
        return INSTANCES.computeIfAbsent(projectId, id -> new AgentRuntime(config));
    }

    /**
     * 移除指定 projectId 的实例（仅用于测试清理）。
     */
    static void removeInstance(String projectId) {
        INSTANCES.remove(projectId);
    }

    private final AgentRuntimeConfig config;
    private final ConcurrentHashMap<String, AgentDefinition> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentSession> activeSessions = new ConcurrentHashMap<>();
    private final ObservabilityManager observability = new ObservabilityManager();
    private final ExecutorService threadPool;

    // 可替换组件（需求 21.5）
    private IntentRecognizer intentRecognizer;
    private ToolFilter toolFilter;
    private KnowledgeBase knowledgeBase;

    // FunctionCallOrchestrator 注入（需求 4.1, 4.2）— UI 集成时由 AgentRuntimeBridge 注入
    private volatile FunctionCallOrchestrator orchestrator;
    private volatile FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller;

    private AgentRuntime(AgentRuntimeConfig config) {
        this.config = config;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32));
        pool.setThreadFactory(r -> {
            Thread t = new Thread(r, "agent-runtime");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        this.threadPool = pool;
        this.intentRecognizer = new IntentRecognizer(observability);
        this.toolFilter = new ToolFilter(observability);
    }

    // ---- 可替换组件注入（需求 21.5）----

    public void setIntentRecognizer(IntentRecognizer ir) { this.intentRecognizer = ir; }
    public void setToolFilter(ToolFilter tf) { this.toolFilter = tf; }
    public void setKnowledgeBase(KnowledgeBase kb) { this.knowledgeBase = kb; }
    public IntentRecognizer getIntentRecognizer() { return intentRecognizer; }
    public ToolFilter getToolFilter() { return toolFilter; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }

    // ---- Orchestrator 注入（需求 4.1, 4.2）----

    public void setOrchestrator(FunctionCallOrchestrator orchestrator) { this.orchestrator = orchestrator; }
    public void setStreamingLlmCaller(FunctionCallOrchestrator.StreamingLlmCaller caller) { this.streamingLlmCaller = caller; }
    public FunctionCallOrchestrator getOrchestrator() { return orchestrator; }
    public FunctionCallOrchestrator.StreamingLlmCaller getStreamingLlmCaller() { return streamingLlmCaller; }

    // ---- 配置与可观测性 ----

    public AgentRuntimeConfig getConfig() { return config; }
    public ObservabilityManager getObservability() { return observability; }

    // ---- 注册管理（需求 4）----

    /**
     * 注册 Agent 定义。
     *
     * @param definition Agent 定义
     * @throws IllegalArgumentException 如果 id 已注册
     */
    public void register(AgentDefinition definition) {
        if (registry.putIfAbsent(definition.getId(), definition) != null) {
            throw new IllegalArgumentException("Agent already registered: " + definition.getId());
        }
    }

    /**
     * 注销 Agent 定义。不存在时静默忽略。
     *
     * @param agentId Agent ID
     */
    public void unregister(String agentId) {
        registry.remove(agentId);
    }

    /**
     * 获取所有已注册的 Agent 定义（不可变列表）。
     */
    public List<AgentDefinition> getRegisteredDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    /**
     * 检查指定 agentId 是否已注册。
     */
    public boolean isRegistered(String agentId) {
        return registry.containsKey(agentId);
    }

    // ---- 会话管理（需求 5）----

    /**
     * 为已注册的 Agent 创建新会话。
     *
     * @param agentId Agent ID（必须已注册）
     * @param context 执行上下文
     * @return 新创建的 AgentSession
     * @throws IllegalArgumentException 如果 agentId 未注册
     * @throws IllegalStateException    如果已达并发会话上限
     */
    public AgentSession createSession(String agentId, ExecutionContext context) {
        LOG.info("createSession:{}"+agentId);
        AgentDefinition def = registry.get(agentId);
        if (def == null) {
            throw new IllegalArgumentException("Agent not registered: " + agentId);
        }
        if (activeSessions.size() >= config.getMaxConcurrentSessions()) {
            throw new IllegalStateException(
                    "Max concurrent sessions reached: " + config.getMaxConcurrentSessions());
        }

        FunctionCallConfig fcConfig = FunctionCallConfig.builder()
                .memoryStrategy(def.getMemoryStrategy())
                .build();
        ConversationMemory memory = MemoryFactory.create(fcConfig, null);

        String sessionId = "agent-session-" + UUID.randomUUID();
        AgentSession session = new AgentSession(sessionId, def, memory, context);

        // 注入 TaskPersistence（需求 18.2, 18.4）
        java.nio.file.Path projectRoot = context.getProjectRoot();
        if (projectRoot != null) {
            TaskPersistence persistence = new TaskPersistence(projectRoot.toString());
            TaskManager taskManager = session.getTaskManager();
            taskManager.setPersistence(persistence);
            taskManager.setSessionId(sessionId);
        }

        // 注入 KnowledgeBase 到 ContextManager（需求 14.11）
        if (knowledgeBase != null) {
            session.getContextManager().setKnowledgeBase(knowledgeBase);
        }

        activeSessions.put(sessionId, session);
        observability.startSession(sessionId);
        return session;
    }

    /**
     * 销毁会话：转换到 DESTROYED 状态，从活跃列表移除，结束追踪。
     *
     * @param sessionId 会话 ID
     */
    public void destroySession(String sessionId) {
        AgentSession session = activeSessions.remove(sessionId);
        if (session != null) {
            try {
                session.transitionTo(SessionState.DESTROYED);
            } catch (Exception ignored) {
                // 状态转换失败时静默忽略（可能已经是 DESTROYED）
            }
            observability.endSession(sessionId);
        }
    }

    /**
     * 获取当前活跃会话数。
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 根据 sessionId 获取会话。
     *
     * @param sessionId 会话 ID
     * @return AgentSession，不存在时返回 null
     */
    public AgentSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    // ---- 消息发送与执行（需求 6）----

    /**
     * 向指定会话发送用户消息并触发执行。
     * 流程：意图识别→工具过滤→上下文组装→委托执行。
     * <p>
     * 当 orchestrator 非 null 时，委托 FunctionCallOrchestrator 执行完整的 LLM 交互和工具调用循环。
     * 当 orchestrator 为 null 时，保持现有行为不变（返回组装后的文本）。
     * 意图识别失败时使用 IntentResult.defaultResult() 继续执行，不阻塞主流程（需求 8.1, 8.2, 8.3）。
     */
    /**
     * 发送消息并执行完整的 Agent 流程（透传原始 ChatContent 版本）。
     * <p>
     * 接收调用方已构建好的 ChatContent（包含完整对话历史、正确的 model 和 stream 设置），
     * 在此基础上进行意图识别、工具过滤，然后委托 orchestrator 执行。
     * 不重建 ChatContent，避免丢失对话历史和模型设置。
     *
     * @param sessionId 会话 ID
     * @param message   用户消息文本（用于意图识别）
     * @param originalChatContent 调用方构建的原始 ChatContent（含完整历史和 model）
     * @param llmCaller LLM 调用器
     * @param callback  进度回调
     * @return FunctionCallResult 执行结果
     */
    public FunctionCallResult send(String sessionId, String message,
                                   ChatContent originalChatContent,
                                   FunctionCallOrchestrator.LlmCaller llmCaller,
                                   FunctionCallOrchestrator.ProgressCallback callback) {
        AgentSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        if (session.getState() == SessionState.DESTROYED) {
            throw new IllegalStateException("Session is DESTROYED: " + sessionId);
        }

        session.transitionTo(SessionState.RUNNING);
        try {
            // 1. 意图识别（sidecar 旁路辅助）— 容错处理（需求 8.1, 8.2, 8.3）
            List<String> availableToolNames = session.getDefinition().getAvailableToolNames();
            IntentResult intent;
            System.out.println("[AgentRuntime] Starting intent recognition for: "
                    + (message.length() > 80 ? message.substring(0, 80) + "..." : message));
            try {
                intent = intentRecognizer.analyze(message, availableToolNames, llmCaller);
                System.out.println("[AgentRuntime] Intent recognition result: "
                        + intent.getClarity() + "/" + intent.getComplexity()
                        + ", filteredTools=" + (intent.getFilteredToolNames() != null ? intent.getFilteredToolNames().size() : 0));
            } catch (Exception intentEx) {
                System.err.println("[AgentRuntime] IntentRecognizer.analyze() FAILED: " + intentEx.getMessage());
                intentEx.printStackTrace();
                intent = IntentResult.defaultResult();
            }

            // 2. 工具过滤
            List<McpTool> allTools = resolveTools(session.getDefinition());
            List<McpTool> filteredTools = toolFilter.filter(intent, allTools);
            System.out.println("[AgentRuntime] Tool filtering: " + allTools.size()
                    + " total → " + filteredTools.size() + " filtered");

            // 3. 委托 FunctionCallOrchestrator 执行（需求 4.3, 4.4, 4.5, 4.6）
            if (orchestrator != null) {
                // 不覆盖 orchestrator 的执行策略 — 尊重用户在 UI 上的选择
                // IntentRecognizer 的推荐策略仅作为日志参考
                LOG.fine("IntentRecognizer recommended strategy: " + intent.getRecommendedStrategy()
                        + ", using orchestrator's current strategy: " + orchestrator.getExecutionStrategyName());

                // 注入流式调用器
                if (streamingLlmCaller != null) {
                    orchestrator.setStreamingLlmCaller(streamingLlmCaller);
                }

                // 使用调用方传入的原始 ChatContent（保留完整对话历史和 model 设置）
                FunctionCallRequest request = FunctionCallRequest.builder()
                        .chatContent(originalChatContent)
                        .availableTools(filteredTools)
                        .maxRounds(20)
                        .config(FunctionCallConfig.builder().build())
                        .build();

                // 使用 session 中保存的完整 McpContext（包含 project 引用，确保审批弹窗等功能正常）
                McpContext mcpContext = session.getExecutionContext().getMcpContext();

                // 委托执行
                FunctionCallResult result = orchestrator.execute(request, mcpContext, llmCaller, callback);
                session.transitionTo(SessionState.COMPLETED);
                return result;
            }

            // 回退：无 orchestrator 时返回错误提示
            session.transitionTo(SessionState.COMPLETED);
            return FunctionCallResult.success("Orchestrator not available", sessionId, Collections.emptyList());
        } catch (Exception e) {
            try {
                session.transitionToError(e);
            } catch (Exception ignored) { }
            return FunctionCallResult.error(e.getMessage(), sessionId);
        }
    }

    /**
     * 向后兼容的 send() 方法（不传 ChatContent，用于 inter-agent 通信等场景）。
     * 内部构建简单的 ChatContent 并委托到主 send() 方法。
     */
    public FunctionCallResult send(String sessionId, String message,
                                   FunctionCallOrchestrator.LlmCaller llmCaller,
                                   FunctionCallOrchestrator.ProgressCallback callback) {
        // 构建最小 ChatContent（用于 inter-agent 通信等不需要完整历史的场景）
        ChatContent chatContent = new ChatContent();
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(message);
        chatContent.setDirectMessages(List.of(userMsg));
        chatContent.setStream(true);

        return send(sessionId, message, chatContent, llmCaller, callback);
    }

    /**
     * 根据 AgentDefinition 的 availableToolNames 从 McpToolRegistry 过滤可用工具。
     */
    private List<McpTool> resolveTools(AgentDefinition def) {
        List<String> names = def.getAvailableToolNames();
        if (names == null || names.isEmpty()) {
            return McpToolRegistry.getAllTools();
        }
        return McpToolRegistry.getAllTools().stream()
                .filter(t -> names.contains(t.name()))
                .collect(Collectors.toList());
    }

    // ---- Agent 间委托（需求 7）----

    /**
     * 从一个 Agent 会话向另一个 Agent 发起委托。
     * 检查委托深度限制，创建临时会话，使用 Future.get(timeout) 超时控制。
     */
    public FunctionCallResult delegate(String fromSessionId, String targetAgentId,
                                       String message,
                                       FunctionCallOrchestrator.LlmCaller llmCaller) {
        AgentSession sourceSession = activeSessions.get(fromSessionId);
        if (sourceSession == null) {
            return FunctionCallResult.error("Source session not found: " + fromSessionId, fromSessionId);
        }

        int newDepth = sourceSession.getDelegationDepth() + 1;
        if (newDepth > config.getMaxDelegationDepth()) {
            return FunctionCallResult.error("Maximum delegation depth exceeded", fromSessionId);
        }

        AgentDefinition targetDef = registry.get(targetAgentId);
        if (targetDef == null) {
            return FunctionCallResult.error("Target agent not registered: " + targetAgentId, fromSessionId);
        }

        // 创建临时会话
        AgentSession targetSession;
        try {
            FunctionCallConfig fcConfig = FunctionCallConfig.builder()
                    .memoryStrategy(targetDef.getMemoryStrategy()).build();
            ConversationMemory memory = MemoryFactory.create(fcConfig, null);
            String targetSessionId = "agent-session-" + UUID.randomUUID();
            targetSession = new AgentSession(targetSessionId, targetDef, memory,
                    sourceSession.getExecutionContext());
            targetSession.setDelegationDepth(newDepth);
            activeSessions.put(targetSessionId, targetSession);
            observability.startSession(targetSessionId);
        } catch (Exception e) {
            return FunctionCallResult.error("Failed to create delegate session: " + e.getMessage(), fromSessionId);
        }

        String targetSessionId = targetSession.getSessionId();
        try {
            Future<FunctionCallResult> future = threadPool.submit(
                    () -> send(targetSessionId, message, llmCaller, null));
            FunctionCallResult result = future.get(config.getDelegationTimeoutSeconds(), TimeUnit.SECONDS);
            return result;
        } catch (TimeoutException e) {
            return FunctionCallResult.error("Delegation timed out", fromSessionId);
        } catch (Exception e) {
            return FunctionCallResult.error("Delegation failed: " + e.getMessage(), fromSessionId);
        } finally {
            destroySession(targetSessionId);
        }
    }

    // ---- 同级 Agent 通信（需求 20）----

    /**
     * 同级 Agent 间消息传递。
     * REQUEST 类型等待目标处理完成并返回 RESPONSE。
     * NOTIFY 类型异步投递不等待。
     * 目标不存在或已 DESTROYED 时返回错误 RESPONSE，不抛异常。
     */
    public AgentMessage sendMessage(AgentMessage message,
                                    FunctionCallOrchestrator.LlmCaller llmCaller) {
        String targetId = message.getTargetAgentId();

        // 查找目标会话（按 agentId 匹配）
        AgentSession targetSession = null;
        for (AgentSession s : activeSessions.values()) {
            if (s.getDefinition().getId().equals(targetId) && s.getState() != SessionState.DESTROYED) {
                targetSession = s;
                break;
            }
        }

        if (targetSession == null) {
            return AgentMessage.builder()
                    .sourceAgentId(targetId)
                    .targetAgentId(message.getSourceAgentId())
                    .messageType(AgentMessage.MessageType.RESPONSE)
                    .payload("Target agent session not found or destroyed: " + targetId)
                    .correlationId(message.getCorrelationId())
                    .build();
        }

        if (message.getMessageType() == AgentMessage.MessageType.NOTIFY) {
            // 异步投递，不等待
            final AgentSession ts = targetSession;
            threadPool.submit(() -> {
                try {
                    send(ts.getSessionId(), message.getPayload(), llmCaller, null);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "NOTIFY delivery failed", e);
                }
            });
            return AgentMessage.builder()
                    .sourceAgentId(targetId)
                    .targetAgentId(message.getSourceAgentId())
                    .messageType(AgentMessage.MessageType.RESPONSE)
                    .payload("NOTIFY delivered")
                    .correlationId(message.getCorrelationId())
                    .build();
        }

        // REQUEST — 同步等待
        try {
            FunctionCallResult result = send(targetSession.getSessionId(),
                    message.getPayload(), llmCaller, null);
            return AgentMessage.builder()
                    .sourceAgentId(targetId)
                    .targetAgentId(message.getSourceAgentId())
                    .messageType(AgentMessage.MessageType.RESPONSE)
                    .payload(result.getContent())
                    .correlationId(message.getCorrelationId())
                    .build();
        } catch (Exception e) {
            return AgentMessage.builder()
                    .sourceAgentId(targetId)
                    .targetAgentId(message.getSourceAgentId())
                    .messageType(AgentMessage.MessageType.RESPONSE)
                    .payload("Error: " + e.getMessage())
                    .correlationId(message.getCorrelationId())
                    .build();
        }
    }

    // ---- 单次执行便捷方法（需求 10）----

    /**
     * 自动注册、创建会话、发送消息、销毁会话。
     */
    public FunctionCallResult executeOneShot(AgentDefinition definition, ExecutionContext context,
                                             String message,
                                             FunctionCallOrchestrator.LlmCaller llmCaller,
                                             FunctionCallOrchestrator.ProgressCallback callback) {
        // 自动注册（如果尚未注册）
        if (!isRegistered(definition.getId())) {
            register(definition);
        }
        AgentSession session = createSession(definition.getId(), context);
        try {
            return send(session.getSessionId(), message, llmCaller, callback);
        } finally {
            destroySession(session.getSessionId());
        }
    }

    // ---- 可观测性导出（需求 11）----

    public String exportSessionTrace(String sessionId, ObservabilityManager.TraceFormat format) {
        return observability.exportTrace(sessionId, format);
    }

    // ---- 资源管理（需求 8）----

    /**
     * 销毁所有活跃会话并关闭线程池。等待最多 5 秒后强制终止。
     */
    public void shutdown() {
        // 销毁所有活跃会话
        for (String sessionId : new ArrayList<>(activeSessions.keySet())) {
            destroySession(sessionId);
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        INSTANCES.values().remove(this);
    }

    /**
     * 立即中断所有正在执行的会话并释放资源。
     */
    public void shutdownNow() {
        for (String sessionId : new ArrayList<>(activeSessions.keySet())) {
            destroySession(sessionId);
        }
        threadPool.shutdownNow();
        INSTANCES.values().remove(this);
    }
}
