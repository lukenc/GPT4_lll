package com.wmsay.gpt4_lll.fc.events;

import com.wmsay.gpt4_lll.fc.state.PlanStepInfo;
import java.util.List;

/**
 * 计划进度监听器 — push 模式接收进度变更事件。
 * 所有参数使用 PlanStepInfo DTO，确保 UI 层与策略层解耦。
 */
public interface PlanProgressListener {
    /** 计划已生成 */
    default void onPlanGenerated(List<PlanStepInfo> steps) {}
    /** 步骤开始执行 */
    default void onStepStarted(int stepIndex, String description) {}
    /** 步骤执行完成 */
    default void onStepCompleted(int stepIndex, boolean success, String resultSummary) {}
    /** 计划已修订（重规划） */
    default void onPlanRevised(List<PlanStepInfo> revisedSteps) {}
    /** 计划执行全部完成 */
    default void onPlanCompleted() {}
    /** 计划已清空（会话重置） */
    default void onPlanCleared() {}
}
