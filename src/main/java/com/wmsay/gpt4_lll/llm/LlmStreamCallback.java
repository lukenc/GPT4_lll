package com.wmsay.gpt4_lll.llm;

/**
 * LLM 流式响应回调接口。
 * 统一处理 SSE 流中的不同内容类型。
 *
 * 不同场景通过不同的回调实现来处理流式数据：
 * - ToolWindow 展示场景：回调内调用 textArea.appendContent()
 * - 编辑器写入场景：回调内通过 WriteCommandAction 写入文档
 * - 纯收集场景：回调内 append 到 StringBuilder
 */
public interface LlmStreamCallback {

    /**
     * 收到 AI 正式回复的内容片段。
     * 保证 contentDelta 非 null 且非空。
     *
     * @param contentDelta 内容片段
     */
    void onContent(String contentDelta);

    /**
     * 收到 AI 思考过程的内容片段（reasoning_content，如 DeepSeek 等模型支持）。
     * 保证 reasoningDelta 非 null 且非空。
     * 默认实现为空（不需要处理思考过程的场景可以不覆盖）。
     *
     * @param reasoningDelta 思考过程片段
     */
    default void onReasoningContent(String reasoningDelta) {
    }

    /**
     * 收到流式工具调用增量数据（streaming tool_calls delta）。
     * OpenAI 格式中，工具调用通过多次 delta 拼接：首次含 id/type/name，后续追加 arguments。
     *
     * @param index          tool_call 在数组中的索引（用于区分并行工具调用）
     * @param id             tool_call ID（仅首次 delta 非空）
     * @param type           工具类型，通常为 "function"（仅首次 delta 非空）
     * @param name           函数名称（仅首次 delta 非空）
     * @param argumentsDelta arguments 增量片段（可能为空字符串）
     */
    default void onToolCallDelta(int index, String id, String type, String name, String argumentsDelta) {
    }

    /**
     * 流式传输完成。
     *
     * @param fullContent 完整的 AI 回复文本
     */
    default void onComplete(String fullContent) {
    }

    /**
     * 发生错误。
     *
     * @param e 异常信息
     */
    default void onError(Exception e) {
    }

    /**
     * 收到 SSE 流中的非 data 行（如空行、错误信息等）。
     * 部分调用方需要收集这些行用于错误展示。
     * 默认实现为空（不需要处理的场景可以不覆盖）。
     *
     * @param line 原始行内容
     */
    default void onNonDataLine(String line) {
    }

    /**
     * 当收到一个 data 行时触发（在解析 JSON 之前）。
     * 用于通知调用方"已收到有效的 SSE 数据"，
     * 例如 GenerateAction 使用此回调来判断响应是否符合预期。
     * 默认实现为空。
     */
    default void onDataLineReceived() {
    }
}
