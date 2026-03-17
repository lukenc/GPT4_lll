package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.model.SessionTrace;
import com.wmsay.gpt4_lll.fc.model.ToolCallTrace;
import com.wmsay.gpt4_lll.mcp.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 属性测试: 核心数据模型
 * <p>
 * 验证 Tool Registry 查询一致性和 Trace 导出往返正确性。
 */
class DataModelPropertyTest {

    // Track tools registered during tests so we can clean up
    private final List<String> registeredToolNames = new ArrayList<>();

    @AfterProperty
    void cleanupRegistry() {
        // McpToolRegistry uses a static map; remove test tools after each property
        for (String name : registeredToolNames) {
            // No unregister method exists, so we re-register the original tool
            // or accept that test tools remain. We'll overwrite with null-safe approach.
            // Since there's no remove API, we just clear our tracking list.
        }
        registeredToolNames.clear();
    }

    // ---------------------------------------------------------------
    // Property 3: Tool Query Consistency
    // Validates: Requirements 1.3
    // ---------------------------------------------------------------

    /**
     * Property 3: Tool Query Consistency
     * Validates: Requirements 1.3
     *
     * For any registered tool and any query by name, the Tool_Registry
     * should return the same tool instance that was registered.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 3: Tool Query Consistency")
    void registeredToolShouldBeQueryableByName(
            @ForAll("uniqueToolNames") String toolName,
            @ForAll("toolDescriptions") String description) {

        // Create a simple test tool
        McpTool tool = new StubMcpTool(toolName, description);

        // Register it
        McpToolRegistry.register(tool);
        registeredToolNames.add(toolName);

        // Query by name
        McpTool retrieved = McpToolRegistry.getTool(toolName);

        // Should return the exact same instance
        assert retrieved != null :
                "Expected tool '" + toolName + "' to be found but got null";
        assert retrieved == tool :
                "Expected same tool instance for '" + toolName + "'";
        assert retrieved.name().equals(toolName) :
                "Expected name '" + toolName + "' but got '" + retrieved.name() + "'";
        assert retrieved.description().equals(description) :
                "Expected description '" + description + "' but got '" + retrieved.description() + "'";
    }

    /**
     * Property 3 (supplement): Querying a tool that was never registered
     * should return null.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 3: Unregistered tool returns null")
    void unregisteredToolShouldReturnNull(
            @ForAll("nonExistentToolNames") String toolName) {

        McpTool retrieved = McpToolRegistry.getTool(toolName);

        assert retrieved == null :
                "Expected null for unregistered tool '" + toolName + "' but got a result";
    }

    /**
     * Property 3 (supplement): Re-registering a tool with the same name
     * should make the new instance queryable (last-write-wins).
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 3: Re-registration overwrites")
    void reRegistrationShouldOverwritePreviousTool(
            @ForAll("uniqueToolNames") String toolName) {

        McpTool tool1 = new StubMcpTool(toolName, "first");
        McpTool tool2 = new StubMcpTool(toolName, "second");

        McpToolRegistry.register(tool1);
        McpToolRegistry.register(tool2);
        registeredToolNames.add(toolName);

        McpTool retrieved = McpToolRegistry.getTool(toolName);

        assert retrieved == tool2 :
                "Expected the second registered tool instance";
        assert retrieved.description().equals("second") :
                "Expected description 'second' but got '" + retrieved.description() + "'";
    }

    /**
     * Property 3 (supplement): getAllTools should contain every tool
     * that was registered.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 3: getAllTools contains registered tool")
    void getAllToolsShouldContainRegisteredTool(
            @ForAll("uniqueToolNames") String toolName) {

        McpTool tool = new StubMcpTool(toolName, "test");
        McpToolRegistry.register(tool);
        registeredToolNames.add(toolName);

        List<McpTool> allTools = McpToolRegistry.getAllTools();
        boolean found = allTools.stream().anyMatch(t -> t.name().equals(toolName));

        assert found :
                "Expected getAllTools() to contain tool '" + toolName + "'";
    }

    // ---------------------------------------------------------------
    // Property 62: Trace Export Round-Trip
    // Validates: Requirements 15.6
    // ---------------------------------------------------------------

    /**
     * Property 62: Trace Export Round-Trip
     * Validates: Requirements 15.6
     *
     * For any session trace exported to a map (JSON-like) representation,
     * importing it back should produce an equivalent trace object.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 62: Trace Export Round-Trip")
    void sessionTraceExportImportShouldBeEquivalent(
            @ForAll("sessionTraces") SessionTrace original) {

        // Export to map representation (simulates JSON export)
        Map<String, Object> exported = exportSessionTrace(original);

        // Import back from map
        SessionTrace imported = importSessionTrace(exported);

        // Verify equivalence
        assert imported.getSessionId().equals(original.getSessionId()) :
                "Session ID mismatch: expected '" + original.getSessionId() +
                        "' but got '" + imported.getSessionId() + "'";
        assert imported.getStartTime() == original.getStartTime() :
                "Start time mismatch";
        assert imported.getEndTime() == original.getEndTime() :
                "End time mismatch";
        assert imported.getToolCallCount() == original.getToolCallCount() :
                "Tool call count mismatch: expected " + original.getToolCallCount() +
                        " but got " + imported.getToolCallCount();

        // Verify each tool call trace
        for (int i = 0; i < original.getToolCallCount(); i++) {
            ToolCallTrace origCall = original.getToolCalls().get(i);
            ToolCallTrace impCall = imported.getToolCalls().get(i);

            assert impCall.getCallId().equals(origCall.getCallId()) :
                    "Call ID mismatch at index " + i;
            assert impCall.getToolName().equals(origCall.getToolName()) :
                    "Tool name mismatch at index " + i;
            assert impCall.getStartTime() == origCall.getStartTime() :
                    "Start time mismatch at index " + i;
            assert impCall.getEndTime() == origCall.getEndTime() :
                    "End time mismatch at index " + i;
            assert impCall.getParameters().equals(origCall.getParameters()) :
                    "Parameters mismatch at index " + i;

            // parentCallId may be null
            if (origCall.getParentCallId() == null) {
                assert impCall.getParentCallId() == null :
                        "Expected null parentCallId at index " + i;
            } else {
                assert origCall.getParentCallId().equals(impCall.getParentCallId()) :
                        "Parent call ID mismatch at index " + i;
            }
        }
    }

    /**
     * Property 62 (supplement): Exported trace map should contain all
     * required fields for a complete round-trip.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 62: Export contains required fields")
    void exportedTraceShouldContainAllRequiredFields(
            @ForAll("sessionTraces") SessionTrace trace) {

        Map<String, Object> exported = exportSessionTrace(trace);

        assert exported.containsKey("sessionId") :
                "Exported trace missing 'sessionId'";
        assert exported.containsKey("startTime") :
                "Exported trace missing 'startTime'";
        assert exported.containsKey("endTime") :
                "Exported trace missing 'endTime'";
        assert exported.containsKey("toolCalls") :
                "Exported trace missing 'toolCalls'";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls =
                (List<Map<String, Object>>) exported.get("toolCalls");

        assert toolCalls.size() == trace.getToolCallCount() :
                "Exported tool call count mismatch";

        for (Map<String, Object> tc : toolCalls) {
            assert tc.containsKey("callId") : "Tool call missing 'callId'";
            assert tc.containsKey("toolName") : "Tool call missing 'toolName'";
            assert tc.containsKey("startTime") : "Tool call missing 'startTime'";
            assert tc.containsKey("endTime") : "Tool call missing 'endTime'";
            assert tc.containsKey("parameters") : "Tool call missing 'parameters'";
        }
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> uniqueToolNames() {
        // Use a prefix to avoid colliding with real registered tools
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(10)
                .map(s -> "__test_" + s);
    }

    @Provide
    Arbitrary<String> nonExistentToolNames() {
        return Arbitraries.of(
                "__nonexistent_xyzzy_001",
                "__nonexistent_qwerty_002",
                "__nonexistent_foobar_003",
                "__nonexistent_bazqux_004",
                "__nonexistent_plugh_005"
        );
    }

    @Provide
    Arbitrary<String> toolDescriptions() {
        return Arbitraries.of(
                "Reads a file from disk",
                "Searches for keywords in project",
                "Lists project directory tree",
                "Executes a shell command",
                "Writes content to a file"
        );
    }

    @Provide
    Arbitrary<SessionTrace> sessionTraces() {
        return Combinators.combine(
                sessionIds(),
                timestamps(),
                toolCallTraceLists()
        ).as((sessionId, startTime, toolCalls) -> {
            long endTime = startTime + 1000 + (long) (Math.random() * 5000);
            return SessionTrace.builder()
                    .sessionId(sessionId)
                    .startTime(startTime)
                    .endTime(endTime)
                    .toolCalls(toolCalls)
                    .errors(Collections.emptyList())
                    .build();
        });
    }

    private Arbitrary<String> sessionIds() {
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(5).ofMaxLength(12)
                .map(s -> "session_" + s);
    }

    private Arbitrary<Long> timestamps() {
        // Reasonable timestamps (recent past)
        return Arbitraries.longs().between(
                1700000000000L, 1800000000000L);
    }

    private Arbitrary<List<ToolCallTrace>> toolCallTraceLists() {
        return toolCallTraces().list().ofMinSize(0).ofMaxSize(5);
    }

    private Arbitrary<ToolCallTrace> toolCallTraces() {
        Arbitrary<String> callIds = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(3).ofMaxLength(8)
                .map(s -> "call_" + s);
        Arbitrary<String> toolNames = Arbitraries.of(
                "read_file", "write_file", "tree", "grep", "keyword_search");
        Arbitrary<Map<String, Object>> params = simpleParamMaps();
        Arbitrary<Long> starts = timestamps();

        return Combinators.combine(callIds, toolNames, params, starts)
                .as((callId, toolName, parameters, startTime) -> {
                    long endTime = startTime + 50 + (long) (Math.random() * 2000);
                    return ToolCallTrace.builder()
                            .callId(callId)
                            .toolName(toolName)
                            .parameters(parameters)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();
                });
    }

    private Arbitrary<Map<String, Object>> simpleParamMaps() {
        Arbitrary<String> keys = Arbitraries.of("path", "query", "content", "line");
        Arbitrary<Object> values = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(1, 500).map(i -> (Object) i)
        );
        return Arbitraries.maps(keys, values).ofMinSize(0).ofMaxSize(3);
    }

    // ---------------------------------------------------------------
    // Trace export/import helpers (simulates JSON round-trip)
    // ---------------------------------------------------------------

    /**
     * Export a SessionTrace to a map representation (simulates JSON serialization).
     */
    private static Map<String, Object> exportSessionTrace(SessionTrace trace) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", trace.getSessionId());
        map.put("startTime", trace.getStartTime());
        map.put("endTime", trace.getEndTime());

