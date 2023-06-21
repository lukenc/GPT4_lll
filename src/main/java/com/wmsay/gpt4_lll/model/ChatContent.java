
package com.wmsay.gpt4_lll.model;

import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;

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
