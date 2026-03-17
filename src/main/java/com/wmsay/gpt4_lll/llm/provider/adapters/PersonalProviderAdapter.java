package com.wmsay.gpt4_lll.llm.provider.adapters;

import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;

/**
 * 自定义（Personal）供应商适配器。
 * <p>
 * URL 和 API Key 均从用户设置中读取，
 * 假设使用 OpenAI 标准消息格式和 SSE 解析格式。
 */
public class PersonalProviderAdapter implements ProviderAdapter {

    @Override
    public String getProviderName() {
        return "Personal"; // ProviderNameEnum.PERSONAL.getProviderName()
    }

    @Override
    public String getApiUrl(MyPluginSettings settings, String modelName) {
        return settings.getPersonalApiUrl();
    }

    @Override
    public String getApiKey(MyPluginSettings settings) {
        return settings.getPersonalApiKey();
    }

    // supportsSystemRole()   → true（默认，假设自定义供应商兼容 OpenAI 标准）
    // adaptMessages()        → 不变（默认，OpenAI 标准消息格式）
    // parseSseLine()         → OpenAI 标准（默认，SseStreamProcessor.processOpenAiLine）
}
