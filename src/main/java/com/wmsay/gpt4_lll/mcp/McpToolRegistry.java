package com.wmsay.gpt4_lll.mcp;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.mcp.tools.DiagnosticsTool;
import com.wmsay.gpt4_lll.mcp.tools.FileReadTool;
import com.wmsay.gpt4_lll.mcp.tools.FileWriteTool;
import com.wmsay.gpt4_lll.mcp.tools.KeywordSearchTool;
import com.wmsay.gpt4_lll.mcp.tools.OpenFilesTool;
import com.wmsay.gpt4_lll.mcp.tools.ProjectTreeTool;
import com.wmsay.gpt4_lll.mcp.tools.ShellExecTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册表。
 * 当前阶段仅注册通用能力工具，不接入业务 Action 工具。
 * <p>
 * 注意：具体工具实现已迁移为 {@link Tool} 接口，本注册表同时支持
 * {@link McpTool} 和 {@link Tool} 类型的注册。
 * 将在 Phase 8（task 15.3）中删除，届时由 {@code fc.tools.ToolRegistry} 完全替代。
 * </p>
 */
public class McpToolRegistry {

    private static final Map<String, Tool> TOOLS = new LinkedHashMap<>();

    static {
        registerTool(new KeywordSearchTool());
        registerTool(new FileReadTool());
        registerTool(new FileWriteTool());
        registerTool(new ProjectTreeTool());
        registerTool(new ShellExecTool());
        registerTool(new OpenFilesTool());
        registerTool(new DiagnosticsTool());
    }

    /**
     * 注册 McpTool（保留向后兼容）。
     * @deprecated 使用 {@link #registerTool(Tool)} 代替
     */
    @Deprecated
    public static void register(McpTool tool) {
        // McpTool 和 Tool 接口方法签名兼容，通过适配器桥接
        TOOLS.put(tool.name(), new McpToolAdapter(tool));
    }

    /**
     * 注册 Tool（框架层接口）。
     */
    public static void registerTool(Tool tool) {
        TOOLS.put(tool.name(), tool);
    }

    public static Tool getTool(String name) {
        return TOOLS.get(name);
    }

    /**
     * 获取所有已注册工具（返回 Tool 列表）。
     */
    public static List<Tool> getAllTools() {
        return new ArrayList<>(TOOLS.values());
    }

    public static String generateToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (Tool tool : TOOLS.values()) {
            sb.append("### ").append(tool.name()).append("\n");
            sb.append(tool.description()).append("\n");
            sb.append("inputSchema: ").append(tool.inputSchema()).append("\n\n");
        }
        return sb.toString();
    }
}
