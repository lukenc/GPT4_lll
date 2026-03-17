package com.wmsay.gpt4_lll.fc;

import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.execution.UserApprovalManager;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.*;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.*;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Function Calling framework.
 * Tests end-to-end flows through FunctionCallOrchestrator with real components
 * and mock LLM responses via the LlmCaller functional interface.
 *
 * Validates: All requirements (integration verification)
 */
class IntegrationTest {

    private static final String TEST_TOOL_NAME = "integration_test_echo";

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

        // Register a test tool in McpToolRegistry for integration tests
        McpToolRegistry.register(new EchoMcpTool());
    }

    @AfterEach
    void tearDown() {
        executionEngine.shutdown();
    }

    // ========================================================================
    // Scenario 1: Full conversation flow
    // FunctionCallOrchestrator with MarkdownProtocolAdapter, mock LLM returns
    // a tool call on first round then plain text on second round.
    // ========================================================================

    @Test
    void fullConversationFlow_toolCallThenPlainText_returnsSuccessWithHistory() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);

        // Mock LLM: first call returns a tool call, second call returns plain text
        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                return "I'll use the echo tool.\n\n"
                        + "```tool_call\n"
                        + "{\"id\":\"call_1\",\"name\":\"" + TEST_TOOL_NAME + "\","
                        + "\"parameters\":{\"input\":\"hello world\"}}\n"
                        + "```";
            }
            return "The echo tool returned the result. Task complete.";
        };

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("Task complete"));
        assertNotNull(result.getSessionId());

        // Should have exactly 1 tool call in history
        assertEquals(1, result.getToolCallHistory().size());
        ToolCallResult toolResult = result.getToolCallHistory().get(0);
        assertEquals(TEST_TOOL_NAME, toolResult.getToolName());
        assertEquals(ToolCallResult.ResultStatus.SUCCESS, toolResult.getStatus());

        // LLM should have been called exactly 2 times
        assertEquals(2, callCount.get());
    }

    // ========================================================================
    // Scenario 2: Multi-round tool calls
    // Mock LLM returns tool calls for 3 rounds, then plain text.
    // ========================================================================

    @Test
    void multiRoundToolCalls_threeRoundsThenPlainText_collectsAllResults() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);

        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round <= 3) {
                return "```tool_call\n"
                        + "{\"id\":\"call_" + round + "\",\"name\":\"" + TEST_TOOL_NAME + "\","
                        + "\"parameters\":{\"input\":\"round " + round + "\"}}\n"
                        + "```";
            }
            return "All 3 rounds of tool calls completed successfully.";
        };

        FunctionCallRequest request = buildRequest(10);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertTrue(result.getContent().contains("3 rounds"));

        // Should have 3 tool call results in history
        assertEquals(3, result.getToolCallHistory().size());
        for (int i = 0; i < 3; i++) {
            ToolCallResult tcr = result.getToolCallHistory().get(i);
            assertEquals(TEST_TOOL_NAME, tcr.getToolName());
            assertEquals(ToolCallResult.ResultStatus.SUCCESS, tcr.getStatus());
        }

        // LLM called 4 times (3 tool call rounds + 1 final plain text)
        assertEquals(4, callCount.get());
    }

    // ========================================================================
    // Scenario 3: Protocol switching
    // ProtocolAdapterRegistry returns correct adapters for different providers.
    // ========================================================================

    @Test
    void protocolSwitching_openaiProvider_returnsOpenAIAdapter() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("openai");
        assertNotNull(adapter);
        assertTrue(adapter.supportsNativeFunctionCalling(),
                "OpenAI adapter should support native function calling");
        assertTrue(adapter instanceof OpenAIProtocolAdapter);
    }

    @Test
    void protocolSwitching_anthropicProvider_returnsAnthropicAdapter() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("claude-3");
        assertNotNull(adapter);
        assertTrue(adapter.supportsNativeFunctionCalling(),
                "Anthropic adapter should support native function calling");
        assertTrue(adapter instanceof AnthropicProtocolAdapter);
    }

    @Test
    void protocolSwitching_unknownProvider_fallsBackToMarkdown() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("unknown-provider-xyz");
        assertNotNull(adapter);
        assertFalse(adapter.supportsNativeFunctionCalling(),
                "Unknown provider should fall back to Markdown (non-native)");
        assertTrue(adapter instanceof MarkdownProtocolAdapter);
    }

    @Test
    void protocolSwitching_endToEnd_openaiAdapterParsesToolCalls() {
        // Verify OpenAI adapter can parse a realistic OpenAI-format response
        OpenAIProtocolAdapter openaiAdapter = new OpenAIProtocolAdapter();
        String openaiResponse = "{\"choices\":[{\"message\":{\"tool_calls\":["
                + "{\"id\":\"call_abc\",\"type\":\"function\","
                + "\"function\":{\"name\":\"" + TEST_TOOL_NAME + "\","
                + "\"arguments\":\"{\\\"input\\\":\\\"test\\\"}\"}}]}}]}";

        List<ToolCall> toolCalls = openaiAdapter.parseToolCalls(openaiResponse);
        assertEquals(1, toolCalls.size());
        assertEquals(TEST_TOOL_NAME, toolCalls.get(0).getToolName());
        assertEquals("call_abc", toolCalls.get(0).getCallId());
    }

    // ========================================================================
    // Scenario 4: Degradation flow
    // Use DegradationManager, record enough failures to disable, then verify
    // orchestrator returns DEGRADED.
    // ========================================================================

    @Test
    void degradationFlow_highFailureRate_orchestratorReturnsDegraded() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        // Record enough failures to trigger auto-disable
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            degradationManager.recordParseAttempt(false);
        }
        assertTrue(degradationManager.isDisabled(),
                "DegradationManager should be disabled after enough failures");

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        FunctionCallRequest request = buildRequest(5);

        // LLM should never be called because degradation check happens first
        AtomicInteger llmCallCount = new AtomicInteger(0);
        FunctionCallResult result = orchestrator.execute(request, null, req -> {
            llmCallCount.incrementAndGet();
            return "This should not be reached";
        });

        assertEquals(FunctionCallResult.ResultType.DEGRADED, result.getType());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("disabled"),
                "Degraded result should mention function calling is disabled");
        assertEquals(0, llmCallCount.get(),
                "LLM should not be called when function calling is disabled");
    }

    @Test
    void degradationFlow_resetAfterDisable_reEnablesFunctionCalling() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        // Disable via failures
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            degradationManager.recordParseAttempt(false);
        }
        assertTrue(degradationManager.isDisabled());

        // Reset and verify function calling works again
        degradationManager.reset();
        assertFalse(degradationManager.isDisabled());

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, null,
                req -> "Plain text after reset.");

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
    }

    // ========================================================================
    // Scenario 5: Error recovery
    // Mock LLM returns a tool call for a non-existent tool → ErrorHandler
    // generates error → tool result contains error → LLM gets error feedback
    // and returns plain text.
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
                // LLM tries to call a tool that doesn't exist
                return "```tool_call\n"
                        + "{\"id\":\"call_err\",\"name\":\"nonexistent_tool_xyz\","
                        + "\"parameters\":{\"query\":\"test\"}}\n"
                        + "```";
            }
            // After receiving the error feedback, LLM responds with plain text
            return "I see the tool doesn't exist. Let me answer directly instead.";
        };

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertTrue(result.getContent().contains("answer directly"));

        // Should have 1 tool call in history — the failed one
        assertEquals(1, result.getToolCallHistory().size());
        ToolCallResult failedCall = result.getToolCallHistory().get(0);
        // The tool call should have failed (either validation error for tool not found,
        // or execution error from ToolNotFoundException)
        assertNotEquals(ToolCallResult.ResultStatus.SUCCESS, failedCall.getStatus());

        // LLM was called twice: once with tool call, once with error feedback
        assertEquals(2, callCount.get());
    }

    @Test
    void errorRecovery_validationFailure_llmReceivesErrorAndRecovers() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        // Register a tool with a required parameter
        McpToolRegistry.register(new RequiredParamTool());

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);

        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                // LLM calls the tool but omits the required "query" parameter
                return "```tool_call\n"
                        + "{\"id\":\"call_v\",\"name\":\"integration_required_param_tool\","
                        + "\"parameters\":{}}\n"
                        + "```";
            }
            return "I see the parameter was missing. Here is my direct answer.";
        };

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());

        // The first tool call should have a validation error
        assertEquals(1, result.getToolCallHistory().size());
        ToolCallResult validationFailure = result.getToolCallHistory().get(0);
        assertEquals(ToolCallResult.ResultStatus.VALIDATION_ERROR, validationFailure.getStatus());

        assertEquals(2, callCount.get());
    }

    // ========================================================================
    // Additional integration: OpenAI adapter end-to-end with orchestrator
    // ========================================================================

    @Test
    void openaiAdapter_fullFlow_toolCallAndResponse() {
        OpenAIProtocolAdapter adapter = new OpenAIProtocolAdapter();
        DegradationManager degradationManager = new DegradationManager();

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler,
                observability, degradationManager);

        AtomicInteger callCount = new AtomicInteger(0);

        FunctionCallOrchestrator.LlmCaller mockLlm = request -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                // Return OpenAI-format tool call
                return "{\"tool_calls\":[{\"id\":\"call_openai_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"" + TEST_TOOL_NAME + "\","
                        + "\"arguments\":\"{\\\"input\\\":\\\"openai test\\\"}\"}}]}";
            }
            return "OpenAI flow complete.";
        };

        FunctionCallRequest request = buildRequest(5);
        FunctionCallResult result = orchestrator.execute(request, testContext(), mockLlm);

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertEquals(1, result.getToolCallHistory().size());
        assertEquals(ToolCallResult.ResultStatus.SUCCESS,
                result.getToolCallHistory().get(0).getStatus());

        // Native adapter should NOT record parse attempts in DegradationManager
        assertEquals(0, degradationManager.getTotalAttempts());
    }

    // ========================================================================
    // Helpers and stub implementations
    // ========================================================================

    /**
     * Create a test McpContext with null project/editor (safe for our test tools).
     */
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

    /**
     * A simple echo tool for integration testing.
     * Returns the "input" parameter value as text result.
     */
    private static class EchoMcpTool implements McpTool {
        @Override
        public String name() {
            return TEST_TOOL_NAME;
        }

        @Override
        public String description() {
            return "Echo tool for integration testing";
        }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> inputField = new LinkedHashMap<>();
            inputField.put("type", "string");
            inputField.put("description", "Input to echo back");
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("input", inputField);
            return schema;
        }

        @Override
        public McpToolResult execute(McpContext context, Map<String, Object> params) {
            String input = params.getOrDefault("input", "").toString();
            return McpToolResult.text("Echo: " + input);
        }
    }

    /**
     * A tool with a required parameter, used to test validation error recovery.
     */
    private static class RequiredParamTool implements McpTool {
        @Override
        public String name() {
            return "integration_required_param_tool";
        }

        @Override
        public String description() {
            return "Tool with required parameter for integration testing";
        }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> queryField = new LinkedHashMap<>();
            queryField.put("type", "string");
            queryField.put("description", "Search query");
            queryField.put("required", true);
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("query", queryField);
            return schema;
        }

        @Override
        public McpToolResult execute(McpContext context, Map<String, Object> params) {
            String query = params.getOrDefault("query", "").toString();
            return McpToolResult.text("Result for: " + query);
        }
    }
}
