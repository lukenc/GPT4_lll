package com.wmsay.gpt4_lll.fc.skill;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Skill 定义 — 不可变对象。
 * 从 .md 文件解析出的所有结构化字段。
 * 使用 Builder 模式构建，与现有 AgentDefinition 风格一致。
 */
public class SkillDefinition {
    private final String name;
    private final String systemPrompt;
    private final String purpose;
    private final String trigger;
    private final String promptTemplate;
    private final List<String> tools;
    private final List<String> searchKeywords;
    private final String examples;
    private final String additionalNotes;
    private final String version;
    private final Path filePath;
    private final long lastModified;
    private final boolean hasUserInputPlaceholder;
    private final SkillComplexity complexity;
    private final boolean generated;

    private SkillDefinition(Builder builder) {
        this.name = builder.name;
        this.systemPrompt = builder.systemPrompt;
        this.purpose = builder.purpose;
        this.trigger = builder.trigger;
        this.promptTemplate = builder.promptTemplate;
        this.tools = builder.tools == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(builder.tools));
        this.searchKeywords = builder.searchKeywords == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(builder.searchKeywords));
        this.examples = builder.examples;
        this.additionalNotes = builder.additionalNotes;
        this.version = builder.version;
        this.filePath = builder.filePath;
        this.lastModified = builder.lastModified;
        this.hasUserInputPlaceholder = builder.hasUserInputPlaceholder;
        this.complexity = builder.complexity;
        this.generated = builder.generated;
    }

    public String getName() { return name; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getPurpose() { return purpose; }
    public String getTrigger() { return trigger; }
    public String getPromptTemplate() { return promptTemplate; }
    public List<String> getTools() { return tools; }
    public List<String> getSearchKeywords() { return searchKeywords; }
    public String getExamples() { return examples; }
    public String getAdditionalNotes() { return additionalNotes; }
    public String getVersion() { return version; }
    public Path getFilePath() { return filePath; }
    public long getLastModified() { return lastModified; }
    public boolean isHasUserInputPlaceholder() { return hasUserInputPlaceholder; }
    public SkillComplexity getComplexity() { return complexity; }
    public boolean isGenerated() { return generated; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String systemPrompt;
        private String purpose;
        private String trigger;
        private String promptTemplate;
        private List<String> tools;
        private List<String> searchKeywords;
        private String examples;
        private String additionalNotes;
        private String version = "1.0";
        private Path filePath;
        private long lastModified;
        private boolean hasUserInputPlaceholder;
        private SkillComplexity complexity = SkillComplexity.MODERATE;
        private boolean generated = false;

        public Builder name(String name) { this.name = name; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }
        public Builder trigger(String trigger) { this.trigger = trigger; return this; }
        public Builder promptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; return this; }
        public Builder tools(List<String> tools) { this.tools = tools; return this; }
        public Builder searchKeywords(List<String> searchKeywords) { this.searchKeywords = searchKeywords; return this; }
        public Builder examples(String examples) { this.examples = examples; return this; }
        public Builder additionalNotes(String additionalNotes) { this.additionalNotes = additionalNotes; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder filePath(Path filePath) { this.filePath = filePath; return this; }
        public Builder lastModified(long lastModified) { this.lastModified = lastModified; return this; }
        public Builder hasUserInputPlaceholder(boolean hasUserInputPlaceholder) { this.hasUserInputPlaceholder = hasUserInputPlaceholder; return this; }
        public Builder complexity(SkillComplexity complexity) { this.complexity = complexity; return this; }
        public Builder generated(boolean generated) { this.generated = generated; return this; }

        public SkillDefinition build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("SkillDefinition name must not be null or empty");
            }
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                throw new IllegalArgumentException("SkillDefinition systemPrompt must not be null or empty");
            }
            if (purpose == null || purpose.isEmpty()) {
                throw new IllegalArgumentException("SkillDefinition purpose must not be null or empty");
            }
            if (trigger == null || trigger.isEmpty()) {
                throw new IllegalArgumentException("SkillDefinition trigger must not be null or empty");
            }
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                throw new IllegalArgumentException("SkillDefinition promptTemplate must not be null or empty");
            }
            return new SkillDefinition(this);
        }
    }
}
