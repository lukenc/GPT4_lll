package com.wmsay.gpt4_lll.fc.agent;

import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.MemoryStats;
import com.wmsay.gpt4_lll.fc.memory.SummaryMetadata;
import com.wmsay.gpt4_lll.fc.memory.TokenUsageInfo;
import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.model.Message;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * AgentRuntime 属性测试。
 * <p>
 * 验证注册/注销往返一致性、重复注册拒绝、会话创建/销毁生命周期、
 * 并发会话数限制、委托深度限制。
 */
class AgentRuntimePropertyTest {

    private static final Path TEST_ROOT = Paths.get("/test/project");

    // 每个属性测试使用独立的 projectId，避免单例污染
    private final String projectId = "test-project-" + UUID.randomUUID();

    @AfterProperty
    void cleanup() {
        try {
            AgentRuntime rt = AgentRuntime.getInstance(projectId);
            rt.shutdownNow();
        } catch (Exception ignored) { }
        AgentRuntime.removeInstance(projectId);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static Project dummyProject() {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[]{Project.class},
                (proxy, method, args) -> {
                    if ("getBasePath".equals(method.getName())) return "/test/project";
                    if ("getName".equals(method.getName())) return "test";
                    if ("isDefault".equals(method.getName())) return false;
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    if ("toString".equals(method.getName())) return "DummyProject";
                    return null;
                });
    }

    private static ExecutionContext completeContext() {
        McpContext mcp = new McpContext(dummyProject(), null, TEST_ROOT);
        return ExecutionContext.builder().mcpContext(mcp).build();
    }

    private static ConversationMemory stubMemory() {
        return new ConversationMemory() {
            private final List<Message> messages = new ArrayList<>();
            @Override public void add(Message message) { if (message != null) messages.add(message); }
            @Override public void addAll(List<Message> msgs) { if (msgs != null) msgs.stream().filter(Objects::nonNull).forEach(messages::add); }
            @Override public List<Message> getMessages() { return Collections.unmodifiableList(new ArrayList<>(messages)); }
            @Override public List<Message> getAllOriginalMessages() { return getMessages(); }
            @Override public void clear() { messages.clear(); }
            @Override public int size() { return messages.size(); }
            @Override public MemoryStats getStats() { return null; }
            @Override public void loadWithSummary(List<Message> orig, List<SummaryMetadata> meta) {}
            @Override public void updateRealTokenUsage(TokenUsageInfo info) {}
            @Override public int getLastKnownPromptTokens() { return -1; }
        };
    }

    private static AgentDefinition makeDef(String id) {
        return AgentDefinition.builder()
                .id(id)
                .name("Agent " + id)
                .systemPrompt("You are agent " + id)
                .build();
    }

    /**
     * 每次调用都返回一个全新的 AgentRuntime 实例（清除旧实例）。
     */
    private AgentRuntime freshRuntime() {
        cleanupRuntime();
        return AgentRuntime.getInstance(projectId);
    }

    private AgentRuntime freshRuntime(AgentRuntimeConfig config) {
        cleanupRuntime();
        return AgentRuntime.getInstance(projectId, config);
    }

    private void cleanupRuntime() {
        try {
            AgentRuntime old = AgentRuntime.getInstance(projectId);
            old.shutdownNow();
        } catch (Exception ignored) { }
        AgentRuntime.removeInstance(projectId);
    }

    // ---------------------------------------------------------------
    // Property 6: AgentRuntime 注册/注销往返一致性
    // Validates: Requirements 4.1, 4.3, 4.5, 4.6
    // ---------------------------------------------------------------

