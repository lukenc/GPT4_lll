package com.wmsay.gpt4_lll.model;

import java.util.Objects;

/**
 * 代码变更操作模型
 * 表示对代码的单个变更操作：删除、插入或修改
 */
public class CodeChange {

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        DELETE,  // 删除行
        INSERT,  // 插入行
        MODIFY   // 修改行（相当于删除+插入）
    }

    /**
     * 变更类型
     */
    private ChangeType type;

    /**
     * 行号（从1开始）
     * - DELETE: 要删除的行号
     * - INSERT: 在此行之后插入（0表示在第一行之前插入）
     * - MODIFY: 要修改的行号
     */
    private int lineNumber;

    /**
     * 原始内容（DELETE和MODIFY时使用）
     */
    private String originalContent;

    /**
     * 新内容（INSERT和MODIFY时使用）
     */
    private String newContent;

    /**
     * 变更原因说明
     */
    private String reason;

    public CodeChange() {
    }

    public CodeChange(ChangeType type, int lineNumber, String originalContent, String newContent, String reason) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.originalContent = originalContent;
        this.newContent = newContent;
        this.reason = reason;
    }

    // Static factory methods
    public static CodeChange delete(int lineNumber, String content, String reason) {
        return new CodeChange(ChangeType.DELETE, lineNumber, content, null, reason);
    }

    public static CodeChange insert(int afterLine, String content, String reason) {
        return new CodeChange(ChangeType.INSERT, afterLine, null, content, reason);
    }

    public static CodeChange modify(int lineNumber, String oldContent, String newContent, String reason) {
        return new CodeChange(ChangeType.MODIFY, lineNumber, oldContent, newContent, reason);
    }

    // Getters and Setters
    public ChangeType getType() {
        return type;
    }

    public void setType(ChangeType type) {
        this.type = type;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public void setOriginalContent(String originalContent) {
        this.originalContent = originalContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * 获取变更类型的显示名称
     */
    public String getTypeDisplayName() {
        return switch (type) {
            case DELETE -> "删除/DELETE";
            case INSERT -> "插入/INSERT";
            case MODIFY -> "修改/MODIFY";
        };
    }

    /**
     * 获取变更类型的简短标识
     */
    public String getTypeSymbol() {
        return switch (type) {
            case DELETE -> "-";
            case INSERT -> "+";
            case MODIFY -> "~";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeChange that = (CodeChange) o;
        return lineNumber == that.lineNumber && 
               type == that.type && 
               Objects.equals(originalContent, that.originalContent) && 
               Objects.equals(newContent, that.newContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lineNumber, originalContent, newContent);
    }

    @Override
    public String toString() {
        return String.format("[%s] Line %d: %s", getTypeSymbol(), lineNumber, 
                reason != null ? reason : (newContent != null ? newContent : originalContent));
    }
}

