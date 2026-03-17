package com.wmsay.gpt4_lll.mcp.tools.shell;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 封装经过规范化处理的 shell_exec 输入参数。
 * 所有字段在构造时已完成默认值填充和基础规范化。
 */
public class ShellExecRequest {

    public enum Mode { ARGV, SHELL }

    private final List<String> command;
    private final Mode mode;
    private final String shellCommand;
    private final Path workingDirectory;
    private final long timeoutMs;
    private final int maxOutputBytes;
    private final boolean captureStderr;
    private final Map<String, String> environmentOverrides;
    private final Charset outputEncoding;

    private ShellExecRequest(Builder builder) {
        this.command = builder.command == null ? List.of() : Collections.unmodifiableList(builder.command);
        this.mode = builder.mode;
        this.shellCommand = builder.shellCommand;
        this.workingDirectory = builder.workingDirectory;
        this.timeoutMs = builder.timeoutMs;
        this.maxOutputBytes = builder.maxOutputBytes;
        this.captureStderr = builder.captureStderr;
        this.environmentOverrides = builder.environmentOverrides == null
                ? Map.of()
                : Collections.unmodifiableMap(builder.environmentOverrides);
        this.outputEncoding = builder.outputEncoding;
    }

    public List<String> getCommand() { return command; }
    public Mode getMode() { return mode; }
    public String getShellCommand() { return shellCommand; }
    public Path getWorkingDirectory() { return workingDirectory; }
    public long getTimeoutMs() { return timeoutMs; }
    public int getMaxOutputBytes() { return maxOutputBytes; }
    public boolean isCaptureStderr() { return captureStderr; }
    public Map<String, String> getEnvironmentOverrides() { return environmentOverrides; }
    public Charset getOutputEncoding() { return outputEncoding; }

    /**
     * 生成人类可读的命令摘要（用于日志和结果展示）。
     */
    public String getDisplayCommand() {
        if (mode == Mode.SHELL) {
            return shellCommand != null ? shellCommand : "";
        }
        return String.join(" ", command);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> command;
        private Mode mode = Mode.ARGV;
        private String shellCommand;
        private Path workingDirectory;
        private long timeoutMs = 30_000;
        private int maxOutputBytes = 65_536;
        private boolean captureStderr = true;
        private Map<String, String> environmentOverrides;
        private Charset outputEncoding = StandardCharsets.UTF_8;

        public Builder command(List<String> command) { this.command = command; return this; }
        public Builder mode(Mode mode) { this.mode = mode; return this; }
        public Builder shellCommand(String shellCommand) { this.shellCommand = shellCommand; return this; }
        public Builder workingDirectory(Path workingDirectory) { this.workingDirectory = workingDirectory; return this; }
        public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder maxOutputBytes(int maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; return this; }
        public Builder captureStderr(boolean captureStderr) { this.captureStderr = captureStderr; return this; }
        public Builder environmentOverrides(Map<String, String> envOverrides) { this.environmentOverrides = envOverrides; return this; }
        public Builder outputEncoding(Charset outputEncoding) { this.outputEncoding = outputEncoding; return this; }

        public ShellExecRequest build() {
            return new ShellExecRequest(this);
        }
    }
}
