package com.wmsay.gpt4_lll.fc.planning;

import com.wmsay.gpt4_lll.fc.events.PlanProgressListener;
import com.wmsay.gpt4_lll.fc.state.PlanProgressSnapshot;
import com.wmsay.gpt4_lll.fc.state.PlanStepInfo;
import com.wmsay.gpt4_lll.fc.state.TaskPersistence;
import com.wmsay.gpt4_lll.fc.state.TaskPersistenceData;
import com.wmsay.gpt4_lll.fc.state.TaskState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 计划进度提供者 — 进度数据中心枢纽。
 * <p>
 * 线程安全：使用 CopyOnWriteArrayList 管理监听器，synchronized 保护状态更新。
 * 内部持有 PlanStep，对外通过 PlanStepInfo DTO 暴露，实现策略层与 UI 层解耦。
 */
public class PlanProgressProvider {

    private static final Logger LOG = Logger.getLogger(PlanProgressProvider.class.getName());

    private final CopyOnWriteArrayList<PlanProgressListener> listeners = new CopyOnWriteArrayList<>();
    private List<PlanStep> currentSteps = Collections.emptyList();

    // Optional persistence integration (actual calls deferred to Task 4.1)
    private TaskPersistence persistence;
    private String sessionId;

    // ── pull 模式 ──────────────────────────────────────────────

    /**
     * 获取当前进度快照（pull 模式）。
     * 将内部 PlanStep 转换为 PlanStepInfo DTO 后构建快照。
     */
    public synchronized PlanProgressSnapshot getProgressSnapshot() {
        if (currentSteps.isEmpty()) {
            return PlanProgressSnapshot.empty();
        }

        List<PlanStepInfo> stepInfos = toStepInfoList(currentSteps);
        int totalCount = stepInfos.size();
        int completedCount = 0;
        int currentStepIndex = -1;

        for (int i = 0; i < stepInfos.size(); i++) {
            PlanStepInfo info = stepInfos.get(i);
            if (info.getStatus() == PlanStepInfo.Status.COMPLETED) {
                completedCount++;
            }
            if (info.getStatus() == PlanStepInfo.Status.IN_PROGRESS && currentStepIndex == -1) {
                currentStepIndex = i;
            }
        }

        double progress = totalCount > 0 ? completedCount / (double) totalCount : 0.0;
        return new PlanProgressSnapshot(stepInfos, completedCount, totalCount, currentStepIndex, progress);
    }

    // ── push 模式 ──────────────────────────────────────────────

