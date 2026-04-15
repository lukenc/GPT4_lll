package com.wmsay.gpt4_lll.fc.model;

import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.tools.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Function Calling 请求。
 * 封装对话内容、可用工具和配置信息。
 */
public class FunctionCallRequest {

    private final ChatContent chatContent;
    private final List<Tool> availableTools;
    private final int maxRounds;
    private final FunctionCallConfig config;

    private FunctionCallRequest(Builder builder) {
        this.chatContent = builder.chatContent;
        this.availableTools = builder.availableTools == null ? 
            Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(builder.availableTools));
        this.maxRounds = builder.maxRounds;
        this.config = builder.config;
    }

    public ChatContent getChatContent() {
        return chatContent;
    }

    public List<Tool> getAvailableTools() {
        return availableTools;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public FunctionCallConfig getConfig() {
        return config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatContent chatContent;
        private List<Tool> availableTools;
        private int maxRounds = 300;
        private FunctionCallConfig config;

        public Builder chatContent(ChatContent chatContent) {
            this.chatContent = chatContent;
            return this;
        }

        public Builder availableTools(List<Tool> availableTools) {
            this.availableTools = availableTools;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder config(FunctionCallConfig config) {
            this.config = config;
            return this;
        }

        public FunctionCallRequest build() {
            if (chatContent == null) {
                throw new IllegalArgumentException("chatContent is required");
            }
            if (maxRounds <= 0) {
                throw new IllegalArgumentException("maxRounds must be positive");
            }
            return new FunctionCallRequest(this);
        }
    }
}
