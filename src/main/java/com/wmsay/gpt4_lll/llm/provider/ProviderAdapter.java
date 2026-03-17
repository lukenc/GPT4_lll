package com.wmsay.gpt4_lll.llm.provider;

import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.LlmStreamCallback;
import com.wmsay.gpt4_lll.llm.SseStreamProcessor;
import com.wmsay.gpt4_lll.model.Message;

import java.util.List;

/**
 * 供应商适配器接口。
 * <p>
 * 将当前散落在 ChatUtils.getUrl()、ChatUtils.getApiKey()、消息适配逻辑、
 * SseStreamProcessor 等多处的供应商差异化逻辑，统一收拢到此接口。
 * <p>
 * 新增供应商只需：
 * 1. 创建一个 XxxProviderAdapter 实现类
 * 2. 在 ProviderAdapterRegistry 中注册
 * <p>
 * ⚠️ 设计约束：
 * - 不引入新的 HTTP 库，URL 和 Key 仍然是字符串
 * - 不修改 Message/ChatContent 的数据结构
 * - adaptMessages() 不能修改原始列表，必须返回新列表
 */
public interface ProviderAdapter {

    /** 供应商名称，必须与 ProviderNameEnum 的 providerName 一致 */
    String getProviderName();

    /**
     * 获取 API URL。
     * 对应当前 ChatUtils.getUrl() 和 getUrlByProvider() 中的 if-else 分支。
     *
     * @param settings  插件设置
     * @param modelName 模型名称
     * @return 完整的 API URL
     */
    String getApiUrl(MyPluginSettings settings, String modelName);

    /**
     * 获取 API Key。
     * 对应当前 ChatUtils.getApiKey() 中的 if-else 分支。
     *
     * @param settings 插件设置
     * @return API Key 字符串
     */
    String getApiKey(MyPluginSettings settings);

    /**
     * 是否支持 system 角色。
     * 百度不支持，其他供应商支持。
     * 对应各 Action 中的 {@code if (BAIDU) systemMessage.setRole("user")} 判断。
     *
     * @return true 支持 system role，false 不支持（降级为 user）
     */
    default boolean supportsSystemRole() {
        return true;
    }

    /**
     * 消息格式适配。
     * 对应当前供应商消息格式适配逻辑。
     * 默认实现不做任何修改（适用于 OpenAI 标准格式的供应商）。
     * <p>
     * ⚠️ 不能修改原始列表，必须返回新列表。
     *
     * @param messages 原始消息列表
     * @return 适配后的消息列表
     */
    default List<Message> adaptMessages(List<Message> messages) {
        return messages;
    }

    /**
     * 解析 SSE 行，提取内容。
     * 对应当前 SseStreamProcessor 中的供应商分支。
     * 默认实现使用 OpenAI 标准格式。
     *
     * @param lineData 去掉 "data:" 前缀后的行内容
     * @param callback 回调接口
     */
    default void parseSseLine(String lineData, LlmStreamCallback callback) {
        SseStreamProcessor.processOpenAiLine(lineData, callback);
    }

    /**
     * 获取 system message 应使用的角色名。
     * 便捷方法，根据 supportsSystemRole() 返回 "system" 或 "user"。
     *
     * @return "system" 或 "user"
     */
    default String getSystemMessageRole() {
        return supportsSystemRole() ? "system" : "user";
    }

    /**
     * 是否支持回复未完成时的续传重试。
     * 当前仅百度文心支持该特性（百度 API 有时会截断响应）。
     *
     * @return true 支持续传重试，默认 false
     */
    default boolean supportsContinuationRetry() {
        return false;
    }

    /**
     * 获取续传重试时追加的消息。
     * 当 supportsContinuationRetry() 为 true 且 AI 回复被截断时，
     * 此消息会被追加到对话历史中以请求 AI 继续输出。
     * <p>
     * 默认实现返回百度风格的续传消息（"请按照上面的要求，继续完成。"），
     * 供应商可覆盖此方法自定义续传提示。
     *
     * @return 续传消息
     */
    default Message getContinuationMessage() {
        Message message = new Message();
        message.setRole("user");
        message.setContent("请按照上面的要求，继续完成。");
        return message;
    }
}
