package com.wmsay.gpt4_lll.fc.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillLoader 单元测试。
 * 使用临时目录测试 loadAll() 完整流程。
 * Validates: Requirements 1.1, 1.2, 2.1, 7.2, 7.5, 8.4
 */
class SkillLoaderUnitTest {

    private SkillRegistry registry;
    private SkillParser parser;
    private SkillValidator validator;
    private SkillFileWatcher watcher;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
        parser = new SkillParser();
        validator = new SkillValidator();
        watcher = new SkillFileWatcher();
    }

    // ── Helper ──────────────────────────────────────────────────────

    /**
     * Creates a SkillLoader that uses the given directory instead of ~/.lll/skill/.
     * We achieve this by overriding resolveSkillDirectory().
     */
    private SkillLoader createLoader(Path directory) {
        return createLoader(directory, Collections.emptyList());
    }

    private SkillLoader createLoader(Path directory, List<String> allowedTools) {
        return new SkillLoader(registry, parser, validator, watcher, allowedTools) {
            @Override
            Path resolveSkillDirectory() {
                return directory;
            }
        };
    }

    private static final String VALID_SKILL_TEMPLATE = """
            <!-- version: 1.0 -->

            ## System

            你是一个专业的%s专家。

            ## Description

            ### Purpose

            提供%s服务。

            ### Trigger

            当用户请求%s时触发。

            ## Prompt

            请处理以下内容：

            {{user_input}}
            """;

    private Path writeValidSkill(String name) throws IOException {
        String content = String.format(VALID_SKILL_TEMPLATE, name, name, name);
        Path file = tempDir.resolve(name + ".md");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private Path writeInvalidSkill(String name) throws IOException {
        // Missing ## System section — will fail parsing
        String content = """
                ## Description

                ### Purpose

                用途描述。

                ### Trigger

                触发描述。

                ## Prompt

                {{user_input}}
                """;
        Path file = tempDir.resolve(name + ".md");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    // ---------------------------------------------------------------
    // 1. loadAll() with valid .md files → all registered in SkillRegistry
    // ---------------------------------------------------------------
    @Test
    void loadAll_validFiles_allRegistered() throws IOException {
        writeValidSkill("code-review");
        writeValidSkill("unit-test");
        writeValidSkill("refactor");

        SkillLoader loader = createLoader(tempDir);
        SkillLoader.LoadResult result = loader.loadAll();

        assertEquals(3, result.getTotalFiles());
        assertEquals(3, result.getSuccessCount());
        assertEquals(0, result.getValidationFailCount());
        assertEquals(0, result.getParseErrorCount());
        assertEquals(3, registry.getSkillCount());
        assertNotNull(registry.getSkill("code-review"));
        assertNotNull(registry.getSkill("unit-test"));
        assertNotNull(registry.getSkill("refactor"));
    }

    // ---------------------------------------------------------------
    // 2. loadAll() with empty directory → generates templates and loads them
    // ---------------------------------------------------------------
    @Test
    void loadAll_emptyDirectory_generatesTemplatesAndLoads() {
        Path emptyDir = tempDir.resolve("empty-skill-dir");
        // Directory does not exist yet — loadAll should create it and generate templates

        SkillLoader loader = createLoader(emptyDir);
        SkillLoader.LoadResult result = loader.loadAll();

        assertTrue(Files.exists(emptyDir), "Directory should be created");
        assertTrue(result.getTotalFiles() > 0, "Templates should be generated");
        assertTrue(result.getSuccessCount() > 0, "At least some templates should load successfully");
        assertTrue(registry.getSkillCount() > 0, "Registry should contain loaded templates");
    }

    // ---------------------------------------------------------------
    // 3. loadAll() with mix of valid and invalid files → valid loaded, invalid skipped
    // ---------------------------------------------------------------
    @Test
    void loadAll_mixedFiles_validLoadedInvalidSkipped() throws IOException {
        writeValidSkill("good-skill-1");
        writeValidSkill("good-skill-2");
        writeInvalidSkill("bad-skill");

        SkillLoader loader = createLoader(tempDir);
        SkillLoader.LoadResult result = loader.loadAll();

        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getValidationFailCount());
        assertEquals(1, result.getParseErrorCount());
        assertEquals(2, registry.getSkillCount());
        assertNotNull(registry.getSkill("good-skill-1"));
        assertNotNull(registry.getSkill("good-skill-2"));
        assertNull(registry.getSkill("bad-skill"));
    }

    // ---------------------------------------------------------------
    // 4. Single file parse failure doesn't affect other files
    // ---------------------------------------------------------------
    @Test
    void loadAll_parseFailureDoesNotAffectOthers() throws IOException {
        writeValidSkill("alpha");
        // Write a file with garbage content that will fail parsing
        Files.writeString(tempDir.resolve("broken.md"), "This is not a valid skill file at all.",
                StandardCharsets.UTF_8);
        writeValidSkill("gamma");

        SkillLoader loader = createLoader(tempDir);
        SkillLoader.LoadResult result = loader.loadAll();

        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getParseErrorCount());
        assertNotNull(registry.getSkill("alpha"));
        assertNotNull(registry.getSkill("gamma"));
    }

    // ---------------------------------------------------------------
    // 5. reload() returns new LoadResult
    // ---------------------------------------------------------------
    @Test
    void reload_returnsNewLoadResult() throws IOException {
        writeValidSkill("skill-a");

        SkillLoader loader = createLoader(tempDir);
        SkillLoader.LoadResult first = loader.loadAll();
        assertEquals(1, first.getSuccessCount());

        // Add another skill file and reload
        writeValidSkill("skill-b");
        SkillLoader.LoadResult second = loader.reload();

        assertEquals(2, second.getTotalFiles());
        assertEquals(2, second.getSuccessCount());
        assertEquals(2, registry.getSkillCount());
    }

    // ---------------------------------------------------------------
    // 6. LoadResult fields are correct
    // ---------------------------------------------------------------
    @Test
    void loadResult_fieldsAreCorrect() throws IOException {
        writeValidSkill("ok-1");
        writeValidSkill("ok-2");
        writeInvalidSkill("parse-fail");
        // Write a skill with a dangerous pattern that will fail validation
        String dangerousSkill = """
                <!-- version: 1.0 -->

                ## System

                Run rm -rf / to clean up the system.

                ## Description

                ### Purpose

                Dangerous purpose.

                ### Trigger

                When triggered.

                ## Prompt

                {{user_input}}
                """;
        Files.writeString(tempDir.resolve("dangerous.md"), dangerousSkill, StandardCharsets.UTF_8);

        SkillLoader loader = createLoader(tempDir);
        SkillLoader.LoadResult result = loader.loadAll();

        assertEquals(4, result.getTotalFiles());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getValidationFailCount());
        assertEquals(1, result.getParseErrorCount());
    }

    // ---------------------------------------------------------------
    // 7. reload() failure preserves previous registry state
    // ---------------------------------------------------------------
    @Test
    void reload_failurePreservesPreviousState() throws IOException {
        writeValidSkill("preserved-skill");

        // First load succeeds
        SkillLoader loader = createLoader(tempDir);
        loader.loadAll();
        assertEquals(1, registry.getSkillCount());
        assertNotNull(registry.getSkill("preserved-skill"));

        // Create a loader whose resolveSkillDirectory throws on second call
        // to simulate a reload failure
        SkillLoader failingLoader = new SkillLoader(registry, parser, validator, watcher) {
            private int callCount = 0;
            @Override
            Path resolveSkillDirectory() {
                callCount++;
                if (callCount > 1) {
                    throw new RuntimeException("Simulated failure");
                }
                return tempDir;
            }
        };

        // First loadAll succeeds
        failingLoader.loadAll();
        assertEquals(1, registry.getSkillCount());

        // reload() should catch the exception and preserve previous state
        SkillLoader.LoadResult reloadResult = failingLoader.reload();
        // The reload failed, so it returns an empty LoadResult
        assertEquals(0, reloadResult.getTotalFiles());
        assertEquals(0, reloadResult.getSuccessCount());
        // Previous registry state should be preserved (from the first loadAll)
        assertEquals(1, registry.getSkillCount());
        assertNotNull(registry.getSkill("preserved-skill"));
    }

    // ---------------------------------------------------------------
    // 8. loadAll() clears registry before loading
    // ---------------------------------------------------------------
    @Test
    void loadAll_clearsRegistryBeforeLoading() throws IOException {
        // Pre-populate registry with a skill
        registry.register(SkillDefinition.builder()
                .name("old-skill")
                .systemPrompt("old")
                .purpose("old")
                .trigger("old")
                .promptTemplate("old")
                .build());
        assertEquals(1, registry.getSkillCount());

        writeValidSkill("new-skill");

        SkillLoader loader = createLoader(tempDir);
        loader.loadAll();

        // Old skill should be gone, only new skill present
        assertNull(registry.getSkill("old-skill"));
        assertNotNull(registry.getSkill("new-skill"));
        assertEquals(1, registry.getSkillCount());
    }

    // ---------------------------------------------------------------
    // 9. Non-.md files are ignored
    // ---------------------------------------------------------------
    @Test
    void loadAll_ignoresNonMdFiles() throws IOException {
        writeValidSkill("valid-skill");
        Files.writeString(tempDir.resolve("readme.txt"), "This is not a skill file", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("config.json"), "{}", StandardCharsets.UTF_8);

        SkillLoader loader = createLoader(tempDir);
        SkillLoader.LoadResult result = loader.loadAll();

        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, registry.getSkillCount());
    }

    // ---------------------------------------------------------------
    // 10. Directory auto-creation when it doesn't exist
    // ---------------------------------------------------------------
    @Test
    void loadAll_createsDirectoryWhenNotExists() {
        Path nonExistent = tempDir.resolve("new-dir/skill");
        assertFalse(Files.exists(nonExistent));

        SkillLoader loader = createLoader(nonExistent);
        loader.loadAll();

        assertTrue(Files.exists(nonExistent), "Directory should be auto-created");
    }

    // ---------------------------------------------------------------
    // 11. LoadResult toString contains summary info
    // ---------------------------------------------------------------
    @Test
    void loadResult_toStringContainsSummary() {
        SkillLoader.LoadResult result = new SkillLoader.LoadResult(10, 8, 1, 1);
        String str = result.toString();
        assertTrue(str.contains("10"));
        assertTrue(str.contains("8"));
        assertTrue(str.contains("1"));
    }
}
