package com.wmsay.gpt4_lll.llm;

import com.wmsay.gpt4_lll.utils.ChatUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 统一 LLM 客户端。
 * 封装 HTTP 构建 + SSE 流式通信的全部通用逻辑，供各调用方共享。
 * <p>
 * 这是后续 MCP Tool / Agent 调用 AI 的唯一入口。
 * <p>
 * 使用方式：
 * <pre>
 * // 1. 构建请求
 * LlmRequest request = LlmRequest.builder()
 *         .url(url).chatContent(chatContent).apiKey(apiKey)
 *         .proxy(proxy).provider(provider).build();
 *
 * // 2a. 流式调用（实时展示到 ToolWindow / 编辑器写入）
 * String result = LlmClient.streamChat(request, callback);
 *
 * // 2b. 同步调用（只需最终结果）
 * String result = LlmClient.syncChat(request);
 * </pre>
 * <p>
 * ⚠️ 此类不负责：
 * - URL / API Key 的获取和校验（由调用方在构建 LlmRequest 前完成）
 * - 运行状态管理（startRunningStatus/stopRunningStatus 由 Action 层负责）
 * - UI 操作（Swing/EDT 操作由回调或调用方负责）
 */
public class LlmClient {

    // ==================== 高层 API（推荐使用）====================

    /**
     * 流式聊天（高层 API）。
     * 内部自动构建 HttpClient 和 HttpRequest，SSE 数据通过 callback 实时推送。
     * 异常通过 callback.onError() 通知，同时以异常形式抛出。
     *
     * @param request  封装好的请求参数
     * @param callback 流式回调（实时接收内容片段）
     * @return 完整的 AI 回复文本
     * @throws IllegalArgumentException 代理格式错误或 URL 格式错误时抛出
     */
    public static String streamChat(LlmRequest request, LlmStreamCallback callback) {
        HttpClient client = ChatUtils.buildHttpClient(request.getProxy());
        HttpRequest httpRequest = ChatUtils.buildHttpRequest(
                request.getUrl(), request.getRequestBody(), request.getApiKey());
        return doStreamChat(client, httpRequest, request.getProvider(), callback);
    }

    /**
     * 同步聊天（高层 API）。
     * 等待完整响应后返回，不提供实时回调。
     *
     * @param request 封装好的请求参数
     * @return 完整的 AI 回复文本
     * @throws IllegalArgumentException 代理格式错误或 URL 格式错误时抛出
     */
    public static String syncChat(LlmRequest request) {
        return streamChat(request, new LlmStreamCallback() {
            @Override
            public void onContent(String contentDelta) {
                // 不需要实时处理，doStreamChat 内部已收集到 fullResponse
            }
        });
    }

    /**
     * 非流式同步聊天（用于 Function Calling）。
     * 发送 stream=false 的请求，返回完整的原始 JSON 响应体。
     * 适用于需要解析 tool_calls 等结构化响应的场景。
     *
     * @param request 封装好的请求参数（requestBody 中 stream 应为 false）
     * @return 原始 JSON 响应体字符串
     * @throws RuntimeException 网络或 HTTP 错误时抛出
     */
    public static String syncChatRaw(LlmRequest request) {
        HttpClient client = ChatUtils.buildHttpClient(request.getProxy());
        HttpRequest httpRequest = ChatUtils.buildHttpRequestJson(
                request.getUrl(), request.getRequestBody(), request.getApiKey());
        try {
            HttpResponse<String> response = client.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 核心流式聊天实现。
     * 所有 SSE 解析、回调分发、错误处理集中在此方法。
     *
     * @param client      已构建的 HttpClient
     * @param httpRequest 已构建的 HttpRequest
     * @param provider    当前供应商名称（用于 SSE 解析分支判断）
     * @param callback    流式回调
     * @return 完整的 AI 回复文本
     */
    static String doStreamChat(HttpClient client, HttpRequest httpRequest,
                               String provider, LlmStreamCallback callback) {
        StringBuilder fullResponse = new StringBuilder();

        try {
            client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        response.body().forEach(line -> {
                            if (line.startsWith("data")) {
                                callback.onDataLineReceived();
                                String lineData = line.substring(5);
                                SseStreamProcessor.processLine(lineData, provider, new LlmStreamCallback() {
                                    @Override
                                    public void onContent(String contentDelta) {
                                        fullResponse.append(contentDelta);
                                        callback.onContent(contentDelta);
                                    }

                                    @Override
                                    public void onReasoningContent(String reasoningDelta) {
                                        callback.onReasoningContent(reasoningDelta);
                                    }

                                    @Override
                                    public void onToolCallDelta(int index, String id, String type,
                                                                String name, String argumentsDelta) {
                                        callback.onToolCallDelta(index, id, type, name, argumentsDelta);
                                    }
                                });
                            } else {
                                callback.onNonDataLine(line);
                            }
                        });
                    }).join();
        } catch (Exception e) {
            callback.onError(e);
            throw e;
        }

        String result = fullResponse.toString();
        callback.onComplete(result);
        return result;
    }
}
