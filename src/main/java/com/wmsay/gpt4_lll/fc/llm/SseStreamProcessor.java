package com.wmsay.gpt4_lll.fc.llm;

import com.alibaba.fastjson.JSON;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapterRegistry;
import com.wmsay.gpt4_lll.model.baidu.BaiduSseResponse;

/**
 * 统一 SSE 行数据解析器。
 * <p>
 * 将原本散落在 GenerateAction.chat()、LinterFixAction.chatWithLinterFix()、
 * ChatUtils.pureChat() 三处的供应商 SSE 响应解析逻辑收拢到此一处。
 * <p>
 * Phase 2 后，解析分支通过 {@link ProviderAdapter#parseSseLine} 派发，
 * 新增供应商只需在 Adapter 中覆盖 parseSseLine()。
 * <p>
 * 使用方式：
 * <pre>
 * SseStreamProcessor.processLine(lineData, provider, callback);
 * </pre>
 */
public class SseStreamProcessor {

    /**
     * 解析单条 SSE 数据行并通过回调输出解析结果。
     * 通过 ProviderAdapterRegistry 查找适配器，委托给适配器的 parseSseLine()。
     *
     * @param lineData 去掉 "data:" 前缀后的行内容（即 line.substring(5)）
     * @param provider 当前供应商名称（ProviderNameEnum.getProviderName()）
     * @param callback 回调接口，用于接收解析出的内容
     */
    public static void processLine(String lineData, String provider, LlmStreamCallback callback) {
        ProviderAdapter adapter = ProviderAdapterRegistry.getAdapter(provider);
        adapter.parseSseLine(lineData, callback);
    }

    /**
     * 解析百度/FREE 供应商的 SSE 数据行。
     * 百度使用 BaiduSseResponse，内容通过 getResult() 获取。
     */
    public static void processBaiduLine(String lineData, LlmStreamCallback callback) {
        try {
            BaiduSseResponse response = JSON.parseObject(lineData, BaiduSseResponse.class);
            if (response != null) {
                String content = response.getResult();
                if (content != null && !content.isEmpty()) {
                    callback.onContent(content);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 解析 OpenAI 标准格式的 SSE 数据行。
     * 适用于 OpenAI / ALI / GROK / DEEP_SEEK / PERSONAL 等供应商。
     * 内容通过 choices[0].delta.content 获取。
     * 思考过程通过 choices[0].delta.reasoning_content 获取（如 DeepSeek 等模型）。
     * 工具调用通过 choices[0].delta.tool_calls 获取（流式增量拼接）。
     */
    public static void processOpenAiLine(String lineData, LlmStreamCallback callback) {
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(lineData);
            if (json == null) return;
            com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) return;

            com.alibaba.fastjson.JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            if (delta == null) return;

            String reasoning = delta.getString("reasoning_content");
            if (reasoning != null && !reasoning.isEmpty()) {
                callback.onReasoningContent(reasoning);
            }

            String content = delta.getString("content");
            if (content != null && !content.isEmpty()) {
                callback.onContent(content);
            }

            com.alibaba.fastjson.JSONArray toolCallsArr = delta.getJSONArray("tool_calls");
            if (toolCallsArr != null) {
                for (int i = 0; i < toolCallsArr.size(); i++) {
                    com.alibaba.fastjson.JSONObject tc = toolCallsArr.getJSONObject(i);
                    if (tc == null) continue;
                    int index = tc.getIntValue("index");
                    String id = tc.getString("id");
                    String type = tc.getString("type");
                    com.alibaba.fastjson.JSONObject function = tc.getJSONObject("function");
                    String name = function != null ? function.getString("name") : null;
                    String argsDelta = function != null ? function.getString("arguments") : null;
                    callback.onToolCallDelta(index, id, type, name, argsDelta);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
