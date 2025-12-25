package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 提供项目结构提取工具，便于将目录概览提供给大模型。
 */
public class ProjectStructureUtils {

    private static final Set<String> DEFAULT_IGNORES = Set.of(
            ".git", ".idea", "node_modules", "target", "build", "out", ".gradle"
    );

    /**
     * 提取项目目录结构。
     *
     * @param project   当前项目（必填）
     * @param maxDepth  最大深度，root 为 0；建议 3-5
     * @param extraIgnores 额外忽略的目录名称
     * @return 结构化的目录树行，如 "├─ src/main/java"
     */
    public static List<String> extractStructure(Project project, int maxDepth, List<String> extraIgnores) {
        if (project == null || project.getBasePath() == null) {
            return List.of();
        }
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        if (root == null) {
            return List.of();
        }
        Set<String> ignore = new HashSet<>(DEFAULT_IGNORES);
        if (extraIgnores != null) {
            ignore.addAll(extraIgnores);
        }
        List<String> lines = new ArrayList<>();
        traverse(root, 0, maxDepth, ignore, lines, "");
        return lines;
    }

    private static void traverse(@NotNull VirtualFile dir,
                                 int depth,
                                 int maxDepth,
                                 Set<String> ignores,
                                 List<String> out,
                                 String prefix) {
        if (depth > maxDepth) {
            return;
        }
        VirtualFile[] children = dir.getChildren();
        if (children == null || children.length == 0) {
            return;
        }
        // 按名称排序，保证稳定输出
        List<VirtualFile> sorted = Stream.of(children).sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
        for (int i = 0; i < sorted.size(); i++) {
            VirtualFile child = sorted.get(i);
            boolean last = (i == sorted.size() - 1);
            if (ignores.contains(child.getName())) {
                continue;
            }
            String connector = last ? "└─ " : "├─ ";
            out.add(prefix + connector + child.getName());
            if (child.isDirectory()) {
                String nextPrefix = prefix + (last ? "   " : "│  ");
                traverse(child, depth + 1, maxDepth, ignores, out, nextPrefix);
            }
        }
    }
}

