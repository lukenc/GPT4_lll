package com.wmsay.gpt4_lll.fc.error;

/**
 * 工具不存在异常。
 * 当请求的工具名称不存在于 McpToolRegistry 时抛出。
 */
public class ToolNotFoundException extends RuntimeException {

    private final String toolName;

    public ToolNotFoundException(String toolName) {
        super(String.format("Tool '%s' not found", toolName));
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
