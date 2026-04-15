package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 关键词搜索工具，行为对齐 grep。
 * 支持单关键词（keyword）或多关键词并行搜索（keywords 数组）。
 * 增强功能：上下文行数、文件大小限制、目录黑名单、二进制文件过滤。
 */
public class KeywordSearchTool implements Tool {

    private static final Set<String> DEFAULT_IGNORE_DIRS = Set.of(
            ".git", ".idea", "node_modules", "target", "build", "out", ".gradle",
            ".vscode", ".intellijPlatform", "bin", ".kiro"
    );

    private static final long DEFAULT_MAX_FILE_BYTES = 1_048_576; // 1 MB

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search one or more keywords in files. Returns 1-based line numbers that can be used directly "
                + "with write_file's insert_after_line mode. "
                + "Supports single 'keyword' or multiple 'keywords' array for parallel search.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("keyword", Map.of("type", "string", "required", false, "description", "single keyword or regex pattern (use 'keyword' or 'keywords', at least one required)"));
        schema.put("keywords", Map.of("type", "array", "items", Map.of("type", "string"), "required", false, "description", "multiple keywords/patterns to search simultaneously, each file is scanned once"));
        schema.put("path", Map.of("type", "string", "required", false, "default", ".", "description", "search root path"));
        schema.put("ignore_case", Map.of("type", "boolean", "required", false, "default", false,
            "description", "Case-insensitive matching (default: false)"));
        schema.put("regex", Map.of("type", "boolean", "required", false, "default", false));
        schema.put("file_pattern", Map.of("type", "string", "required", false,
            "description", "Glob-like suffix filter, e.g. .java"));
        schema.put("max_results", Map.of("type", "integer", "required", false, "default", 200,
            "description", "Maximum number of matches to return (default: 200)"));
        schema.put("context_lines", Map.of("type", "integer", "required", false, "default", 0,
            "description", "Number of context lines before/after each match (default: 0)"));
        schema.put("max_file_bytes", Map.of("type", "integer", "required", false, "default", 1048576,
            "description", "Skip files larger than this size in bytes (default: 1 MB)"));
        schema.put("ignore_dirs", Map.of("type", "array", "required", false,
            "items", Map.of("type", "string"),
            "description", "Additional directory names to ignore during search"));
        schema.put("skip_binary", Map.of("type", "boolean", "required", false, "default", true,
            "description", "Skip binary files (default: true)"));
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        List<String> keywordList = resolveKeywords(params);
        if (keywordList.isEmpty()) {
            return ToolResult.error("Missing keyword or keywords/缺少 keyword 或 keywords 参数");
        }

        Path basePath;
        try {
            basePath = McpFileToolSupport.resolvePath(context, params, "path");
        } catch (IllegalArgumentException ex) {
            return ToolResult.error(ex.getMessage());
        }

        if (!Files.exists(basePath)) {
            return ToolResult.error("Path not found: " + basePath);
        }

        boolean ignoreCase = McpFileToolSupport.getBoolean(params, "ignore_case", false);
        boolean regex = McpFileToolSupport.getBoolean(params, "regex", false);
        String filePattern = McpFileToolSupport.getString(params, "file_pattern", "");
        int maxResults = Math.max(1, McpFileToolSupport.getInt(params, "max_results", 200));
        int contextLines = Math.max(0, McpFileToolSupport.getInt(params, "context_lines", 0));
        long maxFileBytes = Math.max(1, McpFileToolSupport.getLong(params, "max_file_bytes", DEFAULT_MAX_FILE_BYTES));
        boolean skipBinary = McpFileToolSupport.getBoolean(params, "skip_binary", true);

