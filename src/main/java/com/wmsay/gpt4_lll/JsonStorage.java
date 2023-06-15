package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.application.PathManager;
import com.wmsay.gpt4_lll.model.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonStorage {
    private static final String FILE_NAME = "GPT4_lll_chat.json";
    private static final Path FILE_PATH = Paths.get(PathManager.getPluginTempPath(), FILE_NAME);

    public static void saveConservation(String topic, List<Message> messageList){
        try {
            LinkedHashMap<String,List<Message>> data= loadData();
            data.put(topic,messageList);
            saveData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteConservation(String topic){
        try {
            LinkedHashMap<String,List<Message>> data= loadData();
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

    public static LinkedHashMap<String,List<Message>> loadData() throws IOException {
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
        return JSON.parseObject(new String(bytes), new TypeReference<LinkedHashMap<String, List<Message>>>() {});
    }
}
