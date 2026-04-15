package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.core.*;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 属性测试: core 层 Builder 模式不可变性 (Property 4)
 * <p>
 * 验证 AgentDefinition、AgentRuntimeConfig、AgentMessage、FunctionCallConfig、
 * FunctionCallResult、ErrorMessage 构建后为不可变对象。
 * <p>
 * Validates: Requirements 2.1, 2.3, 2.4, 18.1
 */
class CoreDataModelPropertyTest {

    // ---------------------------------------------------------------
    // Property 4: AgentDefinition Builder 不可变性
    // Validates: Requirements 2.1, 18.1
    // ---------------------------------------------------------------

    @Property(tries = 100)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentDefinition fields match builder values")
    void agentDefinitionFieldsMatchBuilderValues(
            @ForAll("agentIds") String id,
            @ForAll("nonEmptyStrings") String name,
            @ForAll("nonEmptyStrings") String systemPrompt,
            @ForAll("toolNameLists") List<String> toolNames,
            @ForAll("strategyNames") String strategyName,
            @ForAll("memoryStrategies") String memoryStrategy) {

        AgentDefinition def = AgentDefinition.builder()
                .id(id)
                .name(name)
                .systemPrompt(systemPrompt)
                .availableToolNames(toolNames)
                .strategyName(strategyName)
                .memoryStrategy(memoryStrategy)
                .build();

        assert def.getId().equals(id) : "id mismatch";
        assert def.getName().equals(name) : "name mismatch";
        assert def.getSystemPrompt().equals(systemPrompt) : "systemPrompt mismatch";
        assert def.getAvailableToolNames().equals(toolNames) : "availableToolNames mismatch";
        assert def.getStrategyName().equals(strategyName) : "strategyName mismatch";
        assert def.getMemoryStrategy().equals(memoryStrategy) : "memoryStrategy mismatch";
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentDefinition availableToolNames is unmodifiable")
    void agentDefinitionToolNamesUnmodifiable(
            @ForAll("agentIds") String id,
            @ForAll("nonEmptyStrings") String systemPrompt) {

        List<String> mutableList = new ArrayList<>(Arrays.asList("tool_a", "tool_b"));
        AgentDefinition def = AgentDefinition.builder()
                .id(id)
                .systemPrompt(systemPrompt)
                .availableToolNames(mutableList)
                .build();

        // Mutating the original list should not affect the built object
        mutableList.add("tool_c");
        assert def.getAvailableToolNames().size() == 2 :
                "Original list mutation affected built object";

        // Returned list should be unmodifiable
        try {
            def.getAvailableToolNames().add("tool_d");
            assert false : "Expected UnsupportedOperationException";
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentDefinition id/systemPrompt non-null validation")
    void agentDefinitionValidation() {
        // null id
        try {
            AgentDefinition.builder().id(null).systemPrompt("prompt").build();
            assert false : "Expected IllegalArgumentException for null id";
        } catch (IllegalArgumentException e) { /* expected */ }

        // empty id
        try {
            AgentDefinition.builder().id("").systemPrompt("prompt").build();
            assert false : "Expected IllegalArgumentException for empty id";
        } catch (IllegalArgumentException e) { /* expected */ }

        // null systemPrompt
        try {
            AgentDefinition.builder().id("test-id").systemPrompt(null).build();
            assert false : "Expected IllegalArgumentException for null systemPrompt";
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentDefinition has no setter methods")
    void agentDefinitionHasNoSetters() {
        assertNoSetters(AgentDefinition.class);
    }

    // ---------------------------------------------------------------
    // Property 4: AgentRuntimeConfig Builder 不可变性
    // Validates: Requirements 2.4, 18.1
    // ---------------------------------------------------------------

    @Property(tries = 100)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentRuntimeConfig fields match builder values")
    void agentRuntimeConfigFieldsMatchBuilderValues(
            @ForAll @IntRange(min = 1, max = 100) int maxSessions,
            @ForAll @IntRange(min = 0, max = 10) int maxDepth,
            @ForAll @IntRange(min = 1, max = 3600) int delegationTimeout,
            @ForAll @IntRange(min = 1, max = 7200) int idleTimeout) {

        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
                .maxConcurrentSessions(maxSessions)
                .maxDelegationDepth(maxDepth)
                .delegationTimeoutSeconds(delegationTimeout)
                .sessionIdleTimeoutSeconds(idleTimeout)
                .build();

        assert config.getMaxConcurrentSessions() == maxSessions : "maxConcurrentSessions mismatch";
        assert config.getMaxDelegationDepth() == maxDepth : "maxDelegationDepth mismatch";
        assert config.getDelegationTimeoutSeconds() == delegationTimeout : "delegationTimeoutSeconds mismatch";
        assert config.getSessionIdleTimeoutSeconds() == idleTimeout : "sessionIdleTimeoutSeconds mismatch";
    }

    @Property(tries = 20)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentRuntimeConfig defaultConfig returns valid defaults")
    void agentRuntimeConfigDefaultConfigValid() {
        AgentRuntimeConfig config = AgentRuntimeConfig.defaultConfig();

        assert config.getMaxConcurrentSessions() >= 1 : "default maxConcurrentSessions must be >= 1";
        assert config.getMaxDelegationDepth() >= 0 : "default maxDelegationDepth must be >= 0";
        assert config.getDelegationTimeoutSeconds() > 0 : "default delegationTimeoutSeconds must be > 0";
        assert config.getSessionIdleTimeoutSeconds() > 0 : "default sessionIdleTimeoutSeconds must be > 0";
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentRuntimeConfig has no setter methods")
    void agentRuntimeConfigHasNoSetters() {
        assertNoSetters(AgentRuntimeConfig.class);
    }

    // ---------------------------------------------------------------
    // Property 4: AgentMessage Builder 不可变性
    // Validates: Requirements 2.3, 18.1
    // ---------------------------------------------------------------

    @Property(tries = 100)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentMessage fields match builder values")
    void agentMessageFieldsMatchBuilderValues(
            @ForAll("agentIds") String sourceId,
            @ForAll("agentIds") String targetId,
            @ForAll("messageTypes") AgentMessage.MessageType msgType,
            @ForAll("nonEmptyStrings") String payload,
            @ForAll("correlationIds") String correlationId) {

        AgentMessage msg = AgentMessage.builder()
                .sourceAgentId(sourceId)
                .targetAgentId(targetId)
                .messageType(msgType)
                .payload(payload)
                .correlationId(correlationId)
                .build();

        assert msg.getSourceAgentId().equals(sourceId) : "sourceAgentId mismatch";
        assert msg.getTargetAgentId().equals(targetId) : "targetAgentId mismatch";
        assert msg.getMessageType() == msgType : "messageType mismatch";
        assert msg.getPayload().equals(payload) : "payload mismatch";
        assert msg.getCorrelationId().equals(correlationId) : "correlationId mismatch";
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — AgentMessage has no setter methods")
    void agentMessageHasNoSetters() {
        assertNoSetters(AgentMessage.class);
    }

    // ---------------------------------------------------------------
    // Property 4: FunctionCallConfig Builder 不可变性
    // Validates: Requirements 18.1
    // ---------------------------------------------------------------

    @Property(tries = 100)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — FunctionCallConfig fields match builder values")
    void functionCallConfigFieldsMatchBuilderValues(
            @ForAll @IntRange(min = 1, max = 120) int timeout,
            @ForAll @IntRange(min = 0, max = 10) int retries,
            @ForAll @IntRange(min = 1, max = 200) int maxRounds,
            @ForAll boolean enableApproval,
            @ForAll("memoryStrategies") String memoryStrategy,
            @ForAll("strategyNames") String executionStrategy) {

        FunctionCallConfig config = FunctionCallConfig.builder()
                .defaultTimeout(timeout)
                .maxRetries(retries)
                .maxRounds(maxRounds)
                .enableApproval(enableApproval)
                .memoryStrategy(memoryStrategy)
                .executionStrategy(executionStrategy)
                .build();

        assert config.getDefaultTimeout() == timeout : "defaultTimeout mismatch";
        assert config.getMaxRetries() == retries : "maxRetries mismatch";
        assert config.getMaxRounds() == maxRounds : "maxRounds mismatch";
        assert config.isEnableApproval() == enableApproval : "enableApproval mismatch";
        assert config.getMemoryStrategy().equals(memoryStrategy) : "memoryStrategy mismatch";
        assert config.getExecutionStrategy().equals(executionStrategy) : "executionStrategy mismatch";
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — FunctionCallConfig maxRounds > 0 validation")
    void functionCallConfigMaxRoundsValidation(
            @ForAll @IntRange(min = -100, max = 0) int invalidMaxRounds) {
        try {
            FunctionCallConfig.builder().maxRounds(invalidMaxRounds).build();
            assert false : "Expected IllegalArgumentException for maxRounds=" + invalidMaxRounds;
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — FunctionCallConfig has no setter methods")
    void functionCallConfigHasNoSetters() {
        assertNoSetters(FunctionCallConfig.class);
    }

    // ---------------------------------------------------------------
    // Property 4: FunctionCallResult Builder 不可变性
    // Validates: Requirements 18.1
    // ---------------------------------------------------------------

    @Property(tries = 100)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — FunctionCallResult fields match builder values")
    void functionCallResultFieldsMatchBuilderValues(
            @ForAll("resultTypes") FunctionCallResult.ResultType type,
            @ForAll("nonEmptyStrings") String content,
            @ForAll("sessionIds") String sessionId) {

        List<ToolCallResult> history = List.of(
                ToolCallResult.builder()
                        .callId("call-1")
                        .toolName("test_tool")
                        .status(ToolCallResult.ResultStatus.SUCCESS)
                        .result(ToolResult.text("ok"))
                        .durationMs(100)
                        .build()
        );

        FunctionCallResult result = FunctionCallResult.builder()
                .type(type)
                .content(content)
                .sessionId(sessionId)
                .toolCallHistory(history)
                .build();

        assert result.getType() == type : "type mismatch";
        assert result.getContent().equals(content) : "content mismatch";
        assert result.getSessionId().equals(sessionId) : "sessionId mismatch";
        assert result.getToolCallHistory().size() == 1 : "toolCallHistory size mismatch";
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — FunctionCallResult toolCallHistory is unmodifiable")
    void functionCallResultToolCallHistoryUnmodifiable() {
        List<ToolCallResult> mutableHistory = new ArrayList<>();
        mutableHistory.add(ToolCallResult.builder()
                .callId("call-1").toolName("tool").status(ToolCallResult.ResultStatus.SUCCESS)
                .result(ToolResult.text("ok")).durationMs(50).build());

        FunctionCallResult result = FunctionCallResult.builder()
                .type(FunctionCallResult.ResultType.SUCCESS)
                .content("done")
                .toolCallHistory(mutableHistory)
                .build();

        // Mutating original list should not affect built object
        mutableHistory.add(ToolCallResult.builder()
                .callId("call-2").toolName("tool2").status(ToolCallResult.ResultStatus.SUCCESS)
                .result(ToolResult.text("ok2")).durationMs(60).build());
        assert result.getToolCallHistory().size() == 1 :
                "Original list mutation affected built object";

        // Returned list should be unmodifiable
        try {
            result.getToolCallHistory().add(ToolCallResult.builder()
                    .callId("call-3").toolName("tool3").status(ToolCallResult.ResultStatus.SUCCESS)
                    .result(ToolResult.text("ok3")).durationMs(70).build());
            assert false : "Expected UnsupportedOperationException";
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — FunctionCallResult has no setter methods")
    void functionCallResultHasNoSetters() {
        assertNoSetters(FunctionCallResult.class);
    }

    // ---------------------------------------------------------------
    // Property 4: ErrorMessage Builder 不可变性
    // Validates: Requirements 18.1
    // ---------------------------------------------------------------

    @Property(tries = 100)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — ErrorMessage fields match builder values")
    void errorMessageFieldsMatchBuilderValues(
            @ForAll("errorTypes") String type,
            @ForAll("nonEmptyStrings") String message,
            @ForAll("nonEmptyStrings") String suggestion) {

        Map<String, Object> details = Map.of("key1", "val1", "key2", 42);

        ErrorMessage err = ErrorMessage.builder()
                .type(type)
                .message(message)
                .details(details)
                .suggestion(suggestion)
                .build();

        assert err.getType().equals(type) : "type mismatch";
        assert err.getMessage().equals(message) : "message mismatch";
        assert err.getSuggestion().equals(suggestion) : "suggestion mismatch";
        assert err.getDetails().equals(details) : "details mismatch";
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — ErrorMessage details is unmodifiable")
    void errorMessageDetailsUnmodifiable() {
        Map<String, Object> mutableDetails = new HashMap<>();
        mutableDetails.put("key", "value");

        ErrorMessage err = ErrorMessage.builder()
                .type("TEST_ERROR")
                .message("test message")
                .details(mutableDetails)
                .build();

        // Mutating original map should not affect built object
        mutableDetails.put("extra", "data");
        assert err.getDetails().size() == 1 :
                "Original map mutation affected built object";

        // Returned map should be unmodifiable
        try {
            err.getDetails().put("new_key", "new_value");
            assert false : "Expected UnsupportedOperationException";
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Property(tries = 50)
    @Label("Feature: agent-framework-extraction, Property 4: Builder 模式不可变性 — ErrorMessage has no setter methods")
    void errorMessageHasNoSetters() {
        assertNoSetters(ErrorMessage.class);
    }

    // ---------------------------------------------------------------
    // Helper: assert no public setter methods on a class
    // ---------------------------------------------------------------

    private void assertNoSetters(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && method.getName().startsWith("set")
                    && method.getParameterCount() == 1) {
                assert false : clazz.getSimpleName() + " has public setter: " + method.getName();
            }
        }
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> agentIds() {
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(12)
                .map(s -> "agent-" + s);
    }

    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }

    @Provide
    Arbitrary<List<String>> toolNameLists() {
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(15)
                .map(s -> "tool_" + s)
                .list().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> strategyNames() {
        return Arbitraries.of("react", "plan_and_execute");
    }

    @Provide
    Arbitrary<String> memoryStrategies() {
        return Arbitraries.of("sliding_window", "summarizing", "adaptive");
    }

    @Provide
    Arbitrary<AgentMessage.MessageType> messageTypes() {
        return Arbitraries.of(AgentMessage.MessageType.values());
    }

    @Provide
    Arbitrary<String> correlationIds() {
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(5).ofMaxLength(15)
                .map(s -> "corr-" + s);
    }

    @Provide
    Arbitrary<FunctionCallResult.ResultType> resultTypes() {
        return Arbitraries.of(FunctionCallResult.ResultType.values());
    }

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(5).ofMaxLength(12)
                .map(s -> "session-" + s);
    }

    @Provide
    Arbitrary<String> errorTypes() {
        return Arbitraries.of("TOOL_NOT_FOUND", "VALIDATION_ERROR", "EXECUTION_ERROR", "TIMEOUT");
    }
}
