package com.wmsay.gpt4_lll.fc.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.model.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 意图识别引擎 — sidecar 方式独立 LLM 调用。
 * <p>
 * 分析用户请求的清晰度、复杂度，输出工具过滤建议和推荐执行策略。
 * LLM 调用失败时返回 IntentResult.defaultResult()，确保主流程不被阻塞。
 */
public class IntentRecognizer {

    private static final Logger LOG = Logger.getLogger(IntentRecognizer.class.getName());

    private static final String SYSTEM_PROMPT =
        "You are an intent analyzer. Analyze the user's request and respond with JSON:\n" +
        "{\"clarity\":\"CLEAR|AMBIGUOUS\",\"complexity\":\"SIMPLE|COMPLEX\"," +
        "\"recommendedStrategy\":\"react|plan_and_execute\"," +
        "\"reasoning\":\"...\",\"filteredToolNames\":[...]}\n" +
        "Available tools: %s\n" +
        "CLEAR = request is specific and actionable. AMBIGUOUS = request needs clarification.\n" +
        "SIMPLE = single step task (use react). COMPLEX = multi-step task (use plan_and_execute).";

    private final ObservabilityManager observability;

    public IntentRecognizer(ObservabilityManager observability) {
        this.observability = observability;
    }

    /**
     * 分析用户消息，返回意图识别结果。
     * 以 sidecar 方式独立 LLM 调用，不加载到主对话上下文中。
     * LLM 调用失败时返回 defaultResult()。
     *
     * @param userMessage        用户消息
     * @param availableToolNames 可用工具名称列表
     * @param llmCaller          LLM 调用器
     * @return IntentResult
     */
    public IntentResult analyze(String userMessage, List<String> availableToolNames,
                                FunctionCallOrchestrator.LlmCaller llmCaller) {
        long startTime = System.currentTimeMillis();
        try {
            String toolList = availableToolNames != null
                    ? String.join(", ", availableToolNames) : "";
            String systemPrompt = String.format(SYSTEM_PROMPT, toolList);

            // 构建独立的 sidecar 请求（不含工具描述详细 schema）
            List<Message> messages = new ArrayList<>();
            Message sysMsg = new Message();
            sysMsg.setRole("system");
            sysMsg.setContent(systemPrompt);
            messages.add(sysMsg);

            Message userMsg = new Message();
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            messages.add(userMsg);

            com.wmsay.gpt4_lll.model.ChatContent chatContent = new com.wmsay.gpt4_lll.model.ChatContent();
            chatContent.setDirectMessages(messages);
            chatContent.setStream(false); // sidecar 调用使用非流式模式

            FunctionCallRequest request = FunctionCallRequest.builder()
                    .chatContent(chatContent)
                    .maxRounds(1)
                    .config(FunctionCallConfig.builder().build())
                    .build();

            System.out.println("[IntentRecognizer] Calling LLM for intent analysis...");
            String response = llmCaller.call(request);
            IntentResult result = parseResponse(response);

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[IntentRecognizer] Completed in " + elapsed + "ms: "
                    + result.getClarity() + "/" + result.getComplexity()
                    + ", strategy=" + result.getRecommendedStrategy());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.err.println("[IntentRecognizer] FAILED after " + elapsed + "ms: " + e.getMessage());
            e.printStackTrace();
            return IntentResult.defaultResult();
        }
    }

    /**
     * 解析 LLM 响应为 IntentResult。
     */
    private IntentResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return IntentResult.defaultResult();
        }

        try {
            // 尝试提取 JSON 部分
            String json = response.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            JSONObject obj = JSON.parseObject(json);

            IntentResult.Clarity clarity = "AMBIGUOUS".equalsIgnoreCase(obj.getString("clarity"))
                    ? IntentResult.Clarity.AMBIGUOUS : IntentResult.Clarity.CLEAR;

            IntentResult.Complexity complexity = "COMPLEX".equalsIgnoreCase(obj.getString("complexity"))
                    ? IntentResult.Complexity.COMPLEX : IntentResult.Complexity.SIMPLE;

            String strategy = complexity == IntentResult.Complexity.COMPLEX
                    ? "plan_and_execute" : "react";
            String rawStrategy = obj.getString("recommendedStrategy");
            if (rawStrategy != null && !rawStrategy.isBlank()) {
                strategy = rawStrategy;
            }

            String reasoning = obj.getString("reasoning");

            List<String> filteredToolNames = Collections.emptyList();
            JSONArray toolsArr = obj.getJSONArray("filteredToolNames");
            if (toolsArr != null) {
                filteredToolNames = toolsArr.toJavaList(String.class);
            }

            return IntentResult.of(clarity, complexity, strategy, reasoning, filteredToolNames);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to parse intent response, using default", e);
            return IntentResult.defaultResult();
        }
    }
}
