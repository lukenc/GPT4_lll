package com.wmsay.gpt4_lll.fc.memory;

/**
 * 摘要元数据，持久化存储在历史对话 JSON 中。
 * <p>
 * 记录每次摘要操作的结果：摘要文本、被摘要的消息索引范围、
 * 生成时间戳以及压缩前后的 token 数。支持 fastjson 序列化/反序列化。
 */
public class SummaryMetadata {

    private String summaryText;
    private int startIndex;
    private int endIndex;
    private long createdAt;
    private int originalTokens;
    private int compressedTokens;

    /** fastjson 反序列化需要无参构造函数 */
    public SummaryMetadata() {
    }

    public SummaryMetadata(String summaryText, int startIndex, int endIndex,
                           long createdAt, int originalTokens, int compressedTokens) {
        this.summaryText = summaryText;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.createdAt = createdAt;
        this.originalTokens = originalTokens;
        this.compressedTokens = compressedTokens;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getOriginalTokens() {
        return originalTokens;
    }

    public void setOriginalTokens(int originalTokens) {
        this.originalTokens = originalTokens;
    }

    public int getCompressedTokens() {
        return compressedTokens;
    }

    public void setCompressedTokens(int compressedTokens) {
        this.compressedTokens = compressedTokens;
    }
}