    public void addListener(PlanProgressListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(PlanProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    // ── 策略层调用 ─────────────────────────────────────────────

    /**
     * 设置计划 — 将 PlanStep 转为 PlanStepInfo 后通知监听器 onPlanGenerated。
     */
    public synchronized void setPlan(List<PlanStep> steps) {
        this.currentSteps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        persistSave();
        List<PlanStepInfo> stepInfos = toStepInfoList(this.currentSteps);
        for (PlanProgressListener l : listeners) {
            try {
                l.onPlanGenerated(stepInfos);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onPlanGenerated threw exception", e);
            }
        }
    }

    /**
     * 更新步骤状态 — 通知 onStepStarted 或 onStepCompleted。
     */
    public synchronized void updateStepStatus(int index, PlanStep.Status status, String result) {
        if (index < 0 || index >= currentSteps.size()) {
            return;
        }

        PlanStep step = currentSteps.get(index);

        // Apply the status change to the internal PlanStep
        switch (status) {
            case IN_PROGRESS:
                step.markInProgress();
                break;
            case COMPLETED:
                step.markCompleted(result, 0);
                break;
            case FAILED:
                step.markFailed(result, 0);
                break;
            case SKIPPED:
                step.markSkipped(result);
                break;
            default:
                break;
        }

        // Notify listeners based on the new status
        if (status == PlanStep.Status.IN_PROGRESS) {
            persistSave();
            for (PlanProgressListener l : listeners) {
                try {
                    l.onStepStarted(index, step.getDescription());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Listener onStepStarted threw exception", e);
                }
            }
        } else {
            persistSave();
            boolean success = (status == PlanStep.Status.COMPLETED);
            String resultSummary = step.getResult();
            for (PlanProgressListener l : listeners) {
                try {
                    l.onStepCompleted(index, success, resultSummary);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Listener onStepCompleted threw exception", e);
                }
            }
        }
    }

    /**
     * 重规划 — 将 PlanStep 转为 PlanStepInfo 后通知监听器 onPlanRevised。
     */
    public synchronized void revisePlan(List<PlanStep> revisedSteps) {
        this.currentSteps = revisedSteps != null ? new ArrayList<>(revisedSteps) : new ArrayList<>();
        persistSave();
        List<PlanStepInfo> stepInfos = toStepInfoList(this.currentSteps);
        for (PlanProgressListener l : listeners) {
            try {
                l.onPlanRevised(stepInfos);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onPlanRevised threw exception", e);
            }
        }
    }

    /**
     * 标记完成 — 通知 onPlanCompleted。
     */
    public synchronized void markCompleted() {
        persistCleanup();
        for (PlanProgressListener l : listeners) {
            try {
                l.onPlanCompleted();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onPlanCompleted threw exception", e);
            }
        }
    }

    /**
     * 清空状态 — 通知 onPlanCleared。
     */
    public synchronized void clear() {
        this.currentSteps = new ArrayList<>();
        for (PlanProgressListener l : listeners) {
            try {
                l.onPlanCleared();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onPlanCleared threw exception", e);
            }
        }
    }

    // ── TaskPersistence 集成（字段和 setter，实际调用在 Task 4.1） ──

    public void setTaskPersistence(TaskPersistence persistence, String sessionId) {
        this.persistence = persistence;
        this.sessionId = sessionId;
    }

    public TaskPersistence getTaskPersistence() {
        return persistence;
    }

    public String getSessionId() {
        return sessionId;
    }

    // ── 持久化数据转换 ─────────────────────────────────────────

    /**
     * 将当前计划状态转换为 TaskPersistenceData。
     * 提取步骤描述列表，并将 PlanStep.Status 映射为 TaskState。
     */
    TaskPersistenceData toData() {
        List<String> stepDescriptions = currentSteps.stream()
                .map(PlanStep::getDescription)
                .collect(Collectors.toList());
        ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
        for (PlanStep step : currentSteps) {
            stepStates.put(step.getIndex(), mapToTaskState(step.getStatus()));
        }
        return new TaskPersistenceData(stepDescriptions, stepStates);
    }

    private TaskState mapToTaskState(PlanStep.Status status) {
        switch (status) {
            case PENDING:     return TaskState.PENDING;
            case IN_PROGRESS: return TaskState.RUNNING;
            case COMPLETED:   return TaskState.COMPLETED;
            case FAILED:      return TaskState.FAILED;
            case SKIPPED:     return TaskState.SKIPPED;
            default:          return TaskState.PENDING;
        }
    }

    private void persistSave() {
        if (persistence != null && sessionId != null) {
            try {
                persistence.save(sessionId, toData());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to persist plan data for session: " + sessionId, e);
            }
        }
    }

    private void persistCleanup() {
        if (persistence != null && sessionId != null) {
            try {
                persistence.cleanup(sessionId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to cleanup persisted plan data for session: " + sessionId, e);
            }
        }
    }

    // ── 内部转换方法 ───────────────────────────────────────────

    private PlanStepInfo toStepInfo(PlanStep step) {
        return new PlanStepInfo(
                step.getIndex(),
                step.getDescription(),
                mapStatus(step.getStatus()),
                step.getResult()
        );
    }

    private List<PlanStepInfo> toStepInfoList(List<PlanStep> steps) {
        return steps.stream()
                .map(this::toStepInfo)
                .collect(Collectors.toList());
    }

    private PlanStepInfo.Status mapStatus(PlanStep.Status status) {
        switch (status) {
            case PENDING:     return PlanStepInfo.Status.PENDING;
            case IN_PROGRESS: return PlanStepInfo.Status.IN_PROGRESS;
            case COMPLETED:   return PlanStepInfo.Status.COMPLETED;
            case FAILED:      return PlanStepInfo.Status.FAILED;
            case SKIPPED:     return PlanStepInfo.Status.SKIPPED;
            default:          return PlanStepInfo.Status.PENDING;
        }
    }
}
