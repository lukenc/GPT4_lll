package com.wmsay.gpt4_lll.fc.events;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具。
 * 对日志中的敏感信息进行脱敏处理，防止 API Key、文件路径和用户输入泄露。
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>API Key：保留最后 4 位，其余替换为 {@code ***}</li>
 *   <li>文件路径：仅保留相对路径部分（去除绝对路径前缀）</li>
 *   <li>用户输入：截断超过 200 字符的内容</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * String sanitized = LogSanitizer.sanitizeApiKey("sk-abc123456789xyz");
 * // => "***xyz"
 *
 * String path = LogSanitizer.sanitizeFilePath("/Users/dev/project/src/Main.java");
 * // => "src/Main.java" (if projectRoot is "/Users/dev/project")
 *
 * String input = LogSanitizer.sanitizeUserInput(veryLongString);
 * // => first 200 chars + "...[truncated]"
 * }</pre>
 */
public final class LogSanitizer {

    /** 用户输入最大显示长度 */
    static final int MAX_USER_INPUT_LENGTH = 200;

    /** API Key 保留的尾部字符数 */
    static final int API_KEY_VISIBLE_SUFFIX = 4;

    /** API Key 掩码前缀 */
    static final String API_KEY_MASK = "***";

    /** 常见 API Key 前缀模式 (sk-, key-, api-, bearer tokens) */
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(sk-|key-|api-|Bearer\\s+)[A-Za-z0-9_\\-]{8,}"
    );

    private LogSanitizer() {
        // utility class
    }

    /**
     * 对 API Key 进行脱敏。保留最后 4 位字符，其余替换为 {@code ***}。
     *
     * @param apiKey API Key 原文
     * @return 脱敏后的字符串；null 或空字符串原样返回
     */
    public static String sanitizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return apiKey;
        }
        if (apiKey.length() <= API_KEY_VISIBLE_SUFFIX) {
            return API_KEY_MASK;
        }
        return API_KEY_MASK + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_SUFFIX);
    }

    /**
     * 对文件路径进行脱敏。如果路径以 projectRoot 开头，则去除前缀只保留相对路径。
     * 如果 projectRoot 为 null 或路径不以其开头，则仅保留文件名。
     *
     * @param filePath    文件路径
     * @param projectRoot 项目根目录（可为 null）
     * @return 脱敏后的路径
     */
    public static String sanitizeFilePath(String filePath, String projectRoot) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }

        // If it's already a relative path (no leading / or drive letter), return as-is
        if (!filePath.startsWith("/") && !isWindowsAbsolutePath(filePath)) {
            return filePath;
        }

        // Strip project root prefix if provided
        if (projectRoot != null && !projectRoot.isEmpty()) {
            String normalizedRoot = projectRoot.endsWith("/") ? projectRoot : projectRoot + "/";
            if (filePath.startsWith(normalizedRoot)) {
                return filePath.substring(normalizedRoot.length());
            }
        }

        // Fallback: extract filename only for absolute paths outside project
        int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSep >= 0 && lastSep < filePath.length() - 1) {
            return ".../" + filePath.substring(lastSep + 1);
        }

        return filePath;
    }

    /**
     * 对用户输入进行脱敏。截断超过 {@value #MAX_USER_INPUT_LENGTH} 字符的内容。
     *
     * @param userInput 用户输入
     * @return 脱敏后的字符串
     */
    public static String sanitizeUserInput(String userInput) {
        if (userInput == null) {
            return null;
        }
        if (userInput.length() <= MAX_USER_INPUT_LENGTH) {
            return userInput;
        }
        return userInput.substring(0, MAX_USER_INPUT_LENGTH) + "...[truncated]";
    }

    /**
     * 对日志消息中的所有敏感信息进行批量脱敏。
     * 自动检测并替换 API Key 模式。
     *
     * @param logMessage 原始日志消息
     * @return 脱敏后的日志消息
     */
    public static String sanitizeLogMessage(String logMessage) {
        if (logMessage == null || logMessage.isEmpty()) {
            return logMessage;
        }

        // Replace API key patterns
        return API_KEY_PATTERN.matcher(logMessage).replaceAll(match -> {
            String matched = match.group();
            return sanitizeApiKey(matched);
        });
    }

    private static boolean isWindowsAbsolutePath(String path) {
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && (path.charAt(2) == '\\' || path.charAt(2) == '/');
    }
}
