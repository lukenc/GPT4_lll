package com.wmsay.gpt4_lll.fc.skill;

import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillMatcher 单元测试。
 * 使用 lambda LlmCaller 替代 mock 框架。
 * Validates: Requirements 9.5, 9.7, 9.8, 9.9
 */
class SkillMatcherUnitTest {

    private SkillMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new SkillMatcher();
    }

    private SkillDefinition createSkill(String name) {
        return createSkill(name, List.of());
    }

    private SkillDefinition createSkill(String name, List<String> keywords) {
        return SkillDefinition.builder()
                .name(name)
                .systemPrompt("system prompt for " + name)
                .purpose("purpose for " + name)
                .trigger("trigger for " + name)
                .promptTemplate("template {{user_input}}")
                .searchKeywords(keywords)
                .hasUserInputPlaceholder(true)
                .filePath(Path.of("/tmp/" + name + ".md"))
                .build();
    }

    // --- Requirement 9.9: empty/null skill list returns unmatched without LLM call ---

    @Test
    void emptySkillList_returnsUnmatched_withoutCallingLlm() {
        AtomicBoolean llmCalled = new AtomicBoolean(false);
        LlmCaller llm = req -> { llmCalled.set(true); return ""; };

        SkillMatchResult result = matcher.match("hello", Collections.emptyList(), llm, "gpt-4");

        assertFalse(result.isMatched());
        assertFalse(llmCalled.get(), "LLM should not be called when skill list is empty");
    }

    @Test
    void nullSkillList_returnsUnmatched_withoutCallingLlm() {
        AtomicBoolean llmCalled = new AtomicBoolean(false);
        LlmCaller llm = req -> { llmCalled.set(true); return ""; };

        SkillMatchResult result = matcher.match("hello", null, llm, "gpt-4");

        assertFalse(result.isMatched());
        assertFalse(llmCalled.get(), "LLM should not be called when skill list is null");
    }

    // --- Requirement 9.5: confidence < 0.7 returns unmatched ---

    @Test
    void llmReturnsHighConfidence_returnsMatchedResult() {
        LlmCaller llm = req -> "{\"intent\":\"code-review\",\"confidence\":0.9,\"reasoning\":\"matches\"}";

        List<SkillDefinition> skills = List.of(createSkill("code-review"));
        SkillMatchResult result = matcher.match("review my code", skills, llm, "gpt-4");

        assertTrue(result.isMatched());
        assertEquals("code-review", result.getSkillName());
        assertEquals(0.9, result.getConfidence(), 0.001);
    }

    @Test
    void llmReturnsLowConfidence_returnsUnmatched() {
        LlmCaller llm = req -> "{\"intent\":\"code-review\",\"confidence\":0.5,\"reasoning\":\"not sure\"}";

        List<SkillDefinition> skills = List.of(createSkill("code-review"));
        SkillMatchResult result = matcher.match("hello world", skills, llm, "gpt-4");

        assertFalse(result.isMatched());
    }

    @Test
    void llmReturnsIntentNone_returnsUnmatched() {
        LlmCaller llm = req -> "{\"intent\":\"none\",\"confidence\":0.9,\"reasoning\":\"no match\"}";

        List<SkillDefinition> skills = List.of(createSkill("code-review"));
        SkillMatchResult result = matcher.match("what is the weather", skills, llm, "gpt-4");

        assertFalse(result.isMatched());
    }

    // --- Requirement 9.7: LLM failure returns unmatched ---

    @Test
    void llmReturnsInvalidJson_returnsUnmatched() {
        LlmCaller llm = req -> "this is not json at all!!!";

        List<SkillDefinition> skills = List.of(createSkill("code-review"));
        SkillMatchResult result = matcher.match("review code", skills, llm, "gpt-4");

        assertFalse(result.isMatched());
    }

    @Test
    void llmThrowsException_returnsUnmatched() {
        LlmCaller llm = req -> { throw new RuntimeException("LLM timeout"); };

        List<SkillDefinition> skills = List.of(createSkill("code-review"));
        SkillMatchResult result = matcher.match("review code", skills, llm, "gpt-4");

        assertFalse(result.isMatched());
    }

    // --- Requirement 9.8: pre-filter reduces candidates when > 20 skills ---

    @Test
    void preFilter_reducesCandidates_whenMoreThan20Skills() {
        // Create 25 skills, some with matching keywords
        List<SkillDefinition> skills = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            List<String> keywords = (i < 3)
                    ? List.of("review", "code")
                    : List.of("unrelated-keyword-" + i);
            skills.add(createSkill("skill-" + i, keywords));
        }

        // Capture the request sent to LLM to verify candidate count
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>();
        LlmCaller llm = req -> {
            // Extract system prompt from the request to count candidates
            if (req.getChatContent() != null
                    && req.getChatContent().getMessages() != null
                    && !req.getChatContent().getMessages().isEmpty()) {
                capturedSystemPrompt.set(req.getChatContent().getMessages().get(0).getContent());
            }
            return "{\"intent\":\"none\",\"confidence\":0.1,\"reasoning\":\"no match\"}";
        };

        matcher.match("review my code", skills, llm, "gpt-4");

        // Verify LLM was called (system prompt captured)
        assertNotNull(capturedSystemPrompt.get(), "LLM should have been called");

        // The pre-filter should reduce 25 skills to at most 10
        // Count numbered skill entries in the system prompt (format: "N. name: skill-X")
        String prompt = capturedSystemPrompt.get();
        int candidateCount = 0;
        for (String line : prompt.split("\n")) {
            if (line.matches("^\\d+\\. name: .*")) {
                candidateCount++;
            }
        }
        assertTrue(candidateCount <= 10,
                "Pre-filter should reduce candidates to <= 10, but got " + candidateCount);
    }

    @Test
    void noPreFilter_whenSkillCountAtOrBelow20() {
        // Create exactly 20 skills
        List<SkillDefinition> skills = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            skills.add(createSkill("skill-" + i));
        }

        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>();
        LlmCaller llm = req -> {
            if (req.getChatContent() != null
                    && req.getChatContent().getMessages() != null
                    && !req.getChatContent().getMessages().isEmpty()) {
                capturedSystemPrompt.set(req.getChatContent().getMessages().get(0).getContent());
            }
            return "{\"intent\":\"none\",\"confidence\":0.1,\"reasoning\":\"no match\"}";
        };

        matcher.match("hello", skills, llm, "gpt-4");

        assertNotNull(capturedSystemPrompt.get());
        // All 20 skills should be passed through without filtering
        String prompt = capturedSystemPrompt.get();
        int candidateCount = 0;
        for (String line : prompt.split("\n")) {
            if (line.matches("^\\d+\\. name: .*")) {
                candidateCount++;
            }
        }
        assertEquals(20, candidateCount,
                "All 20 skills should be passed to LLM without pre-filtering");
    }
}
