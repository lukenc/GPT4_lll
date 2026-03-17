package com.wmsay.gpt4_lll.fc.protocol;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.model.Message;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 代码块格式的协议适配器。
 * <p>
 * 使用自定义 Markdown 代码块格式进行工具调用,适用于不支持原生
 * function calling 的供应商。这是向后兼容的降级方案。
 * <p>
 * 工具调用格式:
 * <pre>
 * ```tool_call
 * {
 *   "id": "call_001",
 *   "name": "read_file",
 *   "parameters": { "path": "src/Main.java" }
 * }
 * ```
 * </pre>
 */
public class MarkdownProtocolAdapter implements ProtocolAdapter {

    /**
     * 匹配 ```tool_call ... ``` 代码块。
     * 支持 tool_call 后可选的空白,以及块内任意内容(非贪婪)。
     */
    static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "```tool_call\\s*\\n(.*?)\\n```",
            Pattern.DOTALL
    );

    private int callIdCounter = 0;

    @Override
    public String getName() {
        return "markdown";
    }

    @Override
    public boolean supports(String providerName) {
        // Markdown 格式是通用降级方案,支持所有供应商
        return true;
    }

    @Override
    public Object formatToolDescriptions(List<McpTool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Available Tools\n\n");
        sb.append("You can call tools using the following format:\n\n");
        sb.append("```tool_call\n");
        sb.append("{\n");
        sb.append("  \"id\": \"call_001\",\n");
        sb.append("  \"name\": \"tool_name\",\n");
        sb.append("  \"parameters\": { ... }\n");
        sb.append("}\n");
        sb.append("```\n\n");

        for (McpTool tool : tools) {
            sb.append("## ").append(tool.name()).append("\n");
            sb.append(tool.description()).append("\n");
            sb.append("Parameters: `").append(formatSchema(tool.inputSchema())).append("`\n\n");
        }

        return sb.toString();
    }

    @Override
    public List<ToolCall> parseToolCalls(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(response);

        while (matcher.find()) {
            String jsonContent = matcher.group(1).trim();
            ToolCall toolCall = parseToolCallJson(jsonContent);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }

        return toolCalls;
    }

    @Override
    public Message formatToolResult(ToolCallResult result) {
        Message message = new Message();
        message.setRole("tool");
        message.setName(result.getToolName());

        if (result.isSuccess() && result.getResult() != null) {
            message.setContent(formatSuccessContent(result));
        } else if (result.getError() != null) {
            message.setContent(formatErrorContent(result));
        } else {
            message.setContent("Tool execution completed with no result.");
        }

        return message;
    }

    @Override
    public boolean supportsNativeFunctionCalling() {
        return false;
    }

    // ---- internal helpers ----

    private ToolCall parseToolCallJson(String jsonContent) {
        try {
            Map<String, Object> parsed = JSON.parseObject(jsonContent);
            if (parsed == null) {
                return null;
            }

            String name = getStringField(parsed, "name");
            if (name == null || name.isEmpty()) {
                return null;
            }

            String id = getStringField(parsed, "id");
            if (id == null || id.isEmpty()) {
                id = generateCallId();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) parsed.get("parameters");
            if (parameters == null) {
                parameters = Collections.emptyMap();
            }

            return ToolCall.builder()
                    .callId(id)
                    .toolName(name)
                    .parameters(parameters)
                    .build();

        } catch (JSONException e) {
            // Malformed JSON inside tool_call block — skip this block
            return null;
        }
    }

    private String getStringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String generateCallId() {
        callIdCounter++;
        return "md_call_" + callIdCounter;
    }

    private String formatSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }
        return JSON.toJSONString(schema);
    }

    private String formatSuccessContent(ToolCallResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Tool Result: ").append(result.getToolName()).append("]\n");
        sb.append("Call ID: ").append(result.getCallId()).append("\n");
        sb.append("Status: SUCCESS\n");

        switch (result.getResult().getType()) {
            case TEXT:
                sb.append("Result:\n").append(result.getResult().getTextContent());
                break;
            case STRUCTURED:
                sb.append("Result:\n").append(
                        JSON.toJSONString(result.getResult().getStructuredData(), true));
                break;
            case ERROR:
                sb.append("Error: ").append(result.getResult().getErrorMessage());
                break;
        }

        return sb.toString();
    }

    private String formatErrorContent(ToolCallResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Tool Error: ").append(result.getToolName()).append("]\n");
        sb.append("Call ID: ").append(result.getCallId()).append("\n");
        sb.append("Status: ").append(result.getStatus()).append("\n");
        sb.append("Error: ").append(result.getError().getMessage()).append("\n");

        if (result.getError().getSuggestion() != null) {
            sb.append("Suggestion: ").append(result.getError().getSuggestion());
        }

        return sb.toString();
    }
}
