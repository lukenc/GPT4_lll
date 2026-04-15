package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.DegradationManager;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.llm.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.planning.ExecutionStrategyContext;
import com.wmsay.gpt4_lll.fc.planning.ReActStrategy;
import com.wmsay.gpt4_lll.fc.tools.*;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bug condition exploration property tests for Stop button cancellation fix.
 * <p>
 * These tests encode the EXPECTED behavior: when Thread.isInterrupted() is true,
 * execution should terminate early. On UNFIXED code, these tests will FAIL because
 * the interrupt checks are missing — confirming the bug exists.
 * <p>
 * Validates: Requirements 1.1, 1.2, 2.1, 2.2
 */
class StopButtonCancellationPropertyTest {

    private ReActStrategy strategy;
    private ObservabilityManager observability;
    private DegradationManager degradationManager;
    private ErrorHandler errorHandler;
    private ProgressCallback noopCallback;

    @BeforeProperty
    void setup() {
        strategy = new ReActStrategy();
        observability = new ObservabilityManager();
        degradationManager = new DegradationManager();
        errorHandler = new ErrorHandler();
        noopCallback = new ProgressCallback() {};
    }

    // ---------------------------------------------------------------
    // Test 1a: ReAct Loop Interrupt — executeReActLoop() should stop
    //          when thread is interrupted between rounds
    // ---------------------------------------------------------------

