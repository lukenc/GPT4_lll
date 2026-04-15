package com.wmsay.gpt4_lll.fc.state;

/**
 * 任务进度 — 不可变数据类。
 */
public class TaskProgress {

    private final int totalSteps;
    private final int completedSteps;
    private final int currentStepIndex;
    private final double overallProgress;

    public TaskProgress(int totalSteps, int completedSteps, int currentStepIndex, double overallProgress) {
        this.totalSteps = totalSteps;
        this.completedSteps = completedSteps;
        this.currentStepIndex = currentStepIndex;
        this.overallProgress = overallProgress;
    }

    public int getTotalSteps() { return totalSteps; }
    public int getCompletedSteps() { return completedSteps; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public double getOverallProgress() { return overallProgress; }
}
