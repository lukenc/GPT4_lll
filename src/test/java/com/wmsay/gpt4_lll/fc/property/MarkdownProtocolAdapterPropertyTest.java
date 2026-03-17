package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.protocol.MarkdownProtocolAdapter;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 属性测试: MarkdownProtocolAdapter
 * <p>
 * 验证 Markdown 代码块格式的工具调用解析器的正确性属性。
 */
class MarkdownProtocolAdapterPropertyTest {

    // ---------------------------------------------------------------
    // Property 6: Multi-Format Parsing Support
    // ---------------------------------------------------------------

    /**
     * Property 6: Multi-Format Parsing Support
     * Validates: Requirements 2.1, 2.2, 2.5
     *
     * For any valid tool call in Markdown format, the parser should
     * successfully extract the tool name, call ID, and parameters.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 6: Multi-Format Parsing Support")
    void validMarkdownToolCallShouldBeParsedCorrectly(
            @ForAll("toolNames") String toolName,
            @ForAll("callIds") String callId,
            @ForAll("parameterMaps") Map<String, Object> params) {

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();

        String paramsJson = toJson(params);
        String response = buildToolCallBlock(callId, toolName, paramsJson);

        List<ToolCall> parsed = adapter.parseToolCalls(response);

        assert parsed.size() == 1 :
                "Expected 1 tool call but got " + parsed.size();

        ToolCall tc = parsed.get(0);
        assert tc.getToolName().equals(toolName) :
                "Expected tool name '" + toolName + "' but got '" + tc.getToolName() + "'";
        assert tc.getCallId().equals(callId) :
                "Expected call ID '" + callId + "' but got '" + tc.getCallId() + "'";
    }

    /**
     * Property 6 (supplement): Tool call with no explicit ID should still parse
     * and receive a generated call ID.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 6: Auto-generated Call ID")
    void toolCallWithoutIdShouldReceiveGeneratedId(
            @ForAll("toolNames") String toolName,
            @ForAll("parameterMaps") Map<String, Object> params) {

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();

        // Build a tool_call block without an "id" field
        String paramsJson = toJson(params);
        String response = "```tool_call\n" +
                "{\n" +
                "  \"name\": \"" + escapeJson(toolName) + "\",\n" +
                "  \"parameters\": " + paramsJson + "\n" +
                "}\n" +
                "```";

        List<ToolCall> parsed = adapter.parseToolCalls(response);

        assert parsed.size() == 1 :
                "Expected 1 tool call but got " + parsed.size();

        ToolCall tc = parsed.get(0);
        assert tc.getToolName().equals(toolName) :
                "Expected tool name '" + toolName + "' but got '" + tc.getToolName() + "'";
        assert tc.getCallId() != null && !tc.getCallId().isEmpty() :
                "Expected a generated call ID but got null/empty";
    }

    /**
     * Property 6 (supplement): Null and empty responses should return empty list.
     * Validates: Requirement 2.7
     */
    @Property(tries = 10)
    @Label("Feature: function-calling-enhancement, Property 6: Empty response returns empty list")
    void emptyOrNullResponseShouldReturnEmptyList(
            @ForAll("emptyResponses") String response) {

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();
        List<ToolCall> parsed = adapter.parseToolCalls(response);

        assert parsed.isEmpty() :
                "Expected empty list for empty/null response but got " + parsed.size() + " calls";
    }

