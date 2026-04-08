package com.wmsay.gpt4_lll.fc.agent;

import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.state.AgentSession;
import com.wmsay.gpt4_lll.fc.state.ExecutionContext;
import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.MemoryStats;
import com.wmsay.gpt4_lll.fc.memory.SummaryMetadata;
import com.wmsay.gpt4_lll.fc.memory.TokenUsageInfo;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.core.SessionState;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * AgentSession 属性测试。
 * <p>
 * 验证状态机合法转换、ExecutionContext 完整性检查、DESTROYED 后拒绝操作、组件隔离性。
 */
class AgentSessionPropertyTest {

    private static final Path TEST_ROOT = Paths.get("/test/project");

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** 创建一个 dummy Project 代理（仅用于满足 non-null 检查）。 */
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

    /** 完整的 ExecutionContext（project 和 projectRoot 均非 null）。 */
    private static ExecutionContext completeContext() {
        McpContext mcp = new McpContext(dummyProject(), null, TEST_ROOT);
        return ExecutionContext.builder().mcpContext(mcp).build();
    }

    /** 不完整的 ExecutionContext（project 为 null）。 */
    private static ExecutionContext incompleteContext() {
        McpContext mcp = new McpContext(null, null, TEST_ROOT);
        return ExecutionContext.builder().mcpContext(mcp).build();
    }

    /** 最小化 ConversationMemory 存根。 */
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

    private static AgentDefinition defaultDefinition(String id) {
        return AgentDefinition.builder()
                .id(id)
                .name("Test Agent " + id)
                .systemPrompt("You are a test agent.")
                .build();
    }

    private AgentSession createSession(ExecutionContext ctx) {
        return new AgentSession(
                "agent-session-" + UUID.randomUUID(),
                defaultDefinition("test-agent"),
                stubMemory(),
                ctx.getToolContext());
    }


    // ---------------------------------------------------------------
    // Property 3: AgentSession 状态机合法转换
    // Validates: Requirements 3.1, 3.2
    // ---------------------------------------------------------------

    /**
     * Property 3: 所有合法状态转换路径均应成功，不抛出异常。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 3: AgentSession 状态机合法转换")
    void legalTransitionsShouldSucceed(
            @ForAll("legalTransitionPaths") List<SessionState> path) {

        AgentSession session = createSession(completeContext());

        // path 的第一个元素是 CREATED（初始状态），从第二个元素开始转换
        for (int i = 1; i < path.size(); i++) {
            SessionState target = path.get(i);
            if (target == SessionState.ERROR) {
                session.transitionToError(new RuntimeException("test error"));
            } else {
                session.transitionTo(target);
            }
            assert session.getState() == target :
                    "Expected state " + target + " but got " + session.getState();
        }
    }

    /**
     * Property 3 (supplement): 非法状态转换应抛出 IllegalStateException。
     */
    @Property(tries = 100)
    @Label("Feature: agent-runtime, Property 3: AgentSession 非法转换抛出异常")
    void illegalTransitionsShouldThrow(
            @ForAll("illegalTransitionPairs") SessionState[] pair) {

        SessionState from = pair[0];
        SessionState to = pair[1];

        AgentSession session = createSession(completeContext());

        // 先将 session 推进到 from 状态
        bringToState(session, from);

        // 尝试非法转换
        try {
            session.transitionTo(to);
            assert false : "Expected IllegalStateException for " + from + " → " + to;
        } catch (IllegalStateException e) {
            // 预期行为
        }
    }

    // ---------------------------------------------------------------
    // Property 4: CREATED→RUNNING 需要完整 ExecutionContext
    // Validates: Requirements 2.4
    // ---------------------------------------------------------------

    /**
     * Property 4: CREATED→RUNNING 是合法转换，使用 ToolContext 时不再验证 ExecutionContext 完整性。
     * 验证 CREATED→RUNNING 转换在任何 ToolContext 下均成功。
     */
    @Property(tries = 20)
    @Label("Feature: agent-runtime, Property 4: AgentSession CREATED→RUNNING 合法转换")
    void createdToRunningShouldSucceed(
            @ForAll("sessionIds") String sessionId) {

        // 不完整上下文（project 为 null）— 使用 ToolContext 后仍可转换
        ToolContext minimalContext = ToolContext.builder()
                .workspaceRoot(TEST_ROOT)
                .build();
        AgentSession session = new AgentSession(
                sessionId, defaultDefinition("agent-minimal"),
                stubMemory(), minimalContext);

        session.transitionTo(SessionState.RUNNING);
        assert session.getState() == SessionState.RUNNING :
                "State should be RUNNING with minimal ToolContext";

        // 完整上下文：也应成功转换
        AgentSession complete = new AgentSession(
                sessionId + "-ok", defaultDefinition("agent-complete"),
                stubMemory(), completeContext().getToolContext());

        complete.transitionTo(SessionState.RUNNING);
        assert complete.getState() == SessionState.RUNNING :
                "State should be RUNNING with complete context";
    }

