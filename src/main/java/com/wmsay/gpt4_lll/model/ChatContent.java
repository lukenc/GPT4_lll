
package com.wmsay.gpt4_lll.model;

import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
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

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        if (model!=null&&model.contains("baidu")){
            adaptBaiduMessages();
        }
    }


    public void adaptBaiduMessages() {
        for (int i =0;i<messages.size();i++){
            Message message=messages.get(i);
            if (i==0){
                message.setRole("user");
            }else {
                if (i%2==1){
                    if ("user".equals(message.getRole())){
                        messages.add(i,ChatUtils.getOddMessage4Baidu());
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
        if (model!=null&&model.contains("baidu")&&messages!=null){
            adaptBaiduMessages();
        }
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
