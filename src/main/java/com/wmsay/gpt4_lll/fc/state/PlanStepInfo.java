package com.wmsay.gpt4_lll.fc.state;

/**
 * 计划步骤信息 DTO — 跨层传递的纯数据对象。
 * UI 层通过此类获取步骤信息，无需依赖 fc.planning.PlanStep。
 */
public class PlanStepInfo {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }

    private final int index;
    private final String description;
    private final Status status;
    private final String result;

    public PlanStepInfo(int index, String description, Status status, String result) {
        this.index = index;
        this.description = description;
        this.status = status;
        this.result = result;
    }

    public int getIndex() { return index; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public String getResult() { return result; }
}
