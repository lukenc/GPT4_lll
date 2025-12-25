package com.wmsay.gpt4_lll.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文件翻译缓存数据模型
 * File translation cache data model
 */
public class FileTranslationCache {
    private String filePath;            // 文件路径
    private String fileHash;            // 文件内容哈希值
    private long lastModified;          // 最后修改时间
    private String targetLanguage;      // 目标语言
    private List<CommentTranslation> translations; // 翻译列表
    
    public FileTranslationCache() {
        this.translations = new ArrayList<>();
    }
    
    public FileTranslationCache(String filePath, String fileHash, String targetLanguage) {
        this.filePath = filePath;
        this.fileHash = fileHash;
        this.targetLanguage = targetLanguage;
        this.lastModified = System.currentTimeMillis();
        this.translations = new ArrayList<>();
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getFileHash() {
        return fileHash;
    }
    
    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    public String getTargetLanguage() {
        return targetLanguage;
    }
    
    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }
    
    public List<CommentTranslation> getTranslations() {
        return translations;
    }
    
    public void setTranslations(List<CommentTranslation> translations) {
        this.translations = translations != null ? translations : new ArrayList<>();
    }
    
    public void addTranslation(CommentTranslation translation) {
        if (translation != null) {
            this.translations.add(translation);
        }
    }
    
    public void clearTranslations() {
        this.translations.clear();
    }
    
    /**
     * 检查缓存是否有效（文件是否被修改）
     * Check if cache is valid (file has not been modified)
     */
    public boolean isValid(String currentFileHash) {
        return Objects.equals(this.fileHash, currentFileHash);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileTranslationCache that = (FileTranslationCache) o;
        return Objects.equals(filePath, that.filePath) &&
               Objects.equals(targetLanguage, that.targetLanguage);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(filePath, targetLanguage);
    }
    
    @Override
    public String toString() {
        return "FileTranslationCache{" +
               "filePath='" + filePath + '\'' +
               ", fileHash='" + fileHash + '\'' +
               ", lastModified=" + lastModified +
               ", targetLanguage='" + targetLanguage + '\'' +
               ", translations=" + translations.size() +
               '}';
    }
}
