
package com.wmsay.gpt4_lll.model;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("net.hexar.json2pojo")
@SuppressWarnings("unused")
public class Choice {

    @Expose
    private Delta delta;
    @SerializedName("finish_reason")
    private Object finishReason;
    @Expose
    private Long index;

    public Delta getDelta() {
        return delta;
    }

    public void setDelta(Delta delta) {
        this.delta = delta;
    }

    public Object getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(Object finishReason) {
        this.finishReason = finishReason;
    }

    public Long getIndex() {
        return index;
    }

    public void setIndex(Long index) {
        this.index = index;
    }

}
