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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 关键词搜索工具，行为对齐 grep。
 */
public class KeywordSearchTool implements McpTool {

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search keyword in files with optional scope, case, regex options.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("keyword", Map.of("type", "string", "required", true, "description", "keyword or regex pattern"));
        schema.put("path", Map.of("type", "string", "required", false, "default", ".", "description", "search root path"));
        schema.put("ignoreCase", Map.of("type", "boolean", "required", false, "default", false));
        schema.put("regex", Map.of("type", "boolean", "required", false, "default", false));
        schema.put("filePattern", Map.of("type", "string", "required", false, "description", "glob-like suffix, e.g. .java"));
        schema.put("maxResults", Map.of("type", "integer", "required", false, "default", 200));
        return schema;
    }

    @Override
    public McpToolResult execute(McpContext context, Map<String, Object> params) {
        String keyword = McpFileToolSupport.getString(params, "keyword", "").trim();
        if (keyword.isEmpty()) {
            return McpToolResult.error("Missing keyword/缺少 keyword");
        }

        Path basePath;
        try {
            basePath = McpFileToolSupport.resolvePath(context, params, "path");
        } catch (IllegalArgumentException ex) {
            return McpToolResult.error(ex.getMessage());
        }

        if (!Files.exists(basePath)) {
            return McpToolResult.error("Path not found: " + basePath);
        }

        boolean ignoreCase = McpFileToolSupport.getBoolean(params, "ignoreCase", false);
        boolean regex = McpFileToolSupport.getBoolean(params, "regex", false);
        String filePattern = McpFileToolSupport.getString(params, "filePattern", "");
        int maxResults = Math.max(1, McpFileToolSupport.getInt(params, "maxResults", 200));

        Pattern pattern;
        try {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            pattern = regex ? Pattern.compile(keyword, flags) : Pattern.compile(Pattern.quote(keyword), flags);
        } catch (PatternSyntaxException ex) {
            return McpToolResult.error("Invalid regex/正则表达式错误: " + ex.getMessage());
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(basePath)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesFilePattern(path, filePattern))
                    .forEach(file -> collectMatches(file, basePath, pattern, matches, maxResults));
        } catch (IOException ex) {
            return McpToolResult.error("Search failed/搜索失败: " + ex.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", name());
        result.put("keyword", keyword);
        result.put("path", basePath.toString());
        result.put("matchCount", matches.size());
        result.put("matches", matches);
        return McpToolResult.structured(result);
    }

    private static boolean matchesFilePattern(Path path, String filePattern) {
        if (filePattern == null || filePattern.isBlank()) {
            return true;
        }
        String name = path.getFileName().toString();
        if (filePattern.startsWith(".")) {
            return name.endsWith(filePattern);
        }
        return name.contains(filePattern);
    }

    private static void collectMatches(Path file, Path basePath, Pattern pattern,
                                       List<Map<String, Object>> matches, int maxResults) {
        if (matches.size() >= maxResults) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            if (matches.size() >= maxResults) {
                break;
            }
            String line = lines.get(i);
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("file", basePath.relativize(file).toString());
            item.put("line", i + 1);
            item.put("content", line);
            matches.add(item);
        }
    }
}
