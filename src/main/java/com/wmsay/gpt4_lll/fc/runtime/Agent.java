package com.wmsay.gpt4_lll.fc.runtime;

import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.state.AgentSession;
import com.wmsay.gpt4_lll.fc.state.ExecutionContext;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agent 高层 API，封装 AgentRuntime + AgentSession + LlmCaller。
 * <p>
 * 提供简洁的对话接口，隐藏底层运行时细节。实现 {@link AutoCloseable}，
 * 支持 try-with-resources 自动释放资源。
 * <p>
 * 使用示例：
 * <pre>{@code
 * try (Agent agent = Agent.builder()
 *         .provider("OpenAI")
 *         .apiKey("sk-xxx")
 *         .model("gpt-4")
 *         .systemPrompt("你是一个编程助手")
 *         .build()) {
 *     String reply = agent.chat("帮我写一个快速排序");
 *     System.out.println(reply);
 * }
 * }</pre>
 *
 * @see AgentBuilder
 * @see AgentRuntime
 */
public class Agent implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Agent.class.getName());

    private final AgentRuntime runtime;
    private final String runtimeId;
    private final AgentDefinition definition;
    private final LlmCaller llmCaller;
    private final int maxRounds;

    private volatile AgentSession session;
    private volatile boolean closed = false;

    /**
     * 内部构造函数，由 {@link AgentBuilder#build()} 调用。
     */
    Agent(AgentRuntime runtime, String runtimeId, AgentDefinition definition,
          LlmCaller llmCaller, int maxRounds) {
        this.runtime = runtime;
        this.runtimeId = runtimeId;
        this.definition = definition;
        this.llmCaller = llmCaller;
        this.maxRounds = maxRounds;
    }

    /**
     * 创建新的 AgentBuilder 实例。
     *
     * @return 新的 AgentBuilder
     */
    public static AgentBuilder builder() {
        return new AgentBuilder();
    }

    /**
     * 同步对话，发送消息并返回 Agent 响应文本。
     * <p>
     * 首次调用时自动创建会话。后续调用复用同一会话，保持对话上下文。
     *
     * @param message 用户消息文本，不能为 null 或空
     * @return Agent 响应文本
     * @throws IllegalStateException    如果 Agent 已关闭
     * @throws IllegalArgumentException 如果 message 为 null 或空
     */
    public String chat(String message) {
        ensureOpen();
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }

        ensureSession();

        FunctionCallResult result = runtime.send(
                session.getSessionId(), message, llmCaller, null);

        return result.getContent();
    }

    /**
     * 流式对话，通过回调实时推送 Agent 响应内容。
     * <p>
     * 首次调用时自动创建会话。后续调用复用同一会话，保持对话上下文。
     *
     * @param message  用户消息文本，不能为 null 或空
     * @param callback 流式回调，不能为 null
     * @throws IllegalStateException    如果 Agent 已关闭
     * @throws IllegalArgumentException 如果 message 或 callback 为 null
     */
    public void chatStream(String message, StreamCallback callback) {
        ensureOpen();
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        ensureSession();

        ProgressCallback progressCallback = new ProgressCallback() {
            @Override
            public void onTextDelta(int round, String delta) {
                callback.onDelta(delta);
            }

            @Override
            public void onTextContent(int round, String content) {
                callback.onDelta(content);
            }
        };

        try {
            FunctionCallResult result = runtime.send(
                    session.getSessionId(), message, llmCaller, progressCallback);
            callback.onComplete(result.getContent());
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * 释放资源：销毁会话并关闭运行时。
     * <p>
     * 多次调用 close() 是安全的（幂等操作）。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (session != null) {
            try {
                runtime.destroySession(session.getSessionId());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to destroy session on close", e);
            }
            session = null;
        }

        try {
            runtime.shutdown();
            AgentRuntime.removeInstance(runtimeId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to shutdown runtime on close", e);
        }

        LOG.fine("Agent closed: " + definition.getId());
    }

    /**
     * 获取底层 AgentRuntime 实例（高级用法）。
     *
     * @return AgentRuntime 实例
     */
    public AgentRuntime getRuntime() {
        return runtime;
    }

    /**
     * 获取配置的最大对话轮次。
     *
     * @return 最大轮次
     */
    public int getMaxRounds() {
        return maxRounds;
    }

    /**
     * 获取当前会话（可能为 null，如果尚未调用 chat）。
     *
     * @return 当前 AgentSession，或 null
     */
    public AgentSession getSession() {
        return session;
    }

    /**
     * 检查 Agent 是否已关闭。
     *
     * @return true 如果已关闭
     */
    public boolean isClosed() {
        return closed;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Agent is closed");
        }
    }

    private synchronized void ensureSession() {
        if (session == null) {
            ToolContext toolContext = ToolContext.builder().build();
            ExecutionContext execContext = ExecutionContext.fromToolContext(toolContext);
            session = runtime.createSession(definition.getId(), execContext);
        }
    }

    /**
     * 流式对话回调接口。
     * <p>
     * 在流式对话过程中接收增量内容、完成通知和错误通知。
     */
    public interface StreamCallback {

        /**
         * 接收增量内容片段。
         *
         * @param delta 内容增量
         */
        void onDelta(String delta);

        /**
         * 对话完成，接收完整响应文本。
         *
         * @param fullResponse 完整响应文本
         */
        void onComplete(String fullResponse);

        /**
         * 对话过程中发生错误。
         *
         * @param error 异常
         */
        void onError(Exception error);
    }
}
