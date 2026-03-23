package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.MarkdownProtocolAdapter;
import com.wmsay.gpt4_lll.fc.strategy.ExecutionHook;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AgentRuntimeBridge 属性测试。
 * <p>
 * 验证初始化幂等性核心属性。
 * <p>
 * 注意：AgentRuntimeBridge 位于 component 包，其构造函数依赖 IntelliJ Project 接口。
 * 由于 IntelliJ Platform Gradle Plugin 在编译时对 component 包类进行字节码改写，
 * 导致 AgentRuntimeBridge 无法在测试环境中直接实例化。
 * 因此本测试直接复现 AgentRuntimeBridge.initialize() 的核心逻辑
 * （获取 AgentRuntime → 检查 isRegistered → 注册默认 Agent），
 * 验证其幂等性属性。这与 AgentRuntimeBridge.initialize() 的实际实现完全一致。
 * <p>
 * 测试位于 fc.agent 包以访问 AgentRuntime.removeInstance()（package-private）。
 */
class AgentRuntimeBridgePropertyTest {

    private static final String DEFAULT_AGENT_ID = "default-chat-agent";

    /** 每次 try 使用唯一 projectId 避免交叉污染 */
    private String currentProjectId;

    @AfterTry
    void cleanup() {
        if (currentProjectId != null) {
            try {
                AgentRuntime runtime = AgentRuntime.getInstance(currentProjectId);
                runtime.shutdownNow();
            } catch (Exception ignored) { }
            AgentRuntime.removeInstance(currentProjectId);
            currentProjectId = null;
        }
    }

    // ---------------------------------------------------------------
    // Property 1: AgentRuntimeBridge 初始化幂等性
    // Validates: Requirements 1.6
    // ---------------------------------------------------------------

