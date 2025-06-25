package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.application.PathManager;
import com.wmsay.gpt4_lll.model.Message;
import groovy.util.logging.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class JsonStorage {
    private static final String FILE_NAME = "GPT4_lll_chat.json";
    private static final Path FILE_PATH = Paths.get(PathManager.getPluginTempPath(), FILE_NAME);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(JsonStorage.class);

    public static void saveConservation(String topic, List<Message> messageList) {
        try {
            LinkedHashMap<String, List<Message>> data = loadData();
            data.put(topic, messageList);
            saveData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteConservation(String topic) {
        try {
            LinkedHashMap<String, List<Message>> data = loadData();
            data.remove(topic);
            saveData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveData(LinkedHashMap<String, List<Message>> data) throws IOException {
        System.out.println(FILE_PATH);
        // Make sure the directory exists
        Files.createDirectories(FILE_PATH.getParent());
        // Convert the map to a JSON string
        String jsonString = JSON.toJSONString(data);
        // Write the JSON string to file
        Files.write(FILE_PATH, jsonString.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static LinkedHashMap<String, List<Message>> loadData() throws IOException {
        if (Files.notExists(FILE_PATH)) {
            // If the file does not exist, return an empty map
            return new LinkedHashMap<>();
        }
        byte[] bytes = Files.readAllBytes(FILE_PATH);
        // If the file is empty, return an empty map
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }
        // Convert the JSON string to a map
        LinkedHashMap<String, List<Message>> data = JSON.parseObject(new String(bytes), new TypeReference<LinkedHashMap<String, List<Message>>>() {});
        return data.entrySet().stream()
                .sorted(Map.Entry.<String, List<Message>>comparingByKey().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, // 合并函数，这里不会用到因为键是唯一的
                        LinkedHashMap::new // 使用LinkedHashMap作为结果容器
                ));
    }

    // --- New methods for enhanced history management ---

    /**
     * Renames a chat history topic
     * @param oldTopic The original topic name
     * @param newTopic The new topic name
     * @return true if successful, false if the old topic wasn't found
     */
    public static boolean renameConservation(String oldTopic, String newTopic) {
        try {
            LinkedHashMap<String, List<Message>> data = loadData();
            List<Message> messages = data.remove(oldTopic);

            if (messages != null) {
                data.put(newTopic, messages);
                saveData(data);
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Export a specific chat history to a file
     * @param topic The chat topic to export
     * @param outputFile The file to export to
     * @throws IOException If an error occurs during export
     */
    public static void exportConservation(String topic, File outputFile) throws IOException {
        LinkedHashMap<String, List<Message>> data = loadData();
        List<Message> messages = data.get(topic);

        if (messages != null) {
            Files.createDirectories(outputFile.getParentFile().toPath());

            StringBuilder content = new StringBuilder();
            content.append("Chat: ").append(topic).append("\n");
            content.append("Date: ").append(topic.split("--")[0]).append("\n");
            content.append("----------------------------------------\n");

            for (Message message : messages) {
                content.append(message.getRole().toUpperCase())
                        .append(": ")
                        .append(message.getContent())
                        .append("\n----------------------------------------\n");
            }

            Files.write(outputFile.toPath(), content.toString().getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            throw new IOException("Chat history not found: " + topic);
        }
    }

    /**
     * Search for chat histories matching the given search term
     * @param searchTerm The term to search for
     * @return A map of topics and their messages that match the search term
     */
    public static LinkedHashMap<String, List<Message>> searchConservations(String searchTerm) {
        try {
            LinkedHashMap<String, List<Message>> allData = loadData();
            LinkedHashMap<String, List<Message>> results = new LinkedHashMap<>();

            String lowerCaseSearch = searchTerm.toLowerCase();

            for (String topic : allData.keySet()) {
                // Search in topic
                if (topic.toLowerCase().contains(lowerCaseSearch)) {
                    results.put(topic, allData.get(topic));
                    continue;
                }

                // Search in messages
                List<Message> messages = allData.get(topic);
                for (Message message : messages) {
                    if (message.getContent() != null &&
                            message.getContent().toLowerCase().contains(lowerCaseSearch)) {
                        results.put(topic, messages);
                        break;
                    }
                }
            }

            return results;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Filter chat histories by date range
     * @param fromDate Start date (inclusive)
     * @param toDate End date (inclusive)
     * @return A map of topics and their messages within the date range
     */
    public static LinkedHashMap<String, List<Message>> filterByDateRange(LocalDateTime fromDate, LocalDateTime toDate) {
        try {
            LinkedHashMap<String, List<Message>> allData = loadData();
            LinkedHashMap<String, List<Message>> results = new LinkedHashMap<>();

            for (String topic : allData.keySet()) {
                try {
                    // Parse the date from the topic (assuming format: "yyyy-MM-dd HH:mm:ss--Chat:...")
                    int dateEndIndex = topic.indexOf("--");
                    if (dateEndIndex > 0) {
                        String dateStr = topic.substring(0, dateEndIndex);
                        LocalDateTime topicDate = LocalDateTime.parse(dateStr, DATE_FORMATTER);

                        // Check if the date is within range
                        if ((fromDate == null || !topicDate.isBefore(fromDate)) &&
                                (toDate == null || !topicDate.isAfter(toDate))) {
                            results.put(topic, allData.get(topic));
                        }
                    }
                } catch (Exception e) {
                    // If date parsing fails, skip this topic
                    JsonStorage.log.info("Failed to parse date from topic: {}" , topic);
                }
            }

            return results;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Filter chat histories by a predefined date range
     * @param range One of: "today", "yesterday", "last7days", "last30days", "thismonth", "lastmonth"
     * @return A map of topics and their messages within the date range
     */
    public static LinkedHashMap<String, List<Message>> filterByDateRange(String range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromDate = null;

        switch (range.toLowerCase()) {
            case "today":
                fromDate = now.toLocalDate().atStartOfDay();
                break;
            case "yesterday":
                fromDate = now.toLocalDate().minusDays(1).atStartOfDay();
                LocalDateTime toDate = now.toLocalDate().atStartOfDay();
                return filterByDateRange(fromDate, toDate);
            case "last7days":
                fromDate = now.minusDays(7);
                break;
            case "last30days":
                fromDate = now.minusDays(30);
                break;
            case "thismonth":
                fromDate = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
                break;
            case "lastmonth":
                fromDate = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
                LocalDateTime lastDayOfLastMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay().minusNanos(1);
                return filterByDateRange(fromDate, lastDayOfLastMonth);
            default:
                // Return all data if range is not recognized
                try {
                    return loadData();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
        }

        return filterByDateRange(fromDate, null);
    }
}
