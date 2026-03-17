package com.wmsay.gpt4_lll.fc.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.JsonStorage;
import com.wmsay.gpt4_lll.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonStorage 新旧格式兼容单元测试。
 * 覆盖需求: 15.2, 15.3, 15.5
 *
 * 每个测试使用唯一 topic 避免共享文件冲突。
 * 测试完成后通过 saveConversationWithMetadata 覆盖为空消息列表来清理。
 */
class JsonStorageMemoryUnitTest {

    private String uniqueTopic(TestInfo info) {
        return "test-mem-" + info.getDisplayName().hashCode() + "-" + System.nanoTime();
    }

    private Message msg(String role, String content) {
        Message m = new Message();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    // --- 需求 15.2, 15.3: 新格式保存和加载 ---

    @Test
    void shouldSaveAndLoadNewFormatWithMetadata(TestInfo info) {
        String topic = uniqueTopic(info);
        List<Message> messages = Arrays.asList(
                msg("system", "You are helpful."),
                msg("user", "Hello"),
                msg("assistant", "Hi there")
        );
        List<SummaryMetadata> metadata = Collections.singletonList(
                new SummaryMetadata("Summary text", 1, 2, 1700000000000L, 5000, 500)
        );

        JsonStorage.saveConversationWithMetadata(topic, messages, metadata, 45000);

        ConversationData loaded = JsonStorage.loadConversationData(topic);
        assertNotNull(loaded);
        assertEquals(3, loaded.getMessages().size());
        assertEquals("system", loaded.getMessages().get(0).getRole());
        assertEquals(45000, loaded.getLastKnownPromptTokens());
        assertNotNull(loaded.getSummaryMetadata());
        assertEquals(1, loaded.getSummaryMetadata().size());
        assertEquals("Summary text", loaded.getSummaryMetadata().get(0).getSummaryText());
        assertEquals(1, loaded.getSummaryMetadata().get(0).getStartIndex());
        assertEquals(2, loaded.getSummaryMetadata().get(0).getEndIndex());
        assertEquals(5000, loaded.getSummaryMetadata().get(0).getOriginalTokens());
        assertEquals(500, loaded.getSummaryMetadata().get(0).getCompressedTokens());
    }

    // --- 需求 15.5: 旧格式数据加载 ---

    @Test
    void shouldLoadOldFormatAsLegacyData(TestInfo info) throws Exception {
        String topic = uniqueTopic(info);
        List<Message> messages = Arrays.asList(
                msg("system", "You are helpful."),
                msg("user", "Old format message")
        );

        // Write old-format JSON: topic maps to a plain array of messages
        java.nio.file.Path filePath = java.nio.file.Paths.get(
                com.intellij.openapi.application.PathManager.getTempPath(),
                "gpt4_lll", "GPT4_lll_chat.json");
        java.nio.file.Files.createDirectories(filePath.getParent());

        // Read existing content and merge
        JSONObject root = new JSONObject(true);
        if (java.nio.file.Files.exists(filePath)) {
            byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
            if (bytes.length > 0) {
                JSONObject existing = JSON.parseObject(new String(bytes));
                if (existing != null) root = existing;
            }
        }
        root.put(topic, JSON.parseArray(JSON.toJSONString(messages)));
        java.nio.file.Files.write(filePath, root.toJSONString().getBytes(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        ConversationData loaded = JsonStorage.loadConversationData(topic);
        assertNotNull(loaded);
        assertEquals(2, loaded.getMessages().size());
        assertEquals("user", loaded.getMessages().get(1).getRole());
        assertEquals("Old format message", loaded.getMessages().get(1).getContent());
        assertNull(loaded.getSummaryMetadata());
        assertEquals(-1, loaded.getLastKnownPromptTokens());
    }

    // --- 多条摘要记录 ---

    @Test
    void shouldSaveAndLoadMultipleSummaryRecords(TestInfo info) {
        String topic = uniqueTopic(info);
        List<Message> messages = Arrays.asList(
                msg("system", "System prompt"),
                msg("user", "Msg 1"), msg("assistant", "Reply 1"),
                msg("user", "Msg 2"), msg("assistant", "Reply 2"),
                msg("user", "Msg 3"), msg("assistant", "Reply 3")
        );
        List<SummaryMetadata> metadata = Arrays.asList(
                new SummaryMetadata("First summary", 1, 2, 1700000000000L, 3000, 300),
                new SummaryMetadata("Second summary", 3, 4, 1700000001000L, 4000, 400)
        );

        JsonStorage.saveConversationWithMetadata(topic, messages, metadata, 60000);

        ConversationData loaded = JsonStorage.loadConversationData(topic);
        assertNotNull(loaded);
        assertEquals(7, loaded.getMessages().size());
        assertEquals(60000, loaded.getLastKnownPromptTokens());
        assertNotNull(loaded.getSummaryMetadata());
        assertEquals(2, loaded.getSummaryMetadata().size());
        assertEquals("First summary", loaded.getSummaryMetadata().get(0).getSummaryText());
        assertEquals("Second summary", loaded.getSummaryMetadata().get(1).getSummaryText());
    }

    // --- lastKnownPromptTokens 持久化和恢复 ---

    @Test
    void shouldPersistAndRestoreLastKnownPromptTokens(TestInfo info) {
        String topic = uniqueTopic(info);
        List<Message> messages = Collections.singletonList(msg("system", "System prompt"));

        JsonStorage.saveConversationWithMetadata(topic, messages, null, 123456);

        ConversationData loaded = JsonStorage.loadConversationData(topic);
        assertNotNull(loaded);
        assertEquals(123456, loaded.getLastKnownPromptTokens());
    }

    @Test
    void shouldReturnNullForNonExistentTopic() {
        ConversationData loaded = JsonStorage.loadConversationData(
                "non-existent-topic-" + System.nanoTime());
        assertNull(loaded);
    }
}
