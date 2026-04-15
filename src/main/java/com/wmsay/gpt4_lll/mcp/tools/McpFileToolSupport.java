package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.mcp.McpContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

final class McpFileToolSupport {

    private McpFileToolSupport() {
    }

    /**
     * 从 ToolContext 解析路径（框架层接口）。
     */
    static Path resolvePath(ToolContext context, Map<String, Object> params, String key) {
        Object value = params.get(key);
        String pathValue = value == null ? "" : String.valueOf(value).trim();
        Path root = context.getWorkspaceRoot();
        if (root == null) {
            throw new IllegalArgumentException("Project root is unavailable/项目根目录不可用");
        }
        if (pathValue.isEmpty() || ".".equals(pathValue)) {
            return root;
        }
        Path candidate = Paths.get(pathValue);
        Path resolved = candidate.isAbsolute() ? candidate.normalize() : root.resolve(candidate).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path out of workspace: " + pathValue + "/路径超出项目范围");
        }
        return resolved;
    }

    /**
     * 从 McpContext 解析路径（保留向后兼容）。
     * @deprecated 使用 {@link #resolvePath(ToolContext, Map, String)} 代替
     */
    @Deprecated
    static Path resolvePath(McpContext context, Map<String, Object> params, String key) {
        Object value = params.get(key);
        String pathValue = value == null ? "" : String.valueOf(value).trim();
        Path root = context.getProjectRoot();
        if (root == null) {
            throw new IllegalArgumentException("Project root is unavailable/项目根目录不可用");
        }
        if (pathValue.isEmpty() || ".".equals(pathValue)) {
            return root;
        }
        Path candidate = Paths.get(pathValue);
        Path resolved = candidate.isAbsolute() ? candidate.normalize() : root.resolve(candidate).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path out of workspace: " + pathValue + "/路径超出项目范围");
        }
        return resolved;
    }

    static int getInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    static long getLong(Map<String, Object> params, String key, long defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    static boolean getBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static String getString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return str.isBlank() ? defaultValue : str;
    }

    /**
     * 获取字符串参数，允许空字符串。
     * 与 getString 不同，当值为空字符串时返回空字符串而非 defaultValue。
     * 当值为 null 时返回 defaultValue。
     */
    static String getStringAllowEmpty(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }
}
