package com.wmsay.gpt4_lll.llm.provider;

import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.provider.adapters.BaiduProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.adapters.FreeProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.adapters.OpenAiProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.adapters.PersonalProviderAdapter;


import java.util.HashMap;
import java.util.Map;

/**
 * 供应商适配器注册表。
 * <p>
 * 采用与 ModelUtils 相同的静态初始化模式（硬编码注册），
 * 而非 SPI 或反射扫描——保持与项目现有风格一致。
 * <p>
 * 新增供应商只需在 static {} 块中 register() 一个新的 Adapter 实例。
 * <p>
 * 覆盖的供应商与 ProviderNameEnum 完全一致：
 * <ul>
 *   <li>OpenAI — OpenAI 标准接口</li>
 *   <li>Alibaba — 通义千问（OpenAI 兼容）</li>
 *   <li>X-GROK — X 平台（OpenAI 兼容）</li>
 *   <li>DeepSeek — 深度求索（OpenAI 兼容）</li>
 *   <li>VolcEngine — 字节跳动火山引擎豆包（OpenAI 兼容）</li>
 *   <li>Baidu — 百度文心一言</li>
 *   <li>免费系列 — 免费百度 ernie-speed</li>
 *   <li>Personal — 用户自定义供应商</li>
 * </ul>
 */
public class ProviderAdapterRegistry {

    private static final Map<String, ProviderAdapter> adapters = new HashMap<>();

    /** 默认 Adapter（OpenAI 标准格式），当供应商未注册时作为 fallback */
    private static final ProviderAdapter DEFAULT_ADAPTER =
            new OpenAiProviderAdapter("OpenAI", MyPluginSettings::getApiKey);

    static {
        // OpenAI 兼容系列：共用标准实现，通过构造参数区分 API Key 获取方式
        register(new OpenAiProviderAdapter("OpenAI", MyPluginSettings::getApiKey));
        register(new OpenAiProviderAdapter("Alibaba", MyPluginSettings::getTongyiApiKey));
        register(new OpenAiProviderAdapter("X-GROK", MyPluginSettings::getGrokApiKey));
        register(new OpenAiProviderAdapter("DeepSeek", MyPluginSettings::getDeepSeekApiKey));
        register(new OpenAiProviderAdapter("VolcEngine", MyPluginSettings::getVolcApiKey));

        // 百度系列
        register(new BaiduProviderAdapter());
        register(new FreeProviderAdapter());

        // 用户自定义
        register(new PersonalProviderAdapter());
    }

    public static void register(ProviderAdapter adapter) {
        adapters.put(adapter.getProviderName(), adapter);
    }

    /**
     * 获取指定供应商的适配器。
     * 如果供应商未注册，返回 OpenAI 标准适配器作为默认实现。
     *
     * @param providerName 供应商名称（与 ProviderNameEnum.getProviderName() 一致）
     * @return 对应的 ProviderAdapter
     */
    public static ProviderAdapter getAdapter(String providerName) {
        return adapters.getOrDefault(providerName, DEFAULT_ADAPTER);
    }
}