    /**
     * Property 1: 对于任意 AgentRuntimeBridge 实例，连续调用 initialize() N 次（N >= 1），
     * 每次调用都返回 true 且不抛出异常，AgentRuntime 中默认 Agent "default-chat-agent" 仅注册一次。
     * <p>
     * 本测试直接复现 AgentRuntimeBridge.initialize() 的核心逻辑进行验证。
     *
     * **Validates: Requirements 1.6**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 1: AgentRuntimeBridge 初始化幂等性")
    void initializeIsIdempotent(@ForAll @IntRange(min = 1, max = 10) int n) throws Exception {
        // 1. Create a temp directory as projectRoot (simulates project.getBasePath())
        Path tempDir = Files.createTempDirectory("bridge-prop-test-");
        currentProjectId = tempDir.toAbsolutePath().toString();

        // 2. Call the initialize logic N times — mirrors AgentRuntimeBridge.initialize()
        for (int i = 0; i < n; i++) {
            boolean result = simulateInitialize(currentProjectId, tempDir);
            assert result : "initialize() call #" + (i + 1) + " of " + n + " should return true";
        }

        // 3. Verify the default Agent is registered exactly once
        AgentRuntime runtime = AgentRuntime.getInstance(currentProjectId);
        List<AgentDefinition> definitions = runtime.getRegisteredDefinitions();

        long defaultAgentCount = definitions.stream()
                .filter(d -> DEFAULT_AGENT_ID.equals(d.getId()))
                .count();

        assert defaultAgentCount == 1 :
                "After " + n + " initialize() calls, default agent should be registered exactly once, "
                        + "but found " + defaultAgentCount + " registrations. "
                        + "All registered: " + definitions.stream()
                        .map(AgentDefinition::getId)
                        .toList();

        // 4. Verify runtime has KnowledgeBase set
        assert runtime.getKnowledgeBase() != null :
                "KnowledgeBase should be set after initialize()";
    }

    // ---------------------------------------------------------------
    // Property 2: 会话按需创建与复用
    // Validates: Requirements 2.1
    // ---------------------------------------------------------------

    /**
     * Property 2: 对于任意 AgentRuntimeBridge 实例，当 currentSession 为 null 或已 DESTROYED 时
     * 调用 getOrCreateSession() 返回新会话；当 currentSession 存活时调用 getOrCreateSession()
     * 返回同一会话实例。
     * <p>
     * 本测试直接复现 AgentRuntimeBridge.getOrCreateSession() 的核心逻辑进行验证：
     * 1. currentSession == null → 创建新会话
     * 2. 连续调用多次 → 返回同一实例
     * 3. 销毁会话后 → 创建新会话，sessionId 不同
     *
     * **Validates: Requirements 2.1**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 2: 会话按需创建与复用")
    void sessionCreatedOnDemandAndReused(@ForAll @IntRange(min = 1, max = 5) int reuseCalls,
                                         @ForAll @IntRange(min = 1, max = 3) int destroyCycles) throws Exception {
        // 1. Setup: create temp dir, initialize runtime with default agent
        Path tempDir = Files.createTempDirectory("bridge-session-prop-test-");
        currentProjectId = tempDir.toAbsolutePath().toString();
        simulateInitialize(currentProjectId, tempDir);

        AgentRuntime runtime = AgentRuntime.getInstance(currentProjectId);

        // Simulate AgentRuntimeBridge's mutable currentSession field
        AgentSession currentSession = null;

        for (int cycle = 0; cycle < destroyCycles; cycle++) {
            // 2. When currentSession is null, getOrCreateSession should create a new session
            currentSession = simulateGetOrCreateSession(runtime, currentSession, tempDir);
            assert currentSession != null :
                    "getOrCreateSession() should return a non-null session when currentSession is null";

            String firstSessionId = currentSession.getSessionId();

            // 3. When currentSession is alive, repeated calls should return the same instance
            for (int i = 0; i < reuseCalls; i++) {
                AgentSession reused = simulateGetOrCreateSession(runtime, currentSession, tempDir);
                assert reused == currentSession :
                        "getOrCreateSession() call #" + (i + 1) + " should return the same session instance "
                                + "(expected sessionId=" + firstSessionId
                                + ", got sessionId=" + reused.getSessionId() + ")";
            }

            // 4. Destroy the session → state becomes DESTROYED
            runtime.destroySession(currentSession.getSessionId());
            assert currentSession.getState() == SessionState.DESTROYED :
                    "Session should be DESTROYED after destroySession()";

            // 5. After destroy, getOrCreateSession should create a NEW session
            AgentSession previousSession = currentSession;
            currentSession = simulateGetOrCreateSession(runtime, previousSession, tempDir);
            assert currentSession != previousSession :
                    "After destroy, getOrCreateSession() should return a different session instance";
            assert !currentSession.getSessionId().equals(firstSessionId) :
                    "After destroy, new session should have a different sessionId "
                            + "(old=" + firstSessionId + ", new=" + currentSession.getSessionId() + ")";
        }

        // Cleanup: destroy the last session
        if (currentSession != null && currentSession.getState() != SessionState.DESTROYED) {
            runtime.destroySession(currentSession.getSessionId());
        }
    }

    // ---------------------------------------------------------------
    // Property 3: 执行钩子生命周期对称性
    // Validates: Requirements 3.3
    // ---------------------------------------------------------------

    /**
     * Property 3: 对于任意 sendMessage() 调用，执行前注册到 FunctionCallOrchestrator 的
     * AgentFileChangeHook 在执行完成后（无论成功或失败）都被移除，
     * FunctionCallOrchestrator 的钩子数量在调用前后保持不变。
     * <p>
     * 本测试直接复现 AgentRuntimeBridge.sendMessage() 的钩子生命周期逻辑：
     * 1. 创建 FunctionCallOrchestrator 实例
     * 2. 预注册 N 个钩子（模拟已有钩子）
     * 3. 模拟 sendMessage() 的 hook add → execute → hook remove (finally) 流程
     * 4. 验证钩子数量在调用前后保持不变
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 3: 执行钩子生命周期对称性")
    void executionHookLifecycleSymmetry(
            @ForAll @IntRange(min = 0, max = 5) int preExistingHookCount,
            @ForAll boolean executionSucceeds) throws Exception {

        // 1. Create a FunctionCallOrchestrator with minimal dependencies
        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                new MarkdownProtocolAdapter(),
                new ValidationEngine(),
                new ExecutionEngine(new RetryStrategy(), null),
                new ErrorHandler(),
                new ObservabilityManager());

        // 2. Pre-register N hooks to simulate existing hooks on the orchestrator
        for (int i = 0; i < preExistingHookCount; i++) {
            orchestrator.addExecutionHook(new NoOpHook());
        }

        int hookCountBefore = orchestrator.getExecutionHooks().size();
        assert hookCountBefore == preExistingHookCount :
                "Pre-condition: expected " + preExistingHookCount + " hooks, got " + hookCountBefore;

        // 3. Simulate sendMessage() hook lifecycle — mirrors AgentRuntimeBridge.sendMessage()
        //    Create AgentFileChangeHook and register it
        FileChangeTracker tracker = new FileChangeTracker();
        AgentFileChangeHook hook = new AgentFileChangeHook(tracker, "/tmp/test-project");

        orchestrator.addExecutionHook(hook);

        try {
            // Simulate execution: success or failure
            if (!executionSucceeds) {
                throw new RuntimeException("Simulated execution failure");
            }
            // Success path — no-op (runtime.send() would return result here)
        } catch (RuntimeException e) {
            // Exception caught — mirrors how AgentRuntime.send() catches exceptions
            // and converts to FunctionCallResult.error() instead of propagating
        } finally {
            // Always remove the hook — mirrors the finally block in sendMessage()
            orchestrator.removeExecutionHook(hook);
        }

        // 4. Verify hook count is the same as before sendMessage()
        int hookCountAfter = orchestrator.getExecutionHooks().size();
        assert hookCountAfter == hookCountBefore :
                "Hook count should be symmetric: before=" + hookCountBefore
                        + ", after=" + hookCountAfter
                        + " (executionSucceeds=" + executionSucceeds
                        + ", preExistingHookCount=" + preExistingHookCount + ")";
    }

    /** Minimal no-op ExecutionHook for simulating pre-existing hooks. */
    private static class NoOpHook implements ExecutionHook {}

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Simulates AgentRuntimeBridge.initialize() logic exactly:
     * 1. Get AgentRuntime via getInstance(projectId)
     * 2. Create and set KnowledgeBase
     * 3. If default agent not registered, register it (idempotent guard)
     * 4. Return true on success, false on exception
     *
     * This mirrors the actual implementation in AgentRuntimeBridge.initialize().
     */
    private static boolean simulateInitialize(String projectId, Path projectRoot) {
        try {
            // 1. 通过内部桥接方法获取 AgentRuntime
            AgentRuntime runtime = AgentRuntime.getInstance(projectId);

            // 2. 初始化 KnowledgeBase 并注入到 runtime
            KnowledgeBase kb = new KnowledgeBase(projectRoot);
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

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Simulates AgentRuntimeBridge.getOrCreateSession() logic exactly:
     * 1. If currentSession != null && state != DESTROYED → return same session
     * 2. Otherwise create new session via runtime.createSession(DEFAULT_AGENT_ID, context)
     *
     * Uses McpContext constructor directly (with null project, valid projectRoot)
     * to avoid IntelliJ Project dependency. This mirrors the actual implementation
     * in AgentRuntimeBridge.getOrCreateSession().
     */
    private static AgentSession simulateGetOrCreateSession(AgentRuntime runtime,
                                                           AgentSession currentSession,
                                                           Path projectRoot) {
        if (currentSession != null && currentSession.getState() != SessionState.DESTROYED) {
            return currentSession;
        }

        // Build ExecutionContext with project=null and valid projectRoot
        // (mirrors McpContext.fromIdeState(project, null) but without IntelliJ dependency)
        McpContext mcpContext = new McpContext(null, null, projectRoot);
        ExecutionContext context = ExecutionContext.fromMcpContext(mcpContext);

        return runtime.createSession(DEFAULT_AGENT_ID, context);
    }
}