        // 构建忽略目录集合
        Set<String> ignoreDirs = new HashSet<>(DEFAULT_IGNORE_DIRS);
        Object ignoreDirsParam = params.get("ignore_dirs");
        if (ignoreDirsParam instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    ignoreDirs.add(String.valueOf(item));
                }
            }
        }

        // 为每个关键词编译 Pattern，保持与原始关键词的映射
        List<PatternEntry> patterns = new ArrayList<>(keywordList.size());
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        for (String kw : keywordList) {
            try {
                Pattern p = regex ? Pattern.compile(kw, flags) : Pattern.compile(Pattern.quote(kw), flags);
                patterns.add(new PatternEntry(kw, p));
            } catch (PatternSyntaxException ex) {
                return ToolResult.error("Invalid regex '" + kw + "'/正则表达式错误: " + ex.getMessage());
            }
        }

        boolean multiKeyword = patterns.size() > 1;
        List<Map<String, Object>> matches = new ArrayList<>();
        int[] skippedFiles = {0};
        Set<String> skippedDirs = new HashSet<>();

        try (Stream<Path> pathStream = Files.walk(basePath)) {
            Path workspaceRoot = context.getWorkspaceRoot();
            pathStream
                    .filter(path -> filterPath(path, basePath, ignoreDirs, skippedDirs))
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesFilePattern(path, filePattern))
                    .filter(path -> checkFileSize(path, maxFileBytes, skippedFiles))
                    .filter(path -> !skipBinary || !isBinaryFile(path))
                    .forEach(file -> collectMatches(file, workspaceRoot, patterns, matches, maxResults, contextLines, multiKeyword));
        } catch (IOException ex) {
            return ToolResult.error("Search failed/搜索失败: " + ex.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", name());
        if (multiKeyword) {
            result.put("keywords", keywordList);
        } else {
            result.put("keyword", keywordList.get(0));
        }
        result.put("workspaceRoot", context.getWorkspaceRoot().toString());
        result.put("path", context.getWorkspaceRoot().relativize(basePath).toString());
        result.put("matchCount", matches.size());
        result.put("matches", matches);
        result.put("skippedFiles", skippedFiles[0]);
        result.put("skippedDirs", new ArrayList<>(skippedDirs));
        return ToolResult.structured(result);
    }

    /**
     * 从参数中解析关键词列表：优先使用 keywords 数组，其次使用 keyword 单值。
     */
    private static List<String> resolveKeywords(Map<String, Object> params) {
        List<String> result = new ArrayList<>();
        Object keywordsParam = params.get("keywords");
        if (keywordsParam instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item != null) {
                    String kw = String.valueOf(item).trim();
                    if (!kw.isEmpty()) {
                        result.add(kw);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            String single = McpFileToolSupport.getString(params, "keyword", "").trim();
            if (!single.isEmpty()) {
                result.add(single);
            }
        }
        return result;
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

    private static boolean filterPath(Path path, Path basePath, Set<String> ignoreDirs, Set<String> skippedDirs) {
        if (Files.isDirectory(path) && !path.equals(basePath)) {
            String dirName = path.getFileName().toString();
            if (ignoreDirs.contains(dirName)) {
                skippedDirs.add(dirName);
                return false;
            }
        }
        return true;
    }

    private static boolean checkFileSize(Path path, long maxFileBytes, int[] skippedFiles) {
        try {
            long size = Files.size(path);
            if (size > maxFileBytes) {
                skippedFiles[0]++;
                return false;
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isBinaryFile(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType != null && !contentType.startsWith("text/")) {
                return true;
            }
            // 额外检查：读取前1024字节，检查是否包含null字节
            byte[] buffer = new byte[1024];
            int bytesRead = Files.newInputStream(path).read(buffer);
            for (int i = 0; i < bytesRead; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private static void collectMatches(Path file, Path basePath, List<PatternEntry> patterns,
                                       List<Map<String, Object>> matches, int maxResults,
                                       int contextLines, boolean multiKeyword) {
        if (matches.size() >= maxResults) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return;
        }

        // 对每一行，检查所有 pattern，记录匹配到的关键词
        Map<Integer, List<String>> lineToKeywords = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            List<String> hitKeywords = null;
            for (PatternEntry pe : patterns) {
                if (pe.pattern.matcher(line).find()) {
                    if (hitKeywords == null) {
                        hitKeywords = new ArrayList<>(patterns.size());
                    }
                    hitKeywords.add(pe.keyword);
                }
            }
            if (hitKeywords != null) {
                lineToKeywords.put(i, hitKeywords);
            }
        }

        String relPath = basePath.relativize(file).toString();
        for (Map.Entry<Integer, List<String>> entry : lineToKeywords.entrySet()) {
            if (matches.size() >= maxResults) {
                break;
            }
            int matchedLine = entry.getKey();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("file", relPath);
            item.put("line", matchedLine + 1);
            item.put("content", lines.get(matchedLine));
            if (multiKeyword) {
                item.put("matchedKeywords", entry.getValue());
            }

            if (contextLines > 0) {
                List<Map<String, Object>> context = new ArrayList<>();
                int startLine = Math.max(0, matchedLine - contextLines);
                int endLine = Math.min(lines.size() - 1, matchedLine + contextLines);
                for (int i = startLine; i <= endLine; i++) {
                    Map<String, Object> contextLine = new LinkedHashMap<>();
                    contextLine.put("line", i + 1);
                    contextLine.put("content", lines.get(i));
                    contextLine.put("matched", i == matchedLine);
                    context.add(contextLine);
                }
                item.put("context", context);
            }

            matches.add(item);
        }
    }

    private record PatternEntry(String keyword, Pattern pattern) {}
}
