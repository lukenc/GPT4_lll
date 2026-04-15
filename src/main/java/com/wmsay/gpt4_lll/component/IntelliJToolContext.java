package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.mcp.McpContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IntelliJ Platform 宿主适配层的 ToolContext 实现。
 * <p>
 * 将 IntelliJ 的 {@link Project} 和 {@link Editor} 适配为框架层的
 * {@link ToolContext} 接口，使框架层工具系统无需直接依赖 IntelliJ Platform API。
 * </p>
 *
 * <p>内部数据存储使用 {@link ConcurrentHashMap}，线程安全。
 * 构造时自动将 Project 存储在 "project" key、Editor 存储在 "editor" key。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ToolContext ctx = new IntelliJToolContext(project, editor);
 * Path root = ctx.getWorkspaceRoot();
 * Project p = ctx.get("project", Project.class);
 * }</pre>
 *
 * @see ToolContext
 */
public class IntelliJToolContext implements ToolContext {

    private final Project project;
    private final Editor editor;
    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();
    private final Map<String, String> settings = new HashMap<>();

    /**
     * 创建 IntelliJ 宿主适配上下文。
     *
     * @param project IntelliJ 项目实例，不能为 null
     * @param editor  当前编辑器实例，可以为 null
     */
    public IntelliJToolContext(Project project, Editor editor) {
        if (project == null) {
            throw new IllegalArgumentException("Project must not be null");
        }
        this.project = project;
        this.editor = editor;
        // 预存 Project 和 Editor，供框架层通过 get() 访问
        data.put("project", project);
        if (editor != null) {
            data.put("editor", editor);
        }
    }

    /**
     * 创建 IntelliJ 宿主适配上下文，并关联 McpContext 以保持向后兼容。
     *
     * @param project    IntelliJ 项目实例，不能为 null
     * @param editor     当前编辑器实例，可以为 null
     * @param mcpContext McpContext 实例，可以为 null
     */
    public IntelliJToolContext(Project project, Editor editor, McpContext mcpContext) {
        this(project, editor);
        if (mcpContext != null) {
            data.put("mcpContext", mcpContext);
        }
    }

    @Override
    public Path getWorkspaceRoot() {
        String basePath = project.getBasePath();
        return basePath != null ? Paths.get(basePath) : null;
    }

    @Override
    public String getSetting(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Setting key must not be null");
        }
        return settings.get(key);
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
        Object value = data.get(key);
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
            data.remove(key);
        } else {
            data.put(key, value);
        }
    }

    /**
     * 设置配置项。
     *
     * @param key   配置键名
     * @param value 配置值
     */
    public void setSetting(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("Setting key must not be null");
        }
        settings.put(key, value);
    }

    /**
     * 获取底层 IntelliJ Project 实例。
     * <p>仅供宿主层代码使用，框架层应通过 {@code get("project", Project.class)} 访问。</p>
     *
     * @return IntelliJ Project 实例
     */
    public Project getProject() {
        return project;
    }

    /**
     * 获取底层 Editor 实例。
     * <p>仅供宿主层代码使用，框架层应通过 {@code get("editor", Editor.class)} 访问。</p>
     *
     * @return Editor 实例，可能为 null
     */
    public Editor getEditor() {
        return editor;
    }
}
