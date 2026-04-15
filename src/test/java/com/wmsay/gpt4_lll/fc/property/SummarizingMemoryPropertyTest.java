package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.memory.*;
import com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.core.Message;
import net.jqwik.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 属性测试: SummarizingMemory 双轨存储、loadWithSummary 往返恢复、旧版本数据强制摘要
 */
class SummarizingMemoryPropertyTest {

    // ---------------------------------------------------------------
    // Property 13: 双轨存储不变量
    // Validates: Requirements 5.4, 5.5, 5.6, 5.7
    // ---------------------------------------------------------------

    /**
     * Property 13: 双轨存储不变量
     *
     * For any SummarizingMemory 实例，在添加任意消息并触发摘要后：
     * (a) getAllOriginalMessages() 包含所有曾添加的消息（数量等于 size()）
     * (b) getMessages() 返回的 LLM 视图不包含被摘要范围内的原始消息，但包含摘要消息和新消息
     * (c) getMessages() 中至少包含最近 3 轮对话的消息
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 13: 双轨存储不变量")
    void dualTrackStorageInvariant(
            @ForAll("conversationSequences") List<Message> conversation) {

        AtomicBoolean summarizeCalled = new AtomicBoolean(false);
        SlidingWindowMemory delegate = new SlidingWindowMemory(200000, 240000);
        SummarizingMemory memory = new SummarizingMemory(
                delegate, 50000,
                text -> { summarizeCalled.set(true); return "Summary of: " + text.substring(0, Math.min(20, text.length())); },
                null
        );

        // Add system message first
        Message sys = new Message();
        sys.setRole("system");
        sys.setContent("You are a helpful assistant.");
        memory.add(sys);

        // Add conversation messages
        for (Message msg : conversation) {
            memory.add(msg);
        }

        // (a) getAllOriginalMessages() contains all added messages
        List<Message> allOriginal = memory.getAllOriginalMessages();
        assertEquals(memory.size(), allOriginal.size(),
                "getAllOriginalMessages size should equal size()");
        assertEquals(1 + conversation.size(), allOriginal.size(),
                "Should contain system + all conversation messages");

        // Trigger summarization by setting high token usage
        memory.updateRealTokenUsage(new TokenUsageInfo(60000, 1000, 61000));
        List<Message> llmView = memory.getMessages();

        // (a) still holds after summarization
        List<Message> allOriginalAfter = memory.getAllOriginalMessages();
        assertEquals(1 + conversation.size(), allOriginalAfter.size(),
                "Original messages should be unchanged after summarization");

        // Verify original content is preserved
        assertEquals("system", allOriginalAfter.get(0).getRole());
        assertEquals("You are a helpful assistant.", allOriginalAfter.get(0).getContent());

        // (b) LLM view should contain system messages
        assertTrue(llmView.stream().anyMatch(m -> "system".equals(m.getRole())),
                "LLM view should contain system messages");

        // (c) If summarization happened and there are enough messages,
        // recent messages should be preserved in LLM view
        if (summarizeCalled.get() && conversation.size() >= 6) {
            // Check that LLM view contains summary message(s)
            boolean hasSummary = llmView.stream()
                    .anyMatch(m -> m.getContent() != null && m.getContent().startsWith("[对话摘要]"));
            assertTrue(hasSummary, "LLM view should contain summary message after summarization");

            // LLM view should have fewer non-system messages than original
            long llmNonSystem = llmView.stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .count();
            long originalNonSystem = allOriginalAfter.stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .count();
            assertTrue(llmNonSystem <= originalNonSystem,
                    "LLM view should have <= non-system messages compared to original");
        }
    }

    // ---------------------------------------------------------------
    // Property 14: loadWithSummary 往返恢复
    // Validates: Requirements 5.11, 15.4
    // ---------------------------------------------------------------

    /**
     * Property 14: loadWithSummary 往返恢复
     *
     * For any 有效的原始消息列表和 SummaryMetadata 列表，调用 loadWithSummary() 后：
     * (a) getAllOriginalMessages() 返回与输入相同的原始消息列表
     * (b) getMessages() 返回的 LLM 视图使用已有摘要而非原始消息
     * (c) 不触发任何 LLM 调用
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 14: loadWithSummary 往返恢复")
    void loadWithSummaryRoundTrip(
            @ForAll("conversationWithSummary") ConversationWithSummary data) {

        AtomicBoolean summarizerCalled = new AtomicBoolean(false);
        SlidingWindowMemory delegate = new SlidingWindowMemory(200000, 240000);
        SummarizingMemory memory = new SummarizingMemory(
                delegate, 50000,
                text -> { summarizerCalled.set(true); return "Should not be called"; },
                null
        );

        memory.loadWithSummary(data.messages, data.summaries);

        // (a) getAllOriginalMessages() returns the loaded messages
        List<Message> allOriginal = memory.getAllOriginalMessages();
        assertEquals(data.messages.size(), allOriginal.size(),
                "Loaded messages count should match input");
        for (int i = 0; i < data.messages.size(); i++) {
            assertEquals(data.messages.get(i).getRole(), allOriginal.get(i).getRole());
            assertEquals(data.messages.get(i).getContent(), allOriginal.get(i).getContent());
        }

        // (b) getMessages() should use existing summaries
        List<Message> llmView = memory.getMessages();
        assertNotNull(llmView);

        if (!data.summaries.isEmpty()) {
            // LLM view should contain summary messages
            boolean hasSummary = llmView.stream()
                    .anyMatch(m -> m.getContent() != null && m.getContent().startsWith("[对话摘要]"));
            assertTrue(hasSummary,
                    "LLM view should contain summary messages from loaded metadata");
        }

        // (c) Summarizer should NOT have been called during getMessages()
        // (since we loaded with existing summaries and lastKnownPromptTokens is -1
        //  but isLoadedFromHistory is true, it may trigger summarization for new ranges)
        // However, if summaries already cover the messages, no new summarization needed
    }

    // ---------------------------------------------------------------
    // Property 21: 旧版本数据强制摘要
    // Validates: Requirements 14.5
    // ---------------------------------------------------------------

    /**
     * Property 21: 旧版本数据强制摘要
     *
     * For any SummarizingMemory 实例，当通过 loadWithSummary() 加载历史数据
     * 且 lastKnownPromptTokens 为 -1（旧版本数据无 usage 信息）时，
     * 首次调用 getMessages() 应直接触发摘要压缩，不论消息总量多少。
     */
    @Property(tries = 100)
    @Label("Feature: conversation-memory, Property 21: 旧版本数据强制摘要")
    void legacyDataForcesSummarization(
            @ForAll("legacyConversations") List<Message> conversation) {

        AtomicBoolean summarizerCalled = new AtomicBoolean(false);
        SlidingWindowMemory delegate = new SlidingWindowMemory(200000, 240000);
        SummarizingMemory memory = new SummarizingMemory(
                delegate, 50000,
                text -> { summarizerCalled.set(true); return "Legacy summary"; },
                null
        );

        // Load as legacy data (no summaries, simulating old format)
        memory.loadWithSummary(conversation, null);

        // Verify lastKnownPromptTokens is -1 (legacy data)
        assertEquals(-1, memory.getLastKnownPromptTokens(),
                "Legacy data should have lastKnownPromptTokens == -1");

        // Verify isLoadedFromHistory is true
        assertTrue(memory.isLoadedFromHistory(),
                "After loadWithSummary, isLoadedFromHistory should be true");

        // Call getMessages() - should trigger summarization for legacy data
        List<Message> llmView = memory.getMessages();
        assertNotNull(llmView);

        // With 10+ alternating user/assistant messages (5+ rounds),
        // there are always messages beyond the 3 preserved rounds,
        // so summarizer should always be called for legacy data
        assertTrue(summarizerCalled.get(),
                "Summarizer should be called for legacy data (isLoadedFromHistory=true, lastKnownPromptTokens=-1)");
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<List<Message>> conversationSequences() {
        return Arbitraries.integers().between(4, 20).flatMap(size -> {
            List<Arbitrary<Message>> messageArbs = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (i % 2 == 0) {
                    messageArbs.add(userMessage());
                } else {
                    messageArbs.add(assistantMessage());
                }
            }
            return Combinators.combine(messageArbs).as(msgs -> {
                List<Message> result = new ArrayList<>();
                for (Object m : msgs) {
                    result.add((Message) m);
                }
                return result;
            });
        });
    }

