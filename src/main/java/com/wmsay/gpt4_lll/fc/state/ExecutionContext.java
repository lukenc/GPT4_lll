package com.wmsay.gpt4_lll.fc.state;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.mcp.McpContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 执行上下文（平台无关）。
 * 基于 {@link ToolContext} 提供类型安全的上下文数据访问、
 * 验证和完整性检查、快照和恢复功能，以及线程安全的数据访问。
 *
 * <p>框架层零 IntelliJ 依赖。宿主层通过 {@code IntelliJToolContext} 实现
 * {@link ToolContext} 接口，将 Project/Editor 等平台对象存储在 ToolContext 的
 * data 中（key: "project"、"editor"），框架层通过 {@code toolContext.get()} 访问。</p>
 *
 * <ul>
 *   <li>类型安全的扩展数据存储</li>
 *   <li>上下文验证和完整性检查</li>
 *   <li>快照和恢复（用于重试场景）</li>
 *   <li>线程安全的读写访问</li>
 * </ul>
 *
 * @see ToolContext
 */
public class ExecutionContext {

    private final ToolContext toolContext;
    private final Map<String, Object> extraData;
    private final ReadWriteLock lock;

    /**
     * 私有构造函数，通过工厂方法或 {@link Builder} 创建实例。
     */
    private ExecutionContext(ToolContext toolContext,
                            Map<String, Object> extraData,
                            ReadWriteLock lock) {
        this.toolContext = toolContext;
        this.extraData = extraData;
        this.lock = lock;
    }

    /**
     * 从 ToolContext 创建 ExecutionContext。
     *
     * @param toolContext 工具上下文
     * @return ExecutionContext 实例
     * @throws IllegalArgumentException 如果 toolContext 为 null
     */
    public static ExecutionContext fromToolContext(ToolContext toolContext) {
        if (toolContext == null) {
            throw new IllegalArgumentException("toolContext must not be null");
        }
        return new ExecutionContext(
                toolContext,
                new ConcurrentHashMap<>(),
                new ReentrantReadWriteLock());
    }

    /**
     * 从 McpContext 创建 ExecutionContext（向后兼容）。
     * 内部将 McpContext 包装为 ToolContext，并将 McpContext 存储在 data 中。
     *
     * @param mcpContext MCP 上下文
     * @return ExecutionContext 实例
     * @throws IllegalArgumentException 如果 mcpContext 为 null
     */
    public static ExecutionContext fromMcpContext(McpContext mcpContext) {
        if (mcpContext == null) {
            throw new IllegalArgumentException("mcpContext must not be null");
        }
        ToolContext.Builder builder = ToolContext.builder()
                .workspaceRoot(mcpContext.getProjectRoot());
        // Store McpContext itself for backward compat
        builder.data("mcpContext", mcpContext);
        // Store Project and Editor if available
        if (mcpContext.getProject() != null) {
            builder.data("project", mcpContext.getProject());
        }
        if (mcpContext.getEditor() != null) {
            builder.data("editor", mcpContext.getEditor());
        }
        ToolContext tc = builder.build();

        // Extract selected text from editor if available
        String selectedText = null;
        if (mcpContext.getEditor() != null) {
            try {
                selectedText = mcpContext.getEditor().getSelectionModel().getSelectedText();
            } catch (Exception ignored) {
                // Editor may not have selection
            }
        }

        Map<String, Object> extra = new ConcurrentHashMap<>();
        if (selectedText != null) {
            extra.put("selectedText", selectedText);
        }

        return new ExecutionContext(tc, extra, new ReentrantReadWriteLock());
    }

