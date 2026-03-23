package com.wmsay.gpt4_lll.model;

/**
 * 纯 UI 展示层的 Agent 阶段状态枚举。
 * 仅用于驱动对话界面的可视化展示，不替代也不修改
 * Agent 内部的 SessionState、TaskState 等状态枚举。
 * 由 AgentRuntimeBridge 在 UI 层使用。
 */
public enum AgentPhase {
    IDLE("空闲", "Idle", "idle"),
    RUNNING("运行中", "Running", "spinner"),
    STOPPED("已停止", "Stopped", "stop"),
    COMPLETED("已完成", "Completed", "check"),
    ERROR("出错", "Error", "error");

    private final String displayTextCn;
    private final String displayTextEn;
    private final String iconId;

    AgentPhase(String displayTextCn, String displayTextEn, String iconId) {
        this.displayTextCn = displayTextCn;
        this.displayTextEn = displayTextEn;
        this.iconId = iconId;
    }

    public String getDisplayTextCn() { return displayTextCn; }
    public String getDisplayTextEn() { return displayTextEn; }
    public String getIconId() { return iconId; }
}
