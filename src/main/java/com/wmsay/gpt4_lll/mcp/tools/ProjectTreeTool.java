package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 项目目录树读取工具，行为接近 tree 命令。
 * 增强功能：默认忽略目录、自定义忽略目录。
 */
public class ProjectTreeTool implements McpTool {

    private static final Set<String> DEFAULT_IGNORE_DIRS = Set.of(
            ".git", ".idea", "node_modules", "target", "build", "out", ".gradle",
            ".vscode", ".intellijPlatform", "bin", ".kiro"
    );

    @Override
    public String name() {
        return "tree";
    }

    @Override
    public String description() {
        return "List project directory tree with depth and file visibility options.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("path", Map.of("type", "string", "required", false, "default", ".", "description", "root path"));
        schema.put("maxDepth", Map.of("type", "integer", "required", false, "default", 3));
        schema.put("showFiles", Map.of("type", "boolean", "required", false, "default", true));
        schema.put("showHidden", Map.of("type", "boolean", "required", false, "default", false));
        schema.put("maxEntries", Map.of("type", "integer", "required", false, "default", 1000));
        schema.put("ignoreDirs", Map.of("type", "array", "required", false, "description", "additional directories to ignore (merged with defaults)"));
        schema.put("useDefaultIgnores", Map.of("type", "boolean", "required", false, "default", true, "description", "whether to ignore .git, .idea, node_modules, etc."));
        return schema;
    }

    @Override
    public McpToolResult execute(McpContext context, Map<String, Object> params) {
        Path root;
        try {
            root = McpFileToolSupport.resolvePath(context, params, "path");
        } catch (IllegalArgumentException ex) {
            return McpToolResult.error(ex.getMessage());
        }
        if (!Files.exists(root)) {
            return McpToolResult.error("Path not found: " + root);
        }

        int maxDepth = Math.max(0, McpFileToolSupport.getInt(params, "maxDepth", 3));
        boolean showFiles = McpFileToolSupport.getBoolean(params, "showFiles", true);
        boolean showHidden = McpFileToolSupport.getBoolean(params, "showHidden", false);
        int maxEntries = Math.max(1, McpFileToolSupport.getInt(params, "maxEntries", 1000));
        boolean useDefaultIgnores = McpFileToolSupport.getBoolean(params, "useDefaultIgnores", true);

        // 构建忽略目录集合
        Set<String> ignoreDirs = new HashSet<>();
        if (useDefaultIgnores) {
            ignoreDirs.addAll(DEFAULT_IGNORE_DIRS);
        }
        Object ignoreDirsParam = params.get("ignoreDirs");
        if (ignoreDirsParam instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    ignoreDirs.add(String.valueOf(item));
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(root.getFileName() == null ? root.toString() : root.getFileName().toString());

        try {
            walk(root, root, "", maxDepth, showFiles, showHidden, maxEntries, lines, ignoreDirs);
        } catch (IOException ex) {
            return McpToolResult.error("Tree read failed/读取目录树失败: " + ex.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", name());
        result.put("path", root.toString());
        result.put("maxDepth", maxDepth);
        result.put("showFiles", showFiles);
        result.put("showHidden", showHidden);
        result.put("entryCount", Math.max(0, lines.size() - 1));
        result.put("truncated", lines.size() > maxEntries + 1);
        result.put("ignoredDirs", new ArrayList<>(ignoreDirs));
        result.put("tree", String.join("\n", lines));
        return McpToolResult.structured(result);
    }

    private static void walk(Path root, Path current, String prefix, int maxDepth, boolean showFiles,
                             boolean showHidden, int maxEntries, List<String> lines, Set<String> ignoreDirs) throws IOException {
        int depth = root.relativize(current).getNameCount();
        if (depth >= maxDepth) {
            return;
        }

        List<Path> children;
        try (Stream<Path> stream = Files.list(current)) {
            children = stream
                    .filter(path -> include(path, showFiles, showHidden, ignoreDirs))
                    .sorted(pathComparator())
                    .collect(Collectors.toList());
        }

        for (int i = 0; i < children.size(); i++) {
            if (lines.size() > maxEntries) {
                lines.add("... (truncated)");
                return;
            }
            Path child = children.get(i);
            boolean last = i == children.size() - 1;
            String branch = last ? "`-- " : "|-- ";
            String nodeName = child.getFileName().toString() + (Files.isDirectory(child) ? "/" : "");
            lines.add(prefix + branch + nodeName);
            if (Files.isDirectory(child)) {
                String childPrefix = prefix + (last ? "    " : "|   ");
                walk(root, child, childPrefix, maxDepth, showFiles, showHidden, maxEntries, lines, ignoreDirs);
            }
        }
    }

    private static boolean include(Path path, boolean showFiles, boolean showHidden, Set<String> ignoreDirs) {
        String name = path.getFileName().toString();
        
        // 检查是否在忽略列表中
        if (ignoreDirs.contains(name)) {
            return false;
        }
        
        // 检查是否显示隐藏文件
        if (!showHidden && name.startsWith(".")) {
            return false;
        }
        
        // 检查是否显示文件
        return showFiles || Files.isDirectory(path);
    }

    private static Comparator<Path> pathComparator() {
        return (a, b) -> {
            boolean ad = Files.isDirectory(a);
            boolean bd = Files.isDirectory(b);
            if (ad != bd) {
                return ad ? -1 : 1;
            }
            return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
        };
    }

}
