package com.wmsay.gpt4_lll.fc.context;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
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
 * 执行上下文。
 * 封装当前 Project、Editor、选中文本等 IDE 状态，提供类型安全的上下文数据访问、
 * 验证和完整性检查、快照和恢复功能，以及线程安全的数据访问。
 *
 * <p>包装 {@link McpContext} 并扩展以下能力：
 * <ul>
 *   <li>选中文本的缓存和访问</li>
 *   <li>类型安全的扩展数据存储</li>
 *   <li>上下文验证和完整性检查</li>
 *   <li>快照和恢复（用于重试场景）</li>
 *   <li>线程安全的读写访问</li>
 * </ul>
 *
 * @see McpContext
 */
public class ExecutionContext {

    private final McpContext mcpContext;
    private final String selectedText;
    private final Map<String, Object> extraData;
    private final ReadWriteLock lock;

    /**
     * 私有构造函数，通过 {@link #fromMcpContext(McpContext)}、{@link #fromSnapshot(Snapshot)}
     * 或 {@link Builder} 创建实例。
     *
     * @param mcpContext   底层 MCP 上下文
     * @param selectedText 编辑器选中文本（可为 null）
     * @param extraData    扩展数据存储
     * @param lock         读写锁
     */
    private ExecutionContext(McpContext mcpContext,
                            String selectedText,
                            Map<String, Object> extraData,
                            ReadWriteLock lock) {
        this.mcpContext = mcpContext;
        this.selectedText = selectedText;
        this.extraData = extraData;
        this.lock = lock;
    }

    /**
     * 从 McpContext 创建 ExecutionContext，自动提取编辑器选中文本。
     *
     * @param mcpContext MCP 上下文
     * @return ExecutionContext 实例
     */
    public static ExecutionContext fromMcpContext(McpContext mcpContext) {
        if (mcpContext == null) {
            throw new IllegalArgumentException("mcpContext must not be null");
        }
        String selected = extractSelectedText(mcpContext.getEditor());
        return new ExecutionContext(
                mcpContext, selected,
                new ConcurrentHashMap<>(),
                new ReentrantReadWriteLock());
    }

