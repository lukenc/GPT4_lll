package com.wmsay.gpt4_lll.model;

public class ModelProvider {
    private String name;
    private String url;
    private String description;

    public ModelProvider(String name, String url, String description) {
        this.name = name;
        this.url = url;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public ModelProvider setName(String name) {
        this.name = name;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public ModelProvider setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ModelProvider setDescription(String description) {
        this.description = description;
        return this;
    }
}
