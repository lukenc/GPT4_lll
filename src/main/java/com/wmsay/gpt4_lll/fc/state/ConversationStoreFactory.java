package com.wmsay.gpt4_lll.fc.state;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ConversationStore} 工厂类。
 * <p>
 * 通过 {@link ServiceLoader} 自动发现并注册 {@link ConversationStore} SPI 实现。
 * 未配置任何 SPI 实现时，默认使用 {@link JsonConversationStore}。
 * <p>
 * SPI 加载异常时记录警告日志并继续，不中断框架初始化。
 * <p>
 * 纯 Java 实现，不依赖任何 {@code com.intellij.*} API。
 *
 * @see ConversationStore
 * @see JsonConversationStore
 */
public final class ConversationStoreFactory {

    private static final Logger LOG = Logger.getLogger(ConversationStoreFactory.class.getName());

    private ConversationStoreFactory() {
    }

    /**
     * 获取默认的 {@link ConversationStore} 实例。
     * <p>
     * 通过 SPI 发现第一个可用的实现；若无 SPI 实现，返回 {@link JsonConversationStore}。
     *
     * @return ConversationStore 实例（非 null）
     */
    public static ConversationStore getDefault() {
        try {
            ServiceLoader<ConversationStore> loader = ServiceLoader.load(ConversationStore.class);
            for (ConversationStore store : loader) {
                LOG.fine(() -> "Discovered ConversationStore SPI: " + store.getClass().getName());
                return store;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to load ConversationStore via SPI, falling back to default", e);
        }
        return new JsonConversationStore();
    }

    /**
     * 根据类型名称获取 {@link ConversationStore} 实例。
     * <p>
     * 支持的类型名称：
     * <ul>
     *   <li>{@code "json"} — 返回 {@link JsonConversationStore}</li>
     *   <li>其他 — 尝试通过 SPI 查找匹配的实现，未找到时返回 {@link JsonConversationStore}</li>
     * </ul>
     *
     * @param type 存储类型名称（非 null）
     * @return ConversationStore 实例（非 null）
     */
    public static ConversationStore create(String type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        if ("json".equalsIgnoreCase(type)) {
            return new JsonConversationStore();
        }

        // Try SPI discovery for custom types
        try {
            ServiceLoader<ConversationStore> loader = ServiceLoader.load(ConversationStore.class);
            for (ConversationStore store : loader) {
                String className = store.getClass().getSimpleName().toLowerCase();
                if (className.contains(type.toLowerCase())) {
                    LOG.fine(() -> "Found ConversationStore for type '" + type + "': "
                            + store.getClass().getName());
                    return store;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to load ConversationStore for type '" + type + "' via SPI", e);
        }

        LOG.info(() -> "No ConversationStore found for type '" + type
                + "', falling back to JsonConversationStore");
        return new JsonConversationStore();
    }
}
