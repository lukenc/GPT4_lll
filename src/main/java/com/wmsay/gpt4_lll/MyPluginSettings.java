package com.wmsay.gpt4_lll;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;



@State(
        name = "MyPluginSettings",
        storages = @Storage("GptlllPluginSettings.xml")
)
public class MyPluginSettings implements PersistentStateComponent<MyPluginSettings.State> {
    public static class State {
        public String proxyAddress;

        public String apiKey;
        //百度配置
        public String baiduAPIKey;
        public String baiduSecretKey;

        //自定义配置
        public String personalApiUrl;
        public String personalModel;
        public String personalApiKey;
        //通义千问配置
        public String tongyiApiKey;

        //Grok配置
        public String grokApiKey;

    }

    private State state = new State();

    public static MyPluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(MyPluginSettings.class);
    }

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

    public void setPersonalApiKey(String personalApiKey) {
        state.personalApiKey = personalApiKey;
    }
    public String getPersonalApiKey() {
        return state.personalApiKey;
    }
    public void setPersonalApiUrl(String personalApiUrl) {
        state.personalApiUrl = personalApiUrl;
    }
    public String getPersonalApiUrl() {
        return state.personalApiUrl;
    }

    public void setPersonalModel(String personalModel) {
        state.personalModel = personalModel;
    }
    public String getPersonalModel() {
        return state.personalModel;
    }

    public void setTongyiApiKey(String tongyiApiKey) {
        state.tongyiApiKey = tongyiApiKey;
    }
    public String getTongyiApiKey() {
        return state.tongyiApiKey;
    }

    public void setGrokApiKey(String grokApiKey) {
        state.grokApiKey = grokApiKey;
    }
    public String getGrokApiKey() {
        return state.grokApiKey;
    }
}