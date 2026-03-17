package com.wmsay.gpt4_lll.mcp.tools.shell;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * shell_exec 执行结果。
 * 封装进程执行的全部状态，可转换为结构化 Map 返回给 Agent。
 */
public class ShellExecResult {

    public enum Status {
        SUCCESS, FAILED, TIMEOUT, BLOCKED, CANCELLED
    }

    private final Status status;
    private final List<String> command;
    private final String displayCommand;
    private final String mode;
    private final String workingDirectory;
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final long stdoutBytes;
    private final long stderrBytes;
    private final long durationMs;
    private final boolean timedOut;
    private final boolean truncated;
    private final boolean approvalRequired;
    private final boolean approved;
    private final ShellRiskLevel riskLevel;
    private final Map<String, Object> error;
    private final Map<String, Object> metadata;

    private ShellExecResult(Builder builder) {
        this.status = builder.status;
        this.command = builder.command;
        this.displayCommand = builder.displayCommand;
        this.mode = builder.mode;
        this.workingDirectory = builder.workingDirectory;
        this.exitCode = builder.exitCode;
        this.stdout = builder.stdout;
        this.stderr = builder.stderr;
        this.stdoutBytes = builder.stdoutBytes;
        this.stderrBytes = builder.stderrBytes;
        this.durationMs = builder.durationMs;
        this.timedOut = builder.timedOut;
        this.truncated = builder.truncated;
        this.approvalRequired = builder.approvalRequired;
        this.approved = builder.approved;
        this.riskLevel = builder.riskLevel;
        this.error = builder.error;
        this.metadata = builder.metadata;
    }

    /**
     * 转换为可序列化的结构化 Map，用于 McpToolResult.structured()。
     */
    public Map<String, Object> toStructuredMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tool", "shell_exec");
        map.put("status", status.name().toLowerCase());

        if (command != null && !command.isEmpty()) {
            map.put("command", command);
        }
        if (displayCommand != null) {
            map.put("displayCommand", displayCommand);
        }
        map.put("mode", mode != null ? mode : "argv");

        if (workingDirectory != null) {
            map.put("workingDirectory", workingDirectory);
        }
        if (exitCode != null) {
            map.put("exitCode", exitCode);
        }
        if (stdout != null) {
            map.put("stdout", stdout);
        }
        if (stderr != null) {
            map.put("stderr", stderr);
        }
        map.put("stdoutBytes", stdoutBytes);
        map.put("stderrBytes", stderrBytes);
        map.put("durationMs", durationMs);
        map.put("timedOut", timedOut);
        map.put("truncated", truncated);
        map.put("approvalRequired", approvalRequired);
        map.put("approved", approved);

        if (riskLevel != null) {
            map.put("riskLevel", riskLevel.getCode());
        }
        if (error != null && !error.isEmpty()) {
            map.put("error", error);
        }
        if (metadata != null && !metadata.isEmpty()) {
            map.put("metadata", metadata);
        }
        return map;
    }

    public Status getStatus() { return status; }
    public ShellRiskLevel getRiskLevel() { return riskLevel; }

    /**
     * 构建阻断结果（命令未实际执行），使用默认 READ_ONLY 风险等级。
     */
    public static ShellExecResult blocked(String errorCode, String message,
                                          Map<String, Object> details, String suggestion) {
        return blocked(errorCode, message, details, suggestion, null);
    }

    /**
     * 构建阻断结果（命令未实际执行）。
     */
    public static ShellExecResult blocked(String errorCode, String message,
                                          Map<String, Object> details, String suggestion,
                                          ShellRiskLevel riskLevel) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", errorCode);
        err.put("message", message);
        if (details != null) {
            err.put("details", details);
        }
        if (suggestion != null) {
            err.put("suggestion", suggestion);
        }

        return new Builder()
                .status(Status.BLOCKED)
                .timedOut(false)
                .truncated(false)
                .riskLevel(riskLevel)
                .error(err)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Status status;
        private List<String> command;
        private String displayCommand;
        private String mode;
        private String workingDirectory;
        private Integer exitCode;
        private String stdout;
        private String stderr;
        private long stdoutBytes;
        private long stderrBytes;
        private long durationMs;
        private boolean timedOut;
        private boolean truncated;
        private boolean approvalRequired;
        private boolean approved;
        private ShellRiskLevel riskLevel;
        private Map<String, Object> error;
        private Map<String, Object> metadata;

        public Builder status(Status status) { this.status = status; return this; }
        public Builder command(List<String> command) { this.command = command; return this; }
        public Builder displayCommand(String displayCommand) { this.displayCommand = displayCommand; return this; }
        public Builder mode(String mode) { this.mode = mode; return this; }
        public Builder workingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; return this; }
        public Builder exitCode(Integer exitCode) { this.exitCode = exitCode; return this; }
        public Builder stdout(String stdout) { this.stdout = stdout; return this; }
        public Builder stderr(String stderr) { this.stderr = stderr; return this; }
        public Builder stdoutBytes(long stdoutBytes) { this.stdoutBytes = stdoutBytes; return this; }
        public Builder stderrBytes(long stderrBytes) { this.stderrBytes = stderrBytes; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder timedOut(boolean timedOut) { this.timedOut = timedOut; return this; }
        public Builder truncated(boolean truncated) { this.truncated = truncated; return this; }
        public Builder approvalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; return this; }
        public Builder approved(boolean approved) { this.approved = approved; return this; }
        public Builder riskLevel(ShellRiskLevel riskLevel) { this.riskLevel = riskLevel; return this; }
        public Builder error(Map<String, Object> error) { this.error = error; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public ShellExecResult build() {
            return new ShellExecResult(this);
        }
    }
}
