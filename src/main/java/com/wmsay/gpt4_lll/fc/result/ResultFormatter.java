package com.wmsay.gpt4_lll.fc.result;

import com.wmsay.gpt4_lll.fc.core.ErrorMessage;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.util.Map;

/**
 * 结果格式化器。
 * 将工具调用结果格式化为 LLM 可理解的消息，支持多种输出格式（TEXT、JSON、Markdown）。
 * 使用 {@link StringBuilder} 以满足性能要求 (Req 17.5)。
 *
 * <p>当结果数据超过 {@value #DEFAULT_MAX_LENGTH} 字符时，自动截断并添加摘要信息。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ResultFormatter formatter = new ResultFormatter();
 *
 * // 格式化成功结果
 * String text = formatter.formatSuccess(result, OutputFormat.TEXT);
 *
 * // 格式化错误结果
 * String json = formatter.formatError(errorResult, OutputFormat.JSON);
 *
 * // 手动截断大文本
 * String truncated = formatter.truncateLargeResult(largeContent, 4000);
 * }</pre>
 *
 * @see ToolCallResult
 * @see OutputFormat
 */
public class ResultFormatter {

    /**
     * 输出格式枚举
     */
    public enum OutputFormat {
        TEXT,
        JSON,
        MARKDOWN
    }

    /** 默认截断限制 (字符数) */
    public static final int DEFAULT_MAX_LENGTH = 4000;