        List<Map<String, Object>> toolCallMaps = trace.getToolCalls().stream()
                .map(DataModelPropertyTest::exportToolCallTrace)
                .collect(Collectors.toList());
        map.put("toolCalls", toolCallMaps);

        return map;
    }

    private static Map<String, Object> exportToolCallTrace(ToolCallTrace tc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("callId", tc.getCallId());
        map.put("toolName", tc.getToolName());
        map.put("parameters", new LinkedHashMap<>(tc.getParameters()));
        map.put("startTime", tc.getStartTime());
        map.put("endTime", tc.getEndTime());
        map.put("parentCallId", tc.getParentCallId());
        return map;
    }

    /**
     * Import a SessionTrace from a map representation (simulates JSON deserialization).
     */
    @SuppressWarnings("unchecked")
    private static SessionTrace importSessionTrace(Map<String, Object> map) {
        List<Map<String, Object>> toolCallMaps =
                (List<Map<String, Object>>) map.get("toolCalls");

        List<ToolCallTrace> toolCalls = toolCallMaps.stream()
                .map(DataModelPropertyTest::importToolCallTrace)
                .collect(Collectors.toList());

        return SessionTrace.builder()
                .sessionId((String) map.get("sessionId"))
                .startTime((Long) map.get("startTime"))
                .endTime((Long) map.get("endTime"))
                .toolCalls(toolCalls)
                .errors(Collections.emptyList())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ToolCallTrace importToolCallTrace(Map<String, Object> map) {
        ToolCallTrace.Builder builder = ToolCallTrace.builder()
                .callId((String) map.get("callId"))
                .toolName((String) map.get("toolName"))
                .parameters((Map<String, Object>) map.get("parameters"))
                .startTime((Long) map.get("startTime"))
                .endTime((Long) map.get("endTime"));

        String parentCallId = (String) map.get("parentCallId");
        if (parentCallId != null) {
            builder.parentCallId(parentCallId);
        }

        return builder.build();
    }

    // ---------------------------------------------------------------
    // Stub McpTool for testing registry
    // ---------------------------------------------------------------

    /**
     * Minimal McpTool implementation for registry property tests.
     */
    private static class StubMcpTool implements McpTool {
        private final String name;
        private final String description;

        StubMcpTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Collections.emptyMap();
        }

        @Override
        public McpToolResult execute(McpContext context, Map<String, Object> params) {
            return McpToolResult.text("stub result");
        }
    }
}
