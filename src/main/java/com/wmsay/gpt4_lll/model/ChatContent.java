
package com.wmsay.gpt4_lll.model;

import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.wmsay.gpt4_lll.WindowTool;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapterRegistry;

@SuppressWarnings("unused")
public class ChatContent {

    @Expose
    private List<Message> messages;
    @Expose
    private String model="gpt-3.5-turbo";

    private Double temperature=1.0;

    @Expose
    private Boolean stream= true;

    /**
     * OpenAI/Anthropic function calling 工具定义列表。
     * 当启用 function calling 时，由 ProtocolAdapter 格式化后设置。
     * 序列化为 JSON 时会作为 "tools" 字段发送给 AI API。
     */
    private List<Object> tools;

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages, String providerName) {
        this.messages = messages;
        if (model != null) {
            ProviderAdapter adapter = ProviderAdapterRegistry.getAdapter(providerName);
            this.messages = adapter.adaptMessages(this.messages);
        }
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
}
