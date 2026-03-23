package com.wmsay.gpt4_lll.fc.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 任务持久化 — 将计划步骤和状态序列化到本地 JSON 文件。
 * <p>
 * 持久化路径: {projectRoot}/.gpt4lll/tasks/{sessionId}.json
 * 使用 fastjson 序列化，与项目现有方式一致。
 * IO 错误时记录日志返回空结果，不影响系统启动。
 */
public class TaskPersistence {

    private static final Logger LOG = Logger.getLogger(TaskPersistence.class.getName());

    private final Path baseDir;

    public TaskPersistence(String projectRoot) {
        this.baseDir = Paths.get(projectRoot, ".gpt4lll", "tasks");
    }

    /**
     * 保存任务数据到持久化文件。
     */
    public void save(String sessionId, TaskPersistenceData data) {
        try {
            Files.createDirectories(baseDir);
            Path file = baseDir.resolve(sessionId + ".json");
            JSONObject json = new JSONObject();
            json.put("steps", data.getSteps());
            JSONObject states = new JSONObject();
            for (var entry : data.getStepStates().entrySet()) {
                states.put(String.valueOf(entry.getKey()), entry.getValue().name());
            }
            json.put("stepStates", states);
            Files.writeString(file, json.toJSONString());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save task data for session: " + sessionId, e);
        }
    }

    /**
     * 从持久化文件中恢复任务数据。
     */
    public TaskPersistenceData load(String sessionId) {
        try {
            Path file = baseDir.resolve(sessionId + ".json");
            if (!Files.exists(file)) {
                return TaskPersistenceData.empty();
            }
            String content = Files.readString(file);
            JSONObject json = JSON.parseObject(content);
            List<String> steps = json.getJSONArray("steps").toJavaList(String.class);
            JSONObject statesJson = json.getJSONObject("stepStates");
            ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
            if (statesJson != null) {
                for (String key : statesJson.keySet()) {
                    stepStates.put(Integer.parseInt(key), TaskState.valueOf(statesJson.getString(key)));
                }
            }
            return new TaskPersistenceData(steps, stepStates);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load task data for session: " + sessionId, e);
            return TaskPersistenceData.empty();
        }
    }

    /**
     * 崩溃恢复 — 将 RUNNING 状态的步骤重置为 PENDING。
     */
    public TaskPersistenceData recoverFromCrash(String sessionId) {
        TaskPersistenceData data = load(sessionId);
        if (data.isEmpty()) return data;

        ConcurrentHashMap<Integer, TaskState> recovered = data.getStepStates();
        for (var entry : recovered.entrySet()) {
            if (entry.getValue() == TaskState.RUNNING) {
                entry.setValue(TaskState.PENDING);
            }
        }
        TaskPersistenceData recoveredData = new TaskPersistenceData(
            new ArrayList<>(data.getSteps()), recovered);
        save(sessionId, recoveredData);
        return recoveredData;
    }

    /**
     * 删除持久化文件。
     */
    public void cleanup(String sessionId) {
        try {
            Path file = baseDir.resolve(sessionId + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to cleanup task file for session: " + sessionId, e);
        }
    }

    /**
     * 检查所有步骤是否都处于终态。
     */
    public boolean isAllTerminal(TaskPersistenceData data) {
        if (data.isEmpty()) return true;
        for (TaskState state : data.getStepStates().values()) {
            if (state == TaskState.PENDING || state == TaskState.RUNNING) {
                return false;
            }
        }
        return true;
    }
}
