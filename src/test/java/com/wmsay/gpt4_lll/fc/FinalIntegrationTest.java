package com.wmsay.gpt4_lll.fc;

import com.wmsay.gpt4_lll.fc.config.ConfigLoader;
import com.wmsay.gpt4_lll.fc.config.ExtensionLoader;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.execution.UserApprovalManager;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.*;
import com.wmsay.gpt4_lll.fc.validation.SecurityValidator;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.*;
import com.wmsay.gpt4_lll.model.ChatContent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final end-to-end integration test for the Function Calling framework.
 * Wires up ALL components together and verifies the complete pipeline:
 *
 * <ul>
 *   <li>ConfigLoader loads configuration</li>
 *   <li>ExtensionLoader loads SPI extensions (including SecurityValidator)</li>
 *   <li>FunctionCallOrchestrator executes multi-turn conversations</li>
 *   <li>SecurityValidator blocks path traversal attempts</li>
 *   <li>DegradationManager tracks parse failures</li>
 *   <li>ObservabilityManager records session metrics</li>
 * </ul>
 *
 * Validates: All requirements (final integration verification)
 */
class FinalIntegrationTest {

    private static final String ECHO_TOOL = "final_integration_echo";
    private static final String FILE_TOOL = "final_integration_file_read";

    private ObservabilityManager observability;
    private ValidationEngine validationEngine;
    private ErrorHandler errorHandler;
    private ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        observability = new ObservabilityManager();
        validationEngine = new ValidationEngine();
        errorHandler = new ErrorHandler();
        executionEngine = new ExecutionEngine(
                new RetryStrategy(), new UserApprovalManager(), Executors.newSingleThreadExecutor());

