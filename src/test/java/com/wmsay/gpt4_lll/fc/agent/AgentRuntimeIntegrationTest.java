package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.memory.MemoryStats;
import com.wmsay.gpt4_lll.fc.memory.SummaryMetadata;
import com.wmsay.gpt4_lll.fc.memory.TokenUsageInfo;
import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.model.Message;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentRuntime 集成单元测试。
 * <p>
 * 使用 JUnit 5，mock LlmCaller 为 lambda。
 * 验证 send()、delegate()、executeOneShot()、sendMessage() 端到端流程，
 * 向后兼容性，以及 fc.agent 包无 com.intellij.* import 约束。
 * <p>
 * Validates: Requirements 6.1, 7.1, 10.1, 10.3, 10.4, 10.5, 10.6, 20.2
 */
class AgentRuntimeIntegrationTest {

    private static final Path TEST_ROOT = Paths.get("/test/project");

    /** Each test gets a unique projectId to avoid cross-test contamination. */
    private String projectId;
    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        projectId = "integration-test-" + UUID.randomUUID();
        runtime = AgentRuntime.getInstance(projectId);
    }

    @AfterEach
    void tearDown() {
        try {
            runtime.shutdownNow();
        } catch (Exception ignored) { }
        AgentRuntime.removeInstance(projectId);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Create a dummy Project proxy via reflection — avoids direct com.intellij.* import
     * in this test file while satisfying McpContext's constructor requirement.
     */
    private static Object dummyProject() {
        try {
            Class<?> projectClass = Class.forName("com.intellij.openapi.project.Project");
            return Proxy.newProxyInstance(
                    projectClass.getClassLoader(),
                    new Class<?>[]{projectClass},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getBasePath": return "/test/project";
                            case "getName": return "test";
                            case "isDefault": return false;
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return proxy == args[0];
                            case "toString": return "DummyProject";
                            default: return null;
                        }
                    });
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Project class not on classpath", e);
        }
    }

    /**
     * Build a complete ExecutionContext using the dummy Project proxy.
     * Uses McpContext constructor directly — McpContext accepts Object for Project param
     * at the JVM level (it's erased to Object in the constructor call).
     */
    @SuppressWarnings("unchecked")
    private static ExecutionContext completeContext() {
        try {
            Object project = dummyProject();
            // McpContext(Project, Editor, Path) — we pass the proxy as Project
            Class<?> mcpContextClass = McpContext.class;
            Class<?> projectClass = Class.forName("com.intellij.openapi.project.Project");
            Class<?> editorClass = Class.forName("com.intellij.openapi.editor.Editor");
            var ctor = mcpContextClass.getConstructor(projectClass, editorClass, Path.class);
            McpContext mcp = (McpContext) ctor.newInstance(project, null, TEST_ROOT);
            return ExecutionContext.builder().mcpContext(mcp).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ExecutionContext", e);
        }
    }

    private static AgentDefinition makeDef(String id) {
        return AgentDefinition.builder()
                .id(id)
                .name("Agent " + id)
                .systemPrompt("You are agent " + id)
                .build();
    }

    /**
     * Mock LlmCaller that returns a valid IntentRecognizer JSON response
     * for the first call, and a simple text response for subsequent calls.
     */
    private static com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.LlmCaller mockLlmCaller() {
        return request -> {
            // IntentRecognizer expects JSON with clarity/complexity/recommendedStrategy
            return "{\"clarity\":\"CLEAR\",\"complexity\":\"SIMPLE\"," +
                    "\"recommendedStrategy\":\"react\",\"reasoning\":\"test\"," +
                    "\"filteredToolNames\":[]}";
        };
    }

    // ---------------------------------------------------------------
    // Test: AgentRuntime.send() end-to-end flow
    // Validates: Requirements 6.1
    // ---------------------------------------------------------------

    @Test
    @DisplayName("send() should complete end-to-end: intent recognition → tool filter → context assembly → result")
    void testSendEndToEnd() {
        String agentId = "send-test-agent-" + UUID.randomUUID();
        runtime.register(makeDef(agentId));
        AgentSession session = runtime.createSession(agentId, completeContext());

        assertEquals(SessionState.CREATED, session.getState());

        FunctionCallResult result = runtime.send(
                session.getSessionId(), "Hello, please help me", mockLlmCaller(), null);

        assertNotNull(result, "send() should return a result");
        assertTrue(result.isSuccess(), "send() should succeed with mock LlmCaller, got: " + result.getContent());
        assertNotNull(result.getContent(), "Result content should not be null");
        assertEquals(SessionState.COMPLETED, session.getState(),
                "Session should be COMPLETED after successful send()");
    }

    @Test
    @DisplayName("send() with DESTROYED session should throw (session removed from active map)")
    void testSendDestroyedSessionThrows() {
        String agentId = "send-destroyed-" + UUID.randomUUID();
        runtime.register(makeDef(agentId));
        AgentSession session = runtime.createSession(agentId, completeContext());
        runtime.destroySession(session.getSessionId());

        // destroySession removes the session from activeSessions map,
        // so send() throws IllegalArgumentException ("Session not found")
        assertThrows(IllegalArgumentException.class, () ->
                runtime.send(session.getSessionId(), "test", mockLlmCaller(), null));
    }

    @Test
    @DisplayName("send() with non-existent session should throw IllegalArgumentException")
    void testSendNonExistentSessionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                runtime.send("non-existent-session", "test", mockLlmCaller(), null));
    }

    @Test
    @DisplayName("send() should transition session through CREATED → RUNNING → COMPLETED")
    void testSendStateTransitions() {
        String agentId = "send-states-" + UUID.randomUUID();
        runtime.register(makeDef(agentId));
        AgentSession session = runtime.createSession(agentId, completeContext());

        assertEquals(SessionState.CREATED, session.getState());

        FunctionCallResult result = runtime.send(
                session.getSessionId(), "test message", mockLlmCaller(), null);

        assertTrue(result.isSuccess());
        assertEquals(SessionState.COMPLETED, session.getState());
    }

    // ---------------------------------------------------------------
    // Test: AgentRuntime.delegate() end-to-end flow
    // Validates: Requirements 7.1
    // ---------------------------------------------------------------

    @Test
    @DisplayName("delegate() should create temp session, execute, and auto-destroy")
    void testDelegateEndToEnd() {
        String sourceAgentId = "delegate-source-" + UUID.randomUUID();
        String targetAgentId = "delegate-target-" + UUID.randomUUID();
        runtime.register(makeDef(sourceAgentId));
        runtime.register(makeDef(targetAgentId));

        AgentSession sourceSession = runtime.createSession(sourceAgentId, completeContext());
        int sessionsBefore = runtime.getActiveSessionCount();

        FunctionCallResult result = runtime.delegate(
                sourceSession.getSessionId(), targetAgentId, "delegated task", mockLlmCaller());

        assertNotNull(result, "delegate() should return a result");
        // The delegate creates a temp session and destroys it after execution,
        // so active count should be back to what it was before (or less if source was also cleaned)
        assertTrue(runtime.getActiveSessionCount() <= sessionsBefore,
                "Temp delegate session should be auto-destroyed");
    }

    @Test
    @DisplayName("delegate() should enforce max delegation depth")
    void testDelegateDepthLimit() {
        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
                .maxDelegationDepth(2)
                .delegationTimeoutSeconds(5)
                .build();
        // Use a separate runtime with custom config
        String customProjectId = "depth-test-" + UUID.randomUUID();
        AgentRuntime customRuntime = AgentRuntime.getInstance(customProjectId, config);
        try {
            String sourceId = "depth-source-" + UUID.randomUUID();
            String targetId = "depth-target-" + UUID.randomUUID();
            customRuntime.register(makeDef(sourceId));
            customRuntime.register(makeDef(targetId));

            AgentSession source = customRuntime.createSession(sourceId, completeContext());
            source.setDelegationDepth(2); // At the limit

            FunctionCallResult result = customRuntime.delegate(
                    source.getSessionId(), targetId, "too deep", mockLlmCaller());

            assertNotNull(result);
            assertFalse(result.isSuccess(), "Should fail when depth exceeded");
            assertTrue(result.getContent().contains("Maximum delegation depth exceeded"),
                    "Error should mention depth exceeded, got: " + result.getContent());
        } finally {
            customRuntime.shutdownNow();
            AgentRuntime.removeInstance(customProjectId);
        }
    }

    @Test
    @DisplayName("delegate() with unregistered target should return error")
    void testDelegateUnregisteredTarget() {
        String sourceId = "delegate-unreg-src-" + UUID.randomUUID();
        runtime.register(makeDef(sourceId));
        AgentSession source = runtime.createSession(sourceId, completeContext());

        FunctionCallResult result = runtime.delegate(
                source.getSessionId(), "nonexistent-target", "test", mockLlmCaller());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("not registered"),
                "Error should mention target not registered, got: " + result.getContent());
    }

    // ---------------------------------------------------------------
    // Test: AgentRuntime.executeOneShot() convenience method
    // Validates: Requirements 10.1
    // ---------------------------------------------------------------

    @Test
    @DisplayName("executeOneShot() should auto-register, create session, execute, and cleanup")
    void testExecuteOneShot() {
        AgentDefinition def = makeDef("oneshot-agent-" + UUID.randomUUID());
        int sessionsBefore = runtime.getActiveSessionCount();

        FunctionCallResult result = runtime.executeOneShot(
                def, completeContext(), "one-shot task", mockLlmCaller(), null);

        assertNotNull(result, "executeOneShot() should return a result");
        assertTrue(result.isSuccess(), "executeOneShot() should succeed, got: " + result.getContent());
        // Session should be auto-destroyed
        assertEquals(sessionsBefore, runtime.getActiveSessionCount(),
                "Session should be cleaned up after executeOneShot()");
        // Agent should be auto-registered
        assertTrue(runtime.isRegistered(def.getId()),
                "Agent should be auto-registered by executeOneShot()");
    }

    @Test
    @DisplayName("executeOneShot() should not re-register already registered agent")
    void testExecuteOneShotAlreadyRegistered() {
        AgentDefinition def = makeDef("oneshot-existing-" + UUID.randomUUID());
        runtime.register(def);

        // Should not throw on second registration
        FunctionCallResult result = runtime.executeOneShot(
                def, completeContext(), "test", mockLlmCaller(), null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    // ---------------------------------------------------------------
    // Test: AgentRuntime.sendMessage() peer communication flow
    // Validates: Requirements 20.2
    // ---------------------------------------------------------------

    @Test
    @DisplayName("sendMessage() REQUEST should route to target and return RESPONSE")
    void testSendMessageRequest() {
        String agentA = "peer-a-" + UUID.randomUUID();
        String agentB = "peer-b-" + UUID.randomUUID();
        runtime.register(makeDef(agentA));
        runtime.register(makeDef(agentB));

        // Create sessions for both agents
        runtime.createSession(agentA, completeContext());
        AgentSession sessionB = runtime.createSession(agentB, completeContext());

        AgentMessage request = AgentMessage.builder()
                .sourceAgentId(agentA)
                .targetAgentId(agentB)
                .messageType(AgentMessage.MessageType.REQUEST)
                .payload("Please review this code")
                .correlationId("corr-" + UUID.randomUUID())
                .build();

        AgentMessage response = runtime.sendMessage(request, mockLlmCaller());

        assertNotNull(response, "sendMessage() should return a response");
        assertEquals(AgentMessage.MessageType.RESPONSE, response.getMessageType(),
                "Response should be RESPONSE type");
        assertEquals(agentB, response.getSourceAgentId(),
                "Response source should be the target agent");
        assertEquals(agentA, response.getTargetAgentId(),
                "Response target should be the source agent");
        assertEquals(request.getCorrelationId(), response.getCorrelationId(),
                "CorrelationId should be preserved");
    }

    @Test
    @DisplayName("sendMessage() NOTIFY should return immediately without waiting")
    void testSendMessageNotify() {
        String agentA = "notify-a-" + UUID.randomUUID();
        String agentB = "notify-b-" + UUID.randomUUID();
        runtime.register(makeDef(agentA));
        runtime.register(makeDef(agentB));

        runtime.createSession(agentA, completeContext());
        runtime.createSession(agentB, completeContext());

        AgentMessage notify = AgentMessage.builder()
                .sourceAgentId(agentA)
                .targetAgentId(agentB)
                .messageType(AgentMessage.MessageType.NOTIFY)
                .payload("FYI: build completed")
                .correlationId("notify-" + UUID.randomUUID())
                .build();

        AgentMessage response = runtime.sendMessage(notify, mockLlmCaller());

        assertNotNull(response, "NOTIFY should return a response");
        assertEquals(AgentMessage.MessageType.RESPONSE, response.getMessageType());
        assertTrue(response.getPayload().contains("NOTIFY delivered"),
                "NOTIFY response should confirm delivery, got: " + response.getPayload());
    }

    @Test
    @DisplayName("sendMessage() to non-existent target should return error response, not throw")
    void testSendMessageNonExistentTarget() {
        String agentA = "msg-src-" + UUID.randomUUID();
        runtime.register(makeDef(agentA));
        runtime.createSession(agentA, completeContext());

        AgentMessage request = AgentMessage.builder()
                .sourceAgentId(agentA)
                .targetAgentId("nonexistent-agent")
                .messageType(AgentMessage.MessageType.REQUEST)
                .payload("hello")
                .correlationId("corr-missing")
                .build();

        // Should NOT throw — returns error response per Requirement 20.7
        AgentMessage response = runtime.sendMessage(request, mockLlmCaller());

        assertNotNull(response);
        assertEquals(AgentMessage.MessageType.RESPONSE, response.getMessageType());
        assertTrue(response.getPayload().contains("not found") || response.getPayload().contains("destroyed"),
                "Error response should indicate target not found, got: " + response.getPayload());
    }

    // ---------------------------------------------------------------
    // Test: Backward compatibility — FunctionCallOrchestrator unchanged
    // Validates: Requirements 10.3, 10.4, 10.5, 10.6
    // ---------------------------------------------------------------

    @Test
    @DisplayName("FunctionCallOrchestrator.LlmCaller interface should still be a functional interface")
    void testLlmCallerFunctionalInterface() {
        // Verify LlmCaller can be used as a lambda — this is a compile-time check
        // that also runs at runtime to confirm the interface hasn't changed
        com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.LlmCaller caller = request -> "response";
        assertNotNull(caller);
        // Verify it's callable
        assertNotNull(caller.getClass());
    }

    @Test
    @DisplayName("AgentRuntime should not modify FunctionCallOrchestrator public API")
    void testFunctionCallOrchestratorApiUnchanged() {
        // Verify key methods still exist on FunctionCallOrchestrator via reflection
        Class<?> fcoClass = com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.class;

        // execute(FunctionCallRequest, McpContext, LlmCaller) should exist
        assertDoesNotThrow(() -> fcoClass.getMethod("execute",
                com.wmsay.gpt4_lll.fc.model.FunctionCallRequest.class,
                McpContext.class,
                com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.LlmCaller.class),
                "FunctionCallOrchestrator.execute(request, context, llmCaller) should still exist");

        // execute with ProgressCallback should exist
        assertDoesNotThrow(() -> fcoClass.getMethod("execute",
                com.wmsay.gpt4_lll.fc.model.FunctionCallRequest.class,
                McpContext.class,
                com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.LlmCaller.class,
                com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.ProgressCallback.class),
                "FunctionCallOrchestrator.execute(request, context, llmCaller, callback) should still exist");
    }

    // ---------------------------------------------------------------
    // Test: fc.agent package has NO com.intellij.* imports
    // Validates: Architecture constraint — Agent core layer is pure Java
    // ---------------------------------------------------------------

    @Test
    @DisplayName("fc.agent package source files should have NO com.intellij.* imports")
    void testNoIntellijImportsInAgentPackage() throws IOException {
        Path agentPackageDir = Paths.get("src/main/java/com/wmsay/gpt4_lll/fc/agent");

        // Skip if running in CI or directory doesn't exist
        if (!Files.exists(agentPackageDir)) {
            // Fallback: try relative to working directory
            agentPackageDir = Paths.get(System.getProperty("user.dir"),
                    "src/main/java/com/wmsay/gpt4_lll/fc/agent");
        }
        if (!Files.exists(agentPackageDir)) {
            System.out.println("WARN: Agent package directory not found, skipping import check. " +
                    "Tried: " + agentPackageDir);
            return;
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(agentPackageDir)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaFile -> {
                        try {
                            List<String> lines = Files.readAllLines(javaFile);
                            for (int i = 0; i < lines.size(); i++) {
                                String line = lines.get(i).trim();
                                if (line.startsWith("import com.intellij")) {
                                    violations.add(javaFile.getFileName() + ":" + (i + 1) + " → " + line);
                                }
                            }
                        } catch (IOException e) {
                            violations.add(javaFile.getFileName() + ": failed to read — " + e.getMessage());
                        }
                    });
        }

        assertTrue(violations.isEmpty(),
                "fc.agent package should have NO com.intellij.* imports, but found:\n" +
                        String.join("\n", violations));
    }

    // ---------------------------------------------------------------
    // Test: Session lifecycle integration
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Full lifecycle: register → createSession → send → destroySession")
    void testFullLifecycle() {
        String agentId = "lifecycle-" + UUID.randomUUID();
        AgentDefinition def = makeDef(agentId);

        // Register
        runtime.register(def);
        assertTrue(runtime.isRegistered(agentId));

        // Create session
        AgentSession session = runtime.createSession(agentId, completeContext());
        assertNotNull(session);
        assertEquals(SessionState.CREATED, session.getState());
        assertEquals(1, runtime.getActiveSessionCount());

        // Send message
        FunctionCallResult result = runtime.send(
                session.getSessionId(), "test", mockLlmCaller(), null);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(SessionState.COMPLETED, session.getState());

        // Destroy session
        runtime.destroySession(session.getSessionId());
        assertEquals(SessionState.DESTROYED, session.getState());
        assertEquals(0, runtime.getActiveSessionCount());
        assertNull(runtime.getSession(session.getSessionId()));
    }

    @Test
    @DisplayName("shutdown() should destroy all active sessions")
    void testShutdownDestroysAllSessions() {
        String agentId = "shutdown-test-" + UUID.randomUUID();
        runtime.register(makeDef(agentId));

        List<AgentSession> sessions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sessions.add(runtime.createSession(agentId, completeContext()));
        }
        assertEquals(3, runtime.getActiveSessionCount());

        runtime.shutdown();

        assertEquals(0, runtime.getActiveSessionCount());
        for (AgentSession s : sessions) {
            assertEquals(SessionState.DESTROYED, s.getState());
        }
    }
}
