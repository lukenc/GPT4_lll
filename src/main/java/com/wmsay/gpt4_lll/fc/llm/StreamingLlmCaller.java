package com.wmsay.gpt4_lll.fc.llm;

import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;

/**
 * 流式 LLM 调用的函数式接口。
 * 在调用过程中通过 {@link StreamingFcCollector.DisplayCallback}
 * 实时推送 reasoning / content 内容，同时收集完整数据用于工具调用解析。
 * <p>
 * 返回的字符串必须是一个与 {@code protocolAdapter.parseToolCalls()} 兼容的
 * 非流式 JSON 响应（由 {@link StreamingFcCollector#reconstructResponse()} 生成）。
 *
 * @see LlmCaller
 * @see StreamingFcCollector
 */
@FunctionalInterface
public interface StreamingLlmCaller {
    /**
     * 流式调用 LLM 并返回重构后的非流式 JSON 响应。
     *
     * @param request         包含对话内容和工具描述的请求
     * @param displayCallback 实时展示回调，推送 reasoning/content 增量内容
     * @return 与 protocolAdapter.parseToolCalls() 兼容的非流式 JSON 响应
     */
    String call(FunctionCallRequest request,
                StreamingFcCollector.DisplayCallback displayCallback);
}
