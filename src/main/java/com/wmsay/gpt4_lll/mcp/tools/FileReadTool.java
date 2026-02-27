package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件读取工具，支持按行读取，行为接近 sed/head/tail 组合。
 */
public class FileReadTool implements McpTool {

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read file content by path with optional line range.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("path", Map.of("type", "string", "required", true, "description", "target file path"));
        schema.put("startLine", Map.of("type", "integer", "required", false, "default", 1));
        schema.put("endLine", Map.of("type", "integer", "required", false, "description", "inclusive end line"));
        schema.put("maxLines", Map.of("type", "integer", "required", false, "default", 400));
        return schema;
    }

    @Override
    public McpToolResult execute(McpContext context, Map<String, Object> params) {
        Path filePath;
        try {
            filePath = McpFileToolSupport.resolvePath(context, params, "path");
        } catch (IllegalArgumentException ex) {
            return McpToolResult.error(ex.getMessage());
        }

        if (!Files.exists(filePath)) {
            return McpToolResult.error("File not found: " + filePath);
        }
        if (!Files.isRegularFile(filePath)) {
            return McpToolResult.error("Path is not a file: " + filePath);
        }

        int startLineInput = Math.max(1, McpFileToolSupport.getInt(params, "startLine", 1));
        int endLineInput = McpFileToolSupport.getInt(params, "endLine", Integer.MAX_VALUE);
        int maxLines = Math.max(1, McpFileToolSupport.getInt(params, "maxLines", 400));

        try {
            List<String> allLines = Files.readAllLines(filePath);
            int total = allLines.size();
            if (total == 0) {
                return McpToolResult.text("");
            }

            int startIndex = Math.min(startLineInput - 1, total - 1);
            int endIndexByInput = endLineInput == Integer.MAX_VALUE
                    ? total - 1
                    : Math.max(startIndex, Math.min(endLineInput - 1, total - 1));
            int endIndex = Math.min(endIndexByInput, startIndex + maxLines - 1);

            List<Map<String, Object>> lines = new ArrayList<>();
            StringBuilder content = new StringBuilder();
            for (int i = startIndex; i <= endIndex; i++) {
                String text = allLines.get(i);
                int lineNo = i + 1;
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                content.append(lineNo).append("|").append(text);

                Map<String, Object> line = new LinkedHashMap<>();
                line.put("line", lineNo);
                line.put("text", text);
                lines.add(line);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tool", name());
            result.put("path", filePath.toString());
            result.put("totalLines", total);
            result.put("startLine", startIndex + 1);
            result.put("endLine", endIndex + 1);
            result.put("truncated", endIndex < endIndexByInput);
            result.put("lines", lines);
            result.put("content", content.toString());
            return McpToolResult.structured(result);
        } catch (IOException ex) {
            return McpToolResult.error("Read file failed/读取文件失败: " + ex.getMessage());
        }
    }
}
