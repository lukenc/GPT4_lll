package com.wmsay.gpt4_lll.fc.config;

import com.wmsay.gpt4_lll.fc.error.CustomErrorHandler;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapterRegistry;
import com.wmsay.gpt4_lll.fc.validation.CustomValidator;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPI 扩展加载器。
 * <p>
 * 使用 Java {@link ServiceLoader} 机制发现并加载自定义扩展实现：
 * <ul>
 *   <li>{@link CustomValidator} — 自定义参数验证器</li>
 *   <li>{@link CustomErrorHandler} — 自定义错误处理器</li>
 *   <li>{@link ProtocolAdapter} — 自定义协议适配器</li>
 * </ul>
 *
 * <p>加载过程中遇到的错误会被记录到日志并跳过，不会中断其他扩展的加载。
 */
public class ExtensionLoader {

    private static final Logger LOG = Logger.getLogger(ExtensionLoader.class.getName());

    /**
     * 通过 SPI 加载所有 {@link CustomValidator} 实现。
     *
     * @return 已加载的自定义验证器列表（不会为 null）
     */
    public static List<CustomValidator> loadCustomValidators() {
        List<CustomValidator> validators = new ArrayList<>();
        try {
            ServiceLoader<CustomValidator> loader = ServiceLoader.load(CustomValidator.class);
            for (CustomValidator validator : loader) {
                validators.add(validator);
                LOG.info("Loaded custom validator: " + validator.getClass().getName());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load custom validators via SPI", e);
        }
        return validators;
    }

    /**
     * 通过 SPI 加载所有 {@link CustomErrorHandler} 实现。
     *
     * @return 已加载的自定义错误处理器列表（不会为 null）
     */
    public static List<CustomErrorHandler> loadCustomErrorHandlers() {
        List<CustomErrorHandler> handlers = new ArrayList<>();
        try {
            ServiceLoader<CustomErrorHandler> loader = ServiceLoader.load(CustomErrorHandler.class);
            for (CustomErrorHandler handler : loader) {
                handlers.add(handler);
                LOG.info("Loaded custom error handler: " + handler.getClass().getName());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load custom error handlers via SPI", e);
        }
        return handlers;
    }

    /**
     * 通过 SPI 加载所有 {@link ProtocolAdapter} 实现。
     *
     * @return 已加载的自定义协议适配器列表（不会为 null）
     */
    public static List<ProtocolAdapter> loadProtocolAdapters() {
        List<ProtocolAdapter> adapters = new ArrayList<>();
        try {
            ServiceLoader<ProtocolAdapter> loader = ServiceLoader.load(ProtocolAdapter.class);
            for (ProtocolAdapter adapter : loader) {
                adapters.add(adapter);
                LOG.info("Loaded custom protocol adapter: " + adapter.getClass().getName());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load custom protocol adapters via SPI", e);
        }
        return adapters;
    }

    /**
     * 加载所有 SPI 扩展并注册到对应的组件中。
     * <ul>
     *   <li>CustomValidator → 注册到 {@link ValidationEngine}</li>
     *   <li>CustomErrorHandler → 日志记录（ErrorHandler 通过构造函数接收）</li>
     *   <li>ProtocolAdapter → 注册到 {@link ProtocolAdapterRegistry}</li>
     * </ul>
     *
     * @param validationEngine 验证引擎，用于注册自定义验证器
     * @param errorHandler     错误处理器（仅用于日志记录，自定义处理器需在构造时传入）
     */
    public static void loadAll(ValidationEngine validationEngine, ErrorHandler errorHandler) {
        LOG.info("Loading SPI extensions...");

        // 加载并注册自定义验证器
        List<CustomValidator> validators = loadCustomValidators();
        for (CustomValidator validator : validators) {
            try {
                validationEngine.registerCustomValidator(validator);
                LOG.info("Registered custom validator: " + validator.getClass().getName());
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "Failed to register custom validator: " + validator.getClass().getName(), e);
            }
        }

        // 加载自定义错误处理器（记录日志；ErrorHandler 通过构造函数接收列表）
        List<CustomErrorHandler> handlers = loadCustomErrorHandlers();
        if (!handlers.isEmpty()) {
            LOG.info("Loaded " + handlers.size() + " custom error handler(s). "
                    + "Note: custom error handlers should be passed to ErrorHandler constructor.");
        }

        // 加载并注册自定义协议适配器
        List<ProtocolAdapter> adapters = loadProtocolAdapters();
        for (ProtocolAdapter adapter : adapters) {
            try {
                ProtocolAdapterRegistry.register(adapter);
                LOG.info("Registered custom protocol adapter: " + adapter.getClass().getName());
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "Failed to register custom protocol adapter: " + adapter.getClass().getName(), e);
            }
        }

        LOG.info("SPI extension loading complete. Validators=" + validators.size()
                + ", ErrorHandlers=" + handlers.size()
                + ", ProtocolAdapters=" + adapters.size());
    }
}
