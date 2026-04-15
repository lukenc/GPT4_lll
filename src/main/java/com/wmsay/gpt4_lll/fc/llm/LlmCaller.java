package com.wmsay.gpt4_lll.fc.llm;

import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;

/**
 * LLM 调用的函数式接口（非流式）。
 * 将实际的 LLM 调用抽象为回调，使编排器不依赖具体的 LlmClient 实现。
 *
 * <p>框架层提供基于 {@link LlmClient} 的默认实现，用户也可自行实现此接口
 * 以对接自定义的 LLM 后端。
 *
 * @see StreamingLlmCaller
 * @see LlmClient
 */
@FunctionalInterface
public interface LlmCaller {
    /**
     * 调用 LLM 并返回响应文本。
     *
     * @param request 包含对话内容和工具描述的请求
     * @return LLM 响应文本
     */
    String call(FunctionCallRequest request);
}
