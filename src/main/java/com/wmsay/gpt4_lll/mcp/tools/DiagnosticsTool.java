package com.wmsay.gpt4_lll.mcp.tools;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IDE 诊断工具：获取指定文件的代码诊断信息（错误、警告等）。
 * <p>
 * 语言无关，依托 IntelliJ Platform 的 DaemonCodeAnalyzerEx API，
 * 适用于所有已安装语言插件支持的文件类型。
 * </p>
 */
public class DiagnosticsTool implements Tool {

    @Override
    public String name() {
        return "diagnostics";
    }

    @Override
    public String category() {
        return "ide";
    }

    @Override
    public String description() {
        return "Get compile, lint, type, and other semantic diagnostics for code files "
                + "from the IDE's built-in code analysis engine. Language-agnostic: works with "
                + "any language supported by installed IDE plugins.\n\n"
                + "WHEN TO USE:\n"
                + "- After editing a file, to verify the change did not introduce new errors\n"
                + "- To check whether a file has syntax or semantic errors before proceeding\n"
                + "- During a fix workflow, to confirm the problem is resolved\n"
                + "- To discover all issues in a set of files at once\n\n"
                + "WHEN NOT TO USE:\n"
                + "- Searching for code content → use grep or keyword_search\n"
                + "- Reading file content → use read_file\n"
                + "- Listing project files → use tree\n\n"
                + "RETURNS:\n"
                + "- files[].file: path relative to workspace root\n"
                + "- files[].status: \"ok\" | \"not_opened\" | \"analysis_timeout\" | \"error\"\n"
                + "- files[].diagnostics[]: array of diagnostic entries, each with:\n"
                + "    file, line, column, endLine, endColumn, severity, message, source\n"
                + "- fileCount: number of requested files\n"
                + "- totalDiagnostics: total count before truncation\n"
                + "- truncated: true if results were limited by maxResults\n\n"
                + "Parameters:\n"
                + "- paths (required): array of file paths relative to workspace root\n"
                + "- severity (optional, default \"warning\"): minimum severity filter — "
                + "\"error\" (errors only), \"warning\" (errors + warnings), \"info\" (all)\n"
                + "- maxResults (optional, default 200): maximum number of diagnostic entries to return\n"
                + "- openAndAnalyze (optional, default false): if true, opens files not yet in the editor "
                + "and waits for analysis (up to 10s timeout)";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("paths", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "required", true,
                "description", "file paths relative to workspace root to get diagnostics for"
        ));
        schema.put("severity", Map.of(
                "type", "string",
                "required", false,
                "default", "warning",
                "description", "minimum severity level filter: \"error\", \"warning\", \"info\""
        ));
        schema.put("maxResults", Map.of(
                "type", "integer",
                "required", false,
                "default", 200,
                "description", "maximum number of diagnostic entries to return"
        ));
        schema.put("openAndAnalyze", Map.of(
                "type", "boolean",
                "required", false,
                "default", false,
                "description", "if true, opens files not in editor and waits for analysis (up to 10s)"
        ));
        return schema;
    }

    private static final Set<String> VALID_SEVERITIES = Set.of("error", "warning", "info");

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        // 1. 获取 Project
        Project project = context.get("project", Project.class);
        if (project == null) {
            return ToolResult.error("IDE project context is unavailable.");
        }

        // 2. 获取 workspace root
        Path workspaceRoot = context.getWorkspaceRoot();
        if (workspaceRoot == null) {
            return ToolResult.error("Workspace root is unavailable.");
        }

        // 3. 解析 paths 参数
        List<String> paths = resolvePaths(params);
        if (paths.isEmpty()) {
            return ToolResult.error("Missing required parameter: paths/缺少必需参数 paths");
        }

        // 4. 解析可选参数
        String severity = McpFileToolSupport.getString(params, "severity", "warning");
        int maxResults = McpFileToolSupport.getInt(params, "maxResults", 200);
        boolean openAndAnalyze = McpFileToolSupport.getBoolean(params, "openAndAnalyze", false);

        // 5. 校验 severity 合法性
        if (!VALID_SEVERITIES.contains(severity)) {
            return ToolResult.error("Invalid severity: '" + severity
                    + "'. Valid options: error, warning, info/severity 参数无效，合法选项: error, warning, info");
        }

        // 6. 校验 maxResults > 0
        if (maxResults <= 0) {
            return ToolResult.error("Invalid maxResults: must be positive/maxResults 必须为正整数");
        }

        // 7. 核心流程：收集诊断、截断、组装结果
        int minSeverityLevel = severityLevel(severity);

        // 7.1 遍历每个路径，收集诊断
        List<FileResult> fileResults = new ArrayList<>();
        for (String path : paths) {
            fileResults.add(collectDiagnostics(project, path, workspaceRoot, minSeverityLevel, openAndAnalyze));
        }

        // 7.2 收集所有 ok / analysis_timeout 文件的诊断到一个扁平列表
        List<Map<String, Object>> allDiagnostics = new ArrayList<>();
        for (FileResult fr : fileResults) {
            if ("ok".equals(fr.status()) || "analysis_timeout".equals(fr.status())) {
                allDiagnostics.addAll(fr.diagnostics());
            }
        }

        // 7.3 截断
        TruncationResult truncationResult = truncateDiagnostics(allDiagnostics, maxResults);

        // 7.4 将截断后的诊断重新分配回各文件
        List<String> relativePaths = new ArrayList<>();
        for (FileResult fr : fileResults) {
            relativePaths.add(fr.file());
        }
        List<List<Map<String, Object>>> redistributed = redistributeToFiles(truncationResult.diagnostics(), relativePaths);

        // 7.5 构建顶层结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "diagnostics");
        result.put("fileCount", paths.size());
        result.put("totalDiagnostics", truncationResult.totalDiagnostics());
        result.put("truncated", truncationResult.truncated());

        // 7.6 构建 files 数组
        List<Map<String, Object>> filesArray = new ArrayList<>();
        for (int i = 0; i < fileResults.size(); i++) {
            FileResult fr = fileResults.get(i);
            Map<String, Object> fileEntry = new LinkedHashMap<>();
            fileEntry.put("file", fr.file());
            fileEntry.put("status", fr.status());
            if (fr.message() != null) {
                fileEntry.put("message", fr.message());
            }
            // For ok/analysis_timeout files, use redistributed diagnostics; for error/not_opened, empty list
            List<Map<String, Object>> fileDiags;
            if ("ok".equals(fr.status()) || "analysis_timeout".equals(fr.status())) {
                fileDiags = redistributed.get(i);
            } else {
                fileDiags = List.of();
            }
            fileEntry.put("diagnosticCount", fileDiags.size());
            fileEntry.put("diagnostics", fileDiags);
            filesArray.add(fileEntry);
        }
        result.put("files", filesArray);

        return ToolResult.structured(result);
    }

    /**
     * 将 HighlightSeverity 映射为字符串。
     * ERROR → "error", WARNING → "warning", WEAK_WARNING → "info", 其余 → "hint"
     * 使用 compareTo 进行级别比较（与 LinterFixAction 一致）。
     */
    String mapSeverity(HighlightSeverity severity) {
        if (severity.compareTo(HighlightSeverity.ERROR) >= 0) {
            return "error";
        } else if (severity.compareTo(HighlightSeverity.WARNING) >= 0) {
            return "warning";
        } else if (severity.compareTo(HighlightSeverity.WEAK_WARNING) >= 0) {
            return "info";
        } else {
            return "hint";
        }
    }

    /**
     * 将 severity 字符串映射为数值优先级（用于过滤和排序）。
     * error=3, warning=2, info=1, hint=0
     */
    int severityLevel(String severity) {
        return switch (severity) {
            case "error" -> 3;
            case "warning" -> 2;
            case "info" -> 1;
            default -> 0;
        };
    }

    /**
     * 将字符偏移量转换为 1-based 的行号和列号。
     * @param document 文件对应的 Document
     * @param offset 字符偏移量
     * @return int[]{line, column}，均从 1 开始
     */
    int[] offsetToLineColumn(Document document, int offset) {
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int line = lineNumber + 1;
        int column = offset - lineStartOffset + 1;
        return new int[]{line, column};
    }

    /**
     * 过滤诊断条目，仅保留 severityLevel >= minLevel 的条目。
     */
    static List<Map<String, Object>> filterBySeverity(List<Map<String, Object>> diagnostics, int minLevel) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entry : diagnostics) {
            Object sev = entry.get("severity");
            if (sev instanceof String s) {
                int level = switch (s) {
                    case "error" -> 3;
                    case "warning" -> 2;
                    case "info" -> 1;
                    default -> 0;
                };
                if (level >= minLevel) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * 打开文件并等待 DaemonCodeAnalyzer 完成分析。
     * <p>
     * 在 EDT 上通过 FileEditorManager.openFile() 打开文件（不聚焦），
     * 然后轮询等待分析完成，超时上限为 timeoutMs（最大 10000ms）。
     * </p>
     * <p>
     * 等待策略：打开文件后以 100ms 间隔轮询，检查 DaemonCodeAnalyzerEx 的
     * FileStatusMap 是否已将该文件标记为分析完成（allDirtyScopesAreNull）。
     * </p>
     *
     * @param project   IntelliJ Project 实例
     * @param vf        要打开的 VirtualFile
     * @param timeoutMs 超时毫秒数，上限 10000
     * @return true 如果分析完成，false 如果超时
     */
    boolean openFileAndWaitForAnalysis(Project project, VirtualFile vf, long timeoutMs) {
        long effectiveTimeout = Math.min(timeoutMs, 10000);

        // 在 EDT 上打开文件（不聚焦）
        ApplicationManager.getApplication().invokeAndWait(() ->
                FileEditorManager.getInstance(project).openFile(vf, false)
        );

        // 轮询等待分析完成
        long deadline = System.currentTimeMillis() + effectiveTimeout;
        while (System.currentTimeMillis() < deadline) {
            boolean analysisReady = ReadAction.compute(() -> {
                PsiFile psi = PsiDocumentManager.getInstance(project)
                        .getPsiFile(FileDocumentManager.getInstance().getDocument(vf));
                if (psi == null) return false;
                // 使用公开 API 检查错误分析是否完成（兼容 222–253）
                return DaemonCodeAnalyzerEx.getInstanceEx(project)
                        .isErrorAnalyzingFinished(psi);
            });
            if (analysisReady) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 单个文件的诊断收集结果。
     *
     * @param file        相对于 workspace root 的文件路径
     * @param status      状态：ok / not_opened / analysis_timeout / error
     * @param message     状态描述信息（仅非 ok 状态时有值）
     * @param diagnostics 该文件的诊断条目列表
     */
    record FileResult(String file, String status, String message, List<Map<String, Object>> diagnostics) {}

    /**
     * 收集单个文件的诊断信息。
     * <p>
     * 解析路径、检查文件存在性和打开状态，在 ReadAction 中通过
     * DaemonCodeAnalyzerEx.processHighlights() 收集 HighlightInfo，
     * 按 severity 过滤后返回 FileResult。
     * </p>
     *
     * @param project          IntelliJ Project 实例
     * @param rawPath          原始文件路径字符串（相对于 workspace root）
     * @param workspaceRoot    workspace 根目录
     * @param minSeverityLevel 最低严重级别数值（error=3, warning=2, info=1, hint=0）
     * @param openAndAnalyze   是否主动打开未在编辑器中打开的文件
     * @return FileResult 包含该文件的诊断收集结果
     */
    FileResult collectDiagnostics(Project project, String rawPath, Path workspaceRoot,
                                  int minSeverityLevel, boolean openAndAnalyze) {
        String relativePath;
        try {
            // a. 解析路径
            Path candidate = Paths.get(rawPath);
            Path resolved = candidate.isAbsolute()
                    ? candidate.normalize()
                    : workspaceRoot.resolve(candidate).normalize();

            // b. 安全检查：路径不能超出 workspace
            if (!resolved.startsWith(workspaceRoot)) {
                relativePath = rawPath;
                return new FileResult(relativePath, "error",
                        "Path out of workspace: " + rawPath + "/路径超出项目范围", List.of());
            }

            relativePath = workspaceRoot.relativize(resolved).toString();

            // c. 查找 VirtualFile
            VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
            if (vf == null) {
                return new FileResult(relativePath, "error",
                        "File not found: " + relativePath + "/文件不存在", List.of());
            }

            // d. 检查文件是否已在编辑器中打开
            boolean isOpen = FileEditorManager.getInstance(project).isFileOpen(vf);
            if (!isOpen && !openAndAnalyze) {
                return new FileResult(relativePath, "not_opened",
                        "File is not opened in editor. DaemonCodeAnalyzer has not run inspection on this file, "
                                + "so no diagnostics are available. Use openAndAnalyze=true to open and analyze.",
                        List.of());
            }

            if (!isOpen && openAndAnalyze) {
                boolean analysisCompleted = openFileAndWaitForAnalysis(project, vf, 10000);
                if (!analysisCompleted) {
                    // 超时：收集已有的诊断并返回 analysis_timeout 状态
                    List<Map<String, Object>> timeoutDiagnostics = ReadAction.compute(() -> {
                        Document document = FileDocumentManager.getInstance().getDocument(vf);
                        if (document == null) return List.<Map<String, Object>>of();

                        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
                        if (psiFile == null) return List.<Map<String, Object>>of();

                        List<Map<String, Object>> diags = new ArrayList<>();
                        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.INFORMATION,
                                0, document.getTextLength(), highlightInfo -> {
                                    if (highlightInfo != null && highlightInfo.getDescription() != null) {
                                        String sevStr = mapSeverity(highlightInfo.getSeverity());
                                        int sevLevel = severityLevel(sevStr);
                                        if (sevLevel >= minSeverityLevel) {
                                            int[] start = offsetToLineColumn(document, highlightInfo.getStartOffset());
                                            int[] end = offsetToLineColumn(document, highlightInfo.getEndOffset());

                                            Map<String, Object> entry = new LinkedHashMap<>();
                                            entry.put("file", relativePath);
                                            entry.put("line", start[0]);
                                            entry.put("column", start[1]);
                                            entry.put("endLine", end[0]);
                                            entry.put("endColumn", end[1]);
                                            entry.put("severity", sevStr);
                                            entry.put("message", highlightInfo.getDescription());
                                            entry.put("source", highlightInfo.getInspectionToolId());
                                            diags.add(entry);
                                        }
                                    }
                                    return true;
                                });
                        return diags;
                    });
                    return new FileResult(relativePath, "analysis_timeout",
                            "Analysis did not complete within timeout/分析未在超时时间内完成", timeoutDiagnostics);
                }
                // 分析完成，继续到下面的正常 ReadAction 诊断收集
            }

            // e. 在 ReadAction 中收集诊断
            return ReadAction.compute(() -> {
                Document document = FileDocumentManager.getInstance().getDocument(vf);
                if (document == null) {
                    return new FileResult(relativePath, "error",
                            "Cannot obtain document for file: " + relativePath + "/无法获取文件 Document",
                            List.of());
                }

                PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
                if (psiFile == null) {
                    return new FileResult(relativePath, "error",
                            "Cannot obtain PsiFile for file: " + relativePath + "/无法获取 PsiFile",
                            List.of());
                }

                List<Map<String, Object>> diagnostics = new ArrayList<>();

                DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.INFORMATION,
                        0, document.getTextLength(), highlightInfo -> {
                            if (highlightInfo != null && highlightInfo.getDescription() != null) {
                                String sevStr = mapSeverity(highlightInfo.getSeverity());
                                int sevLevel = severityLevel(sevStr);
                                if (sevLevel >= minSeverityLevel) {
                                    int[] start = offsetToLineColumn(document, highlightInfo.getStartOffset());
                                    int[] end = offsetToLineColumn(document, highlightInfo.getEndOffset());

                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    entry.put("file", relativePath);
                                    entry.put("line", start[0]);
                                    entry.put("column", start[1]);
                                    entry.put("endLine", end[0]);
                                    entry.put("endColumn", end[1]);
                                    entry.put("severity", sevStr);
                                    entry.put("message", highlightInfo.getDescription());
                                    entry.put("source", highlightInfo.getInspectionToolId());
                                    diagnostics.add(entry);
                                }
                            }
                            return true;
                        });

                return new FileResult(relativePath, "ok", null, diagnostics);
            });

        } catch (Exception e) {
            // f. 异常捕获：返回 error 状态，不中断其他文件处理
            String filePath = rawPath;
            try {
                filePath = workspaceRoot.relativize(
                        workspaceRoot.resolve(Paths.get(rawPath)).normalize()).toString();
            } catch (Exception ignored) {
                // 路径解析失败时使用原始路径
            }
            return new FileResult(filePath, "error",
                    "Exception: " + e.getMessage() + "/处理异常: " + e.getMessage(), List.of());
        }
    }

    /**
     * 从参数中解析 paths 数组。
     */
    private static List<String> resolvePaths(Map<String, Object> params) {
        List<String> result = new ArrayList<>();
        Object pathsParam = params.get("paths");
        if (pathsParam instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String p = String.valueOf(item).trim();
                    if (!p.isEmpty()) {
                        result.add(p);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 截断结果：包含截断后的诊断列表、截断前的总数、是否被截断标志。
     */
    record TruncationResult(List<Map<String, Object>> diagnostics, int totalDiagnostics, boolean truncated) {}

    /**
     * 对所有诊断条目按严重级别降序排序后截断到 maxResults 条。
     * <p>
     * 排序优先级：ERROR(3) > WARNING(2) > INFO(1) > HINT(0)，确保高严重级别的诊断不会被低级别挤出。
     * </p>
     *
     * @param allDiagnostics 所有文件的诊断条目（每条含 "severity" 字段）
     * @param maxResults     最大返回数量
     * @return TruncationResult 包含截断后列表、原始总数、是否截断
     */
    static TruncationResult truncateDiagnostics(List<Map<String, Object>> allDiagnostics, int maxResults) {
        int totalDiagnostics = allDiagnostics.size();

        // 按 severityLevel 降序排序（ERROR 优先）
        List<Map<String, Object>> sorted = new ArrayList<>(allDiagnostics);
        sorted.sort(Comparator.comparingInt((Map<String, Object> entry) -> {
            Object sev = entry.get("severity");
            if (sev instanceof String s) {
                return switch (s) {
                    case "error" -> 3;
                    case "warning" -> 2;
                    case "info" -> 1;
                    default -> 0;
                };
            }
            return 0;
        }).reversed());

        if (totalDiagnostics > maxResults) {
            return new TruncationResult(sorted.subList(0, maxResults), totalDiagnostics, true);
        }
        return new TruncationResult(sorted, totalDiagnostics, false);
    }

    /**
     * 将截断后的诊断条目按 "file" 字段重新分组回各文件列表。
     * <p>
     * 返回的列表按 filePaths 的顺序排列，每个元素是该文件对应的诊断子列表。
     * 如果某个文件在截断后没有诊断条目，对应位置为空列表。
     * </p>
     *
     * @param truncatedDiagnostics 截断后的诊断条目列表
     * @param filePaths            原始文件路径列表（决定返回顺序）
     * @return 按 filePaths 顺序排列的每文件诊断列表
     */
    static List<List<Map<String, Object>>> redistributeToFiles(List<Map<String, Object>> truncatedDiagnostics, List<String> filePaths) {
        // 按文件路径分组
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String path : filePaths) {
            grouped.put(path, new ArrayList<>());
        }
        for (Map<String, Object> entry : truncatedDiagnostics) {
            Object file = entry.get("file");
            if (file instanceof String f && grouped.containsKey(f)) {
                grouped.get(f).add(entry);
            }
        }

        // 按 filePaths 顺序返回
        List<List<Map<String, Object>>> result = new ArrayList<>();
        for (String path : filePaths) {
            result.add(grouped.get(path));
        }
        return result;
    }
}
