package com.wmsay.gpt4_lll.fc.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.*;

/**
 * Anthropic Tool Use 协议适配器。
 * <p>
 * 支持 Anthropic 原生 Tool Use 格式:
 * <ul>
 *   <li>工具描述: JSON 数组,每个元素包含 name, description, input_schema (JSON Schema)</li>
 *   <li>工具调用解析: 从响应 content 数组中解析 type="tool_use" 块,包含 id, name, input</li>
 *   <li>结果格式化: Message with role="user" 包含 type="tool_result" content 块</li>
 * </ul>
 */
public class AnthropicProtocolAdapter implements ProtocolAdapter {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "anthropic", "claude", "claude-3", "claude-3.5", "claude-3-opus",
            "claude-3-sonnet", "claude-3-haiku", "claude-4"
    );

    private int callIdCounter = 0;

    @Override
    public String getName() {
        return "anthropic";
    }

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) {
            return false;
        }
        String lower = providerName.toLowerCase(Locale.ROOT);
        for (String supported : SUPPORTED_PROVIDERS) {
            if (lower.contains(supported)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object formatToolDescriptions(List<Tool> tools) {
        JSONArray toolsArray = new JSONArray();

        for (Tool tool : tools) {
            JSONObject toolObj = new JSONObject(true);
            toolObj.put("name", tool.name());
            toolObj.put("description", tool.description());
            toolObj.put("input_schema", buildInputSchema(tool.inputSchema()));
            toolsArray.add(toolObj);
        }

        return toolsArray.toJSONString();
    }

    @Override
    public List<ToolCall> parseToolCalls(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JSONObject responseObj = JSON.parseObject(response);
            if (responseObj == null) {
                return Collections.emptyList();
            }
            return parseFromResponseObject(responseObj);
        } catch (JSONException e) {
            // Try parsing as a JSON array of content blocks directly
            try {
                JSONArray contentArray = JSON.parseArray(response);
                if (contentArray != null) {
                    return parseContentBlocks(contentArray);
                }
            } catch (JSONException ignored) {
                // Not valid JSON
            }
            return Collections.emptyList();
        }
    }

    @Override
    public Message formatToolResult(ToolCallResult result) {
        Message message = new Message();
        message.setRole("user");

        JSONArray contentArray = new JSONArray();
        JSONObject toolResultBlock = new JSONObject(true);
        toolResultBlock.put("type", "tool_result");
        toolResultBlock.put("tool_use_id", result.getCallId());

        if (result.isSuccess() && result.getResult() != null) {
            toolResultBlock.put("content", formatSuccessContent(result));
        } else if (result.getError() != null) {
            toolResultBlock.put("content", formatErrorContent(result));
            toolResultBlock.put("is_error", true);
        } else {
            toolResultBlock.put("content", "Tool execution completed with no result.");
        }

        contentArray.add(toolResultBlock);
        message.setContent(contentArray.toJSONString());
        message.setName(result.getToolName());

        return message;
    }

    @Override
    public boolean supportsNativeFunctionCalling() {
        return true;
    }

    // ---- internal helpers ----

    /**
     * Build a JSON Schema object for Anthropic's input_schema field.
     * Anthropic expects: { "type": "object", "properties": {...}, "required": [...] }
     */
    private JSONObject buildInputSchema(Map<String, Object> inputSchema) {
        JSONObject schema = new JSONObject(true);
        schema.put("type", "object");

        if (inputSchema == null || inputSchema.isEmpty()) {
            schema.put("properties", new JSONObject());
            return schema;
        }

        JSONObject properties = new JSONObject(true);
        List<String> requiredFields = new ArrayList<>();

        for (Map.Entry<String, Object> entry : inputSchema.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();

            if (fieldValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldSchema = (Map<String, Object>) fieldValue;
                JSONObject propObj = new JSONObject(true);

                Object type = fieldSchema.get("type");
                if (type != null) {
                    propObj.put("type", type.toString());
                }

                Object description = fieldSchema.get("description");
                if (description != null) {
                    propObj.put("description", description.toString());
                }

                Object enumValues = fieldSchema.get("enum");
                if (enumValues != null) {
                    propObj.put("enum", enumValues);
                }

                Boolean required = (Boolean) fieldSchema.get("required");
                if (Boolean.TRUE.equals(required)) {
                    requiredFields.add(fieldName);
                }

                properties.put(fieldName, propObj);
            }
        }

        schema.put("properties", properties);
        if (!requiredFields.isEmpty()) {
            schema.put("required", requiredFields);
        }

        return schema;
    }

    /**
     * Parse tool calls from an Anthropic response object.
     * Supports top-level content array and nested content within the response.
     */
    private List<ToolCall> parseFromResponseObject(JSONObject responseObj) {
        // Try top-level "content" array (standard Anthropic response format)
        JSONArray content = responseObj.getJSONArray("content");
        if (content != null && !content.isEmpty()) {
            return parseContentBlocks(content);
        }

        return Collections.emptyList();
    }

    /**
     * Parse tool_use blocks from a content array.
     * Each tool_use block: { "type": "tool_use", "id": "...", "name": "...", "input": {...} }
     */
    private List<ToolCall> parseContentBlocks(JSONArray contentArray) {
        List<ToolCall> result = new ArrayList<>();

        for (int i = 0; i < contentArray.size(); i++) {
            JSONObject block = contentArray.getJSONObject(i);
            if (block == null) continue;

            String type = block.getString("type");
            if ("tool_use".equals(type)) {
                ToolCall toolCall = parseSingleToolUse(block);
                if (toolCall != null) {
                    result.add(toolCall);
                }
            }
        }

        return result;
    }

    /**
     * Parse a single tool_use content block.
     */
    private ToolCall parseSingleToolUse(JSONObject block) {
        try {
            String name = block.getString("name");
            if (name == null || name.isEmpty()) {
                return null;
            }

            String id = block.getString("id");
            if (id == null || id.isEmpty()) {
                id = generateCallId();
            }

            Map<String, Object> input = parseInput(block.get("input"));

            return ToolCall.builder()
                    .callId(id)
                    .toolName(name)
                    .parameters(input)
                    .build();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Parse the "input" field from a tool_use block.
     * The input can be a JSONObject or a JSON string.
     */
    private Map<String, Object> parseInput(Object input) {
        if (input == null) {
            return Collections.emptyMap();
        }
        if (input instanceof JSONObject) {
            return (JSONObject) input;
        }
        if (input instanceof String) {
            try {
                Map<String, Object> parsed = JSON.parseObject((String) input);
                return parsed != null ? parsed : Collections.emptyMap();
            } catch (JSONException e) {
                return Collections.emptyMap();
            }
        }
        if (input instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) input;
            return map;
        }
        return Collections.emptyMap();
    }

    private String generateCallId() {
        callIdCounter++;
        return "toolu_" + callIdCounter;
    }

    private String formatSuccessContent(ToolCallResult result) {
        switch (result.getResult().getType()) {
            case TEXT:
                return result.getResult().getTextContent();
            case STRUCTURED:
                return JSON.toJSONString(result.getResult().getStructuredData(), true);
            case ERROR:
                return result.getResult().getErrorMessage();
            default:
                return "";
        }
    }

    private String formatErrorContent(ToolCallResult result) {
        JSONObject errorObj = new JSONObject(true);
        errorObj.put("error", result.getError().getMessage());
        errorObj.put("type", result.getError().getType());
        if (result.getError().getSuggestion() != null) {
            errorObj.put("suggestion", result.getError().getSuggestion());
        }
        return errorObj.toJSONString();
    }
}
