package com.wmsay.gpt4_lll.mcp;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * MCP 工具执行上下文。
 * 当前先聚焦通用文件能力，保留 Project / Editor / 项目根目录。
 */
public class McpContext {

    private final Project project;
    private final Editor editor;
    private final Path projectRoot;

    public McpContext(Project project, Editor editor, Path projectRoot) {
        this.project = project;
        this.editor = editor;
        this.projectRoot = projectRoot;
    }

    public static McpContext fromIdeState(Project project, Editor editor) {
        Path root = null;
        if (project != null && project.getBasePath() != null) {
            root = Paths.get(project.getBasePath()).toAbsolutePath().normalize();
        }
        return new McpContext(project, editor, root);
    }

    public Project getProject() {
        return project;
    }

    public Editor getEditor() {
        return editor;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }
}
