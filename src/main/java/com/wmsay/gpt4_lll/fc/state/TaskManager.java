package com.wmsay.gpt4_lll.fc.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务管理器 — 管理 PlanAndExecute 策略的计划步骤状态和进度。
 * <p>
 * 由 AgentSession 持有，每个会话独立。
 * 支持计划初始化、状态更新、进度查询、实时计划更新、执行控制（停止/恢复）。
 * 可选注入 TaskPersistence，状态变更时自动持久化。
 * <p>
 * 纯 Java 实现，不依赖任何 com.intellij.* API。
 */
public class TaskManager {

    private final CopyOnWriteArrayList<String> steps = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile int currentStepIndex = -1;
    private volatile TaskPersistence persistence;
    private volatile String sessionId;

    /**
     * 注入持久化组件（可选）。
     */
    public void setPersistence(TaskPersistence persistence) {
        this.persistence = persistence;
    }

    /**
     * 设置会话 ID，用于持久化时标识文件。
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 初始化任务计划，将所有步骤状态设置为 PENDING。
     */
    public void initPlan(List<String> planSteps) {
        steps.clear();
        stepStates.clear();
        currentStepIndex = -1;
        stopped.set(false);
        if (planSteps != null) {
            steps.addAll(planSteps);
            for (int i = 0; i < planSteps.size(); i++) {
                stepStates.put(i, TaskState.PENDING);
            }
        }
        autoPersist();
    }

    /**
     * 更新指定步骤的状态。
     */
    public void updateStepState(int stepIndex, TaskState state) {
        if (stepIndex >= 0 && stepIndex < steps.size()) {
            stepStates.put(stepIndex, state);
            if (state == TaskState.RUNNING) {
                currentStepIndex = stepIndex;
            }
            autoPersist();
        }
    }

    /**
     * 返回当前任务进度。
     */
    public TaskProgress getProgress() {
        int total = steps.size();
        if (total == 0) {
            return new TaskProgress(0, 0, -1, 0.0);
        }
        int completed = 0;
        for (TaskState s : stepStates.values()) {
            if (s == TaskState.COMPLETED || s == TaskState.FAILED
                    || s == TaskState.SKIPPED || s == TaskState.CANCELLED) {
                completed++;
            }
        }
        double progress = (double) completed / total;
        return new TaskProgress(total, completed, currentStepIndex, progress);
    }

    /**
     * 合并修订后的计划步骤，保留已完成步骤的状态。
     */
    public void updatePlan(List<String> revisedSteps) {
        if (revisedSteps == null) return;

        // 保留已完成步骤
        List<String> newSteps = new ArrayList<>();
        ConcurrentHashMap<Integer, TaskState> newStates = new ConcurrentHashMap<>();

        int idx = 0;
        // 保留已完成的步骤
        for (int i = 0; i < steps.size() && i < revisedSteps.size(); i++) {
            TaskState existing = stepStates.getOrDefault(i, TaskState.PENDING);
            if (existing == TaskState.COMPLETED || existing == TaskState.FAILED
                    || existing == TaskState.SKIPPED) {
                newSteps.add(steps.get(i));
                newStates.put(idx, existing);
            } else {
                newSteps.add(revisedSteps.get(i));
                newStates.put(idx, TaskState.PENDING);
            }
            idx++;
        }
        // 添加新增的步骤
        for (int i = idx; i < revisedSteps.size(); i++) {
            newSteps.add(revisedSteps.get(i));
            newStates.put(i, TaskState.PENDING);
        }

        steps.clear();
        stepStates.clear();
        steps.addAll(newSteps);
        stepStates.putAll(newStates);
        autoPersist();
    }

    /**
     * 返回当前计划步骤列表的不可变快照。
     */
    public List<String> getCurrentPlan() {
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /**
     * 获取指定步骤的状态。
     */
    public TaskState getStepState(int stepIndex) {
        return stepStates.getOrDefault(stepIndex, TaskState.PENDING);
    }

    /**
     * 获取所有步骤状态的快照。
     */
    public Map<Integer, TaskState> getStepStates() {
        return Collections.unmodifiableMap(new HashMap<>(stepStates));
    }

    /**
     * 请求停止当前正在执行的计划。
     * 当前 RUNNING 步骤标记为 CANCELLED。
     */
    public void stop() {
        stopped.set(true);
        // 将当前 RUNNING 步骤标记为 CANCELLED
        for (var entry : stepStates.entrySet()) {
            if (entry.getValue() == TaskState.RUNNING) {
                entry.setValue(TaskState.CANCELLED);
            }
        }
        autoPersist();
    }

    /**
     * 从上次停止的位置恢复执行。
     */
    public void resume() {
        stopped.set(false);
    }

    /**
     * 返回当前是否处于停止状态。
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * 清除所有计划步骤和状态，恢复到初始状态。
     */
    public void reset() {
        steps.clear();
        stepStates.clear();
        currentStepIndex = -1;
        stopped.set(false);
    }

    /**
     * 崩溃恢复 — 从持久化文件中恢复计划状态。
     * RUNNING 状态的步骤会被重置为 PENDING，允许从中断处重新执行。
     *
     * @param sessionId 要恢复的会话 ID
     * @return true 如果成功恢复了数据，false 如果无数据可恢复
     */
    public boolean recoverFromCrash(String sessionId) {
        if (persistence == null) {
            return false;
        }
        try {
            TaskPersistenceData recovered = persistence.recoverFromCrash(sessionId);
            if (recovered.isEmpty()) {
                return false;
            }
            steps.clear();
            stepStates.clear();
            steps.addAll(recovered.getSteps());
            stepStates.putAll(recovered.getStepStates());
            currentStepIndex = -1;
            stopped.set(false);
            // 找到当前应执行的步骤索引（第一个 PENDING 步骤）
            for (int i = 0; i < steps.size(); i++) {
                TaskState state = stepStates.getOrDefault(i, TaskState.PENDING);
                if (state == TaskState.PENDING) {
                    currentStepIndex = i;
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            // 恢复失败不影响主流程
            return false;
        }
    }

    private void autoPersist() {
        if (persistence != null && sessionId != null) {
            try {
                TaskPersistenceData data = new TaskPersistenceData(
                    new ArrayList<>(steps), new ConcurrentHashMap<>(stepStates));
                persistence.save(sessionId, data);
            } catch (Exception e) {
                // 持久化失败不影响主流程
            }
        }
    }
}
