
package com.wmsay.gpt4_lll.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
public class Permission {

    @SerializedName("allow_create_engine")
    private Boolean allowCreateEngine;
    @SerializedName("allow_fine_tuning")
    private Boolean allowFineTuning;
    @SerializedName("allow_logprobs")
    private Boolean allowLogprobs;
    @SerializedName("allow_sampling")
    private Boolean allowSampling;
    @SerializedName("allow_search_indices")
    private Boolean allowSearchIndices;
    @SerializedName("allow_view")
    private Boolean allowView;
    @Expose
    private Long created;
    @Expose
    private Object group;
    @Expose
    private String id;
    @SerializedName("is_blocking")
    private Boolean isBlocking;
    @Expose
    private String object;
    @Expose
    private String organization;

    public Boolean getAllowCreateEngine() {
        return allowCreateEngine;
    }

    public void setAllowCreateEngine(Boolean allowCreateEngine) {
        this.allowCreateEngine = allowCreateEngine;
    }

    public Boolean getAllowFineTuning() {
        return allowFineTuning;
    }

    public void setAllowFineTuning(Boolean allowFineTuning) {
        this.allowFineTuning = allowFineTuning;
    }

    public Boolean getAllowLogprobs() {
        return allowLogprobs;
    }

    public void setAllowLogprobs(Boolean allowLogprobs) {
        this.allowLogprobs = allowLogprobs;
    }

    public Boolean getAllowSampling() {
        return allowSampling;
    }

    public void setAllowSampling(Boolean allowSampling) {
        this.allowSampling = allowSampling;
    }

    public Boolean getAllowSearchIndices() {
        return allowSearchIndices;
    }

    public void setAllowSearchIndices(Boolean allowSearchIndices) {
        this.allowSearchIndices = allowSearchIndices;
    }

    public Boolean getAllowView() {
        return allowView;
    }

    public void setAllowView(Boolean allowView) {
        this.allowView = allowView;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public Object getGroup() {
        return group;
    }

    public void setGroup(Object group) {
        this.group = group;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getIsBlocking() {
        return isBlocking;
    }

    public void setIsBlocking(Boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

}
