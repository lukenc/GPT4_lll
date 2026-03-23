package com.wmsay.gpt4_lll.fc.agent;

/**
 * 知识条目 — 不可变数据类。
 */
public class KnowledgeEntry {

    private final String id;
    private final KnowledgeType type;
    private final String title;
    private final String content;
    private final long createdAt;

    public KnowledgeEntry(String id, KnowledgeType type, String title, String content, long createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public KnowledgeType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getCreatedAt() { return createdAt; }
}
