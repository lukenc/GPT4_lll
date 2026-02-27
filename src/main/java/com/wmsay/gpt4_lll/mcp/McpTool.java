package com.wmsay.gpt4_lll.mcp;

import java.util.Map;

/**
 * MCP 工具接口。
 * 每个工具都通过统一协议暴露 name/description/schema/execute。
 */
public interface McpTool {

    /**
     * 工具唯一名称，例如 grep/read_file/tree。
     */
    String name();

    /**
     * 工具说明，供 Agent 理解该工具能力。
     */
    String description();

    /**
     * 工具参数结构说明（JSON Schema 风格）。
     */
    Map<String, Object> inputSchema();

    /**
     * 执行工具。
     *
     * @param context IDE 上下文
     * @param params  工具参数
     * @return 结构化执行结果
     */
    McpToolResult execute(McpContext context, Map<String, Object> params);
}
