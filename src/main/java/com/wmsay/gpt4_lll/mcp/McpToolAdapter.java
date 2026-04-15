package com.wmsay.gpt4_lll.mcp;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.util.Map;

/**
 * 将旧的 {@link McpTool} 适配为框架层 {@link Tool} 接口的桥接适配器。
 * <p>
 * 仅用于向后兼容：当外部代码通过 {@code McpToolRegistry.register(McpTool)} 注册旧接口工具时，
 * 内部自动包装为 Tool。将在 Phase 8（task 15.3）中随 McpTool 一起删除。
 * </p>
 */
class McpToolAdapter implements Tool {

    private final McpTool delegate;

    McpToolAdapter(McpTool delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return delegate.inputSchema();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        // 构建 McpContext 从 ToolContext
        McpContext mcpContext = buildMcpContext(context);
        McpToolResult mcpResult = delegate.execute(mcpContext, params);
        return convertResult(mcpResult);
    }

    private McpContext buildMcpContext(ToolContext context) {
        if (context == null) {
            return new McpContext(null, null, null);
        }
        // 尝试从 ToolContext 获取原始 McpContext
        McpContext existing = context.get("mcpContext", McpContext.class);
        if (existing != null) {
            return existing;
        }
        // 构建最小 McpContext
        return new McpContext(null, null, context.getWorkspaceRoot());
    }

    private ToolResult convertResult(McpToolResult mcpResult) {
        if (mcpResult == null) {
            return ToolResult.error("Tool returned null result");
        }
        return switch (mcpResult.getType()) {
            case TEXT -> ToolResult.text(mcpResult.getTextContent());
            case STRUCTURED -> ToolResult.structured(mcpResult.getStructuredData());
            case ERROR -> ToolResult.error(mcpResult.getErrorMessage());
        };
    }
}
