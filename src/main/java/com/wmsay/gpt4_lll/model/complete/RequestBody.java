
package com.wmsay.gpt4_lll.model.complete;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RequestBody {

    @SerializedName("max_tokens")
    private Long max_tokens;
    @Expose
    private String model;
    @Expose
    private String prompt;
    @Expose
    private Double temperature;

    public Long getMax_tokens() {
        return max_tokens;
    }

    public void setMax_tokens(Long max_tokens) {
        this.max_tokens = max_tokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    @Expose
    private Boolean stream;


    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

}
