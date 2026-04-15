
package com.wmsay.gpt4_lll.fc.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;

import java.util.List;

/**
 * LLM 请求的核心数据模型，包含消息列表、模型名称、温度参数等。
 * 框架层纯数据类，不包含任何供应商适配逻辑。
 */
@SuppressWarnings("unused")
public class ChatContent {

    private List<Message> messages;
    private String model = "gpt-3.5-turbo";
    private Double temperature = 1.0;
    private Boolean stream = true;

    /**
     * OpenAI/Anthropic function calling 工具定义列表。
     * 当启用 function calling 时，由 ProtocolAdapter 格式化后设置。
     * 序列化为 JSON 时会作为 "tools" 字段发送给 AI API。
     */
    private List<Object> tools;

    public List<Message> getMessages() {
        return messages;
    }

    /**
     * 设置消息列表。
     *
     * @param messages 消息列表
     */
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    /**
     * 直接设置消息列表，不经过 ProviderAdapter 适配。
     * 用于 Memory 集成时临时替换 LLM 视图消息。
     *
     * @param messages 消息列表
     */
    public void setDirectMessages(List<Message> messages) {
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(List<Object> tools) {
        this.tools = tools;
    }

    /**
     * 将 ChatContent 序列化为 JSON 请求体字符串。
     * 序列化时过滤掉 Message 中仅用于 UI/持久化的内部字段
     * （thinking_content、tool_call_summaries、content_blocks），
     * 防止非标准字段发送到 AI API 导致拒绝请求。
     *
     * @return JSON 格式的请求体字符串
     */
    public String toRequestJson() {
        SimplePropertyPreFilter filter = new SimplePropertyPreFilter(Message.class);
        filter.getExcludes().add("thinking_content");
        filter.getExcludes().add("reasoning_content");
        filter.getExcludes().add("tool_call_summaries");
        filter.getExcludes().add("content_blocks");
        return JSON.toJSONString(this, filter);
    }
}