    /**
     * Property 6 (supplement): Malformed JSON inside tool_call block should be
     * skipped without error.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 6: Malformed JSON skipped gracefully")
    void malformedJsonInsideToolCallBlockShouldBeSkipped(
            @ForAll("malformedJson") String badJson) {

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();

        String response = "```tool_call\n" + badJson + "\n```";
        List<ToolCall> parsed = adapter.parseToolCalls(response);

        // Should not throw, and should return empty (or skip the bad block)
        assert parsed.isEmpty() :
                "Expected empty list for malformed JSON but got " + parsed.size() + " calls";
    }

    // ---------------------------------------------------------------
    // Property 7: Multiple Tool Calls Ordering
    // ---------------------------------------------------------------

    /**
     * Property 7: Multiple Tool Calls Ordering
     * Validates: Requirements 2.3
     *
     * For any LLM response containing multiple tool calls, the parser should
     * extract all calls in the order they appear in the response.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 7: Multiple Tool Calls Ordering")
    void multipleToolCallsShouldBeExtractedInOrder(
            @ForAll("toolCallSpecs") List<ToolCallSpec> specs) {

        Assume.that(specs.size() >= 2);

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();

        // Build a response with multiple tool_call blocks
        StringBuilder response = new StringBuilder();
        response.append("Here are the tool calls I want to make:\n\n");
        for (ToolCallSpec spec : specs) {
            response.append(buildToolCallBlock(spec.callId, spec.toolName, toJson(spec.params)));
            response.append("\n\nSome text between calls.\n\n");
        }

        List<ToolCall> parsed = adapter.parseToolCalls(response.toString());

        assert parsed.size() == specs.size() :
                "Expected " + specs.size() + " tool calls but got " + parsed.size();

        // Verify ordering: each parsed call should match the spec at the same index
        for (int i = 0; i < specs.size(); i++) {
            ToolCallSpec expected = specs.get(i);
            ToolCall actual = parsed.get(i);

            assert actual.getToolName().equals(expected.toolName) :
                    "At index " + i + ": expected tool '" + expected.toolName +
                            "' but got '" + actual.getToolName() + "'";
            assert actual.getCallId().equals(expected.callId) :
                    "At index " + i + ": expected call ID '" + expected.callId +
                            "' but got '" + actual.getCallId() + "'";
        }
    }

    /**
     * Property 7 (supplement): Count of parsed tool calls should equal
     * the count of valid tool_call blocks in the response.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 7: Parsed count equals block count")
    void parsedCountShouldEqualValidBlockCount(
            @ForAll("toolCallSpecs") List<ToolCallSpec> specs) {

        MarkdownProtocolAdapter adapter = new MarkdownProtocolAdapter();

        StringBuilder response = new StringBuilder();
        for (ToolCallSpec spec : specs) {
            response.append(buildToolCallBlock(spec.callId, spec.toolName, toJson(spec.params)));
            response.append("\n");
        }

        List<ToolCall> parsed = adapter.parseToolCalls(response.toString());

        assert parsed.size() == specs.size() :
                "Expected " + specs.size() + " tool calls but got " + parsed.size();
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> toolNames() {
        return Arbitraries.of(
                "read_file", "write_file", "tree", "grep", "keyword_search",
                "execute_command", "list_files", "create_dir", "delete_file",
                "search_replace"
        );
    }

    @Provide
    Arbitrary<String> callIds() {
        return Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(12)
                .map(s -> "call_" + s);
    }

    @Provide
    Arbitrary<Map<String, Object>> parameterMaps() {
        Arbitrary<String> keys = Arbitraries.of("path", "query", "content", "line", "recursive");
        Arbitrary<Object> values = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30).map(s -> (Object) s),
                Arbitraries.integers().between(1, 1000).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return Arbitraries.maps(keys, values).ofMinSize(0).ofMaxSize(4);
    }

    @Provide
    Arbitrary<String> emptyResponses() {
        return Arbitraries.of(
                "",
                "   ",
                "Hello, I can help you with that.",
                "No tool calls here, just plain text.\nAnother line.",
                "```java\nSystem.out.println(\"not a tool call\");\n```"
        );
    }

    @Provide
    Arbitrary<String> malformedJson() {
        return Arbitraries.of(
                "{invalid json",
                "not json at all",
                "{\"name\": }",
                "{ \"name\": \"tool\", \"parameters\": {broken}",
                "",
                "   ",
                "[]",
                "{}"  // valid JSON but missing required "name" field
        );
    }

    @Provide
    Arbitrary<List<ToolCallSpec>> toolCallSpecs() {
        return toolCallSpec().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<ToolCallSpec> toolCallSpec() {
        return Combinators.combine(toolNames(), callIds(), parameterMaps())
                .as(ToolCallSpec::new);
    }

    // ---------------------------------------------------------------
    // Helper types and methods
    // ---------------------------------------------------------------

    /** Simple holder for a tool call specification used in generators. */
    static class ToolCallSpec {
        final String toolName;
        final String callId;
        final Map<String, Object> params;

        ToolCallSpec(String toolName, String callId, Map<String, Object> params) {
            this.toolName = toolName;
            this.callId = callId;
            this.params = params;
        }
    }

    /** Build a ```tool_call block from parts. */
    private static String buildToolCallBlock(String callId, String toolName, String paramsJson) {
        return "```tool_call\n" +
                "{\n" +
                "  \"id\": \"" + escapeJson(callId) + "\",\n" +
                "  \"name\": \"" + escapeJson(toolName) + "\",\n" +
                "  \"parameters\": " + paramsJson + "\n" +
                "}\n" +
                "```";
    }

    /** Minimal JSON escaping for string values. */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Convert a Map to a JSON string using simple serialization. */
    private static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\": ");
            Object v = entry.getValue();
            if (v instanceof String) {
                sb.append("\"").append(escapeJson((String) v)).append("\"");
            } else {
                sb.append(v);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
