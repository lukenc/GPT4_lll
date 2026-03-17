package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.memory.TokenUsageInfo;
import com.wmsay.gpt4_lll.fc.memory.UsageTracker;
import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 属性测试: UsageTracker 真实 Token 追踪
 * <p>
 * 验证 UsageTracker 对 OpenAI/Anthropic 格式的解析正确性、
 * 解析失败时返回 null、以及 lastKnownPromptTokens 的更新行为。
 */
class UsageTrackerPropertyTest {

    // ---------------------------------------------------------------
    // Property 4: UsageTracker 解析 OpenAI 格式
    // Validates: Requirements 2A.1, 2A.2, 2A.7
    // ---------------------------------------------------------------

    /**
     * Property 4: UsageTracker 解析 OpenAI 格式
     *
     * For any 包含有效 usage 字段的 OpenAI 格式 JSON 响应，
     * extractUsage() 应返回非 null 的 TokenUsageInfo，
     * 且字段值与 JSON 中的值一致。
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 4: UsageTracker 解析 OpenAI 格式")
    void openAIFormatShouldBeParsedCorrectly(
            @ForAll("positiveInts") int promptTokens,
            @ForAll("nonNegativeInts") int completionTokens) {

        int totalTokens = promptTokens + completionTokens;
        String json = buildOpenAIJson(promptTokens, completionTokens, totalTokens);

        UsageTracker tracker = new UsageTracker();
        TokenUsageInfo info = tracker.extractUsage(json);

        assertNotNull(info, "extractUsage should return non-null for valid OpenAI format");
        assertEquals(promptTokens, info.getPromptTokens());
        assertEquals(completionTokens, info.getCompletionTokens());
        assertEquals(totalTokens, info.getTotalTokens());
    }

    // ---------------------------------------------------------------
    // Property 5: UsageTracker 解析 Anthropic 格式
    // Validates: Requirements 2A.1, 2A.3
    // ---------------------------------------------------------------

    /**
     * Property 5: UsageTracker 解析 Anthropic 格式
     *
     * For any 包含有效 usage 字段的 Anthropic 格式 JSON 响应，
     * extractUsage() 应返回非 null 的 TokenUsageInfo，
     * 且 promptTokens 等于 input_tokens，completionTokens 等于 output_tokens。
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 5: UsageTracker 解析 Anthropic 格式")
    void anthropicFormatShouldBeParsedCorrectly(
            @ForAll("positiveInts") int inputTokens,
            @ForAll("nonNegativeInts") int outputTokens) {

        String json = buildAnthropicJson(inputTokens, outputTokens);

        UsageTracker tracker = new UsageTracker();
        TokenUsageInfo info = tracker.extractUsage(json);

        assertNotNull(info, "extractUsage should return non-null for valid Anthropic format");
        assertEquals(inputTokens, info.getPromptTokens(),
                "promptTokens should equal input_tokens");
        assertEquals(outputTokens, info.getCompletionTokens(),
                "completionTokens should equal output_tokens");
        assertEquals(inputTokens + outputTokens, info.getTotalTokens(),
                "totalTokens should equal input_tokens + output_tokens");
    }

    // ---------------------------------------------------------------
    // Property 6: UsageTracker 解析失败返回 null
    // Validates: Requirements 2A.4
    // ---------------------------------------------------------------

    /**
     * Property 6: UsageTracker 解析失败返回 null
     *
     * For any 不包含 usage 字段的 JSON 字符串、非 JSON 字符串、null 或空字符串，
     * extractUsage() 应返回 null 而非抛出异常。
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 6: UsageTracker 解析失败返回 null")
    void invalidInputShouldReturnNull(
            @ForAll("invalidJsonInputs") String input) {

        UsageTracker tracker = new UsageTracker();
        TokenUsageInfo info = tracker.extractUsage(input);

        assertNull(info, "extractUsage should return null for invalid input: " + input);
    }

    // ---------------------------------------------------------------
    // Property 7: UsageTracker lastKnownPromptTokens 单调更新
    // Validates: Requirements 2A.5, 2A.6, 2A.9
    // ---------------------------------------------------------------

    /**
     * Property 7: UsageTracker lastKnownPromptTokens 单调更新
     *
     * For any 连续多次成功调用 extractUsage() 的序列，
     * getLastKnownPromptTokens() 应始终返回最近一次成功提取的 prompt_tokens 值。
     * 初始状态下返回 -1。
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 7: UsageTracker lastKnownPromptTokens 单调更新")
    void lastKnownPromptTokensShouldTrackLatestValue(
            @ForAll("promptTokenSequences") List<Integer> promptTokensList) {

        UsageTracker tracker = new UsageTracker();

        // 初始状态应为 -1
        assertEquals(-1, tracker.getLastKnownPromptTokens(),
                "Initial lastKnownPromptTokens should be -1");

        int expectedLast = -1;
        for (int pt : promptTokensList) {
            String json = buildOpenAIJson(pt, 10, pt + 10);
            TokenUsageInfo info = tracker.extractUsage(json);
            assertNotNull(info);
            expectedLast = pt;
            assertEquals(expectedLast, tracker.getLastKnownPromptTokens(),
                    "lastKnownPromptTokens should equal the most recent prompt_tokens");
        }

        // 解析失败不应改变 lastKnownPromptTokens
        tracker.extractUsage("not json");
        assertEquals(expectedLast, tracker.getLastKnownPromptTokens(),
                "Failed parse should not change lastKnownPromptTokens");

        // reset 应恢复为 -1
        tracker.reset();
        assertEquals(-1, tracker.getLastKnownPromptTokens(),
                "After reset, lastKnownPromptTokens should be -1");
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<Integer> positiveInts() {
        return Arbitraries.integers().between(1, 500_000);
    }

    @Provide
    Arbitrary<Integer> nonNegativeInts() {
        return Arbitraries.integers().between(0, 500_000);
    }

    @Provide
    Arbitrary<List<Integer>> promptTokenSequences() {
        return Arbitraries.integers().between(1, 500_000)
                .list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<String> invalidJsonInputs() {
        return Arbitraries.oneOf(
                // null handled separately below
                Arbitraries.just(""),
                Arbitraries.just("not json at all"),
                Arbitraries.just("12345"),
                Arbitraries.just("true"),
                Arbitraries.just("[]"),
                // Valid JSON but no usage field
                Arbitraries.just("{\"choices\": []}"),
                Arbitraries.just("{\"id\": \"chatcmpl-123\"}"),
                // usage field is not an object
                Arbitraries.just("{\"usage\": \"string\"}"),
                Arbitraries.just("{\"usage\": 42}"),
                Arbitraries.just("{\"usage\": null}"),
                // usage object with missing required fields
                Arbitraries.just("{\"usage\": {}}"),
                Arbitraries.just("{\"usage\": {\"prompt_tokens\": 10}}"),
                Arbitraries.just("{\"usage\": {\"completion_tokens\": 5}}"),
                // Random strings
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50)
                        .filter(s -> !s.isEmpty())
        );
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private String buildOpenAIJson(int promptTokens, int completionTokens, int totalTokens) {
        return "{\"choices\":[],\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"completion_tokens\":" + completionTokens
                + ",\"total_tokens\":" + totalTokens + "}}";
    }

    private String buildAnthropicJson(int inputTokens, int outputTokens) {
        return "{\"content\":[],\"usage\":{\"input_tokens\":" + inputTokens
                + ",\"output_tokens\":" + outputTokens + "}}";
    }
}
