package com.wmsay.gpt4_lll.mcp.tools.shell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Shell 命令校验器。
 * 在命令进入 ProcessBuilder 之前执行多维度安全校验：
 * <ul>
 *   <li>命令非空 &amp; 每个参数为有效字符串</li>
 *   <li>shell 模式检查</li>
 *   <li>可执行文件 allowlist 检查</li>
 *   <li>denylist 模式匹配</li>
 *   <li>工作目录边界校验（含 symlink 解析）</li>
 *   <li>环境变量白名单校验</li>
 *   <li>argv 参数中 shell 元字符检测</li>
 * </ul>
 */
public class ShellCommandValidator {

    private static final String SHELL_METACHARACTERS = ";|&`$(){}";

    private final ShellExecPolicy policy;

    public ShellCommandValidator(ShellExecPolicy policy) {
        this.policy = policy;
    }

    /**
     * 全面校验请求。返回 null 表示通过，否则返回阻断结果。
     */
    public ShellExecResult validate(ShellExecRequest request, Path projectRoot) {
        // 1. shell 模式检查
        if (request.getMode() == ShellExecRequest.Mode.SHELL) {
            if (!policy.isShellModeEnabled()) {
                return ShellExecResult.blocked(
                        "shell_mode_disabled",
                        "Shell mode is disabled by security policy. Use argv mode instead.",
                        Map.of("mode", "shell"),
                        "Rewrite the command as an argv array, e.g. [\"git\", \"status\"] instead of \"git status\"."
                );
            }
        }

        // 2. 命令非空校验
        List<String> command = request.getCommand();
        if (request.getMode() == ShellExecRequest.Mode.ARGV) {
            if (command == null || command.isEmpty()) {
                return ShellExecResult.blocked(
                        "command_empty",
                        "Command array must not be empty.",
                        null,
                        "Provide a non-empty command array, e.g. [\"git\", \"status\"]."
                );
            }

            for (int i = 0; i < command.size(); i++) {
                String arg = command.get(i);
                if (arg == null || arg.isEmpty()) {
                    return ShellExecResult.blocked(
                            "command_invalid_argument",
                            "Command argument at index " + i + " is null or empty.",
                            Map.of("index", i),
                            "Every element in the command array must be a non-empty string."
                    );
                }
            }
        }

        // 3. 可执行文件 allowlist 检查
        if (request.getMode() == ShellExecRequest.Mode.ARGV && !command.isEmpty()) {
            String executable = command.get(0);
            if (!policy.isExecutableAllowed(executable)) {
                return ShellExecResult.blocked(
                        "command_not_allowed",
                        "Executable '" + executable + "' is not in the allowed list.",
                        Map.of("executable", executable),
                        "Only approved executables can be run. Check your shell policy configuration."
                );
            }
        }

        // 4. denylist 模式匹配
        String displayCommand = request.getDisplayCommand();
        ShellExecPolicy.PolicyViolation denyViolation = policy.checkDenyList(displayCommand);
        if (denyViolation != null) {
            ShellRiskLevel riskLevel = policy.assessRiskLevel(command);
            return ShellExecResult.blocked(
                    denyViolation.code(),
                    denyViolation.message(),
                    denyViolation.details(),
                    denyViolation.suggestion(),
                    riskLevel
            );
        }

        // 5. argv 模式下检测 shell 元字符（防止通过 argv 参数注入 shell 语义）
        if (request.getMode() == ShellExecRequest.Mode.ARGV) {
            for (String arg : command) {
                if (containsShellMetacharacters(arg)) {
                    return ShellExecResult.blocked(
                            "command_blocked",
                            "Argument contains shell metacharacters which are not allowed in argv mode: " + arg,
                            Map.of("argument", arg, "reason", "shell_metacharacter_in_argv"),
                            "In argv mode, each argument is passed directly to the process. " +
                                    "Do not use shell operators (;, |, &&, ||, `, $()). " +
                                    "Split into separate shell_exec calls if you need multiple commands."
                    );
                }
            }
        }

        // 6. 工作目录边界校验（含 symlink 解析）
        ShellExecResult workDirValidation = validateWorkingDirectory(request.getWorkingDirectory(), projectRoot);
        if (workDirValidation != null) {
            return workDirValidation;
        }

        // 7. 环境变量白名单校验
        Map<String, String> envOverrides = request.getEnvironmentOverrides();
        if (envOverrides != null && !envOverrides.isEmpty()) {
            for (String key : envOverrides.keySet()) {
                ShellExecPolicy.PolicyViolation envViolation = policy.checkEnvironmentKey(key);
                if (envViolation != null) {
                    return ShellExecResult.blocked(
                            envViolation.code(),
                            envViolation.message(),
                            envViolation.details(),
                            envViolation.suggestion(),
                            ShellRiskLevel.SYSTEM_MUTATING
                    );
                }
            }
        }

        return null;  // all checks passed
    }

    /**
     * 使用 toRealPath() 做二次校验的工作目录边界检查。
     * 设计文档 §6.6 强制要求，防止 symlink 越界。
     */
    private ShellExecResult validateWorkingDirectory(Path workingDirectory, Path projectRoot) {
        if (workingDirectory == null || projectRoot == null) {
            return null;
        }

        try {
            Path realProjectRoot = projectRoot.toRealPath();

            if (!Files.exists(workingDirectory)) {
                return ShellExecResult.blocked(
                        "working_directory_not_found",
                        "Working directory does not exist: " + workingDirectory,
                        Map.of("path", workingDirectory.toString()),
                        "Ensure the directory exists before using it as workingDirectory."
                );
            }

            if (!Files.isDirectory(workingDirectory)) {
                return ShellExecResult.blocked(
                        "working_directory_not_found",
                        "Path is not a directory: " + workingDirectory,
                        Map.of("path", workingDirectory.toString()),
                        "workingDirectory must point to an existing directory."
                );
            }

            Path realWorkingDir = workingDirectory.toRealPath();

            if (!realWorkingDir.startsWith(realProjectRoot)) {
                return ShellExecResult.blocked(
                        "working_directory_out_of_bounds",
                        "Working directory resolves outside the project workspace after symlink resolution.",
                        Map.of(
                                "requested", workingDirectory.toString(),
                                "resolved", realWorkingDir.toString()
                        ),
                        "Use a directory inside the project root. Symlinks pointing outside the workspace are not allowed."
                );
            }

        } catch (IOException e) {
            return ShellExecResult.blocked(
                    "working_directory_not_found",
                    "Cannot resolve working directory: " + e.getMessage(),
                    Map.of("path", workingDirectory.toString()),
                    "Ensure the directory exists and is accessible."
            );
        }

        return null;
    }

    /**
     * 检测字符串中是否包含 shell 元字符。
     * 排除常见安全场景：路径中的斜杠、等号（环境变量）、冒号（PATH 分隔符）等。
     */
    private boolean containsShellMetacharacters(String arg) {
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (SHELL_METACHARACTERS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
