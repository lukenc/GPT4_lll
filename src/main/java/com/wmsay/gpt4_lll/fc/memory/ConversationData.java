package com.wmsay.gpt4_lll.fc.memory;

import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.List;

/**
 * 对话数据包装类，支持新的存储格式。
 * <p>
 * 新格式: {@code { "messages": [...], "summaryMetadata": [...], "lastKnownPromptTokens": N }}
 * <p>
 * 旧格式（纯消息列表）加载后 {@code summaryMetadata} 为 null，
 * {@code lastKnownPromptTokens} 为 -1。
 * <p>
 * 支持 fastjson 序列化/反序列化。
 */
public class ConversationData {

    private List<Message> messages;
    private List<SummaryMetadata> summaryMetadata;
    private int lastKnownPromptTokens = -1;

    /** fastjson 反序列化需要无参构造函数 */
    public ConversationData() {
    }

    public ConversationData(List<Message> messages, List<SummaryMetadata> summaryMetadata,
                            int lastKnownPromptTokens) {
        this.messages = messages;
        this.summaryMetadata = summaryMetadata;
        this.lastKnownPromptTokens = lastKnownPromptTokens;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<SummaryMetadata> getSummaryMetadata() {
        return summaryMetadata;
    }

    public void setSummaryMetadata(List<SummaryMetadata> summaryMetadata) {
        this.summaryMetadata = summaryMetadata;
    }

    public int getLastKnownPromptTokens() {
        return lastKnownPromptTokens;
    }

    public void setLastKnownPromptTokens(int lastKnownPromptTokens) {
        this.lastKnownPromptTokens = lastKnownPromptTokens;
    }
}
