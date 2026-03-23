package com.wmsay.gpt4_lll.fc.agent;

/**
 * 文件快照 — 记录单个文件在某一时刻的完整内容，用于回滚恢复。
 */
public class FileSnapshot {

    private final String filePath;
    private final String originalContent;
    private final String newContent;
    private final long timestamp;

    public FileSnapshot(String filePath, String originalContent, String newContent, long timestamp) {
        this.filePath = filePath;
        this.originalContent = originalContent;
        this.newContent = newContent;
        this.timestamp = timestamp;
    }

    public String getFilePath() { return filePath; }
    public String getOriginalContent() { return originalContent; }
    public String getNewContent() { return newContent; }
    public long getTimestamp() { return timestamp; }
}