    /**
     * Property 1 (Bug Condition): ReAct Loop Ignores Thread Interrupt
     * **Validates: Requirements 1.1, 2.1**
     *
     * For any ReAct loop with multiple rounds where the thread is interrupted
     * before round N, executeReActLoop() should return before executing round N+.
     *
     * On UNFIXED code this FAILS because executeReActLoop() never checks
     * Thread.currentThread().isInterrupted() between rounds.
     */
    @Property(tries = 20)
    @Label("Feature: stop-button-cancellation-fix, Property 1: ReAct loop interrupt responsiveness")
    void reactLoopShouldTerminateWhenThreadInterrupted(
            @ForAll("interruptAtRoundCases") InterruptAtRoundCase testCase) {

        // Track how many LLM calls (rounds) actually executed
        AtomicInteger roundsExecuted = new AtomicInteger(0);
        int interruptBeforeRound = testCase.interruptBeforeRound;
        int totalRounds = testCase.totalRounds;

        // LlmCaller that always returns a single tool call (forcing another round),
        // and interrupts the thread at the specified round boundary
        LlmCaller llmCaller = request -> {
            int currentRound = roundsExecuted.getAndIncrement();
            // After completing this round's LLM call, set interrupt flag
            // so it should be detected before the NEXT round starts
            if (currentRound == interruptBeforeRound - 1) {
                Thread.currentThread().interrupt();
            }
            // Return a response with a single tool call to force another round
            return buildSingleToolCallResponse(currentRound, "test_tool");
        };

        ProtocolAdapter protocolAdapter = createToolReturningProtocolAdapter();

        // Minimal tool infrastructure — tool always succeeds quickly
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerTool(createStubTool("test_tool"));
        ExecutionEngine executionEngine = new ExecutionEngine(
                toolRegistry, new StubApprovalProvider(), new RetryStrategy());
        ValidationEngine validationEngine = new ValidationEngine(toolRegistry);

        ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability, degradationManager,
                null, null, null);

        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(createUserMessage("test"))));
        chatContent.setStream(false);
        FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(totalRounds)
                .build();

        try {
            FunctionCallResult result = strategy.executeReActLoop(
                    request, createToolContext(), llmCaller, noopCallback, ctx, totalRounds);

            // EXPECTED: execution should have stopped at or before the interrupt point.
            // The LLM caller should have been invoked at most interruptBeforeRound times.
            assert roundsExecuted.get() <= interruptBeforeRound :
                    "Expected at most " + interruptBeforeRound + " LLM calls but "
                            + roundsExecuted.get() + " were made (totalRounds=" + totalRounds + ")."
                            + " This confirms the bug: executeReActLoop() does not check"
                            + " Thread.currentThread().isInterrupted() between rounds.";
        } finally {
            // Clear interrupt flag to avoid affecting other tests
            Thread.interrupted();
        }
    }

    // ---------------------------------------------------------------
    // Test 1b: Tool Execution Interrupt — executeToolCalls() should skip
    //          remaining tools when thread is interrupted
    // ---------------------------------------------------------------

    /**
     * Property 2 (Bug Condition): Tool Execution Ignores Thread Interrupt
     * **Validates: Requirements 1.2, 2.2**
     *
     * For any single round with N tool calls where the thread is interrupted
     * after tool K completes, executeToolCalls() should skip tools K+1..N.
     *
     * We test this through executeReActLoop() since executeToolCalls() is
     * package-private. The LLM returns N tool calls in one round; one of the
     * tools sets the interrupt flag after execution. We verify that not all
     * N tools were executed.
     *
     * On UNFIXED code this FAILS because executeToolCalls() iterates all
     * tools without checking interruption between them.
     */
    @Property(tries = 20)
    @Label("Feature: stop-button-cancellation-fix, Property 2: Tool execution interrupt responsiveness")
    void toolExecutionShouldSkipRemainingToolsWhenInterrupted(
            @ForAll("interruptAtToolCases") InterruptAtToolCase testCase) {

        int toolCount = testCase.toolCount;
        int interruptAfterTool = testCase.interruptAfterTool; // 1-based: interrupt after this tool

        // Track which tools were actually executed
        AtomicInteger toolsExecuted = new AtomicInteger(0);

        // Capture the calling thread so tools (which run on a pool thread via
        // ExecutionEngine's CompletableFuture.supplyAsync) can interrupt the
        // correct thread — the one running executeToolCalls/executeReActLoop.
        final Thread callingThread = Thread.currentThread();

        // Create N distinct tool names
        List<String> toolNames = new ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            toolNames.add("tool_" + i);
        }

        // Build the LLM response that returns all N tool calls at once
        String llmResponse = buildMultiToolCallResponse(toolNames);

        // LlmCaller: first call returns N tool calls, should never be called again
        // because interrupt should stop the loop
        AtomicInteger llmCalls = new AtomicInteger(0);
        LlmCaller llmCaller = request -> {
            int call = llmCalls.getAndIncrement();
            if (call == 0) {
                return llmResponse;
            }
            // If we get here on call > 0, the loop continued after interrupt
            return buildNoToolCallResponse("should not reach here");
        };

        ProtocolAdapter protocolAdapter = createToolReturningProtocolAdapter();

        // Register tools — each tool increments counter, one interrupts the calling thread
        ToolRegistry toolRegistry = new ToolRegistry();
        for (int i = 0; i < toolCount; i++) {
            final int toolIndex = i;
            Tool tool = new Tool() {
                @Override
                public String name() { return "tool_" + toolIndex; }
                @Override
                public String description() { return "Test tool " + toolIndex; }
                @Override
                public Map<String, Object> inputSchema() { return Collections.emptyMap(); }
                @Override
                public ToolResult execute(ToolContext context, Map<String, Object> params) {
                    toolsExecuted.incrementAndGet();
                    // Interrupt the calling thread (not the pool thread) after
                    // the designated tool completes — simulates AgentRuntimeBridge
                    // .requestStop() interrupting the ReAct loop thread.
                    if (toolIndex == interruptAfterTool - 1) {
                        callingThread.interrupt();
                    }
                    return ToolResult.text("result_" + toolIndex);
                }
            };
            toolRegistry.registerTool(tool);
        }

        ExecutionEngine executionEngine = new ExecutionEngine(
                toolRegistry, new StubApprovalProvider(), new RetryStrategy());
        ValidationEngine validationEngine = new ValidationEngine(toolRegistry);

        ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability, degradationManager,
                null, null, null);

        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(createUserMessage("test"))));
        chatContent.setStream(false);
        FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(5)
                .build();

        try {
            FunctionCallResult result = strategy.executeReActLoop(
                    request, createToolContext(), llmCaller, noopCallback, ctx, 5);

            // EXPECTED: only tools up to and including interruptAfterTool should execute.
            // Tools after the interrupt point should be skipped.
            assert toolsExecuted.get() <= interruptAfterTool :
                    "Expected at most " + interruptAfterTool + " tools executed but "
                            + toolsExecuted.get() + " were executed (total=" + toolCount + ")."
                            + " This confirms the bug: executeToolCalls() iterates all"
                            + " tools without checking Thread.currentThread().isInterrupted().";
        } finally {
            // Clear interrupt flag
            Thread.interrupted();
        }
    }

    // ===============================================================
    // PRESERVATION PROPERTY TESTS (Task 2)
    // These tests verify non-interrupted ReAct execution behavior
    // is unchanged. They should PASS on the current unfixed code.
    // ===============================================================

    // ---------------------------------------------------------------
    // Property 3: Normal completion — no tool calls on final round
    //             → result type is SUCCESS and content matches LLM response
    // ---------------------------------------------------------------

    /**
     * Property 3 (Preservation): Normal Completion Returns SUCCESS
     * **Validates: Requirements 3.1, 3.5**
     *
     * For any round count (1-20) where the LLM returns tool calls for
     * rounds 0..(N-2) and no tool calls on the final round (N-1),
     * the result type is SUCCESS and the content matches the LLM's
     * final-round text response.
     */
    @Property(tries = 30)
    @Label("Feature: stop-button-cancellation-fix, Property 3: Normal completion returns SUCCESS with matching content")
    void normalCompletionReturnsSuccessWithMatchingContent(
            @ForAll("normalCompletionCases") NormalCompletionCase testCase) {

        int toolRounds = testCase.toolRounds; // rounds that return tool calls
        String finalContent = testCase.finalContent;

        AtomicInteger roundsExecuted = new AtomicInteger(0);

        // LlmCaller: returns tool calls for rounds 0..(toolRounds-1),
        // then returns a final answer with no tool calls
        LlmCaller llmCaller = request -> {
            int currentRound = roundsExecuted.getAndIncrement();
            if (currentRound < toolRounds) {
                return buildSingleToolCallResponse(currentRound, "test_tool");
            }
            // Final round — no tool calls
            return buildNoToolCallResponse(finalContent);
        };

        ProtocolAdapter protocolAdapter = createToolReturningProtocolAdapter();

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerTool(createStubTool("test_tool"));
        ExecutionEngine executionEngine = new ExecutionEngine(
                toolRegistry, new StubApprovalProvider(), new RetryStrategy());
        ValidationEngine validationEngine = new ValidationEngine(toolRegistry);

        ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability, degradationManager,
                null, null, null);

        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(createUserMessage("test"))));
        chatContent.setStream(false);
        int maxRounds = toolRounds + 5; // enough headroom
        FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(maxRounds)
                .build();

        FunctionCallResult result = strategy.executeReActLoop(
                request, createToolContext(), llmCaller, noopCallback, ctx, maxRounds);

        // Assertions
        assert result.getType() == FunctionCallResult.ResultType.SUCCESS :
                "Expected SUCCESS but got " + result.getType()
                        + " (toolRounds=" + toolRounds + ")";
        assert result.getContent() != null && result.getContent().contains(finalContent) :
                "Expected content to contain '" + finalContent + "' but got '"
                        + result.getContent() + "'";
        assert roundsExecuted.get() == toolRounds + 1 :
                "Expected " + (toolRounds + 1) + " LLM calls but got "
                        + roundsExecuted.get();
    }

    // ---------------------------------------------------------------
    // Property 4: Max rounds exceeded
    //             → result type is MAX_ROUNDS_EXCEEDED
    // ---------------------------------------------------------------

    /**
     * Property 4 (Preservation): Exceeding maxRounds Returns MAX_ROUNDS_EXCEEDED
     * **Validates: Requirements 3.2, 3.6**
     *
     * For any execution where the LLM always returns tool calls (never
     * a final answer), and the loop runs for exactly maxRounds iterations,
     * the result type is MAX_ROUNDS_EXCEEDED.
     */
    @Property(tries = 20)
    @Label("Feature: stop-button-cancellation-fix, Property 4: Exceeding maxRounds returns MAX_ROUNDS_EXCEEDED")
    void exceedingMaxRoundsReturnsMaxRoundsExceeded(
            @ForAll("maxRoundsCases") Integer maxRounds) {

        AtomicInteger roundsExecuted = new AtomicInteger(0);

        // LlmCaller: always returns a tool call, never a final answer
        LlmCaller llmCaller = request -> {
            int currentRound = roundsExecuted.getAndIncrement();
            return buildSingleToolCallResponse(currentRound, "test_tool");
        };

        ProtocolAdapter protocolAdapter = createToolReturningProtocolAdapter();

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerTool(createStubTool("test_tool"));
        ExecutionEngine executionEngine = new ExecutionEngine(
                toolRegistry, new StubApprovalProvider(), new RetryStrategy());
        ValidationEngine validationEngine = new ValidationEngine(toolRegistry);

        ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability, degradationManager,
                null, null, null);

        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(createUserMessage("test"))));
        chatContent.setStream(false);
        FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(maxRounds)
                .build();

        FunctionCallResult result = strategy.executeReActLoop(
                request, createToolContext(), llmCaller, noopCallback, ctx, maxRounds);

        // Assertions
        assert result.getType() == FunctionCallResult.ResultType.MAX_ROUNDS_EXCEEDED :
                "Expected MAX_ROUNDS_EXCEEDED but got " + result.getType()
                        + " (maxRounds=" + maxRounds + ")";
        assert roundsExecuted.get() == maxRounds :
                "Expected exactly " + maxRounds + " LLM calls but got "
                        + roundsExecuted.get();
    }

    // ---------------------------------------------------------------
    // Property 5: N tool calls with no interrupt
    //             → exactly N results returned in order
    // ---------------------------------------------------------------

    /**
     * Property 5 (Preservation): All Tool Calls Execute Without Interrupt
     * **Validates: Requirements 3.1, 3.3**
     *
     * For any single round with N tool calls (1-10) and no thread
     * interrupt, exactly N tool call results are returned in the
     * result's toolCallHistory, and each result corresponds to the
     * correct tool in order.
     */
    @Property(tries = 30)
    @Label("Feature: stop-button-cancellation-fix, Property 5: N tool calls without interrupt returns N results in order")
    void allToolCallsExecuteWithoutInterrupt(
            @ForAll("toolCountCases") Integer toolCount) {

        // Create N distinct tool names
        List<String> toolNames = new ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            toolNames.add("tool_" + i);
        }

        // Build the LLM response that returns all N tool calls at once,
        // then a final answer on the second call
        AtomicInteger llmCalls = new AtomicInteger(0);
        LlmCaller llmCaller = request -> {
            int call = llmCalls.getAndIncrement();
            if (call == 0) {
                return buildMultiToolCallResponse(toolNames);
            }
            return buildNoToolCallResponse("done");
        };

        ProtocolAdapter protocolAdapter = createToolReturningProtocolAdapter();

        // Register tools — each returns a unique result
        ToolRegistry toolRegistry = new ToolRegistry();
        for (int i = 0; i < toolCount; i++) {
            final int toolIndex = i;
            Tool tool = new Tool() {
                @Override
                public String name() { return "tool_" + toolIndex; }
                @Override
                public String description() { return "Test tool " + toolIndex; }
                @Override
                public Map<String, Object> inputSchema() { return Collections.emptyMap(); }
                @Override
                public ToolResult execute(ToolContext context, Map<String, Object> params) {
                    return ToolResult.text("result_" + toolIndex);
                }
            };
            toolRegistry.registerTool(tool);
        }

        ExecutionEngine executionEngine = new ExecutionEngine(
                toolRegistry, new StubApprovalProvider(), new RetryStrategy());
        ValidationEngine validationEngine = new ValidationEngine(toolRegistry);

        ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                protocolAdapter, validationEngine, executionEngine,
                errorHandler, observability, degradationManager,
                null, null, null);

        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(createUserMessage("test"))));
        chatContent.setStream(false);
        FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(5)
                .build();

        FunctionCallResult result = strategy.executeReActLoop(
                request, createToolContext(), llmCaller, noopCallback, ctx, 5);

        // Assertions: result is SUCCESS (final round returned no tool calls)
        assert result.getType() == FunctionCallResult.ResultType.SUCCESS :
                "Expected SUCCESS but got " + result.getType();

        // The toolCallHistory should contain exactly N results from the first round
        List<ToolCallResult> history = result.getToolCallHistory();
        assert history.size() == toolCount :
                "Expected " + toolCount + " tool call results but got " + history.size();

        // Verify each result corresponds to the correct tool in order
        for (int i = 0; i < toolCount; i++) {
            ToolCallResult tcr = history.get(i);
            assert tcr.getToolName().equals("tool_" + i) :
                    "Expected tool_" + i + " at position " + i
                            + " but got " + tcr.getToolName();
            assert tcr.isSuccess() :
                    "Expected SUCCESS status for tool_" + i
                            + " but got " + tcr.getStatus();
        }
    }

    // ---------------------------------------------------------------
    // Test case types
    // ---------------------------------------------------------------

    static class NormalCompletionCase {
        final int toolRounds;
        final String finalContent;

        NormalCompletionCase(int toolRounds, String finalContent) {
            this.toolRounds = toolRounds;
            this.finalContent = finalContent;
        }

        @Override
        public String toString() {
            return "NormalCompletion{toolRounds=" + toolRounds
                    + ", finalContent='" + finalContent + "'}";
        }
    }

    static class InterruptAtRoundCase {
        final int totalRounds;
        final int interruptBeforeRound;

        InterruptAtRoundCase(int totalRounds, int interruptBeforeRound) {
            this.totalRounds = totalRounds;
            this.interruptBeforeRound = interruptBeforeRound;
        }

        @Override
        public String toString() {
            return "InterruptAtRound{total=" + totalRounds
                    + ", interruptBefore=" + interruptBeforeRound + "}";
        }
    }

    static class InterruptAtToolCase {
        final int toolCount;
        final int interruptAfterTool; // 1-based

        InterruptAtToolCase(int toolCount, int interruptAfterTool) {
            this.toolCount = toolCount;
            this.interruptAfterTool = interruptAfterTool;
        }

        @Override
        public String toString() {
            return "InterruptAtTool{count=" + toolCount
                    + ", interruptAfter=" + interruptAfterTool + "}";
        }
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<InterruptAtRoundCase> interruptAtRoundCases() {
        // totalRounds: 3-10, interruptBeforeRound: 2 to totalRounds-1
        return Arbitraries.integers().between(3, 10).flatMap(totalRounds ->
                Arbitraries.integers().between(2, totalRounds - 1)
                        .map(interruptBefore -> new InterruptAtRoundCase(totalRounds, interruptBefore))
        );
    }

    @Provide
    Arbitrary<InterruptAtToolCase> interruptAtToolCases() {
        // toolCount: 3-6, interruptAfterTool: 1 to toolCount-1
        return Arbitraries.integers().between(3, 6).flatMap(toolCount ->
                Arbitraries.integers().between(1, toolCount - 1)
                        .map(interruptAfter -> new InterruptAtToolCase(toolCount, interruptAfter))
        );
    }

    @Provide
    Arbitrary<NormalCompletionCase> normalCompletionCases() {
        // toolRounds: 0-5 (0 means LLM returns final answer immediately)
        // finalContent: a simple alphanumeric string
        return Arbitraries.integers().between(0, 5).flatMap(toolRounds ->
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                        .map(content -> new NormalCompletionCase(toolRounds, content))
        );
    }

    @Provide
    Arbitrary<Integer> maxRoundsCases() {
        // maxRounds: 1-20
        return Arbitraries.integers().between(1, 20);
    }

    @Provide
    Arbitrary<Integer> toolCountCases() {
        // toolCount: 1-10
        return Arbitraries.integers().between(1, 10);
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    /**
     * Build a JSON response with a single tool call.
     */
    private String buildSingleToolCallResponse(int roundIndex, String toolName) {
        return "{\"choices\":[{\"message\":{\"content\":\"round " + roundIndex
                + "\",\"tool_calls\":[{\"id\":\"call_" + roundIndex
                + "\",\"type\":\"function\",\"function\":{\"name\":\"" + toolName
                + "\",\"arguments\":\"{}\"}}]}}]}";
    }

    /**
     * Build a JSON response with multiple tool calls in one round.
     */
    private String buildMultiToolCallResponse(List<String> toolNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"choices\":[{\"message\":{\"content\":\"multi-tool round\",\"tool_calls\":[");
        for (int i = 0; i < toolNames.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"call_").append(i)
                    .append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                    .append(toolNames.get(i))
                    .append("\",\"arguments\":\"{}\"}}");
        }
        sb.append("]}}]}");
        return sb.toString();
    }

    /**
     * Build a JSON response with no tool calls (final answer).
     */
    private String buildNoToolCallResponse(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }

    private ProtocolAdapter createToolReturningProtocolAdapter() {
        return new ProtocolAdapter() {
            @Override
            public String getName() { return "test"; }
            @Override
            public boolean supports(String providerName) { return true; }
            @Override
            public Object formatToolDescriptions(List<Tool> tools) { return "[]"; }
            @Override
            public List<ToolCall> parseToolCalls(String response) {
                try {
                    com.alibaba.fastjson.JSONObject json =
                            com.alibaba.fastjson.JSON.parseObject(response);
                    com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        com.alibaba.fastjson.JSONObject msg =
                                choices.getJSONObject(0).getJSONObject("message");
                        com.alibaba.fastjson.JSONArray tcs = msg.getJSONArray("tool_calls");
                        if (tcs != null) {
                            List<ToolCall> result = new ArrayList<>();
                            for (int i = 0; i < tcs.size(); i++) {
                                com.alibaba.fastjson.JSONObject tc = tcs.getJSONObject(i);
                                com.alibaba.fastjson.JSONObject func =
                                        tc.getJSONObject("function");
                                result.add(ToolCall.builder()
                                        .callId(tc.getString("id"))
                                        .toolName(func.getString("name"))
                                        .parameters(Collections.emptyMap())
                                        .build());
                            }
                            return result;
                        }
                    }
                } catch (Exception e) {
                    // ignore parse errors
                }
                return Collections.emptyList();
            }
            @Override
            public Message formatToolResult(ToolCallResult result) {
                Message msg = new Message();
                msg.setRole("tool");
                msg.setContent(result.getResult() != null
                        ? result.getResult().getDisplayText() : "error");
                msg.setToolCallId(result.getCallId());
                return msg;
            }
            @Override
            public boolean supportsNativeFunctionCalling() { return true; }
        };
    }

    private Tool createStubTool(String name) {
        return new Tool() {
            @Override
            public String name() { return name; }
            @Override
            public String description() { return "Stub tool for testing"; }
            @Override
            public Map<String, Object> inputSchema() { return Collections.emptyMap(); }
            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> params) {
                return ToolResult.text("stub result");
            }
        };
    }

    private ToolContext createToolContext() {
        return ToolContext.builder().build();
    }

    private Message createUserMessage(String content) {
        Message msg = new Message();
        msg.setRole("user");
        msg.setContent(content);
        return msg;
    }

    /**
     * Stub ApprovalProvider that auto-approves everything.
     */
    private static class StubApprovalProvider implements ApprovalProvider {
        @Override
        public boolean requestApproval(ToolCall toolCall, ToolContext context) { return true; }
        @Override
        public boolean isAlwaysAllowed(String toolName) { return true; }
        @Override
        public void setAlwaysAllowed(String toolName) {}
        @Override
        public void clearAlwaysAllowed() {}
    }
}
