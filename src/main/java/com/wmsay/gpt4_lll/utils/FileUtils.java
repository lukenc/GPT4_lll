package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import groovy.util.logging.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用于操作README文件的工具类
 */
@Slf4j
public class FileUtils {

    // README文件的常见命名模式
    private static final Pattern README_PATTERN = Pattern.compile("(?i)^readme(\\.(md|txt|rst))?$");
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(FileUtils.class);

    /**
     * 查找项目中的README文件
     *
     * @param project 当前项目
     * @return README文件的路径，如果没有找到则返回null
     */
    @Nullable
    public static String findReadmeFilePath(@NotNull Project project) {
        if (project.isDisposed()) {
            return null;
        }

        VirtualFile projectDir = project.getBaseDir();
        if (projectDir == null) {
            log.warn("尝试在无地址的项目中查找README文件");
            return null;
        }

        // 首先检查项目根目录
        VirtualFile readmeFile = findReadmeInDirectory(projectDir);
        if (readmeFile != null) {
            return readmeFile.getPath();
        }

        // 如果在根目录没有找到，可以考虑扩展搜索到一定深度的子目录
        List<VirtualFile> readmeFiles = new ArrayList<>();
        findReadmeFilesRecursively(projectDir, readmeFiles, 2); // 限制递归深度为2

        if (!readmeFiles.isEmpty()) {
            // 优先选择MD格式的README
            for (VirtualFile file : readmeFiles) {
                if (file.getName().toLowerCase().endsWith(".md")) {
                    return file.getPath();
                }
            }
            // 如果没有MD格式，返回第一个找到的README
            return readmeFiles.get(0).getPath();
        }

        return null;
    }

    /**
     * 递归查找指定目录及其子目录中的README文件
     *
     * @param directory 要搜索的目录
     * @param results 存储结果的列表
     * @param maxDepth 最大递归深度
     */
    private static void findReadmeFilesRecursively(@NotNull VirtualFile directory,
                                                   @NotNull List<VirtualFile> results,
                                                   int maxDepth) {
        if (maxDepth <= 0) {
            return;
        }

        VirtualFile readmeFile = findReadmeInDirectory(directory);
        if (readmeFile != null) {
            results.add(readmeFile);
        }

        for (VirtualFile child : directory.getChildren()) {
            if (child.isDirectory()) {
                findReadmeFilesRecursively(child, results, maxDepth - 1);
            }
        }
    }

    /**
     * 在指定目录中查找README文件
     *
     * @param directory 要搜索的目录
     * @return README文件，如果没有找到则返回null
     */
    @Nullable
    private static VirtualFile findReadmeInDirectory(@NotNull VirtualFile directory) {
        for (VirtualFile child : directory.getChildren()) {
            if (!child.isDirectory() && README_PATTERN.matcher(child.getName()).matches()) {
                return child;
            }
        }
        return null;
    }

    /**
     * 读取指定路径文件的内容
     *
     * @param filePath 文件路径
     * @return 文件内容，如果读取失败则返回null
     */
    @Nullable
    public static String readFileContent(@NotNull String filePath) {
        try {
            VirtualFile file = VfsUtil.findFile(new java.io.File(filePath).toPath(), true);
            if (file == null || !file.exists()) {
                log.warn("指定路径的文件不存在: " + filePath);
                return null;
            }

            if (file.isDirectory()) {
                log.warn("指定路径是一个目录而不是文件: " + filePath);
                return null;
            }

            return VfsUtil.loadText(file);
        } catch (IOException e) {
            log.warn("读取文件内容时发生错误: " + filePath, e);
            return null;
        }
    }

    /**
     * 读取指定路径文件的内容，使用指定的字符集
     *
     * @param filePath 文件路径
     * @param charset 字符集
     * @return 文件内容，如果读取失败则返回null
     */
    @Nullable
    public static String readFileContent(@NotNull String filePath, @NotNull String charset) {
        try {
            VirtualFile file = VfsUtil.findFile(new java.io.File(filePath).toPath(), true);
            if (file == null || !file.exists()) {
                log.warn("指定路径的文件不存在: " + filePath);
                return null;
            }

            if (file.isDirectory()) {
                log.warn("指定路径是一个目录而不是文件: " + filePath);
                return null;
            }

            return new String(file.contentsToByteArray(), charset);
        } catch (IOException e) {
            log.warn("读取文件内容时发生错误: " + filePath, e);
            return null;
        }
    }
}
