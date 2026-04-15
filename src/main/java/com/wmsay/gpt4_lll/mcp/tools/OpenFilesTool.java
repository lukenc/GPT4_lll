package com.wmsay.gpt4_lll.mcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IDE 上下文工具：获取用户当前在编辑器中打开的所有文件。
 * <p>
 * 返回每个打开文件的名称和相对于 workspace root 的路径，
 * 以及当前活跃（聚焦）的文件信息。路径遵循项目路径规范，
 * 输出可直接传给 read_file / write_file / grep 等工具。
 * </p>
 */
public class OpenFilesTool implements Tool {

    @Override
    public String name() {
        return "open_files";
    }

    @Override
    public String description() {
        return "Get the list of files currently open in the IDE editor tabs, "
                + "including which file the user is actively viewing.\n\n"
                + "WHEN TO USE:\n"
                + "- Understand what the user is currently working on before answering questions\n"
                + "- Discover relevant files the user has open as context for a task\n"
                + "- Find the active file when the user says \"this file\" or \"current file\"\n\n"
                + "WHEN NOT TO USE:\n"
                + "- Listing all project files → use tree\n"
                + "- Reading file content → use read_file (pass the 'file' path from this tool's output)\n"
                + "- Searching for files by name or content → use grep\n\n"
                + "RETURNS:\n"
                + "- files[].file: path relative to workspace root (can be passed directly to read_file, write_file, grep)\n"
                + "- files[].name: file name only (e.g. \"Foo.java\")\n"
                + "- files[].isActive: true if this is the currently focused editor tab\n"
                + "- activeFile: shortcut to the active file's relative path (null if none)\n"
                + "- fileCount: total number of open files\n\n"
                + "No parameters required.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        // 无需参数
        return Map.of();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        Project project = context.get("project", Project.class);
        if (project == null) {
            return ToolResult.error("IDE project context is unavailable.");
        }

        Path workspaceRoot = context.getWorkspaceRoot();
        if (workspaceRoot == null) {
            return ToolResult.error("Workspace root is unavailable.");
        }

        AtomicReference<ToolResult> resultRef = new AtomicReference<>();

        // FileEditorManager 需要在 ReadAction 中访问
        ReadAction.run(() -> {
            FileEditorManager editorManager = FileEditorManager.getInstance(project);
            VirtualFile[] openFiles = editorManager.getOpenFiles();
            VirtualFile selectedFile = editorManager.getSelectedFiles().length > 0
                    ? editorManager.getSelectedFiles()[0]
                    : null;

            List<Map<String, Object>> fileList = new ArrayList<>();
            String activeFilePath = null;

            for (VirtualFile vf : openFiles) {
                String filePath = vf.getPath();
                String relativePath = toRelativePath(workspaceRoot, filePath);

                Map<String, Object> fileInfo = new LinkedHashMap<>();
                fileInfo.put("file", relativePath);
                fileInfo.put("name", vf.getName());
                fileInfo.put("isActive", vf.equals(selectedFile));
                fileList.add(fileInfo);

                if (vf.equals(selectedFile)) {
                    activeFilePath = relativePath;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tool", name());
            result.put("fileCount", fileList.size());
            result.put("activeFile", activeFilePath);
            result.put("files", fileList);
            resultRef.set(ToolResult.structured(result));
        });

        ToolResult result = resultRef.get();
        return result != null ? result : ToolResult.error("Failed to read open files from IDE.");
    }

    /**
     * 将绝对路径转为相对于 workspace root 的路径。
     * 如果文件不在 workspace 下，返回原始路径。
     */
    private static String toRelativePath(Path workspaceRoot, String absolutePath) {
        try {
            Path filePath = Paths.get(absolutePath);
            if (filePath.startsWith(workspaceRoot)) {
                return workspaceRoot.relativize(filePath).toString();
            }
        } catch (Exception ignored) {
            // 路径解析失败时返回原始路径
        }
        return absolutePath;
    }
}
