package com.wmsay.gpt4_lll.fc.tools;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 框架层工具接口。
 * <p>
 * 从 {@code McpTool} 重命名迁移，定义工具的名称、描述、参数 schema 和执行逻辑。
 * 通过 {@link ToolContext} 访问宿主环境能力，返回 {@link ToolResult} 作为执行结果，
 * 使工具实现不依赖任何平台特定 API。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * public class MyTool implements Tool {
 *     public String name() { return "my_tool"; }
 *     public String description() { return "示例工具"; }
 *     public Map<String, Object> inputSchema() { return Map.of(); }
 *     public ToolResult execute(ToolContext context, Map<String, Object> params) {
 *         return ToolResult.text("执行成功");
 *     }
 * }
 * }</pre>
 *
 * @see ToolContext
 * @see ToolResult
 */
public interface Tool {

    /**
     * 工具唯一名称，例如 "read_file"、"keyword_search"、"shell_exec"。
     *
     * @return 工具名称，不能为 null 或空字符串
     */
    String name();

    /**
     * 工具说明，供 Agent 理解该工具能力。
     *
     * @return 工具描述文本，不能为 null
     */
    String description();

    /**
     * 工具参数结构说明（JSON Schema 风格）。
     *
     * @return 参数 schema 定义，不能为 null
     */
    Map<String, Object> inputSchema();

    /**
     * 执行工具。
     *
     * @param context 工具执行上下文，提供宿主环境访问能力，不能为 null
     * @param params  工具参数，键值对形式，不能为 null
     * @return 结构化执行结果
     */
    ToolResult execute(ToolContext context, Map<String, Object> params);

    /**
     * 工具分类。用于工具筛选和组织。
     *
     * @return 分类名称，默认 "general"
     */
    default String category() {
        return "general";
    }

    /**
     * 工具标签集合。用于更细粒度的工具分类和筛选。
     *
     * @return 标签集合，默认空集合（不可变）
     */
    default Set<String> tags() {
        return Collections.emptySet();
    }

    /**
     * 是否需要用户审批后才能执行。
     * <p>
     * 返回 true 时，框架会在执行前通过 {@code ApprovalProvider} 请求用户确认。
     * </p>
     *
     * @return 是否需要审批，默认 false
     */
    default boolean requiresApproval() {
        return false;
    }

    /**
     * 是否支持并发安全执行。
     * <p>
     * 返回 false 时，框架会对同一上下文内的该工具调用进行串行化。
     * </p>
     *
     * @return 是否并发安全，默认 true
     */
    default boolean isConcurrentSafe() {
        return true;
    }
}
