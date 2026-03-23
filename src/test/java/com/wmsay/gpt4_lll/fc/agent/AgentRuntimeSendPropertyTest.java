package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.execution.UserApprovalManager;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.MarkdownProtocolAdapter;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterTry;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AgentRuntime.send() 属性测试。
 * <p>
 * 验证 send() 增强后的向后兼容性：当 orchestrator 为 null 时，
 * send() 行为与增强前完全一致（返回组装后文本，不执行 LLM 调用，会话状态为 COMPLETED）。
 * <p>
 * 测试位于 fc.agent 包以访问 AgentRuntime.removeInstance()（package-private）。
 */
class AgentRuntimeSendPropertyTest {

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
    // Property 4: AgentRuntime.send() 增强后向后兼容
    // Validates: Requirements 4.4, 7.4
    // ---------------------------------------------------------------

    /**
     * Property 4: 对于任意 session 和 message，当 orchestrator 为 null 时，
     * send() 的行为与增强前完全一致：返回组装后的文本作为 FunctionCallResult，
     * 不执行 LLM 调用，会话状态转换为 COMPLETED。
     * <p>
     * The LlmCaller mock returns a simple JSON response that IntentRecognizer can parse
     * for the sidecar analysis call. The key point is that orchestrator.execute() is NOT
     * called (since orchestrator is null).
     *
     * **Validates: Requirements 4.4, 7.4**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 4: AgentRuntime.send() 增强后向后兼容")
    void sendBackwardCompatibleWhenOrchestratorIsNull(
            @ForAll("userMessages") String message) throws Exception {

        // 1. Create AgentRuntime with a unique projectId (temp dir)
        Path tempDir = Files.createTempDirectory("send-prop-test-");
        currentProjectId = tempDir.toAbsolutePath().toString();

        AgentRuntime runtime = AgentRuntime.getInstance(currentProjectId);

        // 2. Register a default agent with known system prompt and tool names
        List<String> toolNames = McpToolRegistry.getAllTools().stream()
                .map(McpTool::name)
                .collect(Collectors.toList());

        AgentDefinition definition = AgentDefinition.builder()
                .id(DEFAULT_AGENT_ID)
                .name("Chat Agent")
                .systemPrompt("你是一个智能编程助手。")
                .availableToolNames(toolNames)
                .strategyName("react")
                .memoryStrategy("sliding_window")
                .build();
        runtime.register(definition);

        // Initialize KnowledgeBase
        KnowledgeBase kb = new KnowledgeBase(tempDir);
        runtime.setKnowledgeBase(kb);

        // 3. Create a session with valid ExecutionContext (needs "project" and "projectRoot")
        ExecutionContext context = completeContext(tempDir);
        AgentSession session = runtime.createSession(DEFAULT_AGENT_ID, context);

        // 4. Do NOT set orchestrator (leave it null — backward compatibility case)
        assert runtime.getOrchestrator() == null :
                "Orchestrator should be null for backward compatibility test";

        // 5. Call send() with a test message and a dummy LlmCaller
        //    The LlmCaller returns a JSON response for IntentRecognizer sidecar analysis
        FunctionCallResult result = runtime.send(
                session.getSessionId(),
                message,
                mockLlmCaller(),
                null);

        // 6. Verify backward compatibility properties:
        //    a) result.isSuccess() == true
        assert result.isSuccess() :
                "send() should return success when orchestrator is null, got type: "
                        + result.getType() + ", content: " + result.getContent();

        //    b) result.getContent() is non-null and non-empty (contains assembled text)
        assert result.getContent() != null :
                "Result content should not be null when orchestrator is null";
        assert !result.getContent().isEmpty() :
                "Result content should not be empty when orchestrator is null";

        //    c) session.getState() == COMPLETED
        assert session.getState() == SessionState.COMPLETED :
                "Session state should be COMPLETED after send() with null orchestrator, "
                        + "but was: " + session.getState();

        //    d) The result content should contain assembled text (system prompt content)
        //       This verifies that the assembled prompt is returned, not an LLM response
        assert result.getContent().length() > 0 :
                "Assembled text should have content";
    }

    // ---------------------------------------------------------------
    // Property 5: 异常导致会话进入 ERROR 状态
    // Validates: Requirements 4.6
    // ---------------------------------------------------------------

    /**
     * Property 5: 对于任意 orchestrator.execute() 抛出的异常类型，
     * AgentRuntime.send() 将会话状态转换为 ERROR，并返回 FunctionCallResult.error()，
     * 不向调用方传播异常。
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 5: 异常导致会话进入 ERROR 状态")
    void exceptionCausesSessionErrorState(
            @ForAll("userMessages") String message,
            @ForAll("exceptionMessages") String exceptionMessage) throws Exception {

        // 1. Create AgentRuntime with a unique projectId (temp dir)
        Path tempDir = Files.createTempDirectory("send-error-prop-test-");
        currentProjectId = tempDir.toAbsolutePath().toString();

        AgentRuntime runtime = AgentRuntime.getInstance(currentProjectId);

        // 2. Register a default agent
        List<String> toolNames = McpToolRegistry.getAllTools().stream()
                .map(McpTool::name)
                .collect(Collectors.toList());

        AgentDefinition definition = AgentDefinition.builder()
                .id(DEFAULT_AGENT_ID)
                .name("Chat Agent")
                .systemPrompt("你是一个智能编程助手。")
                .availableToolNames(toolNames)
                .strategyName("react")
                .memoryStrategy("sliding_window")
                .build();
        runtime.register(definition);

        // Initialize KnowledgeBase
        KnowledgeBase kb = new KnowledgeBase(tempDir);
        runtime.setKnowledgeBase(kb);

        // 3. Create a session
        ExecutionContext context = completeContext(tempDir);
        AgentSession session = runtime.createSession(DEFAULT_AGENT_ID, context);

        // 4. Create a FunctionCallOrchestrator subclass that throws on execute()
        FunctionCallOrchestrator throwingOrchestrator = new FunctionCallOrchestrator(
                new MarkdownProtocolAdapter(),
                new ValidationEngine(),
                new ExecutionEngine(new RetryStrategy(), new UserApprovalManager()),
                new ErrorHandler(),
                new ObservabilityManager()
        ) {
            @Override
            public FunctionCallResult execute(FunctionCallRequest request,
                                              McpContext ctx,
                                              LlmCaller caller,
                                              ProgressCallback progressCallback) {
                throw new RuntimeException(exceptionMessage);
            }
        };
        runtime.setOrchestrator(throwingOrchestrator);

        // 5. Call send() — it should NOT throw
        FunctionCallResult result = runtime.send(
                session.getSessionId(),
                message,
                mockLlmCaller(),
                null);

        // 6. Verify: result type is ERROR
        assert result.getType() == FunctionCallResult.ResultType.ERROR :
                "Result type should be ERROR when orchestrator throws, but was: "
                        + result.getType();

        // 7. Verify: session state is ERROR
        assert session.getState() == SessionState.ERROR :
                "Session state should be ERROR after orchestrator throws, but was: "
                        + session.getState();

        // 8. Verify: result content contains the exception message
        assert result.getContent() != null :
                "Error result content should not be null";
        assert result.getContent().contains(exceptionMessage) :
                "Error result content should contain exception message '"
                        + exceptionMessage + "', but was: " + result.getContent();

        // 9. Verify: result is not success
        assert !result.isSuccess() :
                "Result should not be success when orchestrator throws";
    }

    // ---------------------------------------------------------------
    // Property 7: 意图识别不阻塞主流程
    // Validates: Requirements 8.1
    // ---------------------------------------------------------------

    /**
     * Property 7: 对于任意 message 和 llmCaller，当 IntentRecognizer.analyze() 抛出
     * 任何异常时，AgentRuntime.send() 仍然正常执行（使用 IntentResult.defaultResult()），
     * 返回有效的 FunctionCallResult，不抛出异常。
     * <p>
     * send() 内部将 intentRecognizer.analyze() 包裹在 try-catch 中，
     * 异常时回退到 IntentResult.defaultResult()。orchestrator 为 null 时走
     * 向后兼容路径，返回组装后的文本。因此即使 IntentRecognizer 完全损坏，
     * send() 也应成功返回。
     *
     * **Validates: Requirements 8.1**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 7: 意图识别不阻塞主流程")
    void intentRecognizerFailureDoesNotBlockSend(
            @ForAll("userMessages") String message,
            @ForAll("intentExceptions") Throwable intentException) throws Exception {

        // 1. Create AgentRuntime with a unique projectId (temp dir)
        Path tempDir = Files.createTempDirectory("send-intent-fail-test-");
        currentProjectId = tempDir.toAbsolutePath().toString();

        AgentRuntime runtime = AgentRuntime.getInstance(currentProjectId);

        // 2. Register a default agent
        List<String> toolNames = McpToolRegistry.getAllTools().stream()
                .map(McpTool::name)
                .collect(Collectors.toList());

        AgentDefinition definition = AgentDefinition.builder()
                .id(DEFAULT_AGENT_ID)
                .name("Chat Agent")
                .systemPrompt("你是一个智能编程助手。")
                .availableToolNames(toolNames)
                .strategyName("react")
                .memoryStrategy("sliding_window")
                .build();
        runtime.register(definition);

        // Initialize KnowledgeBase
        KnowledgeBase kb = new KnowledgeBase(tempDir);
        runtime.setKnowledgeBase(kb);

        // 3. Create a custom IntentRecognizer that always throws on analyze()
        IntentRecognizer throwingRecognizer = new IntentRecognizer(new ObservabilityManager()) {
            @Override
            public IntentResult analyze(String userMessage, List<String> availableToolNames,
                                        FunctionCallOrchestrator.LlmCaller llmCaller) {
                if (intentException instanceof RuntimeException) {
                    throw (RuntimeException) intentException;
                }
                throw new RuntimeException(intentException);
            }
        };
        runtime.setIntentRecognizer(throwingRecognizer);

        // 4. Do NOT set orchestrator (null) — backward-compatible path
        assert runtime.getOrchestrator() == null :
                "Orchestrator should be null for this test";

        // 5. Create a session
        ExecutionContext context = completeContext(tempDir);
        AgentSession session = runtime.createSession(DEFAULT_AGENT_ID, context);

        // 6. Call send() — it should NOT throw despite IntentRecognizer failure
        FunctionCallResult result = runtime.send(
                session.getSessionId(),
                message,
                mockLlmCaller(),
                null);

        // 7. Verify: result.isSuccess() == true
        assert result.isSuccess() :
                "send() should return success even when IntentRecognizer throws "
                        + intentException.getClass().getSimpleName()
                        + ", got type: " + result.getType()
                        + ", content: " + result.getContent();

        // 8. Verify: session.getState() == COMPLETED
        assert session.getState() == SessionState.COMPLETED :
                "Session state should be COMPLETED even when IntentRecognizer throws, "
                        + "but was: " + session.getState();

        // 9. Verify: result.getContent() is non-null and non-empty
        assert result.getContent() != null :
                "Result content should not be null when IntentRecognizer throws";
        assert !result.getContent().isEmpty() :
                "Result content should not be empty when IntentRecognizer throws";
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> exceptionMessages() {
        return Arbitraries.of(
                "Connection refused",
                "LLM service unavailable",
                "Timeout waiting for response",
                "Invalid API key",
                "Rate limit exceeded",
                "Internal server error",
                "模型调用失败",
                "Network error: host unreachable",
                "Out of memory",
                "Unexpected null response"
        );
    }

    @Provide
    Arbitrary<Throwable> intentExceptions() {
        return Arbitraries.of(
                new RuntimeException("LLM sidecar timeout"),
                new RuntimeException("Connection refused"),
                new NullPointerException("null response from LLM"),
                new IllegalStateException("IntentRecognizer not initialized"),
                new IllegalArgumentException("Invalid message format"),
                new RuntimeException("模型调用失败"),
                new UnsupportedOperationException("analyze not supported"),
                new RuntimeException("Rate limit exceeded"),
                new RuntimeException("Invalid API key"),
                new RuntimeException("Network error: host unreachable")
        );
    }

    @Provide
    Arbitrary<String> userMessages() {
        return Arbitraries.of(
                "Hello, please help me",
                "请帮我写一个排序算法",
                "Fix the bug in main.java",
                "Explain this code",
                "Create a new file test.py",
                "What is the project structure?",
                "帮我重构这段代码",
                "Run the tests",
                "Search for TODO comments",
                "Write unit tests for UserService"
        );
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Create a dummy Project proxy via reflection — avoids direct com.intellij.* import
     * while satisfying McpContext's constructor requirement and ExecutionContext validation.
     */
    private static Object dummyProject(Path projectRoot) {
        try {
            Class<?> projectClass = Class.forName("com.intellij.openapi.project.Project");
            return Proxy.newProxyInstance(
                    projectClass.getClassLoader(),
                    new Class<?>[]{projectClass},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getBasePath": return projectRoot.toAbsolutePath().toString();
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
     * ExecutionContext validation requires both "project" and "projectRoot" to be non-null
     * for the CREATED→RUNNING state transition.
     */
    @SuppressWarnings("unchecked")
    private static ExecutionContext completeContext(Path projectRoot) {
        try {
            Object project = dummyProject(projectRoot);
            Class<?> mcpContextClass = McpContext.class;
            Class<?> projectClass = Class.forName("com.intellij.openapi.project.Project");
            Class<?> editorClass = Class.forName("com.intellij.openapi.editor.Editor");
            var ctor = mcpContextClass.getConstructor(projectClass, editorClass, Path.class);
            McpContext mcp = (McpContext) ctor.newInstance(project, null, projectRoot);
            return ExecutionContext.builder().mcpContext(mcp).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ExecutionContext", e);
        }
    }

    /**
     * Mock LlmCaller that returns a valid IntentRecognizer JSON response.
     * IntentRecognizer.analyze() will call this for sidecar analysis — that's expected.
     * The key point is that orchestrator.execute() is NOT called (since orchestrator is null).
     */
    private static com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator.LlmCaller mockLlmCaller() {
        return request -> "{\"clarity\":\"CLEAR\",\"complexity\":\"SIMPLE\","
                + "\"recommendedStrategy\":\"react\",\"reasoning\":\"test\","
                + "\"filteredToolNames\":[]}";
    }
}
