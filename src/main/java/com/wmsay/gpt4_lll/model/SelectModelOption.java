package com.wmsay.gpt4_lll.model;

import java.util.StringJoiner;

public class SelectModelOption {
    private String modelName;
    private String description;

    private String provider;

    private String displayName;

    public SelectModelOption(String modelName, String description, String provider, String displayName) {
        this.modelName = modelName;
        this.description = description;
        this.provider = provider;
        this.displayName = displayName;
    }

    public String getModelName() {
        return modelName;
    }

    public SelectModelOption setModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public SelectModelOption setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getProvider() {
        return provider;
    }

    public SelectModelOption setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SelectModelOption setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SelectModelOption.class.getSimpleName() + "[", "]")
                .add("modelName='" + modelName + "'")
                .add("description='" + description + "'")
                .add("provider='" + provider + "'")
                .add("displayName='" + displayName + "'")
                .toString();
    }
}
