
package com.wmsay.gpt4_lll.model;

import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.wmsay.gpt4_lll.WindowTool;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.utils.ChatUtils;

@SuppressWarnings("unused")
public class ChatContent {

    @Expose
    private List<Message> messages;
    @Expose
    private String model="gpt-3.5-turbo";

    private Double temperature=1.0;

    @Expose
    private Boolean stream= true;
    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages,String providerName) {
        this.messages = messages;
        if (model != null &&
                (
                        ProviderNameEnum.BAIDU.getProviderName().equals(providerName)
                                || ProviderNameEnum.FREE.getProviderName().equals(providerName)
                )
        ) {
            adaptBaiduMessages();
        }
    }


/**
 * 调整百度消息格式的方法。
 * 遍历消息列表，根据索引调整每个消息的角色，并在必要时插入新的消息。
 */
public void adaptBaiduMessages() {
    // 遍历消息列表
    for (int i = 0; i < messages.size(); i++) {
        Message message = messages.get(i);
        // 如果是第一条消息，设置角色为"user"
        if (i == 0) {
            message.setRole("user");
        } else {
            // 如果不是第一条消息，检查索引是否为奇数
            if (i % 2 == 1) {
                // 如果当前消息的角色是"user"，则在当前位置插入一个新的消息
                if ("user".equals(message.getRole())) {
                    messages.add(i, ChatUtils.getOddMessage4Baidu());
                }
            }
        }
    }
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
}