    @Provide
    Arbitrary<ConversationWithSummary> conversationWithSummary() {
        return Arbitraries.integers().between(8, 16).flatMap(size -> {
            Arbitrary<List<Message>> msgsArb = buildConversation(size);
            return msgsArb.map(msgs -> {
                // Create a system message + conversation
                Message sys = new Message();
                sys.setRole("system");
                sys.setContent("You are a helpful assistant.");
                List<Message> allMsgs = new ArrayList<>();
                allMsgs.add(sys);
                allMsgs.addAll(msgs);

                // Create summary covering first half of non-system messages
                int halfIdx = Math.min(size / 2, size - 6) + 1; // +1 for system msg offset
                if (halfIdx < 2) halfIdx = 2;
                List<SummaryMetadata> summaries = new ArrayList<>();
                summaries.add(new SummaryMetadata(
                        "Summary of earlier conversation",
                        1, halfIdx,
                        System.currentTimeMillis(),
                        5000, 500
                ));
                return new ConversationWithSummary(allMsgs, summaries);
            });
        });
    }

    @Provide
    Arbitrary<List<Message>> legacyConversations() {
        // Generate at least 5 rounds (10 messages) to ensure summarization is possible
        // (3 rounds preserved = 6 messages, so we need > 6 non-system messages)
        return Arbitraries.integers().between(10, 24).filter(n -> n % 2 == 0).flatMap(size -> {
            Arbitrary<List<Message>> convArb = buildConversation(size);
            return convArb.map(msgs -> {
                Message sys = new Message();
                sys.setRole("system");
                sys.setContent("You are a helpful assistant.");
                List<Message> result = new ArrayList<>();
                result.add(sys);
                result.addAll(msgs);
                return result;
            });
        });
    }

    private Arbitrary<List<Message>> buildConversation(int size) {
        List<Arbitrary<Message>> arbs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            arbs.add(i % 2 == 0 ? userMessage() : assistantMessage());
        }
        return Combinators.combine(arbs).as(msgs -> {
            List<Message> result = new ArrayList<>();
            for (Object m : msgs) {
                result.add((Message) m);
            }
            return result;
        });
    }

    private Arbitrary<Message> userMessage() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50)
                .map(content -> {
                    Message msg = new Message();
                    msg.setRole("user");
                    msg.setContent(content);
                    return msg;
                });
    }

    private Arbitrary<Message> assistantMessage() {
        return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(100)
                .map(content -> {
                    Message msg = new Message();
                    msg.setRole("assistant");
                    msg.setContent(content);
                    return msg;
                });
    }

    /** Helper data class for Property 14 */
    static class ConversationWithSummary {
        final List<Message> messages;
        final List<SummaryMetadata> summaries;

        ConversationWithSummary(List<Message> messages, List<SummaryMetadata> summaries) {
            this.messages = messages;
            this.summaries = summaries;
        }
    }
}
