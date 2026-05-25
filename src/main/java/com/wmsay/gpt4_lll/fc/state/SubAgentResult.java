package com.wmsay.gpt4_lll.fc.state;

/**
 * 子 Agent 执行结果。
 * 封装子 Agent 的执行内容、状态、耗时和匹配的 Skill 名称。
 * 不可变数据对象，通过 Builder 模式构建。
 */
public class SubAgentResult {

    private final String content;
    private final boolean success;
    private final long durationMs;
    private final String skillName;

    private SubAgentResult(Builder builder) {
        this.content = builder.content;
        this.success = builder.success;
        this.durationMs = builder.durationMs;
        this.skillName = builder.skillName;
    }

    public String getContent() {
        return content;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getSkillName() {
        return skillName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SubAgentResult success(String content, String skillName, long durationMs) {
        return builder()
            .content(content)
            .success(true)
            .skillName(skillName)
            .durationMs(durationMs)
            .build();
    }

    public static SubAgentResult failure(String errorMessage, String skillName, long durationMs) {
        return builder()
            .content(errorMessage)
            .success(false)
            .skillName(skillName)
            .durationMs(durationMs)
            .build();
    }

    public static class Builder {
        private String content;
        private boolean success;
        private long durationMs;
        private String skillName;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public SubAgentResult build() {
            if (skillName == null || skillName.isBlank()) {
                throw new IllegalArgumentException("skillName is required");
            }
            if (durationMs < 0) {
                throw new IllegalArgumentException("durationMs must be >= 0");
            }
            return new SubAgentResult(this);
        }
    }
}
