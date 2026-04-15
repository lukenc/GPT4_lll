package com.wmsay.gpt4_lll.fc.state;

import java.util.Collections;
import java.util.List;

/**
 * 计划进度快照 — 不可变数据对象。
 * 使用 PlanStepInfo DTO，确保跨层传递时不暴露策略层内部类型。
 */
public class PlanProgressSnapshot {
    private final List<PlanStepInfo> steps;
    private final int completedCount;
    private final int totalCount;
    private final int currentStepIndex;
    private final double progress; // 0.0 ~ 1.0

    public PlanProgressSnapshot(List<PlanStepInfo> steps, int completedCount,
                                 int totalCount, int currentStepIndex, double progress) {
        this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
        this.completedCount = completedCount;
        this.totalCount = totalCount;
        this.currentStepIndex = currentStepIndex;
        this.progress = progress;
    }

    public static PlanProgressSnapshot empty() {
        return new PlanProgressSnapshot(Collections.emptyList(), 0, 0, -1, 0.0);
    }

    public List<PlanStepInfo> getSteps() { return steps; }
    public int getCompletedCount() { return completedCount; }
    public int getTotalCount() { return totalCount; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public double getProgress() { return progress; }
    public boolean isEmpty() { return steps.isEmpty(); }
}
