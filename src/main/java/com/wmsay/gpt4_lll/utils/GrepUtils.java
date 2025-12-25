package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提供类似 grep 的行过滤能力：
 * - 输入可选的行号范围与关键词
 * - 返回命中行以及上下若干行（默认 1 行）
 * - 未指定关键词时，返回范围内的全部行
 */
public class GrepUtils {

    private static final long DEFAULT_MAX_FILE_BYTES = 1_000_000; // 1 MB 安全阈值

    /**
     * 针对项目中的任意文件执行 grep。
     *
     * @param project      当前项目（用于校验路径，可为空）
     * @param absolutePath 目标文件的绝对路径
     * @param keyword      关键词；为空则返回范围内所有行
     * @param startLine    起始行（1 基）；为空则从首行开始
     * @param endLine      结束行（1 基，包含）；为空则至末行
     * @param contextLines 命中行上下要额外包含的行数（>=0）
     * @return 结构化结果；文件缺失或读取失败时返回空列表
     */
    public static List<GrepMatch> grepFile(Project project,
                                           String absolutePath,
                                           String keyword,
                                           Integer startLine,
                                           Integer endLine,
                                           int contextLines) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return List.of();
        }
        // 若提供了项目上下文，则限制在当前项目路径内
        if (project != null && project.getBasePath() != null && !absolutePath.startsWith(project.getBasePath())) {
            return List.of();
        }
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absolutePath);
        return grepFile(file, keyword, startLine, endLine, contextLines);
    }

    /**
     * 针对指定 VirtualFile 执行 grep。
     */
    public static List<GrepMatch> grepFile(VirtualFile file,
                                           String keyword,
                                           Integer startLine,
                                           Integer endLine,
                                           int contextLines) {
        String content = readFileContent(file);
        return grepWithContext(file == null ? null : file.getPath(), content, keyword, startLine, endLine, contextLines);
    }

    /**
     * 未指定范围时，对项目内所有文件进行搜索。
     *
     * @param project      当前项目（必填，用于限定范围）
     * @param keyword      关键词
     * @param contextLines 命中行上下要额外包含的行数（>=0）
     * @return 结构化结果
     */
    public static List<GrepMatch> grepProject(Project project,
                                              String keyword,
                                              int contextLines) {
        return grepProject(project, keyword, contextLines, DEFAULT_MAX_FILE_BYTES, List.of(), Integer.MAX_VALUE);
    }

    /**
     * 针对整个项目执行 grep，并提供安全限制。
     *
     * @param project        当前项目
     * @param keyword        关键词
     * @param contextLines   上下文行数
     * @param maxFileBytes   文件大小上限，超过则跳过
     * @param ignoreDirNames 忽略的目录名（相对）
     * @param maxMatches     最多收集的匹配条数，达到后停止
     */
    public static List<GrepMatch> grepProject(Project project,
                                              String keyword,
                                              int contextLines,
                                              long maxFileBytes,
                                              List<String> ignoreDirNames,
                                              int maxMatches) {
        if (project == null || keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isEmpty()) {
            return List.of();
        }
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (root == null) {
            return List.of();
        }

        Set<String> ignoreSet = new LinkedHashSet<>();
        ignoreSet.addAll(List.of(".git", ".idea", "node_modules", "target", "build", "out", ".gradle"));
        if (ignoreDirNames != null) {
            ignoreSet.addAll(ignoreDirNames);
        }

        List<GrepMatch> results = new ArrayList<>();
        VfsUtilCore.iterateChildrenRecursively(root, file -> true, file -> {
            if (results.size() >= maxMatches) {
                return false; // 提前终止
            }
            if (file.isDirectory()) {
                if (ignoreSet.contains(file.getName())) {
                    return false; // 跳过此目录
                }
                return true; // 继续递归
            }
            if (file.getFileType() != null && file.getFileType().isBinary()) {
                return true; // 跳过二进制
            }
            if (file.getLength() > maxFileBytes) {
                return true; // 跳过过大的文件
            }
            String content = readFileContent(file);
            if (content == null) {
                return true;
            }
            List<GrepMatch> matches = grepWithContext(file.getPath(), content, keyword, null, null, contextLines);
            if (!matches.isEmpty()) {
                results.addAll(matches);
            }
            // 若达到上限，终止遍历
            if (results.size() >= maxMatches) {
                return false;
            }
            return true;
        });
        return results.size() > maxMatches ? results.subList(0, maxMatches) : results;
    }

    /**
     * 在给定文本中执行类似 grep 的搜索。
     *
     * @param content      文本内容
     * @param keyword      关键词；为空则返回范围内所有行
     * @param startLine    起始行（1 基）；为空则从首行开始
     * @param endLine      结束行（1 基，包含）；为空则至末行
     * @param contextLines 命中行上下要额外包含的行数（>=0）
     * @return 结构化行列表；包含命中与上下文
     */
    public static List<GrepMatch> grepWithContext(String filePath,
                                                  String content,
                                                  String keyword,
                                                  Integer startLine,
                                                  Integer endLine,
                                                  int contextLines) {
        if (content == null) {
            return List.of();
        }
        List<String> lines = Arrays.asList(content.split("\n", -1));
        int safeContext = Math.max(0, contextLines);
        int startIdx = startLine == null ? 0 : Math.max(0, startLine - 1);
        int endIdx = endLine == null ? lines.size() - 1 : Math.min(lines.size() - 1, endLine - 1);
        if (startIdx > endIdx || lines.isEmpty()) {
            return List.of();
        }

        // 未提供关键词，直接返回范围内全部行
        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        Set<Integer> matchedIndexes = new LinkedHashSet<>();
        if (hasKeyword) {
            for (int i = startIdx; i <= endIdx; i++) {
                if (lines.get(i).contains(keyword)) {
                    int from = Math.max(startIdx, i - safeContext);
                    int to = Math.min(endIdx, i + safeContext);
                    for (int j = from; j <= to; j++) {
                        matchedIndexes.add(j);
                    }
                }
            }
            if (matchedIndexes.isEmpty()) {
                return List.of();
            }
        } else {
            // 没有关键词则整段均视为命中
            for (int i = startIdx; i <= endIdx; i++) {
                matchedIndexes.add(i);
            }
        }

        return matchedIndexes.stream()
                .sorted()
                .map(idx -> new GrepMatch(filePath, idx + 1, lines.get(idx), lines.get(idx).contains(keyword)))
                .collect(Collectors.toList());
    }

    /**
     * 使用默认上下文行数（1 行）。
     */
    public static List<GrepMatch> grepWithContext(String filePath,
                                                  String content,
                                                  String keyword,
                                                  Integer startLine,
                                                  Integer endLine) {
        return grepWithContext(filePath, content, keyword, startLine, endLine, 1);
    }

    /**
     * 全文件范围、默认上下文行数的便捷方法。
     */
    public static List<GrepMatch> grepWithContext(String filePath, String content, String keyword) {
        return grepWithContext(filePath, content, keyword, null, null, 1);
    }

    /**
     * 读取 VirtualFile 的文本内容，失败返回 null。
     */
    private static String readFileContent(VirtualFile file) {
        if (file == null) {
            return null;
        }
        try {
            return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 标准化的匹配结果，便于大模型消费。
     */
    public record GrepMatch(String filePath, int lineNumber, String lineContent, boolean matched) { }
}

