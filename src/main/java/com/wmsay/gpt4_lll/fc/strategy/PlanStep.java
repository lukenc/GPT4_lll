package com.wmsay.gpt4_lll.fc.strategy;

/**
 * Plan-and-Execute 策略中的单个计划步骤。
 * <p>
 * 每个步骤有独立的状态生命周期：PENDING → IN_PROGRESS → COMPLETED / FAILED / SKIPPED。
 */
public class PlanStep {

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    private final int index;
    private final String description;
    private Status status;
    private String result;
    private long durationMs;

    public PlanStep(int index, String description) {
        this.index = index;
        this.description = description;
        this.status = Status.PENDING;
    }

    public int getIndex() { return index; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public String getResult() { return result; }
    public long getDurationMs() { return durationMs; }

    public void markInProgress() {
        this.status = Status.IN_PROGRESS;
    }

    public void markCompleted(String result, long durationMs) {
        this.status = Status.COMPLETED;
        this.result = result;
        this.durationMs = durationMs;
    }

    public void markFailed(String reason, long durationMs) {
        this.status = Status.FAILED;
        this.result = reason;
        this.durationMs = durationMs;
    }

    public void markSkipped(String reason) {
        this.status = Status.SKIPPED;
        this.result = reason;
    }

    @Override
    public String toString() {
        return "Step " + (index + 1) + ": " + description + " [" + status + "]";
    }
}
