package com.wmsay.gpt4_lll.fc.tools;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文接口。
 * <p>
 * 抽象宿主环境访问能力，使框架层工具系统不依赖任何平台特定 API（如 IntelliJ Platform）。
 * 宿主层通过实现此接口（如 IntelliJToolContext）将平台能力桥接到框架层。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ToolContext ctx = ToolContext.builder()
 *     .workspaceRoot(Paths.get("/my/project"))
 *     .setting("apiKey", "sk-xxx")
 *     .data("editor", editorInstance)
 *     .build();
 * }</pre>
 *
 * @see Tool
 * @see ToolResult
 */
public interface ToolContext {

    /**
     * 获取工作区根目录路径。
     *
     * @return 工作区根目录，若未配置则返回 null
     */
    Path getWorkspaceRoot();

    /**
     * 获取指定 key 的配置值。
     *
     * @param key 配置键名，不能为 null
     * @return 配置值，若不存在则返回 null
     */
    String getSetting(String key);

    /**
     * 获取指定 key 的上下文数据，并转换为目标类型。
     *
     * @param key  数据键名，不能为 null
     * @param type 目标类型的 Class 对象，不能为 null
     * @param <T>  目标类型
     * @return 转换后的数据对象，若不存在则返回 null
     */
    <T> T get(String key, Class<T> type);

    /**
     * 设置上下文数据。
     *
     * @param key   数据键名，不能为 null
     * @param value 数据值
     */
    void set(String key, Object value);

    /**
     * 创建新的 ToolContext Builder 实例。
     *
     * @return 新的 Builder 实例
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * ToolContext 构建器。
     * <p>
     * 支持配置 workspaceRoot、settings 和自定义数据，构建后返回一个线程安全的 ToolContext 实现。
     * 数据存储使用 {@link ConcurrentHashMap}，settings 使用 {@link HashMap}。
     * </p>
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * ToolContext ctx = ToolContext.builder()
     *     .workspaceRoot(Paths.get("/workspace"))
     *     .setting("timeout", "30")
     *     .data("session", sessionObj)
     *     .build();
     * }</pre>
     */
    class Builder {
        private Path workspaceRoot;
        private final Map<String, Object> data = new ConcurrentHashMap<>();
        private final Map<String, String> settings = new HashMap<>();

        /**
         * 设置工作区根目录路径。
         *
         * @param root 工作区根目录路径
         * @return 当前 Builder 实例
         */
        public Builder workspaceRoot(Path root) {
            this.workspaceRoot = root;
            return this;
        }

        /**
         * 添加一个配置项。
         *
         * @param key   配置键名，不能为 null
         * @param value 配置值，不能为 null
         * @return 当前 Builder 实例
         */
        public Builder setting(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("Setting key must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("Setting value must not be null");
            }
            this.settings.put(key, value);
            return this;
        }

        /**
         * 添加一个上下文数据项。
         *
         * @param key   数据键名，不能为 null
         * @param value 数据值，不能为 null
         * @return 当前 Builder 实例
         */
        public Builder data(String key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("Data key must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("Data value must not be null");
            }
            this.data.put(key, value);
            return this;
        }

        /**
         * 构建 ToolContext 实例。
         *
         * @return 新的 ToolContext 实例
         */
        public ToolContext build() {
            Path root = this.workspaceRoot;
            Map<String, String> builtSettings = new HashMap<>(this.settings);
            Map<String, Object> builtData = new ConcurrentHashMap<>(this.data);

            return new ToolContext() {
                @Override
                public Path getWorkspaceRoot() {
                    return root;
                }

                @Override
                public String getSetting(String key) {
                    if (key == null) {
                        throw new IllegalArgumentException("Setting key must not be null");
                    }
                    return builtSettings.get(key);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> T get(String key, Class<T> type) {
                    if (key == null) {
                        throw new IllegalArgumentException("Data key must not be null");
                    }
                    if (type == null) {
                        throw new IllegalArgumentException("Type must not be null");
                    }
                    Object value = builtData.get(key);
                    if (value == null) {
                        return null;
                    }
                    if (!type.isInstance(value)) {
                        throw new ClassCastException(
                            "Value for key '" + key + "' is " + value.getClass().getName()
                                + ", expected " + type.getName());
                    }
                    return (T) value;
                }

                @Override
                public void set(String key, Object value) {
                    if (key == null) {
                        throw new IllegalArgumentException("Data key must not be null");
                    }
                    if (value == null) {
                        builtData.remove(key);
                    } else {
                        builtData.put(key, value);
                    }
                }
            };
        }
    }
}
