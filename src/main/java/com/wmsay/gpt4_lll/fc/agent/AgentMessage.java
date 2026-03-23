package com.wmsay.gpt4_lll.fc.agent;

/**
 * Agent 间通信的标准消息格式 — 不可变对象。
 * 使用 Builder 模式构建。
 */
public class AgentMessage {

    public enum MessageType { REQUEST, RESPONSE, NOTIFY }

    private final String sourceAgentId;
    private final String targetAgentId;
    private final MessageType messageType;
    private final String payload;
    private final String correlationId;

    private AgentMessage(Builder builder) {
        this.sourceAgentId = builder.sourceAgentId;
        this.targetAgentId = builder.targetAgentId;
        this.messageType = builder.messageType;
        this.payload = builder.payload;
        this.correlationId = builder.correlationId;
    }

    public String getSourceAgentId() { return sourceAgentId; }
    public String getTargetAgentId() { return targetAgentId; }
    public MessageType getMessageType() { return messageType; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sourceAgentId;
        private String targetAgentId;
        private MessageType messageType;
        private String payload;
        private String correlationId;

        public Builder sourceAgentId(String v) { this.sourceAgentId = v; return this; }
        public Builder targetAgentId(String v) { this.targetAgentId = v; return this; }
        public Builder messageType(MessageType v) { this.messageType = v; return this; }
        public Builder payload(String v) { this.payload = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }

        public AgentMessage build() {
            if (sourceAgentId == null || sourceAgentId.isEmpty())
                throw new IllegalArgumentException("sourceAgentId is required");
            if (targetAgentId == null || targetAgentId.isEmpty())
                throw new IllegalArgumentException("targetAgentId is required");
            if (messageType == null)
                throw new IllegalArgumentException("messageType is required");
            return new AgentMessage(this);
        }
    }
}
