package com.wmsay.gpt4_lll.fc.runtime;

import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.planning.ExecutionHook;
import com.wmsay.gpt4_lll.fc.state.FileChangeTracker;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 文件变更追踪钩子 — 在每轮工具执行完成后拦截 write_file 操作，
 * 将文件变更记录到 FileChangeTracker。
 * <p>
 * 位于 fc.runtime 包，纯 Java 实现，不依赖任何 com.intellij.* API。
 * <p>
 * 实现策略：
 * <ul>
 *   <li>在 afterRound() 中遍历本轮工具调用结果</li>
 *   <li>对 write_file 且执行成功的结果，从 McpToolResult 的 structuredData 中提取文件路径</li>
 *   <li>读取文件当前内容（写入后的新内容）</li>
 *   <li>使用 originalContents 缓存追踪首次遇到的文件的原始内容</li>
 *   <li>新建文件场景（首次写入）时原始内容记录为空字符串</li>
 * </ul>
 */
public class AgentFileChangeHook implements ExecutionHook {

    private static final Logger LOG = Logger.getLogger(AgentFileChangeHook.class.getName());
    private static final String WRITE_FILE_TOOL = "write_file";

    private final FileChangeTracker tracker;
    private final String projectRoot;

    /**
     * 缓存文件的原始内容（首次遇到时捕获）。
     * key = 绝对路径字符串, value = 原始内容。
     */
    private final ConcurrentHashMap<String, String> originalContents = new ConcurrentHashMap<>();

    public AgentFileChangeHook(FileChangeTracker tracker, String projectRoot) {
        this.tracker = tracker;
        this.projectRoot = projectRoot;
    }

    @Override
    public HookResult afterRound(int round, List<ToolCallResult> results) {
        if (results == null) {
            return HookResult.continueExecution();
        }
        for (ToolCallResult result : results) {
            if (WRITE_FILE_TOOL.equals(result.getToolName()) && result.isSuccess()) {
                processWriteFileResult(result);
            }
        }
        return HookResult.continueExecution();
    }

    private void processWriteFileResult(ToolCallResult result) {
        String filePath = extractFilePath(result);
        if (filePath == null) {
            LOG.warning("Could not extract file path from write_file result");
            return;
        }

        Path resolved = resolvePath(filePath);
        String absolutePath = resolved.toString();

        try {
            // 从 write_file 结果中提取原始内容（写入前由 FileWriteTool 捕获）
            String originalFromResult = extractOriginalContent(result);

            // 首次遇到该文件时，使用 write_file 提供的原始内容
            if (!originalContents.containsKey(absolutePath)) {
                originalContents.put(absolutePath,
                        originalFromResult != null ? originalFromResult : "");
            }

            // 读取文件当前内容（写入后的新内容）
            String newContent = readFileContent(resolved);

            // 记录变更到 FileChangeTracker
            String originalContent = originalContents.get(absolutePath);
            tracker.trackChange(filePath, originalContent, newContent);

            // 更新缓存：下次写入同一文件时，当前内容就是"原始内容"
            originalContents.put(absolutePath, newContent);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to track file change for: " + filePath, e);
        }
    }

    /**
     * 从 ToolCallResult 的 structured data 中提取 original_content 字段。
     * FileWriteTool 在写入前捕获原始内容并放入结果中。
     */
    private String extractOriginalContent(ToolCallResult result) {
        ToolResult toolResult = result.getResult();
        if (toolResult == null) return null;
        Map<String, Object> data = toolResult.getStructuredData();
        if (data != null && data.containsKey("original_content")) {
            Object obj = data.get("original_content");
            return obj != null ? obj.toString() : null;
        }
        return null;
    }

    /**
     * 从 ToolCallResult 的 ToolResult 中提取文件路径。
     * write_file 工具返回 structured 结果，包含 "path" 字段。
     */
    String extractFilePath(ToolCallResult result) {
        ToolResult toolResult = result.getResult();
        if (toolResult == null) {
            return null;
        }

        // write_file 返回 structured data，包含 "path" 键
        Map<String, Object> data = toolResult.getStructuredData();
        if (data != null && data.containsKey("path")) {
            Object pathObj = data.get("path");
            if (pathObj != null) {
                return pathObj.toString();
            }
        }

        // 回退：尝试从 display text 中解析路径
        String displayText = toolResult.getDisplayText();
        if (displayText != null) {
            return parsePathFromDisplayText(displayText);
        }

        return null;
    }

    /**
     * 从显示文本中尝试解析文件路径（回退策略）。
     */
    private String parsePathFromDisplayText(String text) {
        // 尝试匹配常见的路径模式，如 "Written to: /path/to/file"
        if (text.contains("path")) {
            // 简单的 JSON-like 解析
            int idx = text.indexOf("\"path\"");
            if (idx >= 0) {
                int colonIdx = text.indexOf(':', idx);
                if (colonIdx >= 0) {
                    int startQuote = text.indexOf('"', colonIdx + 1);
                    if (startQuote >= 0) {
                        int endQuote = text.indexOf('"', startQuote + 1);
                        if (endQuote > startQuote) {
                            return text.substring(startQuote + 1, endQuote);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 解析文件路径，支持绝对路径和相对路径（相对于 projectRoot）。
     */
    Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(projectRoot).resolve(path);
    }

    /**
     * 读取文件内容。文件不存在时返回空字符串。
     */
    private String readFileContent(Path path) {
        try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to read file: " + path, e);
        }
        return "";
    }
}
