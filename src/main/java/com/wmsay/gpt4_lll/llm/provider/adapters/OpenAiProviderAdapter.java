package com.wmsay.gpt4_lll.llm.provider.adapters;

import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.utils.ModelUtils;

import java.util.function.Function;

/**
 * OpenAI 标准格式供应商适配器。
 * <p>
 * 覆盖所有使用 OpenAI 兼容接口的供应商：OpenAI / ALI / GROK / DEEP_SEEK。
 * 这些供应商的消息格式和 SSE 解析相同，差异仅在于 URL 和 API Key 获取方式。
 * 通过构造参数区分不同供应商。
 * <p>
 * 使用方式（在 ProviderAdapterRegistry 中注册）：
 * <pre>
 * register(new OpenAiProviderAdapter("OpenAI", MyPluginSettings::getApiKey));
 * register(new OpenAiProviderAdapter("Alibaba", MyPluginSettings::getTongyiApiKey));
 * register(new OpenAiProviderAdapter("X-GROK", MyPluginSettings::getGrokApiKey));
 * register(new OpenAiProviderAdapter("DeepSeek", MyPluginSettings::getDeepSeekApiKey));
 * </pre>
 */
public class OpenAiProviderAdapter implements ProviderAdapter {

    private final String providerName;
    private final Function<MyPluginSettings, String> apiKeyGetter;

    /**
     * @param providerName 供应商名称（与 ProviderNameEnum.getProviderName() 一致）
     * @param apiKeyGetter 从 MyPluginSettings 获取 API Key 的函数
     */
    public OpenAiProviderAdapter(String providerName, Function<MyPluginSettings, String> apiKeyGetter) {
        this.providerName = providerName;
        this.apiKeyGetter = apiKeyGetter;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public String getApiUrl(MyPluginSettings settings, String modelName) {
        // 标准供应商的 URL 统一由 ModelUtils.provider2Url 管理
        return ModelUtils.getUrlByProvider(providerName);
    }

    @Override
    public String getApiKey(MyPluginSettings settings) {
        return apiKeyGetter.apply(settings);
    }

    // supportsSystemRole()   → true（默认，所有标准供应商支持 system role）
    // adaptMessages()        → 不变（默认，OpenAI 标准消息格式）
    // parseSseLine()         → OpenAI 标准（默认，SseStreamProcessor.processOpenAiLine）
}
