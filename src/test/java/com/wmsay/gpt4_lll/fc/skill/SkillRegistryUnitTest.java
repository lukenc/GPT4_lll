package com.wmsay.gpt4_lll.fc.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillRegistry 单元测试。
 * 验证 register/unregister/getSkill/getAllSkills/getSkillCount/clear 行为。
 */
class SkillRegistryUnitTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
    }

    private SkillDefinition createSkill(String name) {
        return SkillDefinition.builder()
                .name(name)
                .systemPrompt("system prompt for " + name)
                .purpose("purpose for " + name)
                .trigger("trigger for " + name)
                .promptTemplate("template {{user_input}}")
                .hasUserInputPlaceholder(true)
                .filePath(Path.of("/tmp/" + name + ".md"))
                .build();
    }

    @Test
    void registerAndGetSkill() {
        SkillDefinition skill = createSkill("test-skill");
        registry.register(skill);

        assertSame(skill, registry.getSkill("test-skill"));
        assertEquals(1, registry.getSkillCount());
    }

    @Test
    void registerDuplicateReplacesOld() {
        SkillDefinition v1 = SkillDefinition.builder()
                .name("dup")
                .systemPrompt("v1")
                .purpose("p")
                .trigger("t")
                .promptTemplate("tpl")
                .version("1.0")
                .filePath(Path.of("/tmp/dup.md"))
                .build();
        SkillDefinition v2 = SkillDefinition.builder()
                .name("dup")
                .systemPrompt("v2")
                .purpose("p")
                .trigger("t")
                .promptTemplate("tpl")
                .version("2.0")
                .filePath(Path.of("/tmp/dup.md"))
                .build();

        registry.register(v1);
        registry.register(v2);

        assertSame(v2, registry.getSkill("dup"));
        assertEquals(1, registry.getSkillCount());
    }

    @Test
    void unregisterRemovesSkill() {
        registry.register(createSkill("to-remove"));
        assertEquals(1, registry.getSkillCount());

        registry.unregister("to-remove");
        assertNull(registry.getSkill("to-remove"));
        assertEquals(0, registry.getSkillCount());
    }

    @Test
    void unregisterNonExistentIsNoOp() {
        registry.unregister("nonexistent");
        assertEquals(0, registry.getSkillCount());
    }

    @Test
    void getSkillReturnsNullForUnknown() {
        assertNull(registry.getSkill("unknown"));
    }

    @Test
    void getSkillWithNullReturnsNull() {
        assertNull(registry.getSkill(null));
    }

    @Test
    void getAllSkillsReturnsImmutableList() {
        registry.register(createSkill("a"));
        registry.register(createSkill("b"));

        List<SkillDefinition> all = registry.getAllSkills();
        assertEquals(2, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(createSkill("c")));
    }

    @Test
    void clearRemovesAll() {
        registry.register(createSkill("x"));
        registry.register(createSkill("y"));
        assertEquals(2, registry.getSkillCount());

        registry.clear();
        assertEquals(0, registry.getSkillCount());
        assertNull(registry.getSkill("x"));
        assertNull(registry.getSkill("y"));
    }

    @Test
    void registerNullIsIgnored() {
        registry.register(null);
        assertEquals(0, registry.getSkillCount());
    }

    @Test
    void unregisterNullIsIgnored() {
        registry.unregister(null);
        assertEquals(0, registry.getSkillCount());
    }
}
