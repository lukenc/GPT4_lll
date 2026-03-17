package com.wmsay.gpt4_lll.mcp.tools.shell;

/**
 * Shell 命令风险等级。
 * 决定是否需要审批、是否允许执行等策略行为。
 */
public enum ShellRiskLevel {

    READ_ONLY("READ_ONLY", "Read-only query commands"),
    WORKSPACE_MUTATING("WORKSPACE_MUTATING", "Commands that modify workspace content"),
    NETWORKED("NETWORKED", "Commands accessing external network resources"),
    SYSTEM_MUTATING("SYSTEM_MUTATING", "Commands that modify system-level state");

    private final String code;
    private final String label;

    ShellRiskLevel(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
