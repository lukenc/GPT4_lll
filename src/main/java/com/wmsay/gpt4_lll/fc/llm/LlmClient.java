package com.wmsay.gpt4_lll.fc.llm;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一 LLM 客户端（框架层）。
 * 封装 HTTP 构建 + SSE 流式通信的全部通用逻辑，供各调用方共享。
 * <p>
 * 内聚 HttpClient/HttpRequest 构建逻辑（含代理支持），
 * 仅使用 Java 17 标准库 {@code java.net.http}，不依赖任何宿主工具类。
 * <p>
 * 这是后续 MCP Tool / Agent 调用 AI 的唯一入口。
 * <p>
 * 使用方式：
 * <pre>
 * // 1. 构建请求
 * LlmRequest request = LlmRequest.builder()
 *         .url(url).requestBody(jsonBody).apiKey(apiKey)
 *         .proxy(proxy).provider(provider).build();
 *
 * // 2a. 流式调用（实时展示到 ToolWindow / 编辑器写入）
 * String result = LlmClient.streamChat(request, callback);
 *
 * // 2b. 同步调用（只需最终结果）
 * String result = LlmClient.syncChat(request);
 *
 * // 2c. 非流式同步调用（用于 Function Calling，返回原始 JSON）
 * String rawJson = LlmClient.syncChatRaw(request);
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
        HttpClient client = buildHttpClient(request.getProxy());
        HttpRequest httpRequest = buildHttpRequestSse(
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
        HttpClient client = buildHttpClient(request.getProxy());
        HttpRequest httpRequest = buildHttpRequestJson(
                request.getUrl(), request.getRequestBody(), request.getApiKey());
        try {
            HttpResponse<String> response = client.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            // HTTP 状态码检查：非 2xx 时分类并抛出 LlmApiException
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                LlmErrorInfo errorInfo = LlmErrorClassifier.classify(statusCode, response.body());
                throw new LlmApiException(errorInfo);
            }

            // 响应体错误字段检查：包含 error 字段且无 choices/content 时抛出 LlmApiException
            String body = response.body();
            if (body != null && LlmErrorClassifier.containsErrorField(body)) {
                LlmErrorInfo errorInfo = LlmErrorClassifier.classifyFromBody(body);
                throw new LlmApiException(errorInfo);
            }

            return body;
        } catch (LlmApiException e) {
            // 直接向上传播，不包装
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    // ==================== 内聚 HTTP 构建逻辑 ====================

    /**
     * 构建 HttpClient，支持可选代理配置。
     * 代理格式为 ip:port，为 null 或空字符串表示不使用代理。
     *
     * @param proxy 代理地址（格式 ip:port），可为 null 或空
     * @return 已配置的 HttpClient 实例
     * @throws IllegalArgumentException 代理格式错误时抛出
     */
    static HttpClient buildHttpClient(String proxy) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        if (proxy != null && !proxy.isEmpty()) {
            String[] addressAndPort = proxy.split(":");
            if (addressAndPort.length == 2 && isValidPort(addressAndPort[1])) {
                int port = Integer.parseInt(addressAndPort[1]);
                clientBuilder.proxy(ProxySelector.of(
                        new InetSocketAddress(addressAndPort[0], port)));
            } else {
                throw new IllegalArgumentException("格式错误，格式为ip:port");
            }
        }
        return clientBuilder.build();
    }

    /**
     * 构建用于 SSE 流式请求的 HttpRequest。
     * Accept 头设置为 text/event-stream。
     */
    private static HttpRequest buildHttpRequestSse(String url, String requestBody, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMinutes(2));
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    /**
     * 构建用于非流式 JSON 请求的 HttpRequest。
     * Accept 头设置为 application/json。
     */
    private static HttpRequest buildHttpRequestJson(String url, String requestBody, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofMinutes(2));
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    /**
     * 验证端口字符串是否为合法端口号（0-65535）。
     */
    private static boolean isValidPort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            return port >= 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 核心流式聊天实现。
     * 所有 SSE 解析、回调分发、错误处理集中在此方法。
     * <p>
     * 支持 Thread.interrupt() 中断：当调用线程被中断时，会取消 HTTP 请求并抛出 RuntimeException。
     * 每行 SSE 数据处理前都会检查中断状态，确保"停止"按钮能及时生效。
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

        // 跨线程取消标志：请求线程被中断时设置为 true，
        // 异步 SSE 处理线程在每行处理前检查此标志。
        // 解决 Thread.interrupt() 只能中断请求线程（future.get()），
        // 而无法中断 HttpClient 异步线程上的 forEach 流处理的问题。
        AtomicBoolean cancelled = new AtomicBoolean(false);

        CompletableFuture<Void> future = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    // HTTP 状态码检查：非 2xx 时收集响应体、分类错误并通过 onError 通知，同时抛出异常使 future 异常完成
                    int statusCode = response.statusCode();
                    if (statusCode < 200 || statusCode >= 300) {
                        StringBuilder errorBody = new StringBuilder();
                        response.body().forEach(errorBody::append);
                        LlmErrorInfo errorInfo = LlmErrorClassifier.classify(statusCode, errorBody.toString());
                        LlmApiException ex = new LlmApiException(errorInfo);
                        callback.onError(ex);
                        throw ex;
                    }

                    AtomicBoolean hasValidData = new AtomicBoolean(false);
                    response.body().forEach(line -> {
                        // 检查跨线程取消标志和本线程中断状态
                        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                            throw new CancellationException("Stream interrupted");
                        }
                        if (line.startsWith("data")) {
                            callback.onDataLineReceived();
                            String lineData = line.substring(5);

                            // SSE error JSON 检测：data 行包含 error 字段时通过 onError 通知
                            if (LlmErrorClassifier.isErrorDataLine(lineData)) {
                                LlmErrorInfo info = LlmErrorClassifier.classifyFromBody(lineData);
                                callback.onError(new LlmApiException(info));
                                return; // 跳过此行的正常处理
                            }

                            hasValidData.set(true);
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

                    // 流结束时检查是否收到有效数据行
                    if (!hasValidData.get()) {
                        LlmApiException ex = new LlmApiException(LlmErrorClassifier.noValidResponse());
                        callback.onError(ex);
                        throw ex;
                    }
                });

        try {
            // 使用 get() 代替 join()：get() 会抛出 InterruptedException，
            // 使得 Thread.interrupt() 能够中断等待，而 join() 不响应中断。
            future.get();
        } catch (InterruptedException e) {
            // 用户点击"停止"触发了 Thread.interrupt()
            // 设置跨线程取消标志，通知异步 SSE 处理线程停止
            cancelled.set(true);
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Stream chat interrupted by user", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException) {
                // 流处理中检测到中断，正常终止
                throw new RuntimeException("Stream chat cancelled", cause);
            }
            // LlmApiException 直接向上传播，不包装为 RuntimeException
            if (cause instanceof LlmApiException) {
                throw (LlmApiException) cause;
            }
            callback.onError(cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
            throw new RuntimeException(cause);
        } catch (CancellationException e) {
            throw new RuntimeException("Stream chat cancelled", e);
        }

        String result = fullResponse.toString();
        callback.onComplete(result);
        return result;
    }
}