    /**
     * 格式化成功的工具调用结果。
     *
     * @param result 工具调用结果，不能为 null
     * @param format 输出格式，不能为 null
     * @return 格式化后的字符串
     * @throws IllegalArgumentException 如果 result 或 format 为 null
     */
    public String formatSuccess(ToolCallResult result, OutputFormat format) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }

        String resultData = extractResultData(result);
        String truncatedData = truncateLargeResult(resultData, DEFAULT_MAX_LENGTH);

        return switch (format) {
            case TEXT -> formatSuccessText(result, truncatedData);
            case JSON -> formatSuccessJson(result, truncatedData);
            case MARKDOWN -> formatSuccessMarkdown(result, truncatedData);
        };
    }

    /**
     * 格式化错误的工具调用结果。
     *
     * @param result 工具调用结果，不能为 null
     * @param format 输出格式，不能为 null
     * @return 格式化后的字符串
     * @throws IllegalArgumentException 如果 result 或 format 为 null
     */
    public String formatError(ToolCallResult result, OutputFormat format) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }

        return switch (format) {
            case TEXT -> formatErrorText(result);
            case JSON -> formatErrorJson(result);
            case MARKDOWN -> formatErrorMarkdown(result);
        };
    }

    /**
     * 截断超过指定长度的结果字符串，并添加摘要。
     *
     * @param content   原始内容，null 时返回空字符串
     * @param maxLength 最大字符数，必须为正数
     * @return 截断后的内容（如果超长则附带摘要）
     * @throws IllegalArgumentException 如果 maxLength 不为正数
     */
    public String truncateLargeResult(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        if (content.length() <= maxLength) {
            return content;
        }

        StringBuilder sb = new StringBuilder(maxLength + 100);
        sb.append(content, 0, maxLength);
        sb.append("\n\n[Truncated: result too large. Showing ");
        sb.append(maxLength);
        sb.append(" of ");
        sb.append(content.length());
        sb.append(" characters]");
        return sb.toString();
    }


    // ── TEXT format ──────────────────────────────────────────────────

    private String formatSuccessText(ToolCallResult result, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(result.getToolName()).append('\n');
        sb.append("Call ID: ").append(result.getCallId()).append('\n');
        sb.append("Status: ").append(result.getStatus()).append('\n');
        sb.append("Duration: ").append(result.getDurationMs()).append("ms\n");
        sb.append("Result:\n").append(data);
        return sb.toString();
    }

    private String formatErrorText(ToolCallResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(result.getToolName()).append('\n');
        sb.append("Call ID: ").append(result.getCallId()).append('\n');
        sb.append("Status: ").append(result.getStatus()).append('\n');

        ErrorMessage error = result.getError();
        if (error != null) {
            sb.append("Error Type: ").append(error.getType()).append('\n');
            sb.append("Message: ").append(error.getMessage()).append('\n');
            if (error.getSuggestion() != null) {
                sb.append("Suggestion: ").append(error.getSuggestion()).append('\n');
            }
        }
        return sb.toString();
    }

    // ── JSON format ─────────────────────────────────────────────────

    private String formatSuccessJson(ToolCallResult result, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"tool\": ").append(jsonString(result.getToolName())).append(",\n");
        sb.append("  \"callId\": ").append(jsonString(result.getCallId())).append(",\n");
        sb.append("  \"status\": ").append(jsonString(result.getStatus().name())).append(",\n");
        sb.append("  \"durationMs\": ").append(result.getDurationMs()).append(",\n");
        sb.append("  \"result\": ").append(jsonString(data)).append('\n');
        sb.append('}');
        return sb.toString();
    }

    private String formatErrorJson(ToolCallResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"tool\": ").append(jsonString(result.getToolName())).append(",\n");
        sb.append("  \"callId\": ").append(jsonString(result.getCallId())).append(",\n");
        sb.append("  \"status\": ").append(jsonString(result.getStatus().name())).append(",\n");

        ErrorMessage error = result.getError();
        if (error != null) {
            sb.append("  \"errorType\": ").append(jsonString(error.getType())).append(",\n");
            sb.append("  \"message\": ").append(jsonString(error.getMessage())).append(",\n");
            if (error.getSuggestion() != null) {
                sb.append("  \"suggestion\": ").append(jsonString(error.getSuggestion())).append('\n');
            } else {
                // remove trailing comma+newline from message line, replace with just newline
                sb.setLength(sb.length() - 2);
                sb.append('\n');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    // ── Markdown format ─────────────────────────────────────────────

    private String formatSuccessMarkdown(ToolCallResult result, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Tool Result: ").append(result.getToolName()).append("\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Tool | ").append(result.getToolName()).append(" |\n");
        sb.append("| Call ID | ").append(result.getCallId()).append(" |\n");
        sb.append("| Status | ").append(result.getStatus()).append(" |\n");
        sb.append("| Duration | ").append(result.getDurationMs()).append("ms |\n\n");
        sb.append("**Result:**\n\n```\n").append(data).append("\n```");
        return sb.toString();
    }

    private String formatErrorMarkdown(ToolCallResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Tool Error: ").append(result.getToolName()).append("\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Tool | ").append(result.getToolName()).append(" |\n");
        sb.append("| Call ID | ").append(result.getCallId()).append(" |\n");
        sb.append("| Status | ").append(result.getStatus()).append(" |\n");

        ErrorMessage error = result.getError();
        if (error != null) {
            sb.append("| Error Type | ").append(error.getType()).append(" |\n");
            sb.append("| Message | ").append(error.getMessage()).append(" |\n");
            if (error.getSuggestion() != null) {
                sb.append("\n> **Suggestion:** ").append(error.getSuggestion());
            }
        }
        return sb.toString();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String extractResultData(ToolCallResult result) {
        ToolResult toolResult = result.getResult();
        if (toolResult == null) {
            return "";
        }

        return switch (toolResult.getType()) {
            case TEXT -> toolResult.getTextContent() != null ? toolResult.getTextContent() : "";
            case STRUCTURED -> formatStructuredData(toolResult.getStructuredData());
            case ERROR -> toolResult.getErrorMessage() != null ? toolResult.getErrorMessage() : "";
        };
    }

    private String formatStructuredData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append("  ").append(jsonString(entry.getKey())).append(": ");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append(jsonString((String) value));
            } else {
                sb.append(value);
            }
            if (++i < data.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Escape a string for JSON output.
     */
    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