    /**
     * 使用 Builder 创建 ExecutionContext。
     *
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ---- 类型安全的上下文数据访问 (Req 9.1, 9.2) ----

    /**
     * 获取底层 McpContext（兼容现有 API）。
     *
     * @return MCP 上下文
     */
    public McpContext getMcpContext() {
        lock.readLock().lock();
        try {
            return mcpContext;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取当前项目。
     *
     * @return IntelliJ Project 实例，可能为 null
     */
    public Project getProject() {
        lock.readLock().lock();
        try {
            return mcpContext.getProject();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取当前编辑器。
     *
     * @return IntelliJ Editor 实例，可能为 null
     */
    public Editor getEditor() {
        lock.readLock().lock();
        try {
            return mcpContext.getEditor();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取项目根目录路径。
     *
     * @return 项目根目录 Path，可能为 null
     */
    public Path getProjectRoot() {
        lock.readLock().lock();
        try {
            return mcpContext.getProjectRoot();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取编辑器选中文本。
     *
     * @return 选中文本，无选中时返回 null
     */
    public String getSelectedText() {
        lock.readLock().lock();
        try {
            return selectedText;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---- 类型安全的扩展数据访问 (Req 9.2) ----

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

    // ---- 上下文验证和完整性检查 (Req 9.3, 9.4) ----

    /**
     * 验证上下文完整性。
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

    // ---- 快照和恢复 (Req 9.5) ----

    /**
     * 创建当前上下文的快照。
     * 快照包含 McpContext 引用、选中文本和扩展数据的深拷贝。
     *
     * @return 上下文快照
     */
    public Snapshot createSnapshot() {
        lock.readLock().lock();
        try {
            Map<String, Object> dataCopy = new ConcurrentHashMap<>(extraData);
            return new Snapshot(mcpContext, selectedText, dataCopy);
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
                snapshot.mcpContext,
                snapshot.selectedText,
                new ConcurrentHashMap<>(snapshot.extraData),
                new ReentrantReadWriteLock());
    }

    // ---- 内部方法 ----

    private ValidationResult validateInternal() {
        List<String> missing = new ArrayList<>();
        if (mcpContext.getProject() == null) {
            missing.add("project");
        }
        if (mcpContext.getProjectRoot() == null) {
            missing.add("projectRoot");
        }
        // editor 和 selectedText 是可选的，不作为必需字段
        if (missing.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(missing);
    }

    private boolean isFieldAvailable(String field) {
        switch (field) {
            case "project":
                return mcpContext.getProject() != null;
            case "editor":
                return mcpContext.getEditor() != null;
            case "projectRoot":
                return mcpContext.getProjectRoot() != null;
            case "selectedText":
                return selectedText != null && !selectedText.isEmpty();
            default:
                return extraData.containsKey(field);
        }
    }

    private static String extractSelectedText(Editor editor) {
        if (editor == null) {
            return null;
        }
        SelectionModel selectionModel = editor.getSelectionModel();
        return selectionModel.getSelectedText();
    }

    // ---- 快照类 (Req 9.5) ----

    /**
     * 上下文快照，用于重试场景的状态保存和恢复。
     * 快照中的扩展数据为不可修改的副本。
     */
    public static class Snapshot {
        private final McpContext mcpContext;
        private final String selectedText;
        private final Map<String, Object> extraData;

        Snapshot(McpContext mcpContext, String selectedText, Map<String, Object> extraData) {
            this.mcpContext = mcpContext;
            this.selectedText = selectedText;
            this.extraData = Collections.unmodifiableMap(extraData);
        }

        /**
         * 获取快照中的 MCP 上下文。
         *
         * @return MCP 上下文
         */
        public McpContext getMcpContext() {
            return mcpContext;
        }

        /**
         * 获取快照中的选中文本。
         *
         * @return 选中文本，可能为 null
         */
        public String getSelectedText() {
            return selectedText;
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

    // ---- 验证结果 (Req 9.3, 9.4) ----

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

        /**
         * 创建验证通过的结果。
         *
         * @return 验证通过的结果
         */
        public static ValidationResult valid() {
            return new ValidationResult(true, Collections.emptyList());
        }

        /**
         * 创建验证失败的结果。
         *
         * @param missingFields 缺失字段列表
         * @return 验证失败的结果
         */
        public static ValidationResult invalid(List<String> missingFields) {
            return new ValidationResult(false, new ArrayList<>(missingFields));
        }

        /**
         * 验证是否通过。
         *
         * @return true 表示验证通过
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * 获取缺失字段列表。
         *
         * @return 不可修改的缺失字段列表，验证通过时为空列表
         */
        public List<String> getMissingFields() {
            return missingFields;
        }

        /**
         * 生成错误消息，列出所有缺失字段。
         */
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
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * ExecutionContext ctx = ExecutionContext.builder()
     *     .mcpContext(mcpContext)
     *     .selectedText("selected code")
     *     .data("customKey", "customValue")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private McpContext mcpContext;
        private String selectedText;
        private final Map<String, Object> extraData = new ConcurrentHashMap<>();

        /**
         * 设置底层 MCP 上下文（必需）。
         *
         * @param mcpContext MCP 上下文
         * @return this
         */
        public Builder mcpContext(McpContext mcpContext) {
            this.mcpContext = mcpContext;
            return this;
        }

        /**
         * 设置编辑器选中文本。
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
         * @throws IllegalArgumentException 如果 mcpContext 未设置
         */
        public ExecutionContext build() {
            if (mcpContext == null) {
                throw new IllegalArgumentException("mcpContext is required");
            }
            return new ExecutionContext(
                    mcpContext, selectedText,
                    new ConcurrentHashMap<>(extraData),
                    new ReentrantReadWriteLock());
        }
    }
}
