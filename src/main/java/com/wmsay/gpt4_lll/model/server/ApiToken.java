package com.wmsay.gpt4_lll.model.server;


import java.util.Date;

public class ApiToken {
    String apiModel;
    String token;
    Date expireDate;

    public String getApiModel() {
        return apiModel;
    }

    public ApiToken setApiModel(String apiModel) {
        this.apiModel = apiModel;
        return this;
    }

    public String getToken() {
        return token;
    }

    public ApiToken setToken(String token) {
        this.token = token;
        return this;
    }

    public Date getExpireDate() {
        return expireDate;
    }

    public ApiToken setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
        return this;
    }
}
