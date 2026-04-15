package com.wmsay.gpt4_lll.fc.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 文件解析器。
 * 按 Markdown 二级标题（##）拆分文件内容，提取各区段文本。
 * 正确处理代码块内的 ## 标记（不作为区段分隔符）。
 */
public class SkillParser {

    private static final Logger LOG = Logger.getLogger(SkillParser.class.getName());
    private static final String LOG_PREFIX = "[Skill] ";

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "<!--\\s*version:\\s*([\\d]+\\.[\\d]+)\\s*-->");

    private static final Pattern COMPLEXITY_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*complexity:\\s*(\\S+)\\s*$");

    private static final String DEFAULT_VERSION = "1.0";
    private static final String USER_INPUT_PLACEHOLDER = "{{user_input}}";

    /**
     * 解析 .md 文件为 SkillDefinition。
     *
     * @param filePath .md 文件路径
     * @return SkillDefinition，解析失败时返回 null
     */
    public SkillDefinition parse(Path filePath) {
        if (filePath == null) {
            LOG.warning(LOG_PREFIX + "File path is null");
            return null;
        }

        String content;
        try {
            content = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.WARNING, LOG_PREFIX + "Failed to read file: " + filePath, e);
            return null;
        }

        return parseContent(content, filePath);
    }

    /**
     * 从字符串内容解析 SkillDefinition（内部方法，也方便测试）。
     */
    SkillDefinition parseContent(String content, Path filePath) {
        // 解析版本注释
        String version = parseVersion(content);

        // 按 ## 二级标题拆分区段（跳过代码块内的 ##）
        Map<String, String> sections = splitSections(content);

        // 校验必填区段
        if (!sections.containsKey("System")) {
            LOG.warning(LOG_PREFIX + "Missing required section '## System' in file: " + filePath);
            return null;
        }
        if (!sections.containsKey("Description")) {
            LOG.warning(LOG_PREFIX + "Missing required section '## Description' in file: " + filePath);
            return null;
        }
        if (!sections.containsKey("Prompt")) {
            LOG.warning(LOG_PREFIX + "Missing required section '## Prompt' in file: " + filePath);
            return null;
        }

        // 解析 Description 子区段
        String descriptionContent = sections.get("Description");
        String purpose = parseSubSection(descriptionContent, "Purpose");
        String trigger = parseSubSection(descriptionContent, "Trigger");

        if (purpose == null || purpose.isEmpty()) {
            LOG.warning(LOG_PREFIX + "Missing '### Purpose' in Description section, file: " + filePath);
            return null;
        }
        if (trigger == null || trigger.isEmpty()) {
            LOG.warning(LOG_PREFIX + "Missing '### Trigger' in Description section, file: " + filePath);
            return null;
        }

        // 解析 complexity 字段（### Complexity 子区段或 complexity: 行）
        SkillComplexity complexity = parseComplexity(descriptionContent);

        // 解析各区段
        String systemPrompt = sections.get("System").trim();
        String promptTemplate = sections.get("Prompt").trim();
        List<String> tools = parseList(sections.get("Tools"));
        List<String> searchKeywords = parseList(sections.get("Search Keywords"));
        String examples = sections.containsKey("Examples")
                ? sections.get("Examples").trim() : null;
        String additionalNotes = sections.containsKey("Additional Notes")
                ? sections.get("Additional Notes").trim() : null;

        // 检测 {{user_input}} 占位符
        boolean hasUserInputPlaceholder = promptTemplate.contains(USER_INPUT_PLACEHOLDER);

        // 获取文件元信息
        String name = extractSkillName(filePath);
        long lastModified = 0;
        try {
            if (filePath != null && Files.exists(filePath)) {
                lastModified = Files.getLastModifiedTime(filePath).toMillis();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, LOG_PREFIX + "Could not read lastModified for: " + filePath, e);
        }

        try {
            return SkillDefinition.builder()
                    .name(name)
                    .systemPrompt(systemPrompt)
                    .purpose(purpose)
                    .trigger(trigger)
                    .complexity(complexity)
                    .promptTemplate(promptTemplate)
                    .tools(tools)
                    .searchKeywords(searchKeywords)
                    .examples(examples)
                    .additionalNotes(additionalNotes)
                    .version(version)
                    .filePath(filePath)
                    .lastModified(lastModified)
                    .hasUserInputPlaceholder(hasUserInputPlaceholder)
                    .build();
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, LOG_PREFIX + "Failed to build SkillDefinition for: " + filePath, e);
            return null;
        }
    }

    /**
     * 将 SkillDefinition 格式化回 .md 文本（用于 round-trip 测试）。
     */
    public String format(SkillDefinition definition) {
        if (definition == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 版本注释
        if (definition.getVersion() != null) {
            sb.append("<!-- version: ").append(definition.getVersion()).append(" -->\n\n");
        }

        // ## System
        sb.append("## System\n\n");
        sb.append(definition.getSystemPrompt()).append("\n\n");

        // ## Description
        sb.append("## Description\n\n");
        sb.append("### Purpose\n\n");
        sb.append(definition.getPurpose()).append("\n\n");
        sb.append("### Trigger\n\n");
        sb.append(definition.getTrigger()).append("\n\n");
        if (definition.getComplexity() != null) {
            sb.append("### Complexity\n\n");
            sb.append(definition.getComplexity().name().toLowerCase()).append("\n\n");
        }

        // ## Prompt
        sb.append("## Prompt\n\n");
        sb.append(definition.getPromptTemplate()).append("\n\n");

        // ## Tools (optional)
        if (definition.getTools() != null && !definition.getTools().isEmpty()) {
            sb.append("## Tools\n\n");
            for (String tool : definition.getTools()) {
                sb.append("- ").append(tool).append("\n");
            }
            sb.append("\n");
        }

        // ## Search Keywords (optional)
        if (definition.getSearchKeywords() != null && !definition.getSearchKeywords().isEmpty()) {
            sb.append("## Search Keywords\n\n");
            for (String keyword : definition.getSearchKeywords()) {
                sb.append("- ").append(keyword).append("\n");
            }
            sb.append("\n");
        }

        // ## Examples (optional)
        if (definition.getExamples() != null && !definition.getExamples().isEmpty()) {
            sb.append("## Examples\n\n");
            sb.append(definition.getExamples()).append("\n\n");
        }

        // ## Additional Notes (optional)
        if (definition.getAdditionalNotes() != null && !definition.getAdditionalNotes().isEmpty()) {
            sb.append("## Additional Notes\n\n");
            sb.append(definition.getAdditionalNotes()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 解析版本注释 <!-- version: X.Y -->。
     * 未找到时返回默认版本 "1.0"。
     */
    String parseVersion(String content) {
        Matcher matcher = VERSION_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_VERSION;
    }

    /**
     * 按 ## 二级标题拆分区段，正确处理代码块内的 ## 标记。
     * 返回 section name -> section content 的有序映射。
     */
    Map<String, String> splitSections(String content) {
        Map<String, String> sections = new LinkedHashMap<>();
        String[] lines = content.split("\n", -1);

        boolean insideCodeBlock = false;
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            // 检测代码块边界（行首 ``` 开始）
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                insideCodeBlock = !insideCodeBlock;
            }

            // 仅在代码块外识别 ## 区段标题
            if (!insideCodeBlock && line.startsWith("## ") && !line.startsWith("### ")) {
                // 保存前一个区段
                if (currentSection != null) {
                    sections.put(currentSection, currentContent.toString());
                }
                // 开始新区段
                currentSection = line.substring(3).trim();
                currentContent = new StringBuilder();
            } else {
                if (currentSection != null) {
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");
                    }
                    currentContent.append(line);
                }
            }
        }

        // 保存最后一个区段
        if (currentSection != null) {
            sections.put(currentSection, currentContent.toString());
        }

        return sections;
    }

    /**
     * 解析 Description 区段中的 ### 子区段。
     */
    String parseSubSection(String descriptionContent, String subSectionName) {
        if (descriptionContent == null) {
            return null;
        }

        String marker = "### " + subSectionName;
        int startIdx = descriptionContent.indexOf(marker);
        if (startIdx < 0) {
            return null;
        }

        // 跳过标题行
        int contentStart = descriptionContent.indexOf("\n", startIdx);
        if (contentStart < 0) {
            return "";
        }
        contentStart++; // skip the newline

        // 查找下一个 ### 标题或区段结束
        int endIdx = descriptionContent.indexOf("\n### ", contentStart);
        if (endIdx < 0) {
            endIdx = descriptionContent.length();
        }

        return descriptionContent.substring(contentStart, endIdx).trim();
    }

    /**
     * 解析 Description 区段中的 complexity 字段。
     * 支持两种格式：
     * 1. ### Complexity 子区段
     * 2. complexity: <value> 行
     * 未找到时默认返回 MODERATE。
     */
    SkillComplexity parseComplexity(String descriptionContent) {
        if (descriptionContent == null) {
            return SkillComplexity.MODERATE;
        }

        // 优先尝试 ### Complexity 子区段
        String subSectionValue = parseSubSection(descriptionContent, "Complexity");
        if (subSectionValue != null && !subSectionValue.isEmpty()) {
            return SkillComplexity.fromString(subSectionValue.trim());
        }

        // 回退：尝试匹配 complexity: <value> 行
        Matcher matcher = COMPLEXITY_LINE_PATTERN.matcher(descriptionContent);
        if (matcher.find()) {
            return SkillComplexity.fromString(matcher.group(1).trim());
        }

        return SkillComplexity.MODERATE;
    }

    /**
     * 解析列表区段（Tools、Search Keywords）。
     * 按行拆分，去除 "- " 前缀，过滤空行。
     */
    List<String> parseList(String sectionContent) {
        if (sectionContent == null || sectionContent.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> items = new ArrayList<>();
        String[] lines = sectionContent.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("- ")) {
                items.add(trimmed.substring(2).trim());
            } else if (!trimmed.isEmpty()) {
                // 非空行但没有 "- " 前缀，也作为列表项
                items.add(trimmed);
            }
        }
        return items;
    }

    /**
     * 从文件路径提取 Skill 名称（文件名不含 .md 后缀）。
     */
    String extractSkillName(Path filePath) {
        if (filePath == null) {
            return "unknown";
        }
        String fileName = filePath.getFileName().toString();
        if (fileName.endsWith(".md")) {
            return fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }
}
