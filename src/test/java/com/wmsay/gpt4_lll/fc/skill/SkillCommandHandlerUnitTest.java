package com.wmsay.gpt4_lll.fc.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillCommandHandler 单元测试。
 * 验证 /skill list、/skill reload、/skill info 命令处理。
 * Requirements: 13.1, 13.2, 13.3, 13.4
 */
class SkillCommandHandlerUnitTest {

    private SkillRegistry registry;
    private SkillCommandHandler handler;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
        // Use a TestableSkillLoader that doesn't touch the file system
        SkillParser parser = new SkillParser();
        SkillValidator validator = new SkillValidator();
        SkillFileWatcher watcher = new SkillFileWatcher();
        TestableSkillLoader loader = new TestableSkillLoader(registry, parser, validator, watcher);
        handler = new SkillCommandHandler(registry, loader);
    }

    // ── isSkillCommand ──────────────────────────────────────────

    @Test
    void isSkillCommand_withValidCommands() {
        assertTrue(handler.isSkillCommand("/skill list"));
        assertTrue(handler.isSkillCommand("/skill reload"));
        assertTrue(handler.isSkillCommand("/skill info test"));
        assertTrue(handler.isSkillCommand("  /skill list  "));
    }

    @Test
    void isSkillCommand_withInvalidInput() {
        assertFalse(handler.isSkillCommand(null));
        assertFalse(handler.isSkillCommand(""));
        assertFalse(handler.isSkillCommand("hello"));
        assertFalse(handler.isSkillCommand("skill list"));
    }

    // ── /skill list ─────────────────────────────────────────────

    @Test
    void handleList_emptyRegistry() {
        String result = handler.handleCommand("/skill list");
        assertTrue(result.contains("没有已加载的 Skill"));
    }

    @Test
    void handleList_withSkills() {
        registerTestSkill("code-review", "对代码进行专业审查", "1.0");
        registerTestSkill("unit-test", "根据代码自动生成单元测试", "2.0");

        String result = handler.handleCommand("/skill list");

        assertTrue(result.contains("2 个"));
        assertTrue(result.contains("code-review"));
        assertTrue(result.contains("unit-test"));
        assertTrue(result.contains("版本: 1.0"));
        assertTrue(result.contains("版本: 2.0"));
        assertTrue(result.contains("对代码进行专业审查"));
        assertTrue(result.contains("根据代码自动生成单元测试"));
    }

    // ── /skill reload ───────────────────────────────────────────

    @Test
    void handleReload_returnsResultSummary() {
        String result = handler.handleCommand("/skill reload");

        assertTrue(result.contains("重载完成"));
        assertTrue(result.contains("总文件数:"));
        assertTrue(result.contains("成功加载:"));
        assertTrue(result.contains("验证失败:"));
        assertTrue(result.contains("解析错误:"));
    }

    // ── /skill info ─────────────────────────────────────────────

    @Test
    void handleInfo_existingSkill() {
        SkillDefinition skill = SkillDefinition.builder()
                .name("code-review")
                .systemPrompt("You are a code reviewer.")
                .purpose("对代码进行专业审查")
                .trigger("当用户请求代码审查时触发")
                .promptTemplate("请审查: {{user_input}}")
                .version("1.5")
                .searchKeywords(Arrays.asList("代码审查", "code review", "审查"))
                .filePath(Paths.get("/tmp/skill/code-review.md"))
                .lastModified(1704067200000L) // 2024-01-01 00:00:00 UTC
                .hasUserInputPlaceholder(true)
                .build();
        registry.register(skill);

        String result = handler.handleCommand("/skill info code-review");

        assertTrue(result.contains("Skill 详情: code-review"));
        assertTrue(result.contains("版本: 1.5"));
        assertTrue(result.contains("用途: 对代码进行专业审查"));
        assertTrue(result.contains("触发: 当用户请求代码审查时触发"));
        assertTrue(result.contains("关键词: 代码审查, code review, 审查"));
        assertTrue(result.contains("code-review.md"));
    }

    @Test
    void handleInfo_nonExistingSkill() {
        String result = handler.handleCommand("/skill info non-existent");
        assertTrue(result.contains("未找到"));
        assertTrue(result.contains("non-existent"));
    }

    @Test
    void handleInfo_emptyName() {
        String result = handler.handleCommand("/skill info ");
        assertTrue(result.contains("请指定 Skill 名称"));
    }

    // ── help text ───────────────────────────────────────────────

    @Test
    void handleCommand_invalidFormat_showsHelp() {
        String result = handler.handleCommand("/skill");
        assertTrue(result.contains("命令帮助"));
        assertTrue(result.contains("/skill list"));
        assertTrue(result.contains("/skill reload"));
        assertTrue(result.contains("/skill info"));
    }

    @Test
    void handleCommand_unknownSubcommand_showsHelp() {
        String result = handler.handleCommand("/skill unknown");
        assertTrue(result.contains("命令帮助"));
    }

    @Test
    void handleCommand_null_showsHelp() {
        String result = handler.handleCommand(null);
        assertTrue(result.contains("命令帮助"));
    }

    // ── helpers ─────────────────────────────────────────────────

    private void registerTestSkill(String name, String purpose, String version) {
        SkillDefinition skill = SkillDefinition.builder()
                .name(name)
                .systemPrompt("System prompt for " + name)
                .purpose(purpose)
                .trigger("Trigger for " + name)
                .promptTemplate("{{user_input}}")
                .version(version)
                .hasUserInputPlaceholder(true)
                .build();
        registry.register(skill);
    }

    /**
     * Testable SkillLoader that returns a fixed LoadResult without touching the file system.
     */
    private static class TestableSkillLoader extends SkillLoader {
        TestableSkillLoader(SkillRegistry registry, SkillParser parser,
                            SkillValidator validator, SkillFileWatcher watcher) {
            super(registry, parser, validator, watcher);
        }

        @Override
        public LoadResult reload() {
            return new LoadResult(3, 2, 1, 0);
        }
    }
}
