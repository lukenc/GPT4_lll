package com.wmsay.gpt4_lll.fc.state;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.memory.ConversationData;
import com.wmsay.gpt4_lll.fc.memory.SummaryMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 基于 JSON 文件的 {@link ConversationStore} 默认实现。
 * <p>
 * 使用 fastjson 将会话历史序列化为 JSON 文件存储，从 {@code JsonStorage} 重构而来。
 * 默认存储目录为 {@code ~/.gpt4lll/}，可通过构造函数自定义。
 * <p>
 * 支持新旧两种存储格式：
 * <ul>
 *   <li>旧格式：value 为纯消息数组 {@code [Message, ...]}</li>
 *   <li>新格式：value 为 {@link ConversationData} 对象 {@code {"messages":[...],"summaryMetadata":[...],"lastKnownPromptTokens":N}}</li>
 * </ul>
 * <p>
 * 纯 Java 实现，不依赖任何 {@code com.intellij.*} API。
 *
 * @see ConversationStore
 * @see ConversationData
 */
public class JsonConversationStore implements ConversationStore {

    private static final Logger LOG = Logger.getLogger(JsonConversationStore.class.getName());
    private static final String DEFAULT_FILE_NAME = "GPT4_lll_chat.json";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path filePath;

    /**
     * 使用默认存储目录 {@code ~/.gpt4lll/} 创建实例。
     */
    public JsonConversationStore() {
        this(Paths.get(System.getProperty("user.home"), ".gpt4lll"));
    }

    /**
     * 使用指定存储目录创建实例。
     *
     * @param storageDir 存储目录路径（非 null）
     */
    public JsonConversationStore(Path storageDir) {
        if (storageDir == null) {
            throw new IllegalArgumentException("storageDir must not be null");
        }
        this.filePath = storageDir.resolve(DEFAULT_FILE_NAME);
    }

