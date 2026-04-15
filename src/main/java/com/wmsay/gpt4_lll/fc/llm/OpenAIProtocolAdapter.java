package com.wmsay.gpt4_lll.fc.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.intellij.openapi.diagnostic.Logger;

import java.util.*;

/**
 * OpenAI Function Calling 协议适配器。
 * <p>
 * 支持 OpenAI 原生 function calling 格式:
 * <ul>
 *   <li>工具描述: JSON 数组,每个元素包含 type="function" 和 function 对象 (name, description, parameters)</li>
 *   <li>工具调用解析: 从响应中解析 tool_calls 数组,每个元素包含 id, function.name, function.arguments</li>
 *   <li>结果格式化: Message with role="tool", tool_call_id, content</li>
 * </ul>
 */
public class OpenAIProtocolAdapter implements ProtocolAdapter {

    private static final Logger log = Logger.getInstance(OpenAIProtocolAdapter.class);

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "openai", "gpt-4", "gpt-3.5", "gpt-4o", "gpt-4-turbo", "gpt-4o-mini",
            "alibaba", "ali", "qwen", "deepseek", "deep_seek", "grok", "x-grok",
            "volcengine", "volc", "doubao",
            "personal"
    );

    private int callIdCounter = 0;

    @Override
    public String getName() {
        return "openai";
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
            toolObj.put("type", "function");

            JSONObject functionObj = new JSONObject(true);
            functionObj.put("name", tool.name());
            functionObj.put("description", tool.description());
            functionObj.put("parameters", buildParametersSchema(tool.inputSchema()));

            toolObj.put("function", functionObj);
            toolsArray.add(toolObj);
        }

        String result = toolsArray.toJSONString();
        log.info("[FC] formatToolDescriptions tools JSON:\n" + result);
        return result;
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
            // Response might be a JSON array of tool_calls directly
            try {
                JSONArray toolCallsArray = JSON.parseArray(response);
                if (toolCallsArray != null) {
                    return parseToolCallsArray(toolCallsArray);
                }
            } catch (JSONException ignored) {
                // Not valid JSON at all
            }
            return Collections.emptyList();
        }
    }

    @Override
    public Message formatToolResult(ToolCallResult result) {
        Message message = new Message();
        message.setRole("tool");
        message.setToolCallId(result.getCallId());
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
        return true;
    }

    // ---- internal helpers ----

    /**
     * Build a JSON Schema-style parameters object from the Tool's inputSchema.
     * The inputSchema is a map where keys are parameter names and values are
     * schema descriptors (maps with "type", "description", "required", etc.).
     */
    private JSONObject buildParametersSchema(Map<String, Object> inputSchema) {
        JSONObject params = new JSONObject(true);
        params.put("type", "object");

        if (inputSchema == null || inputSchema.isEmpty()) {
            params.put("properties", new JSONObject());
            return params;
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

                // array 类型的 items 字段
                Object items = fieldSchema.get("items");
                if (items instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemsMap = (Map<String, Object>) items;
                    JSONObject itemsObj = new JSONObject(true);
                    Object itemType = itemsMap.get("type");
                    if (itemType != null) {
                        itemsObj.put("type", itemType.toString());
                    }
                    propObj.put("items", itemsObj);
                }

                Object description = fieldSchema.get("description");
                if (description != null) {
                    propObj.put("description", description.toString());
                }

                Object enumValues = fieldSchema.get("enum");
                if (enumValues != null) {
                    propObj.put("enum", enumValues);
                }

                // default 值
                Object defaultValue = fieldSchema.get("default");
                if (defaultValue != null) {
                    propObj.put("default", defaultValue);
                }

                // 数值约束 min / max（部分 API 支持 minimum / maximum）
                Object minValue = fieldSchema.get("min");
                if (minValue != null) {
                    propObj.put("minimum", minValue);
                }
                Object maxValue = fieldSchema.get("max");
                if (maxValue != null) {
                    propObj.put("maximum", maxValue);
                }

                Boolean required = (Boolean) fieldSchema.get("required");
                if (Boolean.TRUE.equals(required)) {
                    requiredFields.add(fieldName);
                }

                properties.put(fieldName, propObj);
            }
        }

        params.put("properties", properties);
        if (!requiredFields.isEmpty()) {
            params.put("required", requiredFields);
        }

        return params;
    }

    /**
     * Parse tool calls from an OpenAI response object.
     * Supports both top-level tool_calls and nested choices[].message.tool_calls.
     */
    private List<ToolCall> parseFromResponseObject(JSONObject responseObj) {
        // Try top-level tool_calls first
        JSONArray toolCalls = responseObj.getJSONArray("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            return parseToolCallsArray(toolCalls);
        }

        // Try choices[].message.tool_calls (full API response format)
        JSONArray choices = responseObj.getJSONArray("choices");
        if (choices != null) {
            for (int i = 0; i < choices.size(); i++) {
                JSONObject choice = choices.getJSONObject(i);
                if (choice == null) continue;

                JSONObject message = choice.getJSONObject("message");
                if (message == null) continue;

                JSONArray msgToolCalls = message.getJSONArray("tool_calls");
                if (msgToolCalls != null && !msgToolCalls.isEmpty()) {
                    return parseToolCallsArray(msgToolCalls);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Parse a JSON array of tool call objects.
     * Each element: { "id": "...", "type": "function", "function": { "name": "...", "arguments": "..." } }
     */
    private List<ToolCall> parseToolCallsArray(JSONArray toolCallsArray) {
        List<ToolCall> result = new ArrayList<>();

        for (int i = 0; i < toolCallsArray.size(); i++) {
            JSONObject tcObj = toolCallsArray.getJSONObject(i);
            if (tcObj == null) continue;

            ToolCall toolCall = parseSingleToolCall(tcObj);
            if (toolCall != null) {
                result.add(toolCall);
            }
        }

        return result;
    }

    /**
     * Parse a single tool call object from OpenAI format.
     */
    private ToolCall parseSingleToolCall(JSONObject tcObj) {
        try {
            String id = tcObj.getString("id");
            if (id == null || id.isEmpty()) {
                id = generateCallId();
            }

            JSONObject functionObj = tcObj.getJSONObject("function");
            if (functionObj == null) {
                return null;
            }

            String name = functionObj.getString("name");
            if (name == null || name.isEmpty()) {
                return null;
            }

            Map<String, Object> parameters = parseArguments(functionObj.getString("arguments"));

            return ToolCall.builder()
                    .callId(id)
                    .toolName(name)
                    .parameters(parameters)
                    .build();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Parse the "arguments" field which is a JSON string in OpenAI format.
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(arguments);
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (JSONException e) {
            return Collections.emptyMap();
        }
    }

    private String generateCallId() {
        callIdCounter++;
        return "call_" + callIdCounter;
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