    // ---------------------------------------------------------------
    // Property 5: DESTROYED 后拒绝所有操作
    // Validates: Requirements 2.5
    // ---------------------------------------------------------------

    /**
     * Property 5: 一旦会话进入 DESTROYED 状态，任何后续 transitionTo 调用
     * 都应抛出 IllegalStateException。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 5: AgentSession DESTROYED 后拒绝所有操作")
    void destroyedSessionRejectsAllTransitions(
            @ForAll("destroyablePaths") List<SessionState> pathToDestroyed,
            @ForAll("allStates") SessionState attemptedTarget) {

        AgentSession session = createSession(completeContext());

        // 推进到 DESTROYED
        for (int i = 1; i < pathToDestroyed.size(); i++) {
            SessionState target = pathToDestroyed.get(i);
            if (target == SessionState.ERROR) {
                session.transitionToError(new RuntimeException("test"));
            } else {
                session.transitionTo(target);
            }
        }
        assert session.getState() == SessionState.DESTROYED :
                "Session should be DESTROYED";

        // 任何转换都应被拒绝
        try {
            session.transitionTo(attemptedTarget);
            assert false : "Expected IllegalStateException after DESTROYED, target=" + attemptedTarget;
        } catch (IllegalStateException e) {
            assert e.getMessage().contains("DESTROYED") :
                    "Error message should mention DESTROYED, got: " + e.getMessage();
        }
    }


    // ---------------------------------------------------------------
    // Property 28: AgentSession 组件隔离性
    // Validates: Requirements 2.9
    // ---------------------------------------------------------------

    /**
     * Property 28: 两个不同 AgentSession 的 ContextManager、FileChangeTracker、
     * TaskManager 为不同对象实例，对一个会话的组件操作不影响另一个会话。
     */
    @Property(tries = 30)
    @Label("Feature: agent-runtime, Property 28: AgentSession 组件隔离性")
    void sessionComponentsShouldBeIsolated(
            @ForAll @IntRange(min = 1, max = 10) int fileChanges,
            @ForAll @IntRange(min = 1, max = 5) int taskSteps) {

        AgentSession session1 = createSession(completeContext());
        AgentSession session2 = createSession(completeContext());

        // 不同对象实例
        assert session1.getContextManager() != session2.getContextManager() :
                "ContextManager should be different instances";
        assert session1.getFileChangeTracker() != session2.getFileChangeTracker() :
                "FileChangeTracker should be different instances";
        assert session1.getTaskManager() != session2.getTaskManager() :
                "TaskManager should be different instances";

        // 对 session1 的 FileChangeTracker 操作不影响 session2
        for (int i = 0; i < fileChanges; i++) {
            session1.getFileChangeTracker().trackChange(
                    "file" + i + ".java", "original" + i, "new" + i);
        }
        assert session1.getFileChangeTracker().size() == fileChanges :
                "Session1 tracker should have " + fileChanges + " changes";
        assert session2.getFileChangeTracker().size() == 0 :
                "Session2 tracker should remain empty";

        // 对 session1 的 TaskManager 操作不影响 session2
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < taskSteps; i++) {
            steps.add("Step " + i);
        }
        session1.getTaskManager().initPlan(steps);
        assert session1.getTaskManager().getCurrentPlan().size() == taskSteps :
                "Session1 TaskManager should have " + taskSteps + " steps";
        assert session2.getTaskManager().getCurrentPlan().isEmpty() :
                "Session2 TaskManager should remain empty";

