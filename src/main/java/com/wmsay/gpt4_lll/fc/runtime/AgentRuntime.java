package com.wmsay.gpt4_lll.fc.runtime;

import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.core.AgentMessage;
import com.wmsay.gpt4_lll.fc.core.AgentRuntimeConfig;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.core.SessionState;
import com.wmsay.gpt4_lll.fc.events.AgentLifecycleListener;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.MemoryFactory;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.skill.ContextDistiller;
import com.wmsay.gpt4_lll.fc.skill.InvokeSkillTool;
import com.wmsay.gpt4_lll.fc.skill.SkillComplexity;
import com.wmsay.gpt4_lll.fc.skill.SkillDefinition;
import com.wmsay.gpt4_lll.fc.skill.SkillFileWatcher;
import com.wmsay.gpt4_lll.fc.skill.SkillGenerator;
import com.wmsay.gpt4_lll.fc.skill.SkillLoader;
import com.wmsay.gpt4_lll.fc.skill.SkillMatchResult;
import com.wmsay.gpt4_lll.fc.skill.SkillMatcher;
import com.wmsay.gpt4_lll.fc.skill.SkillMatchingMode;
import com.wmsay.gpt4_lll.fc.skill.SkillParser;
import com.wmsay.gpt4_lll.fc.skill.SkillRegistry;
import com.wmsay.gpt4_lll.fc.skill.SkillValidator;
import com.wmsay.gpt4_lll.fc.skill.SubAgentFactory;
import com.wmsay.gpt4_lll.fc.state.AgentSession;
import com.wmsay.gpt4_lll.fc.state.ExecutionContext;
import com.wmsay.gpt4_lll.fc.state.SubAgentProgressProvider;
import com.wmsay.gpt4_lll.fc.state.SubAgentResult;
import com.wmsay.gpt4_lll.fc.state.TaskManager;
import com.wmsay.gpt4_lll.fc.state.TaskPersistence;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolRegistry;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
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
    public static void removeInstance(String projectId) {
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

    // ToolRegistry 注入（需求 4.2, 13.2）— 用于 resolveTools()，McpToolRegistry 作为后备
    private volatile ToolRegistry toolRegistry;

    // Skill 体系 — AgentRuntime 自包含初始化（不依赖外部注入）
    private volatile SkillRegistry skillRegistry;
    private volatile SkillMatcher skillMatcher;
    private volatile SkillLoader skillLoader;

    // AgentLifecycleListener 支持（需求 19.1, 19.2）
    private final List<AgentLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    // FunctionCallOrchestrator 注入（需求 4.1, 4.2）— UI 集成时由 AgentRuntimeBridge 注入
    private volatile FunctionCallOrchestrator orchestrator;
    private volatile StreamingLlmCaller streamingLlmCaller;

    // 子 Agent 进度提供者（需求 1.5, 11.1）— 由 AgentRuntimeBridge 注入
    private volatile SubAgentProgressProvider subAgentProgressProvider;

    // 上下文蒸馏器（lazy-initialized，需求 8.1）
    private volatile ContextDistiller contextDistiller;

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

        // Skill 体系自包含初始化（异常不影响 AgentRuntime 正常启动）
        initializeSkillSystem();
    }

    /**
     * 初始化 Skill 体系：加载、验证、注册、启动热加载监听。
     * 全部在 agent 核心层完成，不依赖外部注入。
     * 异常时静默降级，不影响主流程。
     */
    private void initializeSkillSystem() {
        try {
            System.out.println("[Skill] Initializing skill system in AgentRuntime...");
            SkillRegistry registry = new SkillRegistry();
            SkillParser parser = new SkillParser();
            SkillValidator validator = new SkillValidator();
            SkillFileWatcher watcher = new SkillFileWatcher();
            SkillMatcher matcher = new SkillMatcher();

            SkillLoader loader = new SkillLoader(registry, parser, validator, watcher);
            SkillLoader.LoadResult loadResult = loader.loadAll();

            this.skillRegistry = registry;
            this.skillMatcher = matcher;
            this.skillLoader = loader;

            loader.startWatching();

            System.out.println("[Skill] Skill system initialized: " + loadResult
                    + ", registry count=" + registry.getSkillCount()
                    + ", directory=" + loader.getSkillDirectory());
        } catch (Exception e) {
            System.err.println("[Skill] Skill system initialization FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---- 可替换组件注入（需求 21.5）----

    public void setIntentRecognizer(IntentRecognizer ir) { this.intentRecognizer = ir; }
    public void setToolFilter(ToolFilter tf) { this.toolFilter = tf; }
    public void setKnowledgeBase(KnowledgeBase kb) { this.knowledgeBase = kb; }
    public IntentRecognizer getIntentRecognizer() { return intentRecognizer; }
    public ToolFilter getToolFilter() { return toolFilter; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }

    // ---- ToolRegistry 注入（需求 4.2, 13.2）----

    public void setToolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }

    // ---- Skill 体系（自包含初始化，setter 仅用于测试或外部覆盖）----

    public void setSkillRegistry(SkillRegistry sr) { this.skillRegistry = sr; }
    public void setSkillMatcher(SkillMatcher sm) { this.skillMatcher = sm; }
    public SkillRegistry getSkillRegistry() { return skillRegistry; }
    public SkillMatcher getSkillMatcher() { return skillMatcher; }
    public SkillLoader getSkillLoader() { return skillLoader; }

    // ---- AgentLifecycleListener 支持（需求 19.1, 19.2）----

    /**
     * 注册生命周期监听器。
     *
     * @param listener 监听器实例，不能为 null
     * @throws IllegalArgumentException 如果 listener 为 null
     */
    public void addLifecycleListener(AgentLifecycleListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        lifecycleListeners.add(listener);
    }

    /**
     * 移除生命周期监听器。
     *
     * @param listener 要移除的监听器
     */
    public void removeLifecycleListener(AgentLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    private void notifySessionCreated(AgentSession session) {
        for (AgentLifecycleListener l : lifecycleListeners) {
            try {
                l.onSessionCreated(session);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Lifecycle listener error on session created", e);
            }
        }
    }

    private void notifySessionDestroyed(AgentSession session) {
        for (AgentLifecycleListener l : lifecycleListeners) {
            try {
                l.onSessionDestroyed(session);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Lifecycle listener error on session destroyed", e);
            }
        }
    }

    // ---- Orchestrator 注入（需求 4.1, 4.2）----

    public void setOrchestrator(FunctionCallOrchestrator orchestrator) { this.orchestrator = orchestrator; }
    public void setStreamingLlmCaller(StreamingLlmCaller caller) { this.streamingLlmCaller = caller; }
    public FunctionCallOrchestrator getOrchestrator() { return orchestrator; }
    public StreamingLlmCaller getStreamingLlmCaller() { return streamingLlmCaller; }

    // ---- SubAgentProgressProvider 注入（需求 1.5, 11.1）----

    public void setSubAgentProgressProvider(SubAgentProgressProvider provider) { this.subAgentProgressProvider = provider; }
    public SubAgentProgressProvider getSubAgentProgressProvider() { return subAgentProgressProvider; }

    // ---- ContextDistiller（lazy-initialized）----

    /**
     * 获取 ContextDistiller 实例（lazy-initialized）。
     */
    private ContextDistiller getOrCreateContextDistiller() {
        if (contextDistiller == null) {
            synchronized (this) {
                if (contextDistiller == null) {
                    contextDistiller = new ContextDistiller();
                }
            }
        }
        return contextDistiller;
    }

    public void setContextDistiller(ContextDistiller distiller) { this.contextDistiller = distiller; }
    public ContextDistiller getContextDistiller() { return contextDistiller; }

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
        if (definition == null) {
            throw new IllegalArgumentException("AgentDefinition must not be null");
        }
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
        if (agentId == null) {
            throw new IllegalArgumentException("agentId must not be null");
        }
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
        AgentSession session = new AgentSession(sessionId, def, memory, context.getToolContext());

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
        notifySessionCreated(session);
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
            notifySessionDestroyed(session);
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
                                   LlmCaller llmCaller,
                                   ProgressCallback callback) {
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
                intent = intentRecognizer.analyze(message, availableToolNames, llmCaller,
                        originalChatContent != null ? originalChatContent.getModel() : null);
                System.out.println("[AgentRuntime] Intent recognition result: "
                        + intent.getClarity() + "/" + intent.getComplexity()
                        + ", filteredTools=" + (intent.getFilteredToolNames() != null ? intent.getFilteredToolNames().size() : 0));
            } catch (Exception intentEx) {
                System.err.println("[AgentRuntime] IntentRecognizer.analyze() FAILED: " + intentEx.getMessage());
                intentEx.printStackTrace();
                intent = IntentResult.defaultResult();
            }

            // 2. Skill 匹配 + Complexity-Based Routing（需求 2.1, 2.2, 2.3, 12.3, 12.4, 12.5）
            //
            // 路由决策:根据 config.skillMatchingMode、provider 原生 FC 能力、skill 数量,决定本轮
            // 通过 sidecar(SkillMatcher 旁路 LLM)还是 in-context(InvokeSkillTool dispatcher)
            // 进行 skill 匹配,或完全禁用 skill 匹配。
            //
            // 这是显式过渡态:当 Markdown 模式弃用、所有目标 provider 都支持原生 FC 时,
            // SIDECAR 路径(以及 SkillMatcher/SkillMatchResult 等相关类)可整体删除,
            // 届时只保留 IN_CONTEXT 与 DISABLED。详见 SkillMatchingMode 类注释。
            SkillRoutingPath skillPath = decideSkillMatchingPath();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[Skill] Routing decision: " + skillPath);
            }
            System.out.println("[Skill] Routing decision: " + skillPath);

            List<String> skillToolNames = null; // null 表示使用默认工具列表
            SkillDefinition matchedSkill = null;
            SkillMatchResult skillResult = null;
            if (skillPath == SkillRoutingPath.SIDECAR
                    && skillRegistry != null && skillRegistry.getSkillCount() > 0 && skillMatcher != null) {
                System.out.println("[Skill] Starting skill matching, registry has "
                        + skillRegistry.getSkillCount() + " skill(s)");
                try {
                    skillResult = skillMatcher.match(message, skillRegistry.getAllSkills(),
                            llmCaller, originalChatContent != null ? originalChatContent.getModel() : null);
                    if (skillResult.isMatched()) {
                        matchedSkill = skillRegistry.getSkill(skillResult.getSkillName());
                    }
                } catch (Exception skillEx) {
                    System.err.println("[Skill] Skill matching FAILED: " + skillEx.getMessage());
                    skillEx.printStackTrace();
                    // 容错：匹配失败不阻塞主流程（需求 10.1）
                }
            } else {
                System.out.println("[Skill] Skipping skill matching — registry="
                        + (skillRegistry != null ? skillRegistry.getSkillCount() + " skills" : "null")
                        + ", matcher=" + (skillMatcher != null ? "set" : "null"));
            }

            // 2a. 路由决策（Complexity-Based Routing）
            if (matchedSkill != null) {
                SkillComplexity complexity = matchedSkill.getComplexity();
                String skillName = matchedSkill.getName();

                if (complexity.requiresSubAgent()) {
                    // MODERATE/COMPLEX → 创建子 Agent（需求 2.1, 12.4, 12.5）
                    LOG.info("[Skill] Routing decision: skill='" + skillName
                            + "', complexity=" + complexity + ", decision=CREATE_SUB_AGENT");

                    try {
                        SubAgentFactory factory = new SubAgentFactory(this, getOrCreateContextDistiller());
                        SubAgentResult subResult = factory.createAndExecute(
                                matchedSkill, message, session, llmCaller, callback, subAgentProgressProvider);

                        if (subResult.isSuccess()) {
                            // 子 Agent 成功：写入主 Agent 的 ConversationMemory 保持对话连续性（需求 5.4）
                            Message assistantMsg = new Message();
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(subResult.getContent());
                            session.getMemory().add(assistantMsg);

                            session.transitionTo(SessionState.COMPLETED);
                            return FunctionCallResult.success(
                                    subResult.getContent(), sessionId, Collections.emptyList());
                        } else {
                            // 子 Agent 失败：记录回退原因，降级到主流程（需求 5.5, 10.2, 10.3）
                            LOG.warning("[Skill] Sub-agent failed for skill '" + skillName
                                    + "', falling back to main flow. Reason: " + subResult.getContent());
                            // 继续到下方的主流程执行
                        }
                    } catch (Exception subAgentEx) {
                        // 子 Agent 创建/执行异常：优雅降级（需求 10.2, 10.3）
                        LOG.log(Level.WARNING, "[Skill] Sub-agent creation/execution failed for skill '"
                                + skillName + "', falling back to main flow", subAgentEx);
                        // 继续到下方的主流程执行
                    }
                } else {
                    // SIMPLE → Inline_Execution（需求 2.2, 12.3, 12.6）
                    LOG.info("[Skill] Routing decision: skill='" + skillName
                            + "', complexity=" + complexity + ", decision=INLINE_EXECUTION");

                    // 注入 promptTemplate 到 ReAct 循环（现有逻辑）
                    String effectiveSystemPrompt = matchedSkill.getSystemPrompt();
                    if (matchedSkill.getAdditionalNotes() != null && !matchedSkill.getAdditionalNotes().isEmpty()) {
                        effectiveSystemPrompt += "\n\n" + matchedSkill.getAdditionalNotes();
                    }
                    String effectiveUserMessage = matchedSkill.getPromptTemplate().replace("{{user_input}}", message);

                    if (originalChatContent != null && originalChatContent.getMessages() != null) {
                        for (Message msg : originalChatContent.getMessages()) {
                            if ("system".equals(msg.getRole())) {
                                msg.setContent(effectiveSystemPrompt);
                            }
                        }
                        List<Message> msgs = originalChatContent.getMessages();
                        for (int i = msgs.size() - 1; i >= 0; i--) {
                            if ("user".equals(msgs.get(i).getRole())) {
                                msgs.get(i).setContent(effectiveUserMessage);
                                break;
                            }
                        }
                    }

                    if (matchedSkill.getTools() != null && !matchedSkill.getTools().isEmpty()) {
                        skillToolNames = matchedSkill.getTools();
                    }

                    System.out.println("[Skill] ✓ Inline execution for skill '" + skillName
                            + "' with confidence " + skillResult.getConfidence());
                }
            } else if (skillResult != null && !skillResult.isMatched() && config.isRecruitMode()) {
                // 未匹配 + recruitMode=true → 招募模式（需求 3.2, 3.4, 3.5）
                LOG.info("[Skill] No skill matched, recruitMode=true, invoking SkillGenerator");

                try {
                    // 蒸馏上下文用于 SkillGenerator
                    String contextSummary = "";
                    try {
                        contextSummary = getOrCreateContextDistiller().distill(
                                session.getMemory(), "general", "unmatched request", null,
                                llmCaller, originalChatContent != null ? originalChatContent.getModel() : null,
                                config.getSubAgentContextFallbackMessageCount());
                    } catch (Exception distillEx) {
                        LOG.log(Level.WARNING, "[Skill] Context distillation for recruit failed", distillEx);
                    }

                    SkillGenerator generator = new SkillGenerator();
                    SkillDefinition generatedSkill = generator.generate(
                            message, contextSummary, skillRegistry.getAllSkills(),
                            llmCaller, originalChatContent != null ? originalChatContent.getModel() : null);

                    // 注册生成的 Skill（需求 3.4, 3.10）
                    skillRegistry.register(generatedSkill);
                    LOG.info("[Skill] Registered generated skill: " + generatedSkill.getName()
                            + " (complexity=" + generatedSkill.getComplexity() + ")");

                    // 根据生成 Skill 的 complexity 决定路由
                    if (generatedSkill.getComplexity().requiresSubAgent()) {
                        SubAgentFactory factory = new SubAgentFactory(this, getOrCreateContextDistiller());
                        SubAgentResult subResult = factory.createAndExecute(
                                generatedSkill, message, session, llmCaller, callback, subAgentProgressProvider);

                        if (subResult.isSuccess()) {
                            Message assistantMsg = new Message();
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(subResult.getContent());
                            session.getMemory().add(assistantMsg);

                            session.transitionTo(SessionState.COMPLETED);
                            return FunctionCallResult.success(
                                    subResult.getContent(), sessionId, Collections.emptyList());
                        } else {
                            LOG.warning("[Skill] Recruited sub-agent failed for generated skill '"
                                    + generatedSkill.getName() + "', falling back. Reason: " + subResult.getContent());
                        }
                    } else {
                        // Generated SIMPLE skill → Inline_Execution
                        LOG.info("[Skill] Generated skill '" + generatedSkill.getName()
                                + "' is SIMPLE, using inline execution");

                        String effectiveSystemPrompt = generatedSkill.getSystemPrompt();
                        if (generatedSkill.getAdditionalNotes() != null && !generatedSkill.getAdditionalNotes().isEmpty()) {
                            effectiveSystemPrompt += "\n\n" + generatedSkill.getAdditionalNotes();
                        }
                        String effectiveUserMessage = generatedSkill.getPromptTemplate().replace("{{user_input}}", message);

                        if (originalChatContent != null && originalChatContent.getMessages() != null) {
                            for (Message msg : originalChatContent.getMessages()) {
                                if ("system".equals(msg.getRole())) {
                                    msg.setContent(effectiveSystemPrompt);
                                }
                            }
                            List<Message> msgs = originalChatContent.getMessages();
                            for (int i = msgs.size() - 1; i >= 0; i--) {
                                if ("user".equals(msgs.get(i).getRole())) {
                                    msgs.get(i).setContent(effectiveUserMessage);
                                    break;
                                }
                            }
                        }

                        if (generatedSkill.getTools() != null && !generatedSkill.getTools().isEmpty()) {
                            skillToolNames = generatedSkill.getTools();
                        }
                    }
                } catch (Exception recruitEx) {
                    // 招募失败：优雅降级到现有流程（需求 3.9）
                    LOG.log(Level.WARNING, "[Skill] Recruit mode failed, falling back to main flow", recruitEx);
                }
            } else {
                // 未匹配 + recruitMode=false → 继续现有执行流程（需求 3.5）
                if (skillResult != null && !skillResult.isMatched()) {
                    System.out.println("[Skill] No skill matched: " + skillResult.getReasoning()
                            + ", recruitMode=false, continuing main flow");
                }
            }

            // 3. 工具过滤
            List<Tool> allTools = resolveTools(session.getDefinition());
            // 如果 Skill 匹配成功且声明了工具列表，则仅保留 Skill 声明的工具
            if (skillToolNames != null) {
                final List<String> finalSkillToolNames = skillToolNames;
                allTools = allTools.stream()
                        .filter(t -> finalSkillToolNames.contains(t.name()))
                        .collect(Collectors.toList());
            }
            List<Tool> filteredTools = toolFilter.filter(intent, allTools);
            System.out.println("[AgentRuntime] Tool filtering: " + allTools.size()
                    + " total → " + filteredTools.size() + " filtered");

            // 2b. In-context skill 路由:把 InvokeSkillTool dispatcher 加入 filteredTools。
            // 模型可在主循环里调用 invoke_skill(skill_name, user_input) 触发对应 skill,
            // 派发到 SubAgentFactory 走与 sidecar 路径同样的子 Agent 隔离机制。
            //
            // 关键:这个 tool 必须同时(a)注册进全局 toolRegistry——ValidationEngine 和
            // ExecutionEngine 都按 toolRegistry.getTool(name) 查找;(b)加入 filteredTools——
            // protocolAdapter.formatToolDescriptions 用 request.availableTools 渲染给 LLM。
            // 缺任何一边都会失败:缺 (a) → "Parameter validation failed";缺 (b) → 模型看不见。
            //
            // Per-call 运行时引用通过 ToolContext 传递。注意:sub-session 与 parent 共享同一
            // ToolContext 实例(AgentRuntime.createSession 通过 ExecutionContext.fromToolContext
            // 复用 parent 的 toolContext),所以这些 set 在 sub-agent 的 send 里会被覆盖——
            // 这正是我们想要的(每次 send 用当前 session 的引用)。自递归(skill X 调 skill X)
            // 由 InvokeSkillTool 自己用 CTX_KEY_CURRENT_SKILL 标记防御,SubAgentFactory 在派发
            // 时 save+restore 该标记,sub-agent 看见自己 → 拒绝。允许跨 skill 组合(X 调 Y),
            // 与 Claude Code 的 Task 工具语义一致。
            if (skillPath == SkillRoutingPath.IN_CONTEXT
                    && skillRegistry != null && skillRegistry.getSkillCount() > 0) {
                Tool invokeSkillTool = ensureInvokeSkillToolRegistered();
                if (invokeSkillTool != null) {
                    List<Tool> withSkill = new ArrayList<>(filteredTools);
                    withSkill.add(invokeSkillTool);
                    filteredTools = withSkill;
                    System.out.println("[Skill] Injected invoke_skill dispatcher tool (registry has "
                            + skillRegistry.getSkillCount() + " skill(s))");
                } else {
                    System.out.println("[Skill] IN_CONTEXT path requested but toolRegistry unavailable; "
                            + "InvokeSkillTool not registered — skipping injection (model won't see it).");
                }
            }

            // 4. 委托 FunctionCallOrchestrator 执行（需求 4.3, 4.4, 4.5, 4.6）
            if (orchestrator != null) {
                // 不覆盖 orchestrator 的执行策略 — 尊重用户在 UI 上的选择
                // IntentRecognizer 的推荐策略仅作为日志参考
                LOG.fine("IntentRecognizer recommended strategy: " + intent.getRecommendedStrategy()
                        + ", using orchestrator's current strategy: " + orchestrator.getExecutionStrategyName());

                // 注入流式调用器
                if (streamingLlmCaller != null) {
                    orchestrator.setStreamingLlmCaller(streamingLlmCaller);
                }

                // 注入 Skill 相关组件（PlanAndExecuteStrategy 步骤级 Skill 匹配使用，需求 7.1-7.6）
                orchestrator.setSkillMatcher(skillMatcher);
                orchestrator.setSkillRegistry(skillRegistry);
                orchestrator.setAgentRuntime(this);
                orchestrator.setSubAgentProgressProvider(subAgentProgressProvider);
                orchestrator.setAgentRuntimeConfig(config);
                orchestrator.setAgentSession(session);

                // 使用调用方传入的原始 ChatContent（保留完整对话历史和 model 设置）
                FunctionCallRequest request = FunctionCallRequest.builder()
                        .chatContent(originalChatContent)
                        .availableTools(filteredTools)
                        .maxRounds(20)
                        .config(FunctionCallConfig.builder().build())
                        .build();

                // 使用 session 中保存的 ToolContext（orchestrator 已迁移到 ToolContext）
                com.wmsay.gpt4_lll.fc.tools.ToolContext toolContext = session.getToolContext();

                // 必须在工具执行前写入 sessionId:ExecutionEngine 用它做锁粒度
                // (isConcurrentSafe=false 工具按 session 隔离,而不是按 workspace),
                // 否则跨 session 同项目的工具调用会相互 ConcurrentExecutionException。
                toolContext.set(com.wmsay.gpt4_lll.fc.tools.ExecutionEngine.CTX_KEY_SESSION_ID, sessionId);

                // 委托执行
                FunctionCallResult result = orchestrator.execute(request, toolContext, llmCaller, callback);
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
                                   LlmCaller llmCaller,
                                   ProgressCallback callback) {
        // 构建最小 ChatContent（用于 inter-agent 通信等不需要完整历史的场景）
        ChatContent chatContent = new ChatContent();
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(message);
        // 必须是可变 List:ReActStrategy.updateConversationHistory 会在收到 LLM 的 tool_calls
        // 后通过 getMessages().add(...) 追加 assistant 消息和 tool_result 消息。
        // 用 List.of() 这种不可变 list 会在第一轮 tool 调用后抛 UnsupportedOperationException。
        // 这是先前用 List.of() 时遗留的 bug —— sub-agent 一旦真的调工具就会触发。
        List<Message> mutableMessages = new ArrayList<>();
        mutableMessages.add(userMsg);
        chatContent.setDirectMessages(mutableMessages);
        chatContent.setStream(true);
        // 清除默认 model（"gpt-3.5-turbo"），避免子 Agent / Skill 匹配等旁路调用
        // 将错误的 model 名发送到 Azure OpenAI 等需要 deployment name 的供应商
        chatContent.setModel(null);

        return send(sessionId, message, chatContent, llmCaller, callback);
    }

    /**
     * 根据 AgentDefinition 的 availableToolNames 解析可用工具。
     * 优先使用注入的 ToolRegistry，McpToolRegistry 作为后备（Phase 8 将移除后备）。
     */
    private List<Tool> resolveTools(AgentDefinition def) {
        List<Tool> allTools;
        if (toolRegistry != null) {
            allTools = toolRegistry.getAllTools();
        } else {
            // 后备：使用 McpToolRegistry（将在 Phase 8 移除）
            allTools = McpToolRegistry.getAllTools();
        }

        List<String> names = def.getAvailableToolNames();
        if (names == null || names.isEmpty()) {
            return allTools;
        }
        return allTools.stream()
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
                                       LlmCaller llmCaller) {
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
                    sourceSession.getToolContext());
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
                                    LlmCaller llmCaller) {
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
                                             LlmCaller llmCaller,
                                             ProgressCallback callback) {
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
        // 停止 Skill 文件监听
        if (skillLoader != null) {
            try {
                skillLoader.stopWatching();
            } catch (Exception e) {
                System.err.println("[Skill] Error stopping skill watcher: " + e.getMessage());
            }
            skillLoader = null;
        }
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
        // 停止 Skill 文件监听
        if (skillLoader != null) {
            try {
                skillLoader.stopWatching();
            } catch (Exception e) {
                System.err.println("[Skill] Error stopping skill watcher: " + e.getMessage());
            }
            skillLoader = null;
        }
        for (String sessionId : new ArrayList<>(activeSessions.keySet())) {
            destroySession(sessionId);
        }
        threadPool.shutdownNow();
        INSTANCES.values().remove(this);
    }

    // ==================== Skill matching routing ====================

    /**
     * Skill 匹配的本轮实际执行路径。
     * <p>
     * 由 {@link #decideSkillMatchingPath()} 根据 {@code config.skillMatchingMode}、
     * 当前 orchestrator 是否支持原生 function calling、以及 skill 数量解算得到。
     * <p>
     * 这是一个内部枚举,与公开的 {@link SkillMatchingMode} 配置不同——
     * 后者是"用户意图"(AUTO / FORCE_* / DISABLED),前者是"本轮已解算的实际行为"
     * (IN_CONTEXT / SIDECAR / DISABLED)。
     */
    enum SkillRoutingPath {
        /** 通过 InvokeSkillTool dispatcher,模型在主循环里自己调用。*/
        IN_CONTEXT,
        /** 通过 SkillMatcher 旁路 LLM,pre-loop 决策后直接派发或内联。*/
        SIDECAR,
        /** 不进行 skill 匹配,主循环只看常规工具。*/
        DISABLED
    }

    /**
     * 纯函数版本的路由决策——无副作用,易测试。
     * <p>
     * 决策表:
     * <ul>
     *   <li>{@code DISABLED} 模式 → DISABLED</li>
     *   <li>{@code FORCE_SIDECAR} 模式 → SIDECAR</li>
     *   <li>{@code FORCE_IN_CONTEXT} 模式 → IN_CONTEXT</li>
     *   <li>{@code AUTO} 模式 + nativeFc 为 true + skillCount &lt; threshold → IN_CONTEXT</li>
     *   <li>{@code AUTO} 模式其他情况 → SIDECAR</li>
     * </ul>
     */
    static SkillRoutingPath decideSkillMatchingPath(SkillMatchingMode mode,
                                                    boolean nativeFc,
                                                    int skillCount,
                                                    int threshold) {
        if (mode == null) {
            // 防御性:未配置等价于 AUTO 的 SIDECAR 降级
            return SkillRoutingPath.SIDECAR;
        }
        switch (mode) {
            case DISABLED:
                return SkillRoutingPath.DISABLED;
            case FORCE_SIDECAR:
                return SkillRoutingPath.SIDECAR;
            case FORCE_IN_CONTEXT:
                return SkillRoutingPath.IN_CONTEXT;
            case AUTO:
            default:
                if (nativeFc && skillCount < threshold) {
                    return SkillRoutingPath.IN_CONTEXT;
                }
                return SkillRoutingPath.SIDECAR;
        }
    }

    /**
     * 实例方法:从 {@link #config}、{@link #orchestrator}、{@link #skillRegistry} 拉取当前
     * 状态并委托给 {@link #decideSkillMatchingPath(SkillMatchingMode, boolean, int, int)}。
     * <p>
     * orchestrator 为 null 时(尚未注入),AUTO 模式降级为 SIDECAR——无法判定原生 FC 支持时,
     * 保守选择代价更低的旁路 LLM 路径。
     */
    private SkillRoutingPath decideSkillMatchingPath() {
        boolean nativeFc = orchestrator != null && orchestrator.supportsNativeFunctionCalling();
        int skillCount = skillRegistry != null ? skillRegistry.getSkillCount() : 0;
        return decideSkillMatchingPath(
                config.getSkillMatchingMode(),
                nativeFc,
                skillCount,
                config.getInContextSkillThreshold());
    }

    /**
     * 确保 InvokeSkillTool dispatcher 已经注册进当前的工具注册表并返回它,
     * 用于 in-context 路由路径。幂等:同一 AgentRuntime 内多次调用只注册一次,
     * 复用同一个加载器实例(无状态,只持有 SkillRegistry 引用)。
     * <p>
     * 注册目标的选择顺序:
     * <ol>
     *   <li>若 {@link #toolRegistry} 已注入(例如通过 AgentBuilder 路径),优先用它</li>
     *   <li>否则用 {@code orchestrator.getToolRegistry()}——这是 ValidationEngine 和
     *       ExecutionEngine 实际查询的 canonical 注册表。在 plugin 走 WindowTool 路径
     *       下,{@code AgentRuntime.toolRegistry} 不会被注入,但 orchestrator 内部的
     *       注册表才是 validation 真正使用的</li>
     * </ol>
     * 两者都不可用时返回 null,调用方应跳过 InvokeSkillTool 的注入(模型不会看到这个
     * tool,但主流程仍可继续——这是 fallback 安全行为)。
     * <p>
     * 注:不再需要给 invoke_skill 设置 outer timeout——本工具是纯内容加载器,字符串
     * 拼接 ms 级返回,ExecutionEngine 默认 30s 超时绰绰有余。先前为 sub-agent 派发
     * 路径配的"subAgentTimeout + 30s"已不再适用(那条路径已经从工具里移除)。
     */
    private Tool ensureInvokeSkillToolRegistered() {
        if (skillRegistry == null) {
            return null;
        }
        ToolRegistry targetRegistry = toolRegistry;
        if (targetRegistry == null && orchestrator != null) {
            targetRegistry = orchestrator.getToolRegistry();
        }
        if (targetRegistry == null) {
            return null;
        }
        Tool existing = targetRegistry.getTool(InvokeSkillTool.TOOL_NAME);
        if (existing != null) {
            return existing;
        }
        InvokeSkillTool tool = new InvokeSkillTool(skillRegistry);
        targetRegistry.registerTool(tool);
        return tool;
    }
}
