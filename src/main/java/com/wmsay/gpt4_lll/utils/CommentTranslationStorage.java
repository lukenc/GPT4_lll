package com.wmsay.gpt4_lll.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.wmsay.gpt4_lll.model.CommentTranslation;
import com.wmsay.gpt4_lll.model.FileTranslationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 注释翻译本地存储管理器
 * Comment translation local storage manager
 */
public class CommentTranslationStorage {
    private static final Logger log = LoggerFactory.getLogger(CommentTranslationStorage.class);
    
    private static final String STORAGE_DIR = "gpt4lll-translations";
    private static final String CACHE_FILE = "translation-cache.json";
    
    private static CommentTranslationStorage instance;
    private Map<String, FileTranslationCache> cache;
    private Path storagePath;
    
    private CommentTranslationStorage() {
        initializeStorage();
        loadCache();
    }
    
    public static synchronized CommentTranslationStorage getInstance() {
        if (instance == null) {
            instance = new CommentTranslationStorage();
        }
        return instance;
    }
    
    /**
     * 初始化存储目录
     * Initialize storage directory
     */
    private void initializeStorage() {
        try {
            String configPath = PathManager.getConfigPath();
            storagePath = Paths.get(configPath, STORAGE_DIR);
            Files.createDirectories(storagePath);
            log.info("Translation storage initialized at: {}", storagePath);
        } catch (IOException e) {
            log.error("Failed to initialize translation storage", e);
            // 使用临时目录作为备选
            storagePath = Paths.get(System.getProperty("java.io.tmpdir"), STORAGE_DIR);
            try {
                Files.createDirectories(storagePath);
            } catch (IOException ex) {
                log.error("Failed to create backup storage directory", ex);
            }
        }
    }
    
    /**
     * 加载缓存数据
     * Load cache data
     */
    private void loadCache() {
        cache = new HashMap<>();
        Path cacheFile = storagePath.resolve(CACHE_FILE);
        
        if (Files.exists(cacheFile)) {
            try {
                String content = Files.readString(cacheFile, StandardCharsets.UTF_8);
                Map<String, FileTranslationCache> loadedCache = JSON.parseObject(
                    content, 
                    new TypeReference<Map<String, FileTranslationCache>>() {}
                );
                if (loadedCache != null) {
                    cache = loadedCache;
                }
                log.info("Loaded {} translation cache entries", cache.size());
            } catch (Exception e) {
                log.error("Failed to load translation cache", e);
                cache = new HashMap<>();
            }
        }
    }
    
    /**
     * 保存缓存数据
     * Save cache data
     */
    private void saveCache() {
        try {
            Path cacheFile = storagePath.resolve(CACHE_FILE);
            String content = JSON.toJSONString(cache, true);
            Files.writeString(cacheFile, content, StandardCharsets.UTF_8);
            log.debug("Translation cache saved successfully");
        } catch (IOException e) {
            log.error("Failed to save translation cache", e);
        }
    }
    
    /**
     * 计算文件内容哈希值
     * Calculate file content hash
     */
    private String calculateFileHash(VirtualFile file) {
        try {
            byte[] content = file.contentsToByteArray();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate file hash for: {}", file.getPath(), e);
            return String.valueOf(file.getModificationStamp());
        }
    }
    
    /**
     * 生成缓存键
     * Generate cache key
     */
    private String generateCacheKey(String filePath, String targetLanguage) {
        return filePath + "|" + targetLanguage;
    }
    
    /**
     * 获取文件的翻译缓存
     * Get translation cache for file
     */
    public FileTranslationCache getTranslationCache(VirtualFile file, String targetLanguage) {
        String filePath = file.getPath();
        String cacheKey = generateCacheKey(filePath, targetLanguage);
        String currentHash = calculateFileHash(file);
        
        FileTranslationCache fileCache = cache.get(cacheKey);
        
        // 检查缓存是否有效
        if (fileCache != null && !fileCache.isValid(currentHash)) {
            log.debug("Cache invalid for file: {}, removing old cache", filePath);
            cache.remove(cacheKey);
            fileCache = null;
        }
        
        return fileCache;
    }
    
    /**
     * 保存文件翻译缓存
     * Save file translation cache
     */
    public void saveTranslationCache(VirtualFile file, String targetLanguage, 
                                   List<CommentTranslation> translations) {
        String filePath = file.getPath();
        String fileHash = calculateFileHash(file);
        String cacheKey = generateCacheKey(filePath, targetLanguage);
        
        FileTranslationCache fileCache = new FileTranslationCache(filePath, fileHash, targetLanguage);
        fileCache.setTranslations(translations);
        
        cache.put(cacheKey, fileCache);
        saveCache();
        
        log.debug("Saved translation cache for file: {} with {} translations", 
                  filePath, translations.size());
    }
    
    /**
     * 清除文件的翻译缓存
     * Clear translation cache for file
     */
    public void clearTranslationCache(VirtualFile file, String targetLanguage) {
        String filePath = file.getPath();
        String cacheKey = generateCacheKey(filePath, targetLanguage);
        
        if (cache.remove(cacheKey) != null) {
            saveCache();
            log.debug("Cleared translation cache for file: {}", filePath);
        }
    }
    
    /**
     * 清除所有翻译缓存
     * Clear all translation cache
     */
    public void clearAllCache() {
        cache.clear();
        saveCache();
        log.info("Cleared all translation cache");
    }
    
    /**
     * 获取缓存统计信息
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", cache.size());
        
        int totalTranslations = cache.values().stream()
            .mapToInt(fileCache -> fileCache.getTranslations().size())
            .sum();
        stats.put("totalTranslations", totalTranslations);
        
        return stats;
    }
}
