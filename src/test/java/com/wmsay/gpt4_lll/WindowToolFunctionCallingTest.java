package com.wmsay.gpt4_lll;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.execution.UserApprovalManager;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.MarkdownProtocolAdapter;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WindowTool Function Calling 集成的单元测试。
 * 测试 FC 框架组件的初始化和对话流程逻辑（不依赖 IntelliJ Platform UI）。
 */
class WindowToolFunctionCallingTest {

    private FunctionCallOrchestrator orchestrator;
    private FunctionCallConfig config;
    private ObservabilityManager observabilityManager;

    @BeforeEach
    void setUp() {
        ProtocolAdapter protocolAdapter = new MarkdownProtocolAdapter();
        ValidationEngine validationEngine = new ValidationEngine();
        ErrorHandler errorHandler = new ErrorHandler();
        observabilityManager = new ObservabilityManager();
        RetryStrategy retryStrategy = new RetryStrategy();
        UserApprovalManager approvalManager = new UserApprovalManager();
        ExecutionEngine executionEngine = new ExecutionEngine(retryStrategy, approvalManager);

        orchestrator = new FunctionCallOrchestrator(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observabilityManager);

        config = FunctionCallConfig.defaultConfig();
    }

    @Test
    void testFunctionCallingEnabledCheck() {
        // Default config has function calling enabled
        assertTrue(config.isEnableFunctionCalling());

        // Disabled config
        FunctionCallConfig disabledConfig = FunctionCallConfig.builder()
                .enableFunctionCalling(false)
                .build();
        assertFalse(disabledConfig.isEnableFunctionCalling());
    }

    @Test
    void testOrchestratorCreation() {
        assertNotNull(orchestrator);
        assertNotNull(orchestrator.getDegradationManager());
    }

    @Test
    void testDefaultConfigValues() {
        assertEquals(30, config.getDefaultTimeout());
        assertEquals(3, config.getMaxRetries());
        assertEquals(20, config.getMaxRounds());
        assertTrue(config.isEnableApproval());
        assertTrue(config.isEnableFunctionCalling());
        assertEquals(FunctionCallConfig.LogLevel.INFO, config.getLogLevel());
    }

    @Test
    void testToolCallResultDisplayLogic_Success() {
        // Simulate a successful tool call result
        McpToolResult mcpResult = McpToolResult.text("File content here");
        ToolCallResult tcr = ToolCallResult.success("call-1", "read_file", mcpResult, 100);

        assertTrue(tcr.isSuccess());
        assertEquals("read_file", tcr.getToolName());
        assertNotNull(tcr.getResult());
        assertEquals("File content here", tcr.getResult().getTextContent());
    }

    @Test
    void testToolCallResultDisplayLogic_Error() {
        // Simulate an error tool call result
        ErrorMessage error = ErrorMessage.builder()
                .type("tool_not_found")
                .message("Tool 'unknown_tool' not found")
                .suggestion("Did you mean 'read_file'?")
                .build();
        ToolCallResult tcr = ToolCallResult.validationError("call-2", "unknown_tool", error);

        assertFalse(tcr.isSuccess());
        assertEquals("unknown_tool", tcr.getToolName());
        assertNotNull(tcr.getError());
        assertEquals("Tool 'unknown_tool' not found", tcr.getError().getMessage());
        assertEquals("Did you mean 'read_file'?", tcr.getError().getSuggestion());
    }

    @Test
    void testFunctionCallResultTypes() {
        // SUCCESS
        FunctionCallResult success = FunctionCallResult.success("Hello", "session-1", Collections.emptyList());
        assertTrue(success.isSuccess());
        assertEquals(FunctionCallResult.ResultType.SUCCESS, success.getType());
        assertEquals("Hello", success.getContent());

        // ERROR
        FunctionCallResult error = FunctionCallResult.error("Something went wrong", "session-2");
        assertFalse(error.isSuccess());
        assertEquals(FunctionCallResult.ResultType.ERROR, error.getType());

        // MAX_ROUNDS_EXCEEDED
        FunctionCallResult maxRounds = FunctionCallResult.maxRoundsExceeded("session-3", Collections.emptyList());
        assertFalse(maxRounds.isSuccess());
        assertEquals(FunctionCallResult.ResultType.MAX_ROUNDS_EXCEEDED, maxRounds.getType());

        // DEGRADED
        FunctionCallResult degraded = FunctionCallResult.degraded("FC disabled", "session-4");
        assertFalse(degraded.isSuccess());
        assertEquals(FunctionCallResult.ResultType.DEGRADED, degraded.getType());
    }

    @Test
    void testToolCallHistoryInResult() {
        McpToolResult mcpResult = McpToolResult.text("result data");
        ToolCallResult tcr1 = ToolCallResult.success("call-1", "read_file", mcpResult, 50);
        ToolCallResult tcr2 = ToolCallResult.success("call-2", "tree", mcpResult, 30);

        FunctionCallResult result = FunctionCallResult.success(
                "Final answer", "session-1", List.of(tcr1, tcr2));

        assertEquals(2, result.getToolCallHistory().size());
        assertEquals("read_file", result.getToolCallHistory().get(0).getToolName());
        assertEquals("tree", result.getToolCallHistory().get(1).getToolName());
    }

    @Test
    void testObservabilityManagerLogLevel() {
        observabilityManager.setLogLevel(FunctionCallConfig.LogLevel.DEBUG);
        assertEquals(FunctionCallConfig.LogLevel.DEBUG, observabilityManager.getLogLevel());

        observabilityManager.setLogLevel(FunctionCallConfig.LogLevel.ERROR);
        assertEquals(FunctionCallConfig.LogLevel.ERROR, observabilityManager.getLogLevel());
    }

    @Test
    void testNullOrchestratorMeansFCDisabled() {
        // Simulates the isFunctionCallingEnabled() logic in WindowTool
        FunctionCallOrchestrator nullOrchestrator = null;
        FunctionCallConfig nullConfig = null;

        // When orchestrator is null, FC should be disabled
        boolean enabled = nullOrchestrator != null
                && nullConfig != null
                && nullConfig.isEnableFunctionCalling();
        assertFalse(enabled);
    }

    @Test
    void testFCEnabledWithAllComponentsPresent() {
        // Simulates the isFunctionCallingEnabled() logic in WindowTool
        boolean enabled = orchestrator != null
                && config != null
                && config.isEnableFunctionCalling();
        assertTrue(enabled);
    }

    @Test
    void testFCDisabledWhenConfigDisabled() {
        FunctionCallConfig disabledConfig = FunctionCallConfig.builder()
                .enableFunctionCalling(false)
                .build();

        boolean enabled = orchestrator != null
                && disabledConfig != null
                && disabledConfig.isEnableFunctionCalling();
        assertFalse(enabled);
    }
}