    /**
     * Property 6: 注册一组 Agent 定义后，所有定义均可查询到；
     * 注销后不再可查询，且注销不存在的 id 静默忽略。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 6: AgentRuntime 注册/注销往返一致性")
    void registerUnregisterRoundTrip(
            @ForAll @IntRange(min = 1, max = 10) int count) {

        AgentRuntime rt = freshRuntime();

        // 注册 count 个不同的 Agent
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "agent-p6-" + i + "-" + UUID.randomUUID();
            ids.add(id);
            rt.register(makeDef(id));
        }

        // 全部可查询
        for (String id : ids) {
            assert rt.isRegistered(id) : "Agent " + id + " should be registered";
        }
        assert rt.getRegisteredDefinitions().size() >= count :
                "Should have at least " + count + " registered definitions";

        // 逐个注销
        for (String id : ids) {
            rt.unregister(id);
            assert !rt.isRegistered(id) : "Agent " + id + " should be unregistered";
        }

        // 注销不存在的 id 不抛异常
        rt.unregister("nonexistent-agent-id");
    }

    // ---------------------------------------------------------------
    // Property 7: AgentRuntime 重复注册拒绝
    // Validates: Requirements 4.2
    // ---------------------------------------------------------------

    /**
     * Property 7: 注册已存在的 agentId 应抛出 IllegalArgumentException。
     */
    @Property(tries = 30)
    @Label("Feature: agent-runtime, Property 7: AgentRuntime 重复注册拒绝")
    void duplicateRegistrationShouldThrow(
            @ForAll("agentIds") String agentId) {

        AgentRuntime rt = freshRuntime();
        rt.register(makeDef(agentId));

        try {
            rt.register(makeDef(agentId));
            assert false : "Expected IllegalArgumentException for duplicate registration of " + agentId;
        } catch (IllegalArgumentException e) {
            assert e.getMessage().contains(agentId) :
                    "Error message should contain agentId, got: " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------
    // Property 8: AgentRuntime 会话创建/销毁生命周期
    // Validates: Requirements 5.1, 5.5, 5.7, 5.8
    // ---------------------------------------------------------------

    /**
     * Property 8: 为已注册的 Agent 创建会话后，activeSessionCount 增加；
     * 销毁会话后 activeSessionCount 减少；会话状态变为 DESTROYED。
     * 未注册的 agentId 创建会话应抛出 IllegalArgumentException。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 8: AgentRuntime 会话创建/销毁生命周期")
    void sessionCreateDestroyLifecycle(
            @ForAll @IntRange(min = 1, max = 4) int sessionCount) {

        AgentRuntime rt = freshRuntime();
        String agentId = "agent-p8-" + UUID.randomUUID();
        rt.register(makeDef(agentId));

        int baseline = rt.getActiveSessionCount();
        List<AgentSession> sessions = new ArrayList<>();

        // 创建 sessionCount 个会话
        for (int i = 0; i < sessionCount; i++) {
            AgentSession s = rt.createSession(agentId, completeContext());
            sessions.add(s);
            assert s.getSessionId() != null && s.getSessionId().startsWith("agent-session-") :
                    "Session ID should start with 'agent-session-'";
            assert s.getState() == SessionState.CREATED :
                    "New session should be in CREATED state";
            assert s.getDefinition().getId().equals(agentId) :
                    "Session definition should match registered agent";
        }
        assert rt.getActiveSessionCount() == baseline + sessionCount :
                "Active session count should increase by " + sessionCount;

        // 通过 getSession 可查询
        for (AgentSession s : sessions) {
            assert rt.getSession(s.getSessionId()) == s :
                    "getSession should return the same session instance";
        }

        // 销毁所有会话
        for (AgentSession s : sessions) {
            rt.destroySession(s.getSessionId());
            assert s.getState() == SessionState.DESTROYED :
                    "Destroyed session should be in DESTROYED state";
            assert rt.getSession(s.getSessionId()) == null :
                    "Destroyed session should not be retrievable";
        }
        assert rt.getActiveSessionCount() == baseline :
                "Active session count should return to baseline";

        // 未注册的 agentId 创建会话应抛异常
        try {
            rt.createSession("unregistered-agent", completeContext());
            assert false : "Expected IllegalArgumentException for unregistered agent";
        } catch (IllegalArgumentException e) {
            assert e.getMessage().contains("unregistered-agent") :
                    "Error message should contain the agent id";
        }
    }

    // ---------------------------------------------------------------
    // Property 9: AgentRuntime 并发会话数限制
    // Validates: Requirements 8.1
    // ---------------------------------------------------------------

    /**
     * Property 9: 当活跃会话数达到 maxConcurrentSessions 时，
     * 再创建会话应抛出 IllegalStateException。
     */
    @Property(tries = 20)
    @Label("Feature: agent-runtime, Property 9: AgentRuntime 并发会话数限制")
    void concurrentSessionLimitEnforced(
            @ForAll @IntRange(min = 1, max = 5) int maxSessions) {

        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
                .maxConcurrentSessions(maxSessions)
                .build();
        AgentRuntime rt = freshRuntime(config);

        String agentId = "agent-p9-" + UUID.randomUUID();
        rt.register(makeDef(agentId));

        // 创建到上限
        List<AgentSession> sessions = new ArrayList<>();
        for (int i = 0; i < maxSessions; i++) {
            sessions.add(rt.createSession(agentId, completeContext()));
        }
        assert rt.getActiveSessionCount() == maxSessions :
                "Should have exactly " + maxSessions + " active sessions";

        // 超出上限应抛异常
        try {
            rt.createSession(agentId, completeContext());
            assert false : "Expected IllegalStateException when exceeding max concurrent sessions";
        } catch (IllegalStateException e) {
            assert e.getMessage().contains(String.valueOf(maxSessions)) :
                    "Error message should contain the limit, got: " + e.getMessage();
        }

        // 销毁一个后可以再创建
        rt.destroySession(sessions.get(0).getSessionId());
        AgentSession newSession = rt.createSession(agentId, completeContext());
        assert newSession != null : "Should be able to create session after destroying one";
        assert rt.getActiveSessionCount() == maxSessions :
                "Active count should be back to max after create";
    }

    // ---------------------------------------------------------------
    // Property 11: 委托深度限制
    // Validates: Requirements 7.3, 7.4
    // ---------------------------------------------------------------

    /**
     * Property 11: 当委托深度超过 maxDelegationDepth 时，
     * delegate 应返回包含 "Maximum delegation depth exceeded" 的错误结果。
     */
    @Property(tries = 20)
    @Label("Feature: agent-runtime, Property 11: 委托深度限制")
    void delegationDepthLimitEnforced(
            @ForAll @IntRange(min = 0, max = 3) int maxDepth) {

        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
                .maxDelegationDepth(maxDepth)
                .delegationTimeoutSeconds(5)
                .build();
        AgentRuntime rt = freshRuntime(config);

        String sourceAgentId = "source-agent-" + UUID.randomUUID();
        String targetAgentId = "target-agent-" + UUID.randomUUID();
        rt.register(makeDef(sourceAgentId));
        rt.register(makeDef(targetAgentId));

        AgentSession sourceSession = rt.createSession(sourceAgentId, completeContext());
        // 将源会话的委托深度设置为刚好达到上限
        sourceSession.setDelegationDepth(maxDepth);

        // 委托应被拒绝（newDepth = maxDepth + 1 > maxDelegationDepth）
        FunctionCallResult result = rt.delegate(
                sourceSession.getSessionId(), targetAgentId, "test message", null);

        assert result != null : "Delegate should return a result";
        assert !result.isSuccess() : "Delegate should fail when depth exceeded";
        assert result.getContent().contains("Maximum delegation depth exceeded") :
                "Error message should mention depth exceeded, got: " + result.getContent();
    }

    /**
     * Property 11 (supplement): 当委托深度未超限时，delegate 应正常创建目标会话。
     * 由于 send() 内部需要 LlmCaller（此处为 null 会导致 NPE），
     * 我们验证的是：delegate 不会因深度限制而拒绝，而是进入执行流程。
     */
    @Property(tries = 20)
    @Label("Feature: agent-runtime, Property 11: 委托深度未超限时不拒绝")
    void delegationWithinDepthLimitNotRejected(
            @ForAll @IntRange(min = 1, max = 5) int maxDepth) {

        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
                .maxDelegationDepth(maxDepth)
                .delegationTimeoutSeconds(2)
                .build();
        AgentRuntime rt = freshRuntime(config);

        String sourceAgentId = "src-" + UUID.randomUUID();
        String targetAgentId = "tgt-" + UUID.randomUUID();
        rt.register(makeDef(sourceAgentId));
        rt.register(makeDef(targetAgentId));

        AgentSession sourceSession = rt.createSession(sourceAgentId, completeContext());
        // 深度 0，maxDepth >= 1，所以 newDepth=1 <= maxDepth，不应被深度限制拒绝
        sourceSession.setDelegationDepth(0);

        FunctionCallResult result = rt.delegate(
                sourceSession.getSessionId(), targetAgentId, "test", null);

        // 不应因深度限制被拒绝（可能因其他原因失败，如 LlmCaller 为 null）
        assert result != null : "Delegate should return a result";
        if (!result.isSuccess()) {
            assert !result.getContent().contains("Maximum delegation depth exceeded") :
                    "Should not be rejected by depth limit when within bounds";
        }
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> agentIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12)
                .map(s -> "agent-" + s);
    }
}
