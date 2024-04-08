package com.wmsay.gpt4_lll.model.baidu;

import java.io.Serializable;
import java.util.StringJoiner;

public class TokenInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String refresh_token;
    private int expires_in;
    private String session_key;
    private String access_token;
    private String scope;
    private String session_secret;

    // 构造方法
    public TokenInfo() {
        // 默认构造方法
    }

    // Getter 和 Setter 方法
    public String getRefresh_token() {
        return refresh_token;
    }

    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }

    public int getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(int expires_in) {
        this.expires_in = expires_in;
    }

    public String getSession_key() {
        return session_key;
    }

    public void setSession_key(String session_key) {
        this.session_key = session_key;
    }

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSession_secret() {
        return session_secret;
    }

    public void setSession_secret(String session_secret) {
        this.session_secret = session_secret;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TokenInfo.class.getSimpleName() + "[", "]")
                .add("refresh_token='" + refresh_token + "'")
                .add("expires_in=" + expires_in)
                .add("session_key='" + session_key + "'")
                .add("access_token='" + access_token + "'")
                .add("scope='" + scope + "'")
                .add("session_secret='" + session_secret + "'")
                .toString();
    }
}