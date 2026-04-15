package com.wmsay.gpt4_lll.fc.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 框架层工具注册表。
 * <p>
 * 从 {@code McpToolRegistry} 重构，使用 {@link ConcurrentHashMap} 存储工具实例，
 * 支持注册/注销/查询操作，支持 SPI（{@link ServiceLoader}）自动发现 {@link Tool} 实现，
 * 并在工具注册/注销时通知所有 {@link ToolRegistrationListener}。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ToolRegistry registry = new ToolRegistry();
 * registry.addToolRegistrationListener(new ToolRegistrationListener() {
 *     public void onToolRegistered(Tool tool) {
 *         System.out.println("Registered: " + tool.name());
 *     }
 *     public void onToolUnregistered(String toolName) {
 *         System.out.println("Unregistered: " + toolName);
 *     }
 * });
 * registry.registerTool(myTool);
 * registry.loadFromServiceLoader();
 * List<Tool> all = registry.getAllTools();
 * }</pre>
 *
 * @see Tool
 * @see ToolRegistrationListener
 */
public class ToolRegistry {

    private static final Logger LOG = Logger.getLogger(ToolRegistry.class.getName());

    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();
    private final List<ToolRegistrationListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 注册工具到注册表。
     * <p>
     * 以 {@link Tool#name()} 作为 key 存储。若同名工具已存在，将被覆盖。
     * 注册成功后通知所有 {@link ToolRegistrationListener}。
     * </p>
     *
     * @param tool 要注册的工具实例，不能为 null
     * @throws IllegalArgumentException 如果 tool 为 null，或 tool.name() 为 null 或空字符串
     */
    public void registerTool(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool must not be null");
        }
        String name = tool.name();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Tool name must not be null or empty");
        }
        tools.put(name, tool);
        LOG.fine(() -> "Tool registered: " + name);
        for (ToolRegistrationListener listener : listeners) {
            try {
                listener.onToolRegistered(tool);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener error on tool registration: " + name, e);
            }
        }
    }

    /**
     * 从注册表中注销指定名称的工具。
     * <p>
     * 若工具存在并被移除，通知所有 {@link ToolRegistrationListener}。
     * 若工具不存在，不做任何操作。
     * </p>
     *
     * @param name 要注销的工具名称，不能为 null
     * @throws IllegalArgumentException 如果 name 为 null
     */
    public void unregisterTool(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Tool name must not be null");
        }
        Tool removed = tools.remove(name);
        if (removed != null) {
            LOG.fine(() -> "Tool unregistered: " + name);
            for (ToolRegistrationListener listener : listeners) {
                try {
                    listener.onToolUnregistered(name);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Listener error on tool unregistration: " + name, e);
                }
            }
        }
    }

    /**
     * 获取指定名称的工具。
     *
     * @param name 工具名称，不能为 null
     * @return 对应的 Tool 实例，若不存在则返回 null
     * @throws IllegalArgumentException 如果 name 为 null
     */
    public Tool getTool(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Tool name must not be null");
        }
        return tools.get(name);
    }

    /**
     * 获取所有已注册工具的不可变列表副本。
     * <p>
     * 返回的列表是当前注册工具的快照，后续对注册表的修改不会影响已返回的列表。
     * </p>
     *
     * @return 不可变的工具列表
     */
    public List<Tool> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /**
     * 添加工具注册/注销事件监听器。
     *
     * @param listener 监听器实例，不能为 null
     * @throws IllegalArgumentException 如果 listener 为 null
     */
    public void addToolRegistrationListener(ToolRegistrationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        listeners.add(listener);
    }

    /**
     * 通过 Java SPI（{@link ServiceLoader}）自动发现并注册 {@link Tool} 实现。
     * <p>
     * 扫描 classpath 中所有通过 {@code META-INF/services/com.wmsay.gpt4_lll.fc.tools.Tool}
     * 声明的 Tool 实现类，逐个注册到本注册表。单个工具加载失败时记录警告日志并继续加载其他工具。
     * </p>
     */
    public void loadFromServiceLoader() {
        LOG.info("Loading tools via ServiceLoader...");
        ServiceLoader<Tool> loader = ServiceLoader.load(Tool.class);
        for (Tool tool : loader) {
            try {
                registerTool(tool);
                LOG.info(() -> "SPI tool loaded: " + tool.name());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to load SPI tool: " + e.getMessage(), e);
            }
        }
    }
}
