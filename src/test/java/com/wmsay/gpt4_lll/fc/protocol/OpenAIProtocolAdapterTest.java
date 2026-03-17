package com.wmsay.gpt4_lll.fc.protocol;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.model.ErrorMessage;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import com.wmsay.gpt4_lll.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenAIProtocolAdapter.
 * Validates: Requirements 2.1, 8.1, 8.4, 8.6, 8.7
 */
class OpenAIProtocolAdapterTest {

    private OpenAIProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAIProtocolAdapter();
    }

    // ---- getName / supports / supportsNativeFunctionCalling ----

    @Test
    void getName_returnsOpenai() {
        assertEquals("openai", adapter.getName());
    }

    @Test
    void supportsNativeFunctionCalling_returnsTrue() {
        assertTrue(adapter.supportsNativeFunctionCalling());
    }

    @Test
    void supports_openaiProviders() {
        assertTrue(adapter.supports("openai"));
        assertTrue(adapter.supports("gpt-4"));
        assertTrue(adapter.supports("gpt-3.5"));
        assertTrue(adapter.supports("gpt-4o"));
        assertTrue(adapter.supports("gpt-4-turbo"));
        assertTrue(adapter.supports("gpt-4o-mini"));
    }

    @Test
    void supports_caseInsensitive() {
        assertTrue(adapter.supports("OpenAI"));
        assertTrue(adapter.supports("GPT-4"));
        assertTrue(adapter.supports("GPT-4o"));
    }

    @Test
    void supports_containsMatch() {
        assertTrue(adapter.supports("my-openai-provider"));
        assertTrue(adapter.supports("provider-gpt-4-custom"));
    }

    @Test
    void supports_rejectsNonOpenai() {
        assertFalse(adapter.supports("anthropic"));
        assertFalse(adapter.supports("claude"));
        assertFalse(adapter.supports("gemini"));
        assertFalse(adapter.supports(null));
    }

    // ---- formatToolDescriptions ----

    @Test
    void formatToolDescriptions_singleTool() {
        McpTool tool = createMcpTool("read_file", "Read a file",
                Map.of("path", Map.of("type", "string", "description", "File path", "required", true)));

        String result = (String) adapter.formatToolDescriptions(List.of(tool));
        JSONArray arr = JSON.parseArray(result);

        assertEquals(1, arr.size());
        JSONObject toolObj = arr.getJSONObject(0);
        assertEquals("function", toolObj.getString("type"));

        JSONObject func = toolObj.getJSONObject("function");
        assertEquals("read_file", func.getString("name"));
        assertEquals("Read a file", func.getString("description"));

        JSONObject params = func.getJSONObject("parameters");
        assertEquals("object", params.getString("type"));
        assertNotNull(params.getJSONObject("properties").getJSONObject("path"));
        assertTrue(params.getJSONArray("required").contains("path"));
    }

    @Test
    void formatToolDescriptions_multipleTools() {
        McpTool tool1 = createMcpTool("read_file", "Read a file", Map.of());
        McpTool tool2 = createMcpTool("tree", "List directory tree", Map.of());

        String result = (String) adapter.formatToolDescriptions(List.of(tool1, tool2));
        JSONArray arr = JSON.parseArray(result);

        assertEquals(2, arr.size());
        assertEquals("read_file", arr.getJSONObject(0).getJSONObject("function").getString("name"));
        assertEquals("tree", arr.getJSONObject(1).getJSONObject("function").getString("name"));
    }

    @Test
    void formatToolDescriptions_emptyList() {
        String result = (String) adapter.formatToolDescriptions(List.of());
        JSONArray arr = JSON.parseArray(result);
        assertEquals(0, arr.size());
    }

    @Test
    void formatToolDescriptions_nullSchema() {
        McpTool tool = createMcpTool("simple_tool", "A simple tool", null);
        String result = (String) adapter.formatToolDescriptions(List.of(tool));
        JSONArray arr = JSON.parseArray(result);

        JSONObject params = arr.getJSONObject(0).getJSONObject("function").getJSONObject("parameters");
        assertEquals("object", params.getString("type"));
        assertNotNull(params.getJSONObject("properties"));
    }

    // ---- parseToolCalls ----

    @Test
    void parseToolCalls_topLevelToolCalls() {
        String response = buildTopLevelToolCallsJson("call_123", "read_file", "{\"path\":\"src/Main.java\"}");
        List<ToolCall> calls = adapter.parseToolCalls(response);

        assertEquals(1, calls.size());
        assertEquals("call_123", calls.get(0).getCallId());
        assertEquals("read_file", calls.get(0).getToolName());
        assertEquals("src/Main.java", calls.get(0).getParameters().get("path"));
    }

    @Test
    void parseToolCalls_choicesFormat() {
        String response = buildChoicesFormatJson("call_456", "tree", "{\"path\":\".\"}");
        List<ToolCall> calls = adapter.parseToolCalls(response);

        assertEquals(1, calls.size());
        assertEquals("call_456", calls.get(0).getCallId());
        assertEquals("tree", calls.get(0).getToolName());
        assertEquals(".", calls.get(0).getParameters().get("path"));
    }

    @Test
    void parseToolCalls_multipleToolCalls() {
        JSONObject response = new JSONObject();
        JSONArray toolCalls = new JSONArray();
        toolCalls.add(buildToolCallObj("call_1", "read_file", "{\"path\":\"a.txt\"}"));
        toolCalls.add(buildToolCallObj("call_2", "tree", "{\"path\":\".\"}"));
        response.put("tool_calls", toolCalls);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(2, calls.size());
        assertEquals("call_1", calls.get(0).getCallId());
        assertEquals("read_file", calls.get(0).getToolName());
        assertEquals("call_2", calls.get(1).getCallId());
        assertEquals("tree", calls.get(1).getToolName());
    }

    @Test
    void parseToolCalls_nullResponse() {
        assertEquals(Collections.emptyList(), adapter.parseToolCalls(null));
    }

    @Test
    void parseToolCalls_emptyResponse() {
        assertEquals(Collections.emptyList(), adapter.parseToolCalls(""));
    }

    @Test
    void parseToolCalls_nonJsonResponse() {
        assertEquals(Collections.emptyList(), adapter.parseToolCalls("Hello, how can I help?"));
    }

    @Test
    void parseToolCalls_noToolCallsInResponse() {
        assertEquals(Collections.emptyList(), adapter.parseToolCalls("{\"content\":\"Hello\"}"));
    }

    @Test
    void parseToolCalls_missingFunctionName() {
        JSONObject response = new JSONObject();
        JSONArray toolCalls = new JSONArray();
        JSONObject tc = new JSONObject();
        tc.put("id", "call_1");
        tc.put("type", "function");
        JSONObject func = new JSONObject();
        func.put("arguments", "{}");
        // no "name" field
        tc.put("function", func);
        toolCalls.add(tc);
        response.put("tool_calls", toolCalls);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertTrue(calls.isEmpty());
    }

    @Test
    void parseToolCalls_emptyArguments() {
        String response = buildTopLevelToolCallsJson("call_1", "simple_tool", "");
        List<ToolCall> calls = adapter.parseToolCalls(response);

        assertEquals(1, calls.size());
        assertTrue(calls.get(0).getParameters().isEmpty());
    }

    @Test
    void parseToolCalls_nullArguments() {
        JSONObject response = new JSONObject();
        JSONArray toolCalls = new JSONArray();
        JSONObject tc = new JSONObject();
        tc.put("id", "call_1");
        tc.put("type", "function");
        JSONObject func = new JSONObject();
        func.put("name", "simple_tool");
        // no arguments field
        tc.put("function", func);
        toolCalls.add(tc);
        response.put("tool_calls", toolCalls);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(1, calls.size());
        assertTrue(calls.get(0).getParameters().isEmpty());
    }

    @Test
    void parseToolCalls_generatesIdWhenMissing() {
        JSONObject response = new JSONObject();
        JSONArray toolCalls = new JSONArray();
        JSONObject tc = new JSONObject();
        // no "id" field
        tc.put("type", "function");
        JSONObject func = new JSONObject();
        func.put("name", "read_file");
        func.put("arguments", "{\"path\":\"test.txt\"}");
        tc.put("function", func);
        toolCalls.add(tc);
        response.put("tool_calls", toolCalls);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(1, calls.size());
        assertNotNull(calls.get(0).getCallId());
        assertFalse(calls.get(0).getCallId().isEmpty());
    }

    // ---- formatToolResult ----

    @Test
    void formatToolResult_success_textResult() {
        ToolCallResult result = ToolCallResult.success(
                "call_1", "read_file",
                McpToolResult.text("file content here"), 100L);

        Message msg = adapter.formatToolResult(result);

        assertEquals("tool", msg.getRole());
        assertEquals("call_1", msg.getToolCallId());
        assertEquals("read_file", msg.getName());
        assertEquals("file content here", msg.getContent());
    }

    @Test
    void formatToolResult_success_structuredResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);
        data.put("items", List.of("a", "b"));
        ToolCallResult result = ToolCallResult.success(
                "call_2", "search",
                McpToolResult.structured(data), 200L);

        Message msg = adapter.formatToolResult(result);

        assertEquals("tool", msg.getRole());
        assertEquals("call_2", msg.getToolCallId());
        assertNotNull(msg.getContent());
        assertTrue(msg.getContent().contains("count"));
    }

    @Test
    void formatToolResult_error() {
        ErrorMessage error = ErrorMessage.builder()
                .type("tool_not_found")
                .message("Tool 'xyz' not found")
                .suggestion("Did you mean 'read_file'?")
                .build();
        ToolCallResult result = ToolCallResult.executionError("call_3", "xyz", error, 50L);

        Message msg = adapter.formatToolResult(result);

        assertEquals("tool", msg.getRole());
        assertEquals("call_3", msg.getToolCallId());
        assertEquals("xyz", msg.getName());
        // Error content should be JSON with error details
        JSONObject errorContent = JSON.parseObject(msg.getContent());
        assertEquals("Tool 'xyz' not found", errorContent.getString("error"));
        assertEquals("tool_not_found", errorContent.getString("type"));
        assertEquals("Did you mean 'read_file'?", errorContent.getString("suggestion"));
    }

    @Test
    void formatToolResult_noResultNoError() {
        ToolCallResult result = ToolCallResult.builder()
                .callId("call_4")
                .toolName("noop")
                .status(ToolCallResult.ResultStatus.SUCCESS)
                .build();

        Message msg = adapter.formatToolResult(result);

        assertEquals("tool", msg.getRole());
        assertEquals("Tool execution completed with no result.", msg.getContent());
    }

    // ---- helper methods ----

    private String buildTopLevelToolCallsJson(String id, String name, String arguments) {
        JSONObject response = new JSONObject();
        JSONArray toolCalls = new JSONArray();
        toolCalls.add(buildToolCallObj(id, name, arguments));
        response.put("tool_calls", toolCalls);
        return response.toJSONString();
    }

    private String buildChoicesFormatJson(String id, String name, String arguments) {
        JSONObject response = new JSONObject();
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        JSONObject message = new JSONObject();
        JSONArray toolCalls = new JSONArray();
        toolCalls.add(buildToolCallObj(id, name, arguments));
        message.put("tool_calls", toolCalls);
        message.put("role", "assistant");
        choice.put("message", message);
        choices.add(choice);
        response.put("choices", choices);
        return response.toJSONString();
    }

    private JSONObject buildToolCallObj(String id, String name, String arguments) {
        JSONObject tc = new JSONObject();
        tc.put("id", id);
        tc.put("type", "function");
        JSONObject func = new JSONObject();
        func.put("name", name);
        func.put("arguments", arguments);
        tc.put("function", func);
        return tc;
    }

    private McpTool createMcpTool(String name, String description, Map<String, Object> schema) {
        return new McpTool() {
            @Override
            public String name() { return name; }
            @Override
            public String description() { return description; }
            @Override
            public Map<String, Object> inputSchema() { return schema; }
            @Override
            public McpToolResult execute(McpContext ctx, Map<String, Object> params) {
                return McpToolResult.text("ok");
            }
        };
    }
}
