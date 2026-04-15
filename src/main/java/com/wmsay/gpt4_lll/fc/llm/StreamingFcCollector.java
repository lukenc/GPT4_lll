package com.wmsay.gpt4_lll.fc.llm;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Map;
import java.util.TreeMap;

/**
 * 流式 FC 数据收集器。
 * <p>
 * 在流式 LLM 调用期间，同时完成两件事：
 * <ol>
 *   <li>通过 {@link DisplayCallback} 将 reasoning/content 实时推送到 UI 展示</li>
 *   <li>收集所有流式数据（reasoning、content、tool_calls），
 *       流结束后可通过 {@link #reconstructResponse()} 构造一个等价的非流式 JSON 响应，
 *       供 {@code protocolAdapter.parseToolCalls()} 解析。</li>
 * </ol>
 */
public class StreamingFcCollector implements LlmStreamCallback {

    /**
     * 实时展示回调，由调用方（编排器）实现，将增量内容推送到 UI。
     */
    public interface DisplayCallback {
        void onReasoningDelta(String delta);
        void onContentDelta(String delta);
    }

    private final DisplayCallback displayCallback;

    private final StringBuilder reasoning = new StringBuilder();
    private final StringBuilder content = new StringBuilder();
    private final Map<Integer, ToolCallAccumulator> toolCalls = new TreeMap<>();

    public StreamingFcCollector(DisplayCallback displayCallback) {
        this.displayCallback = displayCallback;
    }

    @Override
    public void onContent(String contentDelta) {
        content.append(contentDelta);
        if (displayCallback != null) {
            displayCallback.onContentDelta(contentDelta);
        }
    }

    @Override
    public void onReasoningContent(String reasoningDelta) {
        reasoning.append(reasoningDelta);
        if (displayCallback != null) {
            displayCallback.onReasoningDelta(reasoningDelta);
        }
    }

    @Override
    public void onToolCallDelta(int index, String id, String type, String name, String argumentsDelta) {
        ToolCallAccumulator acc = toolCalls.computeIfAbsent(index, k -> new ToolCallAccumulator());
        if (id != null) acc.id = id;
        if (type != null) acc.type = type;
        if (name != null) acc.name = name;
        if (argumentsDelta != null) acc.arguments.append(argumentsDelta);
    }

    public String getReasoningText() {
        return reasoning.toString();
    }

    public String getContentText() {
        return content.toString();
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * 将流式收集的数据重构为 OpenAI 非流式响应 JSON，
     * 使 {@code protocolAdapter.parseToolCalls()} 能正常解析工具调用。
     * <p>
     * 输出格式：
     * <pre>
     * {"choices":[{"message":{
     *   "reasoning_content":"...",
     *   "content":"...",
     *   "tool_calls":[{"id":"...","type":"function","function":{"name":"...","arguments":"..."}}]
     * }}]}
     * </pre>
     */
    public String reconstructResponse() {
        JSONObject message = new JSONObject();
        if (reasoning.length() > 0) {
            message.put("reasoning_content", reasoning.toString());
        }
        if (content.length() > 0) {
            message.put("content", content.toString());
        }
        if (!toolCalls.isEmpty()) {
            JSONArray tcArray = new JSONArray();
            for (ToolCallAccumulator tc : toolCalls.values()) {
                JSONObject tcObj = new JSONObject();
                tcObj.put("id", tc.id);
                tcObj.put("type", tc.type != null ? tc.type : "function");
                JSONObject function = new JSONObject();
                function.put("name", tc.name);
                function.put("arguments", tc.arguments.toString());
                tcObj.put("function", function);
                tcArray.add(tcObj);
            }
            message.put("tool_calls", tcArray);
        }

        JSONObject choice = new JSONObject();
        choice.put("message", message);
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject response = new JSONObject();
        response.put("choices", choices);
        return response.toJSONString();
    }

    private static class ToolCallAccumulator {
        String id;
        String type;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}
