package com.wmsay.gpt4_lll.fc.state;

/**
 * 任务步骤状态枚举（PlanAndExecute 策略专用）。
 */
public enum TaskState {
    PENDING, RUNNING, COMPLETED, FAILED, SKIPPED, CANCELLED
}
