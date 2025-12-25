package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件类型检测器，用于判断文件是否属于项目内部或外部库代码
 * File type detector for determining if a file belongs to the project or external library code
 */
public class FileTypeDetector {
    private static final Logger log = LoggerFactory.getLogger(FileTypeDetector.class);
    
    /**
     * 检查文件是否为外部库文件（非项目内代码）
     * Check if the file is an external library file (non-project code)
     * 
     * @param project 当前项目
     * @param file 要检查的文件
     * @return true 如果是外部库文件，false 如果是项目内文件
     */
    public static boolean isExternalLibraryFile(Project project, VirtualFile file) {
        if (project == null || file == null) {
            log.debug("Project or file is null: project={}, file={}", project, file);
            return false;
        }
        
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        
        // 检查文件是否在源代码根目录中
        boolean isInSource = projectFileIndex.isInSource(file);
        
        // 检查文件是否在测试根目录中
        boolean isInTestSource = projectFileIndex.isInTestSourceContent(file);
        
        // 检查文件是否在库文件中
        boolean isInLibrary = projectFileIndex.isInLibrary(file);
        
        // 检查文件是否在外部库的源代码中
        boolean isInLibrarySource = projectFileIndex.isInLibrarySource(file);
        
        // 检查文件是否在项目内容中
        boolean isInContent = projectFileIndex.isInContent(file);
        
        // 检查是否为类文件
        boolean isClassFile = file.getExtension() != null && "class".equals(file.getExtension());
        
        // 检查文件路径是否包含JDK相关路径
        String filePath = file.getPath();
        boolean isJdkFile = filePath.contains("/jdk") || filePath.contains("/openjdk") || 
                           filePath.contains("java.base") || filePath.contains("java.lang") ||
                           filePath.contains("rt.jar") || filePath.contains("lib/modules");
        
        log.debug("File analysis for {}: isInSource={}, isInTestSource={}, isInLibrary={}, " +
                 "isInLibrarySource={}, isInContent={}, isClassFile={}, isJdkFile={}", 
                 filePath, isInSource, isInTestSource, isInLibrary, isInLibrarySource, 
                 isInContent, isClassFile, isJdkFile);
        
        // 扩展检测逻辑：
        // 1. 传统的库文件检测
        // 2. JDK文件检测  
        // 3. 不在项目源码或测试代码中的文件
        // 4. 宽松模式：如果不在项目源码中，就认为是外部文件
        boolean isExternalFile = (isInLibrary || isInLibrarySource || isJdkFile || 
                                 !isInSource) && !isClassFile; // 排除.class文件
        
        // 额外的宽松检查：如果文件路径不包含项目路径，也认为是外部文件
        if (!isExternalFile && project.getBasePath() != null) {
            String projectPath = project.getBasePath();
            boolean isOutsideProject = !filePath.startsWith(projectPath);
            isExternalFile = isOutsideProject && !isClassFile;
            log.debug("Outside project check: projectPath={}, isOutsideProject={}", 
                     projectPath, isOutsideProject);
        }
        
        log.debug("File {} detected as external: {}", filePath, isExternalFile);
        return isExternalFile;
    }
    
    /**
     * 检查文件是否应该显示翻译功能
     * Check if the file should show translation functionality
     * 
     * @param project 当前项目
     * @param file 要检查的文件
     * @return true 如果应该显示翻译功能
     */
    public static boolean shouldShowTranslation(Project project, VirtualFile file) {
        if (project == null || file == null) {
            return false;
        }
        
        // 检查文件扩展名
        String extension = file.getExtension();
        if (extension == null || !isSourceCodeFile(extension)) {
            log.debug("File {} rejected due to extension: {}", file.getPath(), extension);
            return false;
        }
        
        boolean isExternal = isExternalLibraryFile(project, file);
        log.debug("File {} translation availability: {}", file.getPath(), isExternal);
        return isExternal;
    }
    
    /**
     * 检查是否为源代码文件
     * Check if it's a source code file
     */
    private static boolean isSourceCodeFile(String extension) {
        return "java".equals(extension) || "kt".equals(extension) || "scala".equals(extension) ||
               "groovy".equals(extension) || "js".equals(extension) || "ts".equals(extension) ||
               "py".equals(extension) || "cpp".equals(extension) || "c".equals(extension) ||
               "h".equals(extension) || "hpp".equals(extension) || "cs".equals(extension) ||
               "go".equals(extension) || "rs".equals(extension) || "php".equals(extension);
    }
    
    /**
     * 获取文件的详细类型信息
     * Get detailed file type information
     * 
     * @param project 当前项目
     * @param file 要检查的文件
     * @return 文件类型描述
     */
    public static String getFileTypeDescription(Project project, VirtualFile file) {
        if (project == null || file == null) {
            return "Unknown";
        }
        
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        
        if (projectFileIndex.isInSource(file)) {
            return "Project Source";
        } else if (projectFileIndex.isInTestSourceContent(file)) {
            return "Project Test";
        } else if (projectFileIndex.isInLibrarySource(file)) {
            return "Library Source";
        } else if (projectFileIndex.isInLibrary(file)) {
            return "Library";
        } else if (projectFileIndex.isInContent(file)) {
            return "Project Content";
        } else {
            return "External";
        }
    }
}
