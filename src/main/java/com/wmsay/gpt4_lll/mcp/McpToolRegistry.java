package com.wmsay.gpt4_lll.mcp;

import com.wmsay.gpt4_lll.mcp.tools.FileReadTool;
import com.wmsay.gpt4_lll.mcp.tools.KeywordSearchTool;
import com.wmsay.gpt4_lll.mcp.tools.ProjectTreeTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册表。
 * 当前阶段仅注册通用能力工具，不接入业务 Action 工具。
 */
public class McpToolRegistry {

    private static final Map<String, McpTool> TOOLS = new LinkedHashMap<>();

    static {
        register(new KeywordSearchTool());
        register(new FileReadTool());
        register(new ProjectTreeTool());
    }

    public static void register(McpTool tool) {
        TOOLS.put(tool.name(), tool);
    }

    public static McpTool getTool(String name) {
        return TOOLS.get(name);
    }

    public static List<McpTool> getAllTools() {
        return new ArrayList<>(TOOLS.values());
    }

    public static String generateToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (McpTool tool : TOOLS.values()) {
            sb.append("### ").append(tool.name()).append("\n");
            sb.append(tool.description()).append("\n");
            sb.append("inputSchema: ").append(tool.inputSchema()).append("\n\n");
        }
        return sb.toString();
    }
}
