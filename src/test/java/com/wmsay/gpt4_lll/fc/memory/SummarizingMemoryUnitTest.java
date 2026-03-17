package com.wmsay.gpt4_lll.fc.memory;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SummarizingMemory 单元测试。
 * 覆盖需求: 5.3, 5.8, 7.1, 7.3, 7.5, 14.5
 */
class SummarizingMemoryUnitTest {

    private SlidingWindowMemory delegate;
    private AtomicBoolean summarizerCalled;
    private AtomicReference<String> summarizerInput;
    private TestProgressCallback callback;

    @BeforeEach
    void setUp() {
        delegate = new SlidingWindowMemory(200000, 240000);
        summarizerCalled = new AtomicBoolean(false);
        summarizerInput = new AtomicReference<>();
        callback = new TestProgressCallback();
    }

    private SummarizingMemory createMemory(int tokenThreshold) {
        return new SummarizingMemory(delegate, tokenThreshold,
                text -> {
                    summarizerCalled.set(true);
                    summarizerInput.set(text);
                    return "Summary result";
                }, callback);
    }

    private List<Message> buildConversation(int rounds) {
        List<Message> msgs = new ArrayList<>();
        Message sys = new Message();
        sys.setRole("system");
        sys.setContent("You are a helpful assistant.");
        msgs.add(sys);
        for (int i = 0; i < rounds; i++) {
            Message user = new Message();
            user.setRole("user");
            user.setContent("User message " + i);
            msgs.add(user);
            Message assistant = new Message();
            assistant.setRole("assistant");
            assistant.setContent("Assistant response " + i);
            msgs.add(assistant);
        }
        return msgs;
    }

    // --- 需求 5.3: 摘要触发条件（真实 prompt_tokens 超过阈值时触发）---

    @Test
    void shouldTriggerSummarizationWhenTokensExceedThreshold() {
        SummarizingMemory memory = createMemory(1000);
        List<Message> conversation = buildConversation(5); // 1 system + 10 user/assistant
        for (Message msg : conversation) {
            memory.add(msg);
        }

        // Set token usage above threshold
        memory.updateRealTokenUsage(new TokenUsageInfo(2000, 500, 2500));
        memory.getMessages();

        assertTrue(summarizerCalled.get(),
                "Summarizer should be called when prompt_tokens exceeds threshold");
    }

    @Test
    void shouldNotTriggerSummarizationWhenTokensBelowThreshold() {
        SummarizingMemory memory = createMemory(5000);
        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        // Set token usage below threshold
        memory.updateRealTokenUsage(new TokenUsageInfo(3000, 500, 3500));
        memory.getMessages();

        assertFalse(summarizerCalled.get(),
                "Summarizer should NOT be called when prompt_tokens is below threshold");
    }

    // --- 需求 14.5: 旧版本数据直接触发摘要 ---

    @Test
    void shouldTriggerSummarizationForLegacyDataWithoutUsage() {
        SummarizingMemory memory = createMemory(50000);
        List<Message> conversation = buildConversation(6); // enough for summarization

        // Load as legacy data (no summaries)
        memory.loadWithSummary(conversation, null);

        assertEquals(-1, memory.getLastKnownPromptTokens());
        assertTrue(memory.isLoadedFromHistory());

        memory.getMessages();

        assertTrue(summarizerCalled.get(),
                "Legacy data (lastKnownPromptTokens=-1, isLoadedFromHistory=true) should trigger summarization");
    }

    // --- 全新对话跳过预检查 ---

    @Test
    void shouldSkipPreCheckForNewConversation() {
        SummarizingMemory memory = createMemory(50000);
        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        // lastKnownPromptTokens is -1, isLoadedFromHistory is false → skip
        assertEquals(-1, memory.getLastKnownPromptTokens());
        memory.getMessages();

        assertFalse(summarizerCalled.get(),
                "New conversation (not loaded from history) should skip summarization");
    }

    // --- 需求 5.8: summarizer 调用失败时的回退行为 ---

    @Test
    void shouldFallbackWhenSummarizerFails() {
        SummarizingMemory memory = new SummarizingMemory(delegate, 1000,
                text -> { throw new RuntimeException("LLM call failed"); },
                callback);

        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        memory.updateRealTokenUsage(new TokenUsageInfo(2000, 500, 2500));
        List<Message> result = memory.getMessages();

        // Should still return messages (fallback to delegate behavior)
        assertNotNull(result);
        assertFalse(result.isEmpty(), "Should return messages even when summarizer fails");

        // ProgressCallback should have been notified of failure
        assertTrue(callback.failedCalled, "onMemorySummarizingFailed should be called");
        assertNotNull(callback.failReason);
    }

    @Test
    void shouldFallbackWhenSummarizerReturnsNull() {
        SummarizingMemory memory = new SummarizingMemory(delegate, 1000,
                text -> null, callback);

        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        memory.updateRealTokenUsage(new TokenUsageInfo(2000, 500, 2500));
        List<Message> result = memory.getMessages();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(callback.failedCalled, "Should notify failure when summarizer returns null");
    }

    // --- 需求 7.1, 7.3, 7.5: ProgressCallback 通知 ---

    @Test
    void shouldNotifyProgressCallbackOnSummarizationStart() {
        SummarizingMemory memory = createMemory(1000);
        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        memory.updateRealTokenUsage(new TokenUsageInfo(2000, 500, 2500));
        memory.getMessages();

        assertTrue(callback.startedCalled, "onMemorySummarizingStarted should be called");
    }

    @Test
    void shouldNotifyProgressCallbackOnSummarizationComplete() {
        SummarizingMemory memory = createMemory(1000);
        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        memory.updateRealTokenUsage(new TokenUsageInfo(2000, 500, 2500));
        memory.getMessages();

        assertTrue(callback.completedCalled, "onMemorySummarizingCompleted should be called");
        assertTrue(callback.originalTokens > 0, "originalTokens should be positive");
        assertTrue(callback.compressedTokens > 0, "compressedTokens should be positive");
    }

    @Test
    void shouldNotifyProgressCallbackOnSummarizationFailure() {
        SummarizingMemory memory = new SummarizingMemory(delegate, 1000,
                text -> { throw new RuntimeException("Boom"); },
                callback);

        List<Message> conversation = buildConversation(5);
        for (Message msg : conversation) {
            memory.add(msg);
        }

        memory.updateRealTokenUsage(new TokenUsageInfo(2000, 500, 2500));
        memory.getMessages();

        assertTrue(callback.startedCalled, "Started should be called before failure");
        assertTrue(callback.failedCalled, "onMemorySummarizingFailed should be called");
        assertEquals("Boom", callback.failReason);
    }

    // --- Helper: TestProgressCallback ---

    private static class TestProgressCallback implements FunctionCallOrchestrator.ProgressCallback {
        boolean startedCalled = false;
        boolean completedCalled = false;
        boolean failedCalled = false;
        int originalTokens = 0;
        int compressedTokens = 0;
        String failReason = null;

        @Override
        public void onMemorySummarizingStarted() {
            startedCalled = true;
        }

        @Override
        public void onMemorySummarizingCompleted(int originalTokens, int compressedTokens) {
            completedCalled = true;
            this.originalTokens = originalTokens;
            this.compressedTokens = compressedTokens;
        }

        @Override
        public void onMemorySummarizingFailed(String reason) {
            failedCalled = true;
            this.failReason = reason;
        }
    }
}
