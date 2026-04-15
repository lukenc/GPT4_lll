package com.wmsay.gpt4_lll.fc.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillParser 单元测试。
 * Validates: Requirements 3.7, 4.5, 4.6
 */
class SkillParserUnitTest {

    private SkillParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new SkillParser();
    }

    private Path writeSkillFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static final String VALID_SKILL = """
            <!-- version: 2.0 -->

            ## System

            你是一个专业的代码审查专家。

            ## Description

            ### Purpose

            对代码进行专业审查。

            ### Trigger

            当用户请求代码审查时触发。

            ## Prompt

            请对以下代码进行审查：

            {{user_input}}

            ## Tools

            - read_file
            - keyword_search

            ## Search Keywords

            - 代码审查
            - code review

            ## Examples

            输入：请审查这段排序代码

            ## Additional Notes

            审查时请特别关注线程安全问题。
            """;

    // ---------------------------------------------------------------
    // 1. Parse a valid .md file with all sections
    // ---------------------------------------------------------------
    @Test
    void parseValidFilePopulatesAllFields() throws IOException {
        Path file = writeSkillFile("code-review.md", VALID_SKILL);

        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill, "Valid skill file should parse successfully");
        assertEquals("code-review", skill.getName());
        assertEquals("你是一个专业的代码审查专家。", skill.getSystemPrompt());
        assertEquals("对代码进行专业审查。", skill.getPurpose());
        assertEquals("当用户请求代码审查时触发。", skill.getTrigger());
        assertTrue(skill.getPromptTemplate().contains("{{user_input}}"));
        assertEquals("2.0", skill.getVersion());
        assertEquals(2, skill.getTools().size());
        assertTrue(skill.getTools().contains("read_file"));
        assertTrue(skill.getTools().contains("keyword_search"));
        assertEquals(2, skill.getSearchKeywords().size());
        assertTrue(skill.getSearchKeywords().contains("代码审查"));
        assertTrue(skill.getSearchKeywords().contains("code review"));
        assertNotNull(skill.getExamples());
        assertNotNull(skill.getAdditionalNotes());
        assertTrue(skill.isHasUserInputPlaceholder());
        assertEquals(file, skill.getFilePath());
        assertEquals(SkillComplexity.MODERATE, skill.getComplexity(),
                "VALID_SKILL without complexity field should default to MODERATE");
    }

    // ---------------------------------------------------------------
    // 2. Code block containing `## ` should NOT be treated as section delimiter
    // ---------------------------------------------------------------
    @Test
    void codeBlockHashDoesNotSplitSection() throws IOException {
        String content = """
                ## System

                你是一个 Markdown 专家。

                ## Description

                ### Purpose

                帮助用户编写 Markdown。

                ### Trigger

                当用户请求 Markdown 帮助时触发。

                ## Prompt

                请参考以下示例：

                ```markdown
                ## 这是代码块内的二级标题
                这不应该被当作区段分隔符。
                ```

                {{user_input}}
                """;

        Path file = writeSkillFile("markdown-help.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill, "Code block ## should not break parsing");
        assertTrue(skill.getPromptTemplate().contains("## 这是代码块内的二级标题"),
                "Code block content with ## should be preserved in Prompt section");
        assertTrue(skill.isHasUserInputPlaceholder());
    }

    // ---------------------------------------------------------------
    // 3. Missing `## System` section → returns null
    // ---------------------------------------------------------------
    @Test
    void missingSystemSectionReturnsNull() throws IOException {
        String content = """
                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("no-system.md", content);
        assertNull(parser.parse(file), "Missing ## System should return null");
    }

    // ---------------------------------------------------------------
    // 4. Missing `## Description` section → returns null
    // ---------------------------------------------------------------
    @Test
    void missingDescriptionSectionReturnsNull() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("no-description.md", content);
        assertNull(parser.parse(file), "Missing ## Description should return null");
    }

    // ---------------------------------------------------------------
    // 5. Missing `## Prompt` section → returns null
    // ---------------------------------------------------------------
    @Test
    void missingPromptSectionReturnsNull() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。
                """;

        Path file = writeSkillFile("no-prompt.md", content);
        assertNull(parser.parse(file), "Missing ## Prompt should return null");
    }

    // ---------------------------------------------------------------
    // 6. Version comment `<!-- version: 2.0 -->` → version field is "2.0"
    // ---------------------------------------------------------------
    @Test
    void versionCommentParsedCorrectly() throws IOException {
        Path file = writeSkillFile("versioned.md", VALID_SKILL);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertEquals("2.0", skill.getVersion());
    }

    // ---------------------------------------------------------------
    // 7. No version comment → version defaults to "1.0"
    // ---------------------------------------------------------------
    @Test
    void noVersionCommentDefaultsToOne() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("no-version.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertEquals("1.0", skill.getVersion());
    }

    // ---------------------------------------------------------------
    // 8. File with `{{user_input}}` in Prompt → hasUserInputPlaceholder is true
    // ---------------------------------------------------------------
    @Test
    void userInputPlaceholderDetectedTrue() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ## Prompt

                请处理：{{user_input}}
                """;

        Path file = writeSkillFile("with-placeholder.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertTrue(skill.isHasUserInputPlaceholder(),
                "Prompt containing {{user_input}} should set hasUserInputPlaceholder to true");
    }

    // ---------------------------------------------------------------
    // 9. File without `{{user_input}}` in Prompt → hasUserInputPlaceholder is false
    // ---------------------------------------------------------------
    @Test
    void userInputPlaceholderDetectedFalse() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ## Prompt

                这是一个没有占位符的提示模板。
                """;

        Path file = writeSkillFile("no-placeholder.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertFalse(skill.isHasUserInputPlaceholder(),
                "Prompt without {{user_input}} should set hasUserInputPlaceholder to false");
    }

    // ---------------------------------------------------------------
    // 10. ### Complexity subsection → complexity parsed correctly
    // ---------------------------------------------------------------
    @Test
    void complexitySubSectionParsedCorrectly() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ### Complexity

                simple

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("simple-skill.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertEquals(SkillComplexity.SIMPLE, skill.getComplexity(),
                "### Complexity subsection with 'simple' should parse to SIMPLE");
    }

    // ---------------------------------------------------------------
    // 11. complexity: <value> line in Description → complexity parsed correctly
    // ---------------------------------------------------------------
    @Test
    void complexityLineInDescriptionParsedCorrectly() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                complexity: complex

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("complex-skill.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertEquals(SkillComplexity.COMPLEX, skill.getComplexity(),
                "complexity: complex line should parse to COMPLEX");
    }

    // ---------------------------------------------------------------
    // 12. No complexity field → defaults to MODERATE
    // ---------------------------------------------------------------
    @Test
    void noComplexityFieldDefaultsToModerate() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("no-complexity.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertEquals(SkillComplexity.MODERATE, skill.getComplexity(),
                "Missing complexity field should default to MODERATE");
    }

    // ---------------------------------------------------------------
    // 13. Complexity field is case-insensitive
    // ---------------------------------------------------------------
    @Test
    void complexityCaseInsensitive() throws IOException {
        String content = """
                ## System

                系统提示词。

                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ### Complexity

                COMPLEX

                ## Prompt

                {{user_input}}
                """;

        Path file = writeSkillFile("uppercase-complexity.md", content);
        SkillDefinition skill = parser.parse(file);

        assertNotNull(skill);
        assertEquals(SkillComplexity.COMPLEX, skill.getComplexity(),
                "Complexity parsing should be case-insensitive");
    }
}
