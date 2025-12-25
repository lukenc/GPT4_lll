package com.wmsay.gpt4_lll.model;

import java.util.Objects;

/**
 * 注释翻译数据模型
 * Comment translation data model
 */
public class CommentTranslation {
    private String originalComment;  // 原始注释
    private String translatedComment; // 翻译后的注释
    private int startOffset;         // 注释开始位置
    private int endOffset;           // 注释结束位置
    private long timestamp;          // 翻译时间戳
    private String targetLanguage;   // 目标语言
    
    public CommentTranslation() {
    }
    
    public CommentTranslation(String originalComment, String translatedComment, 
                            int startOffset, int endOffset, String targetLanguage) {
        this.originalComment = originalComment;
        this.translatedComment = translatedComment;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.targetLanguage = targetLanguage;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getOriginalComment() {
        return originalComment;
    }
    
    public void setOriginalComment(String originalComment) {
        this.originalComment = originalComment;
    }
    
    public String getTranslatedComment() {
        return translatedComment;
    }
    
    public void setTranslatedComment(String translatedComment) {
        this.translatedComment = translatedComment;
    }
    
    public int getStartOffset() {
        return startOffset;
    }
    
    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }
    
    public int getEndOffset() {
        return endOffset;
    }
    
    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getTargetLanguage() {
        return targetLanguage;
    }
    
    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommentTranslation that = (CommentTranslation) o;
        return startOffset == that.startOffset &&
               endOffset == that.endOffset &&
               Objects.equals(originalComment, that.originalComment);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(originalComment, startOffset, endOffset);
    }
    
    @Override
    public String toString() {
        return "CommentTranslation{" +
               "originalComment='" + originalComment + '\'' +
               ", translatedComment='" + translatedComment + '\'' +
               ", startOffset=" + startOffset +
               ", endOffset=" + endOffset +
               ", timestamp=" + timestamp +
               ", targetLanguage='" + targetLanguage + '\'' +
               '}';
    }
}