        // 对 session1 的 ContextManager 操作不影响 session2
        int tokens1 = session1.getContextManager().estimateTokenCount("hello world test");
        int tokens2 = session2.getContextManager().estimateTokenCount("hello world test");
        assert tokens1 == tokens2 :
                "Both ContextManagers should produce same estimate for same input";
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    /**
     * 生成合法的状态转换路径（从 CREATED 开始）。
     */
    @Provide
    Arbitrary<List<SessionState>> legalTransitionPaths() {
        // 枚举所有合法路径
        List<List<SessionState>> paths = new ArrayList<>();
        // CREATED → RUNNING → COMPLETED → DESTROYED
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.COMPLETED, SessionState.DESTROYED));
        // CREATED → RUNNING → ERROR → DESTROYED
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.ERROR, SessionState.DESTROYED));
        // CREATED → RUNNING → PAUSED → RUNNING → COMPLETED → DESTROYED
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.PAUSED, SessionState.RUNNING, SessionState.COMPLETED, SessionState.DESTROYED));
        // CREATED → RUNNING → PAUSED → RUNNING → ERROR → DESTROYED
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.PAUSED, SessionState.RUNNING, SessionState.ERROR, SessionState.DESTROYED));
        // CREATED → DESTROYED
        paths.add(List.of(SessionState.CREATED, SessionState.DESTROYED));
        // CREATED → RUNNING → COMPLETED → RUNNING → COMPLETED → DESTROYED（多轮对话）
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.COMPLETED, SessionState.RUNNING, SessionState.COMPLETED, SessionState.DESTROYED));
        // CREATED → RUNNING → ERROR → RUNNING → COMPLETED → DESTROYED（错误恢复）
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.ERROR, SessionState.RUNNING, SessionState.COMPLETED, SessionState.DESTROYED));
        return Arbitraries.of(paths);
    }

    /**
     * 生成非法状态转换对 [from, to]。
     */
    @Provide
    Arbitrary<SessionState[]> illegalTransitionPairs() {
        // 合法转换表（与 AgentSession.VALID_TRANSITIONS 保持一致）
        Map<SessionState, Set<SessionState>> legal = new EnumMap<>(SessionState.class);
        legal.put(SessionState.CREATED, EnumSet.of(SessionState.RUNNING, SessionState.DESTROYED));
        legal.put(SessionState.RUNNING, EnumSet.of(SessionState.PAUSED, SessionState.COMPLETED, SessionState.ERROR));
        legal.put(SessionState.PAUSED, EnumSet.of(SessionState.RUNNING));
        legal.put(SessionState.COMPLETED, EnumSet.of(SessionState.RUNNING, SessionState.DESTROYED));
        legal.put(SessionState.ERROR, EnumSet.of(SessionState.RUNNING, SessionState.DESTROYED));
        // DESTROYED 已在 Property 5 中测试

        List<SessionState[]> pairs = new ArrayList<>();
        for (SessionState from : new SessionState[]{
                SessionState.CREATED, SessionState.RUNNING, SessionState.PAUSED,
                SessionState.COMPLETED, SessionState.ERROR}) {
            Set<SessionState> allowed = legal.getOrDefault(from, EnumSet.noneOf(SessionState.class));
            for (SessionState to : SessionState.values()) {
                if (!allowed.contains(to) && to != from) {
                    pairs.add(new SessionState[]{from, to});
                }
            }
        }
        return Arbitraries.of(pairs);
    }

    /**
     * 生成到达 DESTROYED 状态的路径。
     */
    @Provide
    Arbitrary<List<SessionState>> destroyablePaths() {
        List<List<SessionState>> paths = new ArrayList<>();
        paths.add(List.of(SessionState.CREATED, SessionState.DESTROYED));
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.COMPLETED, SessionState.DESTROYED));
        paths.add(List.of(SessionState.CREATED, SessionState.RUNNING, SessionState.ERROR, SessionState.DESTROYED));
        return Arbitraries.of(paths);
    }

    @Provide
    Arbitrary<SessionState> allStates() {
        return Arbitraries.of(SessionState.values());
    }

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "agent-session-" + s);
    }

    // ---------------------------------------------------------------
    // Helper: bring session to a target state
    // ---------------------------------------------------------------

    private void bringToState(AgentSession session, SessionState target) {
        if (target == SessionState.CREATED) return;

        switch (target) {
            case RUNNING:
                session.transitionTo(SessionState.RUNNING);
                break;
            case PAUSED:
                session.transitionTo(SessionState.RUNNING);
                session.transitionTo(SessionState.PAUSED);
                break;
            case COMPLETED:
                session.transitionTo(SessionState.RUNNING);
                session.transitionTo(SessionState.COMPLETED);
                break;
            case ERROR:
                session.transitionTo(SessionState.RUNNING);
                session.transitionToError(new RuntimeException("test error"));
                break;
            case DESTROYED:
                session.transitionTo(SessionState.RUNNING);
                session.transitionTo(SessionState.COMPLETED);
                session.transitionTo(SessionState.DESTROYED);
                break;
            default:
                throw new IllegalArgumentException("Unexpected state: " + target);
        }
    }
}