    @Override
    public void save(String topic, List<Message> messages) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        try {
            LinkedHashMap<String, List<Message>> data = loadRawData();
            data.put(topic, messages);
            writeRawData(data);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to save conversation: " + e.getMessage(), e,
                    "Check file permissions and disk space for: " + filePath);
        }
    }

    @Override
    public ConversationData load(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        try {
            if (Files.notExists(filePath)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return null;
            }

            JSONObject root = JSON.parseObject(new String(bytes));
            if (root == null || !root.containsKey(topic)) {
                return null;
            }

            return parseConversationEntry(root.get(topic));
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to load conversation: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConversationData> loadAll() {
        try {
            if (Files.notExists(filePath)) {
                return Collections.emptyList();
            }
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return Collections.emptyList();
            }

            JSONObject root = JSON.parseObject(new String(bytes));
            if (root == null) {
                return Collections.emptyList();
            }

            // Sort by key descending (matching original JsonStorage behavior)
            List<String> sortedKeys = new ArrayList<>(root.keySet());
            sortedKeys.sort(Collections.reverseOrder());

            List<ConversationData> result = new ArrayList<>();
            for (String key : sortedKeys) {
                ConversationData cd = parseConversationEntry(root.get(key));
                if (cd != null) {
                    result.add(cd);
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to load all conversations: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        try {
            LinkedHashMap<String, List<Message>> data = loadRawData();
            data.remove(topic);
            writeRawData(data);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to delete conversation: " + e.getMessage(), e);
        }
    }

    @Override
    public void rename(String oldTopic, String newTopic) {
        if (oldTopic == null) {
            throw new IllegalArgumentException("oldTopic must not be null");
        }
        if (newTopic == null) {
            throw new IllegalArgumentException("newTopic must not be null");
        }
        try {
            // Use raw JSON to preserve mixed old/new format entries
            String rawJson = readRawJson();
            JSONObject root = JSON.parseObject(rawJson);
            if (root == null) {
                root = new JSONObject(true);
            }

            Object value = root.remove(oldTopic);
            if (value == null) {
                throw new ConversationStoreException(
                        "Conversation not found: " + oldTopic, null,
                        "Check the topic name and try again");
            }
            root.put(newTopic, value);

            Files.createDirectories(filePath.getParent());
            Files.write(filePath, root.toJSONString().getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (ConversationStoreException e) {
            throw e;
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to rename conversation: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConversationData> search(String keyword) {
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        try {
            if (Files.notExists(filePath)) {
                return Collections.emptyList();
            }
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return Collections.emptyList();
            }

            JSONObject root = JSON.parseObject(new String(bytes));
            if (root == null) {
                return Collections.emptyList();
            }

            String lowerKeyword = keyword.toLowerCase();
            List<ConversationData> results = new ArrayList<>();

            for (String topic : root.keySet()) {
                // Search in topic name
                if (topic.toLowerCase().contains(lowerKeyword)) {
                    ConversationData cd = parseConversationEntry(root.get(topic));
                    if (cd != null) {
                        results.add(cd);
                    }
                    continue;
                }

                // Search in message content
                ConversationData cd = parseConversationEntry(root.get(topic));
                if (cd != null && cd.getMessages() != null) {
                    for (Message msg : cd.getMessages()) {
                        if (msg.getContent() != null &&
                                msg.getContent().toLowerCase().contains(lowerKeyword)) {
                            results.add(cd);
                            break;
                        }
                    }
                }
            }
            return Collections.unmodifiableList(results);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to search conversations: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConversationData> filterByDateRange(LocalDateTime from, LocalDateTime to) {
        try {
            if (Files.notExists(filePath)) {
                return Collections.emptyList();
            }
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return Collections.emptyList();
            }

            JSONObject root = JSON.parseObject(new String(bytes));
            if (root == null) {
                return Collections.emptyList();
            }

            List<ConversationData> results = new ArrayList<>();

            for (String topic : root.keySet()) {
                try {
                    // Parse date from topic (format: "yyyy-MM-dd HH:mm:ss--Chat:...")
                    int dateEndIndex = topic.indexOf("--");
                    if (dateEndIndex > 0) {
                        String dateStr = topic.substring(0, dateEndIndex);
                        LocalDateTime topicDate = LocalDateTime.parse(dateStr, DATE_FORMATTER);

                        if ((from == null || !topicDate.isBefore(from)) &&
                                (to == null || !topicDate.isAfter(to))) {
                            ConversationData cd = parseConversationEntry(root.get(topic));
                            if (cd != null) {
                                results.add(cd);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to parse date from topic: " + topic, e);
                }
            }
            return Collections.unmodifiableList(results);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to filter conversations by date range: " + e.getMessage(), e);
        }
    }

    @Override
    public void export(String topic, Path outputPath, ExportFormat format) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }

        ConversationData data = load(topic);
        if (data == null || data.getMessages() == null) {
            throw new ConversationStoreException(
                    "Conversation not found: " + topic, null,
                    "Check the topic name and try again");
        }

        try {
            Files.createDirectories(outputPath.getParent());
            String content;
            switch (format) {
                case JSON:
                    content = JSON.toJSONString(data, true);
                    break;
                case MARKDOWN:
                    content = exportAsMarkdown(topic, data.getMessages());
                    break;
                case TEXT:
                default:
                    content = exportAsText(topic, data.getMessages());
                    break;
            }
            Files.write(outputPath, content.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to export conversation: " + e.getMessage(), e,
                    "Check file permissions for: " + outputPath);
        }
    }

    @Override
    public void saveWithMetadata(String topic, List<Message> messages,
                                  List<SummaryMetadata> metadata, int lastKnownPromptTokens) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        try {
            String rawJson = readRawJson();
            JSONObject root = JSON.parseObject(rawJson);
            if (root == null) {
                root = new JSONObject(true);
            }

            ConversationData data = new ConversationData(messages, metadata, lastKnownPromptTokens);
            root.put(topic, JSON.toJSON(data));

            Files.createDirectories(filePath.getParent());
            Files.write(filePath, root.toJSONString().getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ConversationStoreException(
                    "Failed to save conversation with metadata: " + e.getMessage(), e,
                    "Check file permissions and disk space for: " + filePath);
        }
    }

    // ---- Internal helpers ----

    /**
     * 获取存储文件路径（供测试和调试使用）。
     */
    Path getFilePath() {
        return filePath;
    }

    private LinkedHashMap<String, List<Message>> loadRawData() throws IOException {
        if (Files.notExists(filePath)) {
            return new LinkedHashMap<>();
        }
        byte[] bytes = Files.readAllBytes(filePath);
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, List<Message>> data = JSON.parseObject(
                new String(bytes),
                new TypeReference<LinkedHashMap<String, List<Message>>>() {});
        // Sort by key descending (matching original JsonStorage behavior)
        return data.entrySet().stream()
                .sorted(Map.Entry.<String, List<Message>>comparingByKey().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    private void writeRawData(LinkedHashMap<String, List<Message>> data) throws IOException {
        Files.createDirectories(filePath.getParent());
        String jsonString = JSON.toJSONString(data);
        Files.write(filePath, jsonString.getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String readRawJson() throws IOException {
        if (Files.exists(filePath)) {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length > 0) {
                return new String(bytes);
            }
        }
        return "{}";
    }

    /**
     * 解析单个对话条目，自动识别新旧格式。
     */
    private ConversationData parseConversationEntry(Object value) {
        if (value instanceof JSONArray) {
            // Old format: value is a plain message list
            List<Message> messages = JSON.parseArray(value.toString(), Message.class);
            return new ConversationData(messages, null, -1);
        } else if (value instanceof JSONObject) {
            // New format: value is a ConversationData object
            return JSON.parseObject(value.toString(), ConversationData.class);
        } else {
            LOG.warning("Unknown conversation entry format, skipping");
            return null;
        }
    }

    private String exportAsText(String topic, List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Chat: ").append(topic).append("\n");
        int dashIdx = topic.indexOf("--");
        if (dashIdx > 0) {
            sb.append("Date: ").append(topic.substring(0, dashIdx)).append("\n");
        }
        sb.append("----------------------------------------\n");
        for (Message msg : messages) {
            sb.append(msg.getRole() != null ? msg.getRole().toUpperCase() : "UNKNOWN")
                    .append(": ")
                    .append(msg.getContent() != null ? msg.getContent() : "")
                    .append("\n----------------------------------------\n");
        }
        return sb.toString();
    }

    private String exportAsMarkdown(String topic, List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(topic).append("\n\n");
        int dashIdx = topic.indexOf("--");
        if (dashIdx > 0) {
            sb.append("**Date:** ").append(topic.substring(0, dashIdx)).append("\n\n");
        }
        sb.append("---\n\n");
        for (Message msg : messages) {
            String role = msg.getRole() != null ? msg.getRole() : "unknown";
            sb.append("### ").append(role.substring(0, 1).toUpperCase())
                    .append(role.substring(1)).append("\n\n");
            sb.append(msg.getContent() != null ? msg.getContent() : "").append("\n\n");
            sb.append("---\n\n");
        }
        return sb.toString();
    }
}
