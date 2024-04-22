package com.wmsay.gpt4_lll;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.*;

@State(
        name = "MyPluginSettings",
        storages = @Storage("GptlllPluginSettings.xml")
)
public class MyPluginSettings implements PersistentStateComponent<MyPluginSettings.State> {
    public static class State {
        public String apiKey;
        public String proxyAddress;
        public String gptUrl;

        public String baiduAPIKey;
        public String baiduSecretKey;
        public String baiduApiUrl;
    }

    private State state = new State();

    public static MyPluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(MyPluginSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getApiKey() {
        return state.apiKey;
    }

    public void setApiKey(String apiKey) {
        state.apiKey = apiKey;
    }

    public String getProxyAddress() {
        return state.proxyAddress;
    }

    public void setProxyAddress(String proxyAddress) {
        state.proxyAddress = proxyAddress;
    }

    public String getBaiduAPIKey() {
        return state.baiduAPIKey;
    }

    public void setBaiduAPIKey(String baiduAPIKey) {
        state.baiduAPIKey = baiduAPIKey;
    }

    public String getBaiduSecretKey() {
        return state.baiduSecretKey;
    }

    public void setBaiduSecretKey(String baiduSecretKey) {
        state.baiduSecretKey = baiduSecretKey;
    }

    public String getBaiduApiUrl() {
        return state.baiduApiUrl;
    }

    public void setBaiduApiUrl(String baiduApiUrl) {
        state.baiduApiUrl = baiduApiUrl;
    }

    public String getGptUrl() {
        if (state.gptUrl==null|| state.gptUrl.isEmpty()){
            return "https://api.openai.com/v1/chat/completions";
        }
        return state.gptUrl;
    }

    public void setGptUrl(String gptUrl) {
        state.gptUrl = gptUrl;
    }
}