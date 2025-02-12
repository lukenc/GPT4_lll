package com.wmsay.gpt4_lll.model.enums;

public enum ProviderNameEnum {
    FREE("免费系列"),
    PERSONAL("Personal"),
    BAIDU("Baidu"),
    ALI("Alibaba"),
    OPEN_AI("OpenAI"),
    GROK("X-GROK"),
    DEEP_SEEK("DeepSeek"),
    ;
    final String providerName;


    ProviderNameEnum(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