        // Register test tools
        McpToolRegistry.register(new EchoTool());
        McpToolRegistry.register(new FileReadTool());
    }

    @AfterEach
    void tearDown() {
        executionEngine.shutdown();
    }

    // ========================================================================
    // 1. Full pipeline: ConfigLoader → ExtensionLoader → Orchestrator
    //    Loads config, registers SecurityValidator, runs a multi-turn conversation.
    // ========================================================================

    @Test
    void fullPipeline_configAndExtensions_multiTurnConversation() {
        // 1. Load config (no file → defaults)
        ConfigLoader configLoader = new ConfigLoader(name -> null);
        FunctionCallConfig config = configLoader.loadFromFile(null);
        assertNotNull(config);
        assertEquals(20, config.getMaxRounds());
        assertTrue(config.isEnableFunctionCalling());

        // 2. Load SPI extensions (SecurityValidator registered manually for test)
        SecurityValidator securityValidator = new SecurityValidator();
        validationEngine.registerCustomValidator(securityValidator);
        ExtensionLoader.loadAll(validationEngine, errorHandler);

        // 3. Build orchestrator with all components
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        // 4. Mock LLM: round 1 = tool call, round 2 = plain text
        AtomicInteger callCount = new AtomicInteger(0);
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                return "Let me echo something.\n\n"
                        + "```tool_call\n"
                        + "{\"id\":\"call_final_1\",\"name\":\"" + ECHO_TOOL + "\","
                        + "\"parameters\":{\"input\":\"hello from final test\"}}\n"
                        + "```";
            }
            return "Echo completed successfully. All done.";
        };

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        // Verify success
        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertTrue(result.getContent().contains("All done"));
        assertNotNull(result.getSessionId());

        // Verify tool call history
        assertEquals(1, result.getToolCallHistory().size());
        ToolCallResult toolResult = result.getToolCallHistory().get(0);
        assertEquals(ECHO_TOOL, toolResult.getToolName());
        assertEquals(ToolCallResult.ResultStatus.SUCCESS, toolResult.getStatus());

        // Verify LLM called exactly twice
        assertEquals(2, callCount.get());
    }

    // ========================================================================
    // 2. SecurityValidator blocks path traversal via the orchestrator pipeline
    // ========================================================================

    @Test
    void securityValidator_blocksPathTraversal_throughOrchestrator() {
        // Register SecurityValidator
        SecurityValidator securityValidator = new SecurityValidator();
        validationEngine.registerCustomValidator(securityValidator);

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);

        // LLM tries path traversal on round 1, then gives up on round 2
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                return "```tool_call\n"
                        + "{\"id\":\"call_sec_1\",\"name\":\"" + FILE_TOOL + "\","
                        + "\"parameters\":{\"path\":\"../../etc/passwd\"}}\n"
                        + "```";
            }
            return "I see the path was blocked. Let me answer directly.";
        };

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertTrue(result.getContent().contains("answer directly"));

        // The path traversal tool call should have been rejected by validation
        assertEquals(1, result.getToolCallHistory().size());
        ToolCallResult blocked = result.getToolCallHistory().get(0);
        assertEquals(ToolCallResult.ResultStatus.VALIDATION_ERROR, blocked.getStatus());
        assertEquals(FILE_TOOL, blocked.getToolName());
    }

    // ========================================================================
    // 3. DegradationManager tracks failures and disables function calling
    // ========================================================================

    @Test
    void degradationManager_tracksParseFails_disablesFunctionCalling() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        // Simulate enough parse failures to trigger auto-disable
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            degradationManager.recordParseAttempt(false);
        }
        assertTrue(degradationManager.isDisabled());
        assertTrue(degradationManager.getFailureRate() > DegradationManager.FAILURE_RATE_THRESHOLD);

        // Build orchestrator with disabled degradation manager
        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger llmCallCount = new AtomicInteger(0);
        FunctionCallResult result = orchestrator.execute(buildRequest(5), null, req -> {
            llmCallCount.incrementAndGet();
            return "Should not be called";
        });

        // Should return DEGRADED without calling LLM
        assertEquals(FunctionCallResult.ResultType.DEGRADED, result.getType());
        assertTrue(result.getContent().contains("disabled"));
        assertEquals(0, llmCallCount.get());

        // Reset and verify recovery
        degradationManager.reset();
        assertFalse(degradationManager.isDisabled());
        assertEquals(0, degradationManager.getTotalAttempts());
    }

    // ========================================================================
    // 4. ObservabilityManager records session metrics end-to-end
    // ========================================================================

    @Test
    void observabilityManager_recordsSessionMetrics_endToEnd() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round <= 2) {
                return "```tool_call\n"
                        + "{\"id\":\"call_obs_" + round + "\",\"name\":\"" + ECHO_TOOL + "\","
                        + "\"parameters\":{\"input\":\"metrics round " + round + "\"}}\n"
                        + "```";
            }
            return "Metrics test complete.";
        };

        FunctionCallResult result = orchestrator.execute(buildRequest(10), testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertEquals(2, result.getToolCallHistory().size());

        // Verify observability recorded metrics
        PerformanceMetrics metrics = observability.getMetrics();
        assertNotNull(metrics);
        // At least 2 tool calls should have been recorded
        assertTrue(metrics.getTotalToolCalls() >= 2,
                "Should have recorded at least 2 tool calls, got " + metrics.getTotalToolCalls());
    }

    // ========================================================================
    // 5. Multi-protocol: OpenAI adapter end-to-end through orchestrator
    // ========================================================================

    @Test
    void openaiAdapter_endToEnd_nativeProtocolNoParseTracking() {
        OpenAIProtocolAdapter adapter = new OpenAIProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                return "{\"tool_calls\":[{\"id\":\"call_oai_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"" + ECHO_TOOL + "\","
                        + "\"arguments\":\"{\\\"input\\\":\\\"openai pipeline\\\"}\"}}]}";
            }
            return "OpenAI pipeline complete.";
        };

        FunctionCallResult result = orchestrator.execute(buildRequest(5), testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertEquals(1, result.getToolCallHistory().size());
        assertEquals(ToolCallResult.ResultStatus.SUCCESS,
                result.getToolCallHistory().get(0).getStatus());

        // Native adapter should NOT record parse attempts in DegradationManager
        assertEquals(0, degradationManager.getTotalAttempts());
    }

    // ========================================================================
    // 6. Error recovery: non-existent tool → error feedback → LLM recovers
    // ========================================================================

    @Test
    void errorRecovery_nonExistentTool_llmReceivesErrorAndRecovers() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                return "```tool_call\n"
                        + "{\"id\":\"call_nf\",\"name\":\"totally_nonexistent_tool\","
                        + "\"parameters\":{\"q\":\"test\"}}\n"
                        + "```";
            }
            return "Tool not found, answering directly.";
        };

        FunctionCallResult result = orchestrator.execute(buildRequest(5), testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertTrue(result.getContent().contains("answering directly"));
        assertEquals(1, result.getToolCallHistory().size());
        assertNotEquals(ToolCallResult.ResultStatus.SUCCESS,
                result.getToolCallHistory().get(0).getStatus());
        assertEquals(2, callCount.get());
    }

    // ========================================================================
    // 7. Max rounds exceeded
    // ========================================================================

    @Test
    void maxRoundsExceeded_alwaysToolCalls_returnsMaxRoundsExceeded() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        // LLM always returns a tool call
        FunctionCallOrchestrator.LlmCaller mockLlm = request ->
                "```tool_call\n"
                        + "{\"id\":\"call_loop\",\"name\":\"" + ECHO_TOOL + "\","
                        + "\"parameters\":{\"input\":\"loop\"}}\n"
                        + "```";

        // maxRounds = 3
        FunctionCallRequest request = buildRequest(3);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.MAX_ROUNDS_EXCEEDED, result.getType());
        assertEquals(3, result.getToolCallHistory().size());
    }

    // ========================================================================
    // 8. Config loading with env var override
    // ========================================================================

    @Test
    void configLoader_envVarOverride_mergesCorrectly() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("LLL_FC_ENABLED", "false");
        envVars.put("LLL_FC_LOG_LEVEL", "DEBUG");
        envVars.put("LLL_FC_MAX_ROUNDS", "10");

        ConfigLoader loader = new ConfigLoader(envVars::get);
        FunctionCallConfig config = loader.loadFromEnv();

        assertFalse(config.isEnableFunctionCalling());
        assertEquals(FunctionCallConfig.LogLevel.DEBUG, config.getLogLevel());
        assertEquals(10, config.getMaxRounds());
    }

    @Test
    void configLoader_fileWithEnvOverride_envTakesPrecedence(@TempDir Path tempDir) throws IOException {
        // Write a config file
        Path configFile = tempDir.resolve("fc-config.json");
        Files.writeString(configFile, "{\"maxRounds\": 15, \"logLevel\": \"WARN\"}");

        // Env overrides maxRounds
        Map<String, String> envVars = new HashMap<>();
        envVars.put("LLL_FC_MAX_ROUNDS", "5");

        ConfigLoader loader = new ConfigLoader(envVars::get);
        FunctionCallConfig config = loader.load(configFile);

        // Env override wins
        assertEquals(5, config.getMaxRounds());
        // File value preserved for non-overridden fields
        assertEquals(FunctionCallConfig.LogLevel.WARN, config.getLogLevel());
    }

    // ========================================================================
    // 9. SecurityValidator standalone: command injection blocked
    // ========================================================================

    @Test
    void securityValidator_blocksCommandInjection() {
        SecurityValidator validator = new SecurityValidator();

        // Path traversal
        ValidationResult pathResult = validator.validate("read_file",
                Map.of("path", "../../../etc/shadow"));
        assertFalse(pathResult.isValid());

        // Command injection
        ValidationResult cmdResult = validator.validate("exec_command",
                Map.of("command", "ls; rm -rf /"));
        assertFalse(cmdResult.isValid());

        // Safe parameters pass
        ValidationResult safeResult = validator.validate("read_file",
                Map.of("path", "src/main/java/App.java"));
        assertTrue(safeResult.isValid());
    }

    // ========================================================================
    // 10. Complete pipeline with SecurityValidator blocking absolute path
    // ========================================================================

    @Test
    void securityValidator_blocksAbsolutePath_throughOrchestrator() {
        SecurityValidator securityValidator = new SecurityValidator();
        validationEngine.registerCustomValidator(securityValidator);

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                // Absolute path attempt
                return "```tool_call\n"
                        + "{\"id\":\"call_abs\",\"name\":\"" + FILE_TOOL + "\","
                        + "\"parameters\":{\"path\":\"/etc/passwd\"}}\n"
                        + "```";
            }
            return "Absolute path blocked. Using relative path instead.";
        };

        FunctionCallResult result = orchestrator.execute(buildRequest(5), testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertEquals(1, result.getToolCallHistory().size());
        assertEquals(ToolCallResult.ResultStatus.VALIDATION_ERROR,
                result.getToolCallHistory().get(0).getStatus());
    }

    // ========================================================================
    // 11. Anthropic adapter end-to-end
    // ========================================================================

    @Test
    void anthropicAdapter_endToEnd_nativeProtocol() {
        AnthropicProtocolAdapter adapter = new AnthropicProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                return "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_ant_1\","
                        + "\"name\":\"" + ECHO_TOOL + "\","
                        + "\"input\":{\"input\":\"anthropic pipeline\"}}]}";
            }
            return "Anthropic pipeline complete.";
        };

        FunctionCallResult result = orchestrator.execute(buildRequest(5), testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertEquals(1, result.getToolCallHistory().size());
        assertEquals(ToolCallResult.ResultStatus.SUCCESS,
                result.getToolCallHistory().get(0).getStatus());

        // Native adapter should NOT record parse attempts
        assertEquals(0, degradationManager.getTotalAttempts());
    }

    // ========================================================================
    // 12. Protocol switching via ProtocolAdapterRegistry
    // ========================================================================

    @Test
    void protocolAdapterRegistry_selectsCorrectAdapter() {
        ProtocolAdapter openai = ProtocolAdapterRegistry.getAdapter("openai");
        assertTrue(openai instanceof OpenAIProtocolAdapter);
        assertTrue(openai.supportsNativeFunctionCalling());

        ProtocolAdapter anthropic = ProtocolAdapterRegistry.getAdapter("claude-3");
        assertTrue(anthropic instanceof AnthropicProtocolAdapter);
        assertTrue(anthropic.supportsNativeFunctionCalling());

        ProtocolAdapter fallback = ProtocolAdapterRegistry.getAdapter("unknown-provider");
        assertTrue(fallback instanceof MarkdownProtocolAdapter);
        assertFalse(fallback.supportsNativeFunctionCalling());
    }

    // ========================================================================
    // Helpers and stub tools
    // ========================================================================

    private McpContext testContext() {
        return new McpContext(null, null, Paths.get("."));
    }

    private FunctionCallRequest buildRequest(int maxRounds) {
        ChatContent chatContent = new ChatContent();
        return FunctionCallRequest.builder()
                .chatContent(chatContent)
                .availableTools(Collections.emptyList())
                .maxRounds(maxRounds)
                .build();
    }

    /** Simple echo tool for integration testing. */
    private static class EchoTool implements McpTool {
        @Override public String name() { return ECHO_TOOL; }
        @Override public String description() { return "Echo tool for final integration test"; }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> inputField = new LinkedHashMap<>();
            inputField.put("type", "string");
            inputField.put("description", "Input to echo");
            return Map.of("input", inputField);
        }
        @Override public McpToolResult execute(McpContext context, Map<String, Object> params) {
            return McpToolResult.text("Echo: " + params.getOrDefault("input", ""));
        }
    }

    /** File read tool with a "path" parameter (triggers SecurityValidator checks). */
    private static class FileReadTool implements McpTool {
        @Override public String name() { return FILE_TOOL; }
        @Override public String description() { return "File read tool for security testing"; }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> pathField = new LinkedHashMap<>();
            pathField.put("type", "string");
            pathField.put("description", "File path to read");
            pathField.put("required", true);
            return Map.of("path", pathField);
        }
        @Override public McpToolResult execute(McpContext context, Map<String, Object> params) {
            return McpToolResult.text("Content of: " + params.getOrDefault("path", ""));
        }
    }
}
