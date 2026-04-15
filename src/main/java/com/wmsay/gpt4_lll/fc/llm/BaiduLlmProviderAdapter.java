package com.wmsay.gpt4_lll.fc.llm;

import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 框架层百度文心一言供应商适配器。
 * <p>
 * 内聚百度特有的消息交替规则和 SSE 解析逻辑，不依赖宿主层工具方法。
 * 原 {@code ChatUtils.getOddMessage4Baidu()} 的逻辑已内聚到
 * {@link #adaptMessages(List)} 方法中。
 * <p>
 * 百度 API 要求：
 * <ul>
 *   <li>不支持 system role（降级为 user）</li>
 *   <li>消息列表中 user/assistant 角色必须严格交替</li>
 *   <li>第一条消息必须是 user 角色</li>
 *   <li>SSE 响应使用 BaiduSseResponse 格式（result 字段）</li>
 * </ul>
 * <p>
 * 设计约束：
 * <ul>
 *   <li>不引用 {@code com.intellij.*} 或 {@code MyPluginSettings}</li>
 *   <li>不调用 {@code ChatUtils.getOddMessage4Baidu()} 等宿主工具方法</li>
 *   <li>所有配置通过 {@link LlmProviderConfig} 获取</li>
 * </ul>
 *
 * @see LlmProviderAdapter
 * @see LlmProviderConfig
 * @see LlmProviderAdapterRegistry
 */
public class BaiduLlmProviderAdapter implements LlmProviderAdapter {

    private static final Logger LOG = Logger.getLogger(BaiduLlmProviderAdapter.class.getName());

    /** 百度 assistant 占位消息内容（与原 ChatUtils.getOddMessage4Baidu() 一致） */
    private static final String PLACEHOLDER_ASSISTANT_CONTENT =
            "好的。还有更多内容需要提供么？以便让我更好解决您后面的问题。";

    @Override
    public String getProviderName() {
        return "Baidu";
    }

    /**
     * 获取 API URL。
     * 百度 URL 包含 access_token，已在 {@link LlmProviderConfig} 中预配置
     * （由宿主层拼接 model + access_token 后传入）。
     *
     * @param config 供应商配置
     * @return 完整的百度 API URL（含 access_token）
     */
    @Override
    public String getApiUrl(LlmProviderConfig config) {
        return config.getApiUrl();
    }

    /**
     * 获取 API Key。
     * 百度使用 access_token 认证，已在 {@link LlmProviderConfig} 中预配置。
     *
     * @param config 供应商配置
     * @return access_token 字符串
     */
    @Override
    public String getApiKey(LlmProviderConfig config) {
        return config.getApiKey();
    }

    /**
     * 百度不支持 system role，需降级为 user。
     *
     * @return false
     */
    @Override
    public boolean supportsSystemRole() {
        return false;
    }

    /**
     * 百度消息格式适配。
     * <p>
     * 消息适配规则（内聚自原 {@code ChatUtils.getOddMessage4Baidu()} 和
     * {@code BaiduProviderAdapter.adaptMessages()}）：
     * <ul>
     *   <li>第一条消息 role 强制设为 "user"</li>
     *   <li>奇数索引位置如果是 user 角色，插入 assistant 占位消息</li>
     * </ul>
     * <p>
     * ⚠️ 返回新列表，不修改原始列表中的 Message 对象（对第一条消息创建副本）。
     *
     * @param messages 原始消息列表
     * @return 适配后的消息列表，满足百度 user/assistant 交替要求
     */
    @Override
    public List<Message> adaptMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> adapted = new ArrayList<>(messages.size() + messages.size() / 2);
        for (int i = 0; i < messages.size(); i++) {
            Message original = messages.get(i);
            if (i == 0) {
                // 第一条消息强制 user 角色 — 创建副本避免修改原始对象
                Message first = copyMessage(original);
                first.setRole("user");
                adapted.add(first);
            } else {
                adapted.add(original);
            }
        }

        // 在奇数索引位置插入 assistant 占位消息（确保 user/assistant 交替）
        for (int i = 1; i < adapted.size(); i++) {
            if (i % 2 == 1 && "user".equals(adapted.get(i).getRole())) {
                adapted.add(i, createPlaceholderAssistantMessage());
            }
        }

        return adapted;
    }

    /**
     * 解析百度 SSE 行。
     * 百度使用 BaiduSseResponse 格式，内容通过 result 字段获取。
     *
     * @param lineData 去掉 "data:" 前缀后的行内容
     * @param callback 回调接口
     */
    @Override
    public void parseSseLine(String lineData, LlmStreamCallback callback) {
        SseStreamProcessor.processBaiduLine(lineData, callback);
    }

    /**
     * 创建百度 assistant 占位消息。
     * 当消息列表中出现连续 user 消息时，插入此占位消息以满足交替要求。
     *
     * @return assistant 角色的占位消息
     */
    private static Message createPlaceholderAssistantMessage() {
        Message message = new Message();
        message.setRole("assistant");
        message.setContent(PLACEHOLDER_ASSISTANT_CONTENT);
        return message;
    }

    /**
     * 创建 Message 的浅拷贝（仅复制 role 和 content）。
     * 用于避免修改原始消息列表中的对象。
     *
     * @param original 原始消息
     * @return 新的 Message 实例，包含相同的 role 和 content
     */
    private static Message copyMessage(Message original) {
        Message copy = new Message();
        copy.setRole(original.getRole());
        copy.setContent(original.getContent());
        copy.setName(original.getName());
        copy.setToolCallId(original.getToolCallId());
        copy.setToolCalls(original.getToolCalls());
        copy.setThinkingContent(original.getThinkingContent());
        copy.setToolCallSummaries(original.getToolCallSummaries());
        copy.setContentBlocks(original.getContentBlocks());
        return copy;
    }
}
