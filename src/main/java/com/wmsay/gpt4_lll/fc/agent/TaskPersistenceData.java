package com.wmsay.gpt4_lll.fc.agent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务持久化数据 — 不可变数据类。
 * 包含计划步骤列表和各步骤状态。
 */
public class TaskPersistenceData {

    private final List<String> steps;
    private final ConcurrentHashMap<Integer, TaskState> stepStates;

    public TaskPersistenceData(List<String> steps, ConcurrentHashMap<Integer, TaskState> stepStates) {
        this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
        this.stepStates = stepStates != null ? new ConcurrentHashMap<>(stepStates) : new ConcurrentHashMap<>();
    }

    public static TaskPersistenceData empty() {
        return new TaskPersistenceData(Collections.emptyList(), new ConcurrentHashMap<>());
    }

    public List<String> getSteps() { return steps; }
    public ConcurrentHashMap<Integer, TaskState> getStepStates() { return new ConcurrentHashMap<>(stepStates); }

    public boolean isEmpty() { return steps.isEmpty(); }
}
