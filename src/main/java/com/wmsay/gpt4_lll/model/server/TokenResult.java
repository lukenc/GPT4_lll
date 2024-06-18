package com.wmsay.gpt4_lll.model.server;

import java.util.List;
import java.util.StringJoiner;

public class TokenResult {
    List<ApiToken> tokenList;

    public List<ApiToken> getTokenList() {
        return tokenList;
    }

    public TokenResult setTokenList(List<ApiToken> tokenList) {
        this.tokenList = tokenList;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TokenResult.class.getSimpleName() + "[", "]")
                .add("tokenList=" + tokenList)
                .toString();
    }
}
