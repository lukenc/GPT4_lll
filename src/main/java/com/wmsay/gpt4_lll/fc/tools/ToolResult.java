package com.wmsay.gpt4_lll.fc.tools;

import java.util.Collections;
import java.util.Map;

/**
 * 工具执行结果。
 * <p>
 * 从 {@code McpToolResult} 直接迁移，提供三种结果类型的静态工厂方法：
 * 文本结果、结构化数据结果和错误结果。构建后为不可变对象。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 文本结果
 * ToolResult result = ToolResult.text("文件内容...");
 *
 * // 结构化结果
 * ToolResult result = ToolResult.structured(Map.of("key", "value"));
 *
 * // 错误结果
 * ToolResult result = ToolResult.error("文件不存在");
 * }</pre>
 *
 * @see Tool
 * @see ToolContext
 */
public class ToolResult {

    /**
     * 结果类型枚举。
     */
    public enum ResultType {
        /** 纯文本结果 */
        TEXT,
        /** 结构化数据结果 */
        STRUCTURED,
        /** 错误结果 */
        ERROR
    }

    private final ResultType type;
    private final String textContent;
    private final Map<String, Object> structuredData;
    private final String errorMessage;

    private ToolResult(ResultType type, String textContent,
                       Map<String, Object> structuredData, String errorMessage) {
        this.type = type;
        this.textContent = textContent;
        this.structuredData = structuredData == null ? null : Collections.unmodifiableMap(structuredData);
        this.errorMessage = errorMessage;
    }

    /**
     * 创建文本类型的工具结果。
     *
     * @param content 文本内容
     * @return 文本类型的 ToolResult 实例
     */
    public static ToolResult text(String content) {
        return new ToolResult(ResultType.TEXT, content, null, null);
    }

    /**
     * 创建结构化数据类型的工具结果。
     *
     * @param data 结构化数据，键值对形式
     * @return 结构化类型的 ToolResult 实例
     */
    public static ToolResult structured(Map<String, Object> data) {
        return new ToolResult(ResultType.STRUCTURED, null, data, null);
    }

    /**
     * 创建错误类型的工具结果。
     *
     * @param message 错误消息
     * @return 错误类型的 ToolResult 实例
     */
    public static ToolResult error(String message) {
        return new ToolResult(ResultType.ERROR, null, null, message);
    }

    /**
     * 获取结果类型。
     *
     * @return 结果类型枚举值
     */
    public ResultType getType() {
        return type;
    }

    /**
     * 获取文本内容。仅 TEXT 类型有值。
     *
     * @return 文本内容，非 TEXT 类型返回 null
     */
    public String getTextContent() {
        return textContent;
    }

    /**
     * 获取结构化数据。仅 STRUCTURED 类型有值。
     * 返回不可变 Map。
     *
     * @return 结构化数据，非 STRUCTURED 类型返回 null
     */
    public Map<String, Object> getStructuredData() {
        return structuredData;
    }

    /**
     * 获取错误消息。仅 ERROR 类型有值。
     *
     * @return 错误消息，非 ERROR 类型返回 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取用于展示的文本内容。
     * <ul>
     *   <li>TEXT 类型返回 textContent</li>
     *   <li>STRUCTURED 类型将 structuredData 序列化为 JSON 字符串</li>
     *   <li>ERROR 类型返回 errorMessage</li>
     * </ul>
     *
     * @return 展示文本，若无内容则返回 null
     */
    public String getDisplayText() {
        if (textContent != null) {
            return textContent;
        }
        if (structuredData != null && !structuredData.isEmpty()) {
            try {
                return com.alibaba.fastjson.JSON.toJSONString(structuredData, true);
            } catch (Exception e) {
                return structuredData.toString();
            }
        }
        if (errorMessage != null) {
            return errorMessage;
        }
        return null;
    }
}
