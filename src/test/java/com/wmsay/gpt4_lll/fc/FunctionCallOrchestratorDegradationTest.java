package com.wmsay.gpt4_lll.fc;

import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.execution.UserApprovalManager;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.DegradationManager;
import com.wmsay.gpt4_lll.fc.protocol.MarkdownProtocolAdapter;
import com.wmsay.gpt4_lll.fc.protocol.OpenAIProtocolAdapter;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import com.wmsay.gpt4_lll.model.ChatContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for degradation/fallback integration in FunctionCallOrchestrator.
 * Validates: Requirements 16.1, 16.2, 16.3, 16.5, 16.6
 */
class FunctionCallOrchestratorDegradationTest {

    private DegradationManager degradationManager;
    private ObservabilityManager observability;
    private ValidationEngine validationEngine;
    private ErrorHandler errorHandler;
    private ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        degradationManager = new DegradationManager();
        observability = new ObservabilityManager();
        validationEngine = new ValidationEngine();
        errorHandler = new ErrorHandler();
        executionEngine = new ExecutionEngine(
                new RetryStrategy(), new UserApprovalManager(), Executors.newSingleThreadExecutor());
    }

    // ---- Req 16.2, 16.5: disabled DegradationManager returns DEGRADED ----

    @Test
    void execute_whenDisabled_returnsDegradedResult() {
        // Pre-disable function calling
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            degradationManager.recordParseAttempt(false);
        }
        assertTrue(degradationManager.isDisabled());

        ProtocolAdapter adapter = new MarkdownProtocolAdapter();
        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler, observability, degradationManager);

        FunctionCallRequest request = buildRequest();
        FunctionCallResult result = orchestrator.execute(request, null, req -> "no tool calls");

        assertEquals(FunctionCallResult.ResultType.DEGRADED, result.getType());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("disabled"));
    }

    // ---- Req 16.1: non-native adapter logs degradation to PE mode ----

    @Test
    void execute_withNonNativeAdapter_logsPromptEngineeringMode() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        assertFalse(adapter.supportsNativeFunctionCalling());

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler, observability, degradationManager);

        FunctionCallRequest request = buildRequest();
        // LLM returns plain text (no tool calls) → should succeed
        FunctionCallResult result = orchestrator.execute(request, null, req -> "Hello, no tools here.");

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        // DegradationManager should have recorded a parse attempt (empty = failure for PE mode)
        assertEquals(1, degradationManager.getTotalAttempts());
    }

    // ---- Req 16.2: parse failures accumulate and auto-disable mid-session ----

    @Test
    void execute_parseFailuresAccumulate_autoDisablesMidSession() {
        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();

        // Pre-seed with failures just below threshold
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE - 1; i++) {
            degradationManager.recordParseAttempt(false);
        }
        assertFalse(degradationManager.isDisabled());

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler, observability, degradationManager);

        FunctionCallRequest request = buildRequest();
        // LLM returns plain text → parse returns empty → recorded as failure → triggers disable
        FunctionCallResult result = orchestrator.execute(request, null, req -> "plain text response");

        // The first round parses empty (failure recorded), but since toolCalls is empty,
        // it returns SUCCESS for this call. The degradation manager should now be disabled.
        // Note: the response is SUCCESS because no tool calls means "conversation done"
        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        assertTrue(degradationManager.isDisabled());
    }

    // ---- Req 16.1: native adapter does NOT record parse attempts ----

    @Test
    void execute_withNativeAdapter_doesNotRecordParseAttempts() {
        OpenAIProtocolAdapter adapter = new OpenAIProtocolAdapter();
        assertTrue(adapter.supportsNativeFunctionCalling());

        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler, observability, degradationManager);

        FunctionCallRequest request = buildRequest();
        FunctionCallResult result = orchestrator.execute(request, null, req -> "Hello from GPT-4");

        assertEquals(FunctionCallResult.ResultType.SUCCESS, result.getType());
        // No parse attempts should be recorded for native adapters
        assertEquals(0, degradationManager.getTotalAttempts());
    }

    // ---- Req 16.5: degraded result has DEGRADED type ----

    @Test
    void degradedFactoryMethod_createsCorrectResult() {
        FunctionCallResult result = FunctionCallResult.degraded("test reason", "session-123");
        assertEquals(FunctionCallResult.ResultType.DEGRADED, result.getType());
        assertEquals("test reason", result.getContent());
        assertEquals("session-123", result.getSessionId());
        assertTrue(result.getToolCallHistory().isEmpty());
    }

    // ---- Req 16.3: getCurrentModeDescription reflects state ----

    @Test
    void getDegradationManager_exposedFromOrchestrator() {
        ProtocolAdapter adapter = new MarkdownProtocolAdapter();
        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler, observability, degradationManager);

        assertSame(degradationManager, orchestrator.getDegradationManager());
    }

    // ---- backward compatibility: 5-arg constructor still works ----

    @Test
    void fiveArgConstructor_createsDefaultDegradationManager() {
        ProtocolAdapter adapter = new MarkdownProtocolAdapter();
        FunctionCallOrchestrator orchestrator = new FunctionCallOrchestrator(
                adapter, validationEngine, executionEngine, errorHandler, observability);

        assertNotNull(orchestrator.getDegradationManager());
        assertFalse(orchestrator.getDegradationManager().isDisabled());
    }

    // ---- helper ----

    private FunctionCallRequest buildRequest() {
        ChatContent chatContent = new ChatContent();
        return FunctionCallRequest.builder()
                .chatContent(chatContent)
                .availableTools(Collections.emptyList())
                .maxRounds(5)
                .build();
    }
}
