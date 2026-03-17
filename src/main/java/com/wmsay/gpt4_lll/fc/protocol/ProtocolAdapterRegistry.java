package com.wmsay.gpt4_lll.fc.protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议适配器注册表。
 * <p>
 * 管理所有已注册的 {@link ProtocolAdapter} 实例,并提供基于供应商名称的
 * 自动选择逻辑:
 * <ol>
 *   <li>优先选择支持该供应商且具有原生 function calling 能力的适配器</li>
 *   <li>若无匹配,降级到 {@link MarkdownProtocolAdapter}(Prompt Engineering 模式）</li>
 * </ol>
 *
 * <p>预注册了 OpenAI、Anthropic 和 Markdown 三种适配器。支持通过
 * {@link #register(ProtocolAdapter)} 动态注册自定义适配器。
 */
public class ProtocolAdapterRegistry {

    private static final Map<String, ProtocolAdapter> adapters = new ConcurrentHashMap<>();

    private static final ProtocolAdapter FALLBACK_ADAPTER = new MarkdownProtocolAdapter();

    static {
        register(new OpenAIProtocolAdapter());
        register(new AnthropicProtocolAdapter());
        register(new MarkdownProtocolAdapter());
    }

    /**
     * 注册一个协议适配器。如果已存在同名适配器,将被覆盖。
     *
     * @param adapter 要注册的适配器,不能为 null
     * @throws IllegalArgumentException 如果 adapter 为 null 或 getName() 返回 null/空字符串
     */
    public static void register(ProtocolAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        String name = adapter.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("adapter name must not be null or empty");
        }
        adapters.put(name, adapter);
    }

    /**
     * 根据供应商名称获取最佳协议适配器。
     * <p>
     * 选择策略:
     * <ol>
     *   <li>优先返回支持该供应商且支持原生 function calling 的适配器</li>
     *   <li>若无匹配,降级返回 {@link MarkdownProtocolAdapter}</li>
     * </ol>
     *
     * @param providerName 供应商名称（如 "openai"、"claude-3" 等）
     * @return 最佳匹配的协议适配器,永远不会返回 null
     */
    public static ProtocolAdapter getAdapter(String providerName) {
        // 优先使用支持该供应商且具有原生 function calling 的适配器
        for (ProtocolAdapter adapter : adapters.values()) {
            if (adapter.supports(providerName)
                    && adapter.supportsNativeFunctionCalling()) {
                return adapter;
            }
        }
        // 降级到 Prompt Engineering 模式
        return FALLBACK_ADAPTER;
    }

    /**
     * 获取所有已注册的协议适配器。
     *
     * @return 不可修改的适配器集合
     */
    public static Collection<ProtocolAdapter> getAll() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    /**
     * 按名称获取指定的协议适配器。
     *
     * @param name 适配器名称
     * @return 对应的适配器,如果不存在则返回 null
     */
    public static ProtocolAdapter getByName(String name) {
        return adapters.get(name);
    }

    /**
     * 移除指定名称的协议适配器。
     *
     * @param name 要移除的适配器名称
     * @return 被移除的适配器,如果不存在则返回 null
     */
    public static ProtocolAdapter unregister(String name) {
        return adapters.remove(name);
    }
}
