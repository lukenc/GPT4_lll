package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 关键词搜索工具，行为对齐 grep。
 * 支持单关键词（keyword）或多关键词并行搜索（keywords 数组）。
 * 增强功能：上下文行数、文件大小限制、目录黑名单、二进制文件过滤。
 */
public class KeywordSearchTool implements McpTool {

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
        return "Search one or more keywords in files. Supports single 'keyword' or multiple 'keywords' array for parallel search.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("keyword", Map.of("type", "string", "required", false, "description", "single keyword or regex pattern (use 'keyword' or 'keywords', at least one required)"));
        schema.put("keywords", Map.of("type", "array", "items", Map.of("type", "string"), "required", false, "description", "multiple keywords/patterns to search simultaneously, each file is scanned once"));
        schema.put("path", Map.of("type", "string", "required", false, "default", ".", "description", "search root path"));
        schema.put("ignoreCase", Map.of("type", "boolean", "required", false, "default", false));
        schema.put("regex", Map.of("type", "boolean", "required", false, "default", false));
        schema.put("filePattern", Map.of("type", "string", "required", false, "description", "glob-like suffix, e.g. .java"));
        schema.put("maxResults", Map.of("type", "integer", "required", false, "default", 200));
        schema.put("contextLines", Map.of("type", "integer", "required", false, "default", 0, "description", "number of context lines before/after match"));
        schema.put("maxFileBytes", Map.of("type", "integer", "required", false, "default", 1048576, "description", "skip files larger than this (bytes)"));
        schema.put("ignoreDirs", Map.of("type", "array", "required", false, "description", "additional directories to ignore"));
        schema.put("skipBinary", Map.of("type", "boolean", "required", false, "default", true, "description", "skip binary files"));
        return schema;
    }

    @Override
    public McpToolResult execute(McpContext context, Map<String, Object> params) {
        List<String> keywordList = resolveKeywords(params);
        if (keywordList.isEmpty()) {
            return McpToolResult.error("Missing keyword or keywords/缺少 keyword 或 keywords 参数");
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
        int contextLines = Math.max(0, McpFileToolSupport.getInt(params, "contextLines", 0));
        long maxFileBytes = Math.max(1, McpFileToolSupport.getLong(params, "maxFileBytes", DEFAULT_MAX_FILE_BYTES));
        boolean skipBinary = McpFileToolSupport.getBoolean(params, "skipBinary", true);

        // 构建忽略目录集合
        Set<String> ignoreDirs = new HashSet<>(DEFAULT_IGNORE_DIRS);
        Object ignoreDirsParam = params.get("ignoreDirs");
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
                return McpToolResult.error("Invalid regex '" + kw + "'/正则表达式错误: " + ex.getMessage());
            }
        }

        boolean multiKeyword = patterns.size() > 1;
        List<Map<String, Object>> matches = new ArrayList<>();
        int[] skippedFiles = {0};
        Set<String> skippedDirs = new HashSet<>();

        try (Stream<Path> pathStream = Files.walk(basePath)) {
            pathStream
                    .filter(path -> filterPath(path, basePath, ignoreDirs, skippedDirs))
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesFilePattern(path, filePattern))
                    .filter(path -> checkFileSize(path, maxFileBytes, skippedFiles))
                    .filter(path -> !skipBinary || !isBinaryFile(path))
                    .forEach(file -> collectMatches(file, basePath, patterns, matches, maxResults, contextLines, multiKeyword));
        } catch (IOException ex) {
            return McpToolResult.error("Search failed/搜索失败: " + ex.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", name());
        if (multiKeyword) {
            result.put("keywords", keywordList);
        } else {
            result.put("keyword", keywordList.get(0));
        }
        result.put("path", basePath.toString());
        result.put("matchCount", matches.size());
        result.put("matches", matches);
        result.put("skippedFiles", skippedFiles[0]);
        result.put("skippedDirs", new ArrayList<>(skippedDirs));
        return McpToolResult.structured(result);
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