    /**
     * 使用 Builder 创建 ExecutionContext。
     *
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ---- ToolContext 访问 ----

    /**
     * 获取底层 ToolContext。
     *
     * @return 工具上下文
     */
    public ToolContext getToolContext() {
        lock.readLock().lock();
        try {
            return toolContext;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取项目根目录路径（委托给 ToolContext）。
     *
     * @return 项目根目录 Path，可能为 null
     */
    public Path getProjectRoot() {
        lock.readLock().lock();
        try {
            return toolContext.getWorkspaceRoot();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---- 类型安全的扩展数据访问 ----

    /**
     * 存储扩展数据。
     *
     * @param key   数据键，不能为 null
     * @param value 数据值
     * @throws IllegalArgumentException 如果 key 为 null
     */
    public void putData(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        lock.writeLock().lock();
        try {
            extraData.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取扩展数据（类型安全）。
     *
     * @param key  数据键，不能为 null
     * @param type 期望类型
     * @param <T>  返回类型
     * @return 数据值，不存在时返回 null
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws ClassCastException       类型不匹配时抛出
     */
    public <T> T getData(String key, Class<T> type) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        lock.readLock().lock();
        try {
            Object value = extraData.get(key);
            if (value == null) {
                return null;
            }
            if (!type.isInstance(value)) {
                throw new ClassCastException(
                        "Context data '" + key + "' is " + value.getClass().getName()
                                + ", expected " + type.getName());
            }
            return type.cast(value);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取扩展数据，不存在时返回默认值。
     *
     * @param key          数据键
     * @param type         期望类型
     * @param defaultValue 默认值
     * @param <T>          返回类型
     * @return 数据值，不存在时返回 defaultValue
     */
    public <T> T getDataOrDefault(String key, Class<T> type, T defaultValue) {
        T value = getData(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * 检查是否包含指定扩展数据。
     *
     * @param key 数据键
     * @return true 表示包含该键
     */
    public boolean hasData(String key) {
        lock.readLock().lock();
        try {
            return extraData.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---- 上下文验证和完整性检查 ----

    /**
     * 验证上下文完整性。
     * 检查 workspaceRoot 是否可用。
     *
     * @return 验证结果，包含所有缺失字段的错误信息
     */
    public ValidationResult validate() {
        lock.readLock().lock();
        try {
            return validateInternal();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 验证指定字段是否可用。
     *
     * @param requiredFields 需要检查的字段名列表
     * @return 验证结果
     */
    public ValidationResult validateRequired(String... requiredFields) {
        lock.readLock().lock();
        try {
            List<String> missing = new ArrayList<>();
            for (String field : requiredFields) {
                if (!isFieldAvailable(field)) {
                    missing.add(field);
                }
            }
            if (missing.isEmpty()) {
                return ValidationResult.valid();
            }
            return ValidationResult.invalid(missing);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---- 快照和恢复 ----

    /**
     * 创建当前上下文的快照。
     * 快照包含 ToolContext 引用和扩展数据的深拷贝。
     *
     * @return 上下文快照
     */
    public Snapshot createSnapshot() {
        lock.readLock().lock();
        try {
            Map<String, Object> dataCopy = new ConcurrentHashMap<>(extraData);
            return new Snapshot(toolContext, dataCopy);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 从快照恢复 ExecutionContext。
     *
     * @param snapshot 快照
     * @return 恢复的 ExecutionContext
     */
    public static ExecutionContext fromSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return new ExecutionContext(
                snapshot.toolContext,
                new ConcurrentHashMap<>(snapshot.extraData),
                new ReentrantReadWriteLock());
    }

    // ---- 内部方法 ----

    private ValidationResult validateInternal() {
        List<String> missing = new ArrayList<>();
        // "project" is checked via toolContext.get() — stored by IntelliJToolContext or fromMcpContext
        if (toolContext.get("project", Object.class) == null) {
            missing.add("project");
        }
        if (toolContext.getWorkspaceRoot() == null) {
            missing.add("projectRoot");
        }
        if (missing.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(missing);
    }

    private boolean isFieldAvailable(String field) {
        switch (field) {
            case "project":
                return toolContext.get("project", Object.class) != null;
            case "editor":
                return toolContext.get("editor", Object.class) != null;
            case "projectRoot":
                return toolContext.getWorkspaceRoot() != null;
            case "selectedText":
                // Check extraData first, then toolContext
                Object sel = extraData.get("selectedText");
                if (sel instanceof String) {
                    return !((String) sel).isEmpty();
                }
                return false;
            default:
                return extraData.containsKey(field);
        }
    }

    // ---- 快照类 ----

    /**
     * 上下文快照，用于重试场景的状态保存和恢复。
     * 快照中的扩展数据为不可修改的副本。
     */
    public static class Snapshot {
        private final ToolContext toolContext;
        private final Map<String, Object> extraData;

        Snapshot(ToolContext toolContext, Map<String, Object> extraData) {
            this.toolContext = toolContext;
            this.extraData = Collections.unmodifiableMap(extraData);
        }

        /**
         * 获取快照中的 ToolContext。
         *
         * @return 工具上下文
         */
        public ToolContext getToolContext() {
            return toolContext;
        }

        /**
         * 获取快照中的扩展数据（不可修改）。
         *
         * @return 不可修改的扩展数据映射
         */
        public Map<String, Object> getExtraData() {
            return extraData;
        }
    }

    // ---- 验证结果 ----

    /**
     * 上下文验证结果。
     * 包含验证是否通过以及缺失字段列表。
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> missingFields;

        private ValidationResult(boolean valid, List<String> missingFields) {
            this.valid = valid;
            this.missingFields = Collections.unmodifiableList(missingFields);
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult invalid(List<String> missingFields) {
            return new ValidationResult(false, new ArrayList<>(missingFields));
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }

        public String getErrorMessage() {
            if (valid) {
                return "";
            }
            return "Required context data unavailable: " + String.join(", ", missingFields);
        }
    }

    // ---- Builder ----

    /**
     * ExecutionContext 构建器。
     * 接受 ToolContext 作为必需参数。
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * ExecutionContext ctx = ExecutionContext.builder()
     *     .toolContext(toolContext)
     *     .data("customKey", "customValue")
     *     .build();
     * }</pre>
     *
     * <p>向后兼容（通过 McpContext）：</p>
     * <pre>{@code
     * ExecutionContext ctx = ExecutionContext.builder()
     *     .mcpContext(mcpContext)
     *     .selectedText("selected code")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private ToolContext toolContext;
        private McpContext mcpContext;
        private String selectedText;
        private final Map<String, Object> extraData = new ConcurrentHashMap<>();

        /**
         * 设置 ToolContext（推荐方式）。
         *
         * @param toolContext 工具上下文
         * @return this
         */
        public Builder toolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
            return this;
        }

        /**
         * 设置 McpContext（向后兼容）。
         * 内部会将 McpContext 包装为 ToolContext。
         *
         * @param mcpContext MCP 上下文
         * @return this
         */
        public Builder mcpContext(McpContext mcpContext) {
            this.mcpContext = mcpContext;
            return this;
        }

        /**
         * 设置选中文本（存储在 extraData 中）。
         *
         * @param selectedText 选中文本
         * @return this
         */
        public Builder selectedText(String selectedText) {
            this.selectedText = selectedText;
            return this;
        }

        /**
         * 添加扩展数据。
         *
         * @param key   数据键
         * @param value 数据值
         * @return this
         */
        public Builder data(String key, Object value) {
            this.extraData.put(key, value);
            return this;
        }

        /**
         * 构建 ExecutionContext 实例。
         *
         * @return 新的 ExecutionContext
         * @throws IllegalArgumentException 如果 toolContext 和 mcpContext 均未设置
         */
        public ExecutionContext build() {
            ToolContext tc = this.toolContext;
            if (tc == null && mcpContext != null) {
                // Wrap McpContext into ToolContext for backward compat
                ToolContext.Builder tcBuilder = ToolContext.builder()
                        .workspaceRoot(mcpContext.getProjectRoot());
                tcBuilder.data("mcpContext", mcpContext);
                if (mcpContext.getProject() != null) {
                    tcBuilder.data("project", mcpContext.getProject());
                }
                if (mcpContext.getEditor() != null) {
                    tcBuilder.data("editor", mcpContext.getEditor());
                }
                tc = tcBuilder.build();
            }
            if (tc == null) {
                throw new IllegalArgumentException("toolContext or mcpContext is required");
            }

            Map<String, Object> data = new ConcurrentHashMap<>(extraData);
            if (selectedText != null) {
                data.put("selectedText", selectedText);
            }

            return new ExecutionContext(tc, data, new ReentrantReadWriteLock());
        }
    }
}
