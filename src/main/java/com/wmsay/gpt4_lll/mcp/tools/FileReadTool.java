package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

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
public class FileReadTool implements Tool {

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read file content by line range. Returns 1-based line numbers in format 'N|text'. "
                + "Output is capped by max_lines (default 400). "
                + "Line numbers in output can be used directly with write_file's insert_after_line mode. "
                + "Use with write_file for read-modify-write workflows.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("path", Map.of("type", "string", "required", true, "description", "target file path"));
        schema.put("start_line", Map.of("type", "integer", "required", false, "default", 1,
                "description", "1-based start line number (default: 1, the first line)"));
        schema.put("end_line", Map.of("type", "integer", "required", false,
                "description", "1-based inclusive end line number (default: last line of file)"));
        schema.put("max_lines", Map.of("type", "integer", "required", false, "default", 400,
                "description", "Maximum number of lines to return (default: 400). Output is truncated if the requested range exceeds this limit."));
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        Path filePath;
        try {
            filePath = McpFileToolSupport.resolvePath(context, params, "path");
        } catch (IllegalArgumentException ex) {
            return ToolResult.error(ex.getMessage());
        }

        if (!Files.exists(filePath)) {
            return ToolResult.error("File not found: " + filePath);
        }
        if (!Files.isRegularFile(filePath)) {
            return ToolResult.error("Path is not a file: " + filePath);
        }

        int startLineInput = Math.max(1, McpFileToolSupport.getInt(params, "start_line", 1));
        int endLineInput = McpFileToolSupport.getInt(params, "end_line", Integer.MAX_VALUE);
        int maxLines = Math.max(1, McpFileToolSupport.getInt(params, "max_lines", 400));

        try {
            List<String> allLines = Files.readAllLines(filePath);
            int total = allLines.size();
            if (total == 0) {
                return ToolResult.text("");
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
            result.put("path", context.getWorkspaceRoot().relativize(filePath).toString());
            result.put("totalLines", total);
            result.put("startLine", startIndex + 1);
            result.put("endLine", endIndex + 1);
            result.put("truncated", endIndex < endIndexByInput);
            result.put("lines", lines);
            result.put("content", content.toString());
            return ToolResult.structured(result);
        } catch (IOException ex) {
            return ToolResult.error("Read file failed/读取文件失败: " + ex.getMessage());
        }
    }
}
