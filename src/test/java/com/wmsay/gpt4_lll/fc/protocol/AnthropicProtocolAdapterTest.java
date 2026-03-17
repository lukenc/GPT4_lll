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
 * Unit tests for AnthropicProtocolAdapter.
 * Validates: Requirements 2.1, 8.2, 8.4, 8.6, 8.7
 */
class AnthropicProtocolAdapterTest {

    private AnthropicProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AnthropicProtocolAdapter();
    }

    // ---- getName / supports / supportsNativeFunctionCalling ----

    @Test
    void getName_returnsAnthropic() {
        assertEquals("anthropic", adapter.getName());
    }

    @Test
    void supportsNativeFunctionCalling_returnsTrue() {
        assertTrue(adapter.supportsNativeFunctionCalling());
    }

    @Test
    void supports_anthropicProviders() {
        assertTrue(adapter.supports("anthropic"));
        assertTrue(adapter.supports("claude"));
        assertTrue(adapter.supports("claude-3"));
        assertTrue(adapter.supports("claude-3.5"));
        assertTrue(adapter.supports("claude-3-opus"));
        assertTrue(adapter.supports("claude-3-sonnet"));
        assertTrue(adapter.supports("claude-3-haiku"));
    }

    @Test
    void supports_caseInsensitive() {
        assertTrue(adapter.supports("Anthropic"));
        assertTrue(adapter.supports("CLAUDE"));
        assertTrue(adapter.supports("Claude-3"));
    }

    @Test
    void supports_containsMatch() {
        assertTrue(adapter.supports("my-anthropic-provider"));
        assertTrue(adapter.supports("provider-claude-custom"));
    }

    @Test
    void supports_rejectsNonAnthropic() {
        assertFalse(adapter.supports("openai"));
        assertFalse(adapter.supports("gpt-4"));
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
        assertEquals("read_file", toolObj.getString("name"));
        assertEquals("Read a file", toolObj.getString("description"));

        JSONObject inputSchema = toolObj.getJSONObject("input_schema");
        assertEquals("object", inputSchema.getString("type"));
        assertNotNull(inputSchema.getJSONObject("properties").getJSONObject("path"));
        assertTrue(inputSchema.getJSONArray("required").contains("path"));
    }

    @Test
    void formatToolDescriptions_multipleTools() {
        McpTool tool1 = createMcpTool("read_file", "Read a file", Map.of());
        McpTool tool2 = createMcpTool("tree", "List directory tree", Map.of());

        String result = (String) adapter.formatToolDescriptions(List.of(tool1, tool2));
        JSONArray arr = JSON.parseArray(result);

        assertEquals(2, arr.size());
        assertEquals("read_file", arr.getJSONObject(0).getString("name"));
        assertEquals("tree", arr.getJSONObject(1).getString("name"));
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

        JSONObject inputSchema = arr.getJSONObject(0).getJSONObject("input_schema");
        assertEquals("object", inputSchema.getString("type"));
        assertNotNull(inputSchema.getJSONObject("properties"));
    }

    @Test
    void formatToolDescriptions_noFunctionWrapper() {
        // Anthropic format should NOT have "type":"function" wrapper like OpenAI
        McpTool tool = createMcpTool("test_tool", "Test", Map.of());
        String result = (String) adapter.formatToolDescriptions(List.of(tool));
        JSONArray arr = JSON.parseArray(result);

        JSONObject toolObj = arr.getJSONObject(0);
        assertNull(toolObj.getString("type"));
        assertNull(toolObj.getJSONObject("function"));
        assertNotNull(toolObj.getString("name"));
        assertNotNull(toolObj.getJSONObject("input_schema"));
    }

    // ---- parseToolCalls ----

    @Test
    void parseToolCalls_singleToolUse() {
        String response = buildAnthropicResponse("toolu_123", "read_file",
                new JSONObject(Map.of("path", "src/Main.java")));
        List<ToolCall> calls = adapter.parseToolCalls(response);

        assertEquals(1, calls.size());
        assertEquals("toolu_123", calls.get(0).getCallId());
        assertEquals("read_file", calls.get(0).getToolName());
        assertEquals("src/Main.java", calls.get(0).getParameters().get("path"));
    }

    @Test
    void parseToolCalls_multipleToolUseBlocks() {
        JSONObject response = new JSONObject();
        JSONArray content = new JSONArray();
        content.add(buildToolUseBlock("toolu_1", "read_file",
                new JSONObject(Map.of("path", "a.txt"))));
        content.add(buildTextBlock("Let me also check the tree."));
        content.add(buildToolUseBlock("toolu_2", "tree",
                new JSONObject(Map.of("path", "."))));
        response.put("content", content);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(2, calls.size());
        assertEquals("toolu_1", calls.get(0).getCallId());
        assertEquals("read_file", calls.get(0).getToolName());
        assertEquals("toolu_2", calls.get(1).getCallId());
        assertEquals("tree", calls.get(1).getToolName());
    }

    @Test
    void parseToolCalls_mixedContentBlocks() {
        JSONObject response = new JSONObject();
        JSONArray content = new JSONArray();
        content.add(buildTextBlock("I'll read the file for you."));
        content.add(buildToolUseBlock("toolu_1", "read_file",
                new JSONObject(Map.of("path", "test.txt"))));
        response.put("content", content);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(1, calls.size());
        assertEquals("read_file", calls.get(0).getToolName());
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
    void parseToolCalls_noToolUseInContent() {
        JSONObject response = new JSONObject();
        JSONArray content = new JSONArray();
        content.add(buildTextBlock("Just a text response."));
        response.put("content", content);

        assertEquals(Collections.emptyList(), adapter.parseToolCalls(response.toJSONString()));
    }

    @Test
    void parseToolCalls_missingName() {
        JSONObject response = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject block = new JSONObject();
        block.put("type", "tool_use");
        block.put("id", "toolu_1");
        block.put("input", new JSONObject());
        // no "name" field
        content.add(block);
        response.put("content", content);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertTrue(calls.isEmpty());
    }

    @Test
    void parseToolCalls_nullInput() {
        JSONObject response = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject block = new JSONObject();
        block.put("type", "tool_use");
        block.put("id", "toolu_1");
        block.put("name", "simple_tool");
        // no "input" field
        content.add(block);
        response.put("content", content);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(1, calls.size());
        assertTrue(calls.get(0).getParameters().isEmpty());
    }

    @Test
    void parseToolCalls_generatesIdWhenMissing() {
        JSONObject response = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject block = new JSONObject();
        block.put("type", "tool_use");
        block.put("name", "read_file");
        block.put("input", new JSONObject(Map.of("path", "test.txt")));
        // no "id" field
        content.add(block);
        response.put("content", content);

        List<ToolCall> calls = adapter.parseToolCalls(response.toJSONString());
        assertEquals(1, calls.size());
        assertNotNull(calls.get(0).getCallId());
        assertFalse(calls.get(0).getCallId().isEmpty());
    }

    @Test
    void parseToolCalls_contentArrayDirectly() {
        // Parse a raw content array (not wrapped in response object)
        JSONArray content = new JSONArray();
        content.add(buildToolUseBlock("toolu_1", "read_file",
                new JSONObject(Map.of("path", "test.txt"))));

        List<ToolCall> calls = adapter.parseToolCalls(content.toJSONString());
        assertEquals(1, calls.size());
        assertEquals("read_file", calls.get(0).getToolName());
    }

    // ---- formatToolResult ----

    @Test
    void formatToolResult_success_textResult() {
        ToolCallResult result = ToolCallResult.success(
                "toolu_1", "read_file",
                McpToolResult.text("file content here"), 100L);

        Message msg = adapter.formatToolResult(result);

        assertEquals("user", msg.getRole());
        String content = msg.getContent();
        JSONArray contentArr = JSON.parseArray(content);
        assertEquals(1, contentArr.size());

        JSONObject block = contentArr.getJSONObject(0);
        assertEquals("tool_result", block.getString("type"));
        assertEquals("toolu_1", block.getString("tool_use_id"));
        assertEquals("file content here", block.getString("content"));
        assertNull(block.getBoolean("is_error"));
    }

    @Test
    void formatToolResult_success_structuredResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", 5);
        data.put("items", List.of("a", "b"));
        ToolCallResult result = ToolCallResult.success(
                "toolu_2", "search",
                McpToolResult.structured(data), 200L);

        Message msg = adapter.formatToolResult(result);

        assertEquals("user", msg.getRole());
        JSONArray contentArr = JSON.parseArray(msg.getContent());
        JSONObject block = contentArr.getJSONObject(0);
        assertEquals("tool_result", block.getString("type"));
        assertEquals("toolu_2", block.getString("tool_use_id"));
        assertNotNull(block.getString("content"));
        assertTrue(block.getString("content").contains("count"));
    }

    @Test
    void formatToolResult_error() {
        ErrorMessage error = ErrorMessage.builder()
                .type("tool_not_found")
                .message("Tool 'xyz' not found")
                .suggestion("Did you mean 'read_file'?")
                .build();
        ToolCallResult result = ToolCallResult.executionError("toolu_3", "xyz", error, 50L);

        Message msg = adapter.formatToolResult(result);

        assertEquals("user", msg.getRole());
        JSONArray contentArr = JSON.parseArray(msg.getContent());
        JSONObject block = contentArr.getJSONObject(0);
        assertEquals("tool_result", block.getString("type"));
        assertEquals("toolu_3", block.getString("tool_use_id"));
        assertTrue(block.getBoolean("is_error"));

        JSONObject errorContent = JSON.parseObject(block.getString("content"));
        assertEquals("Tool 'xyz' not found", errorContent.getString("error"));
        assertEquals("tool_not_found", errorContent.getString("type"));
    }

    @Test
    void formatToolResult_noResultNoError() {
        ToolCallResult result = ToolCallResult.builder()
                .callId("toolu_4")
                .toolName("noop")
                .status(ToolCallResult.ResultStatus.SUCCESS)
                .build();

        Message msg = adapter.formatToolResult(result);

        assertEquals("user", msg.getRole());
        JSONArray contentArr = JSON.parseArray(msg.getContent());
        JSONObject block = contentArr.getJSONObject(0);
        assertEquals("tool_result", block.getString("type"));
        assertEquals("Tool execution completed with no result.", block.getString("content"));
    }

    // ---- helper methods ----

    private String buildAnthropicResponse(String id, String name, JSONObject input) {
        JSONObject response = new JSONObject();
        response.put("role", "assistant");
        JSONArray content = new JSONArray();
        content.add(buildToolUseBlock(id, name, input));
        response.put("content", content);
        return response.toJSONString();
    }

    private JSONObject buildToolUseBlock(String id, String name, JSONObject input) {
        JSONObject block = new JSONObject(true);
        block.put("type", "tool_use");
        block.put("id", id);
        block.put("name", name);
        block.put("input", input);
        return block;
    }

    private JSONObject buildTextBlock(String text) {
        JSONObject block = new JSONObject(true);
        block.put("type", "text");
        block.put("text", text);
        return block;
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
