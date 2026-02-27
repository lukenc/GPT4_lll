package com.wmsay.gpt4_lll.mcp;

import java.util.Collections;
import java.util.Map;

/**
 * MCP 工具执行结果。
 */
public class McpToolResult {

    public enum ResultType {
        TEXT,
        STRUCTURED,
        ERROR
    }

    private final ResultType type;
    private final String textContent;
    private final Map<String, Object> structuredData;
    private final String errorMessage;

    private McpToolResult(ResultType type, String textContent,
                          Map<String, Object> structuredData, String errorMessage) {
        this.type = type;
        this.textContent = textContent;
        this.structuredData = structuredData == null ? null : Collections.unmodifiableMap(structuredData);
        this.errorMessage = errorMessage;
    }

    public static McpToolResult text(String content) {
        return new McpToolResult(ResultType.TEXT, content, null, null);
    }

    public static McpToolResult structured(Map<String, Object> data) {
        return new McpToolResult(ResultType.STRUCTURED, null, data, null);
    }

    public static McpToolResult error(String message) {
        return new McpToolResult(ResultType.ERROR, null, null, message);
    }

    public ResultType getType() {
        return type;
    }

    public String getTextContent() {
        return textContent;
    }

    public Map<String, Object> getStructuredData() {
        return structuredData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
