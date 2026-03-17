package com.wmsay.gpt4_lll.mcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import com.wmsay.gpt4_lll.mcp.tools.shell.*;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * shell_exec 工具实现。
 * 在受控工作区内执行非交互式本地命令，返回结构化结果。
 * <p>
 * 安全四层防线：Schema 约束 → Validator 语义校验 → Policy 风险策略 → Runtime 运行时限制。
 *
 * @see ShellExecPolicy
 * @see ShellCommandValidator
 * @see LocalProcessExecutor
 */
public class ShellExecTool implements McpTool {

    private static final Logger LOG = Logger.getInstance(ShellExecTool.class);

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final long MIN_TIMEOUT_MS = 1_000;
    private static final long MAX_TIMEOUT_MS = 300_000;
    private static final int DEFAULT_MAX_OUTPUT_BYTES = 65_536;
    private static final int MIN_MAX_OUTPUT_BYTES = 1_024;
    private static final int MAX_MAX_OUTPUT_BYTES = 1_048_576;

    private final ShellExecPolicy policy;
    private final ShellCommandValidator validator;
    private final LocalProcessExecutor processExecutor;

    private boolean policyLoaded = false;

    public ShellExecTool() {
        this.policy = new ShellExecPolicy();
        this.validator = new ShellCommandValidator(policy);
        this.processExecutor = new LocalProcessExecutor();
    }

    @Override
    public String name() {
        return "shell_exec";
    }

    @Override
    public String description() {
        return "Execute a single, short-lived, non-interactive shell command inside the project workspace. "
                + "Returns structured output including stdout, stderr, exit code, duration, and risk classification.\n\n"
                + "WHEN TO USE:\n"
                + "- Run build or test commands: [\"./gradlew\", \"test\"], [\"mvn\", \"package\"]\n"
                + "- Query runtime environment: [\"java\", \"-version\"], [\"uname\", \"-a\"], [\"pwd\"]\n"
                + "- Inspect version control state: [\"git\", \"status\"], [\"git\", \"log\", \"--oneline\", \"-10\"]\n"
                + "- Run project-specific scripts that cannot be covered by dedicated tools\n\n"
                + "WHEN NOT TO USE — prefer dedicated tools instead:\n"
                + "- Reading file content → use read_file\n"
                + "- Listing directory structure → use tree\n"
                + "- Searching code by keyword → use grep\n"
                + "- Writing or patching files → use write_file\n\n"
                + "NEVER use shell_exec for:\n"
                + "- Commands requiring interactive input (ssh, top, vim, sudo with password)\n"
                + "- Long-running background processes (npm run dev, watchers, daemons)\n"
                + "- System-level destructive operations (shutdown, rm -rf /, mkfs)\n"
                + "- Chaining multiple steps via shell metacharacters (;, &&, ||, |, `, $())\n"
                + "- Downloading and executing scripts (curl ... | sh, wget ... | bash)\n\n"
                + "PARAMETER TIPS:\n"
                + "- command: argv-style array, e.g. [\"git\", \"status\"]. First element is the executable.\n"
                + "- workingDirectory: relative to project root, must be inside workspace.\n"
                + "- timeoutMs: default 30000ms, increase for slow builds (max 300000ms).\n"
                + "- If status=failed, inspect stderr for details before retrying.\n"
                + "- If status=blocked, read error.suggestion for the recommended alternative.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("command", Map.of(
                "type", "array",
                "required", false,
                "description", "Recommended. Command and arguments array, e.g. [\"git\", \"status\"]. "
                        + "First element is the executable, rest are arguments."
        ));

        schema.put("mode", Map.of(
                "type", "string",
                "required", false,
                "default", "argv",
                "enum", List.of("argv", "shell"),
                "description", "Execution mode. Default 'argv' (recommended). "
                        + "'shell' mode is disabled by policy in Phase 1."
        ));

        schema.put("shellCommand", Map.of(
                "type", "string",
                "required", false,
                "description", "Only used when mode=shell and policy allows it. Currently disabled."
        ));

        schema.put("workingDirectory", Map.of(
                "type", "string",
                "required", false,
                "default", ".",
                "description", "Working directory relative to project root. Must be inside workspace."
        ));

        schema.put("timeoutMs", Map.of(
                "type", "integer",
                "required", false,
                "default", DEFAULT_TIMEOUT_MS,
                "min", MIN_TIMEOUT_MS,
                "max", MAX_TIMEOUT_MS,
                "description", "Execution timeout in milliseconds. Default 30000. Range [1000, 300000]."
        ));

        schema.put("maxOutputBytes", Map.of(
                "type", "integer",
                "required", false,
                "default", DEFAULT_MAX_OUTPUT_BYTES,
                "min", MIN_MAX_OUTPUT_BYTES,
                "max", MAX_MAX_OUTPUT_BYTES,
                "description", "Max bytes retained per output stream. Default 65536. Range [1024, 1048576]."
        ));

        schema.put("captureStderr", Map.of(
                "type", "boolean",
                "required", false,
                "default", true,
                "description", "Whether to capture stderr separately. Default true."
        ));

        schema.put("environmentOverrides", Map.of(
                "type", "object",
                "required", false,
                "description", "Env var overrides. Only whitelisted keys accepted, e.g. {\"JAVA_HOME\": \"/path/to/jdk\"}."
        ));

        schema.put("outputEncoding", Map.of(
                "type", "string",
                "required", false,
                "default", "UTF-8",
                "description", "Charset for decoding stdout/stderr. Default UTF-8. Use GBK for Windows Chinese systems."
        ));

        return schema;
    }

    @Override
    public McpToolResult execute(McpContext context, Map<String, Object> params) {
        Path projectRoot = context.getProjectRoot();
        if (projectRoot == null) {
            return McpToolResult.error("Project root is unavailable. Cannot execute shell commands without a workspace context.");
        }

        ensurePolicyLoaded(projectRoot);

        // ---- 1. 参数解析 ----
        ShellExecRequest request;
        try {
            request = parseRequest(params, projectRoot);
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Parameter validation failed: " + e.getMessage());
        }

        // ---- 2. 安全校验 ----
        ShellExecResult blocked = validator.validate(request, projectRoot);
        if (blocked != null) {
            logExecution("shell_exec_blocked", request, blocked.getRiskLevel(), 0);
            return McpToolResult.structured(blocked.toStructuredMap());
        }

        // ---- 3. 风险评估 ----
        ShellRiskLevel riskLevel = policy.assessRiskLevel(request.getCommand());

        // SYSTEM_MUTATING 命令直接阻断
        if (riskLevel == ShellRiskLevel.SYSTEM_MUTATING) {
            ShellExecResult systemBlocked = ShellExecResult.blocked(
                    "command_blocked",
                    "System-mutating commands are not allowed.",
                    Map.of("riskLevel", riskLevel.getCode()),
                    "This command modifies system state and is blocked by policy.",
                    riskLevel
            );
            logExecution("shell_exec_blocked", request, riskLevel, 0);
            return McpToolResult.structured(systemBlocked.toStructuredMap());
        }

        // ---- 4. 日志记录 ----
        logExecution("shell_exec_start", request, riskLevel, 0);

        // ---- 5. 执行命令 ----
        ShellExecResult result = processExecutor.execute(request, riskLevel, projectRoot);

        // ---- 6. 补充审批信息到结果 ----
        boolean approvalRequired = policy.requiresApproval(riskLevel);
        Map<String, Object> resultMap = result.toStructuredMap();
        resultMap.put("approvalRequired", approvalRequired);
        resultMap.put("approved", true);

        logExecution("shell_exec_end", request, riskLevel,
                result.toStructuredMap().containsKey("durationMs")
                        ? ((Number) result.toStructuredMap().get("durationMs")).longValue() : 0);

        return McpToolResult.structured(resultMap);
    }

    // ---- 参数解析 ----

    private ShellExecRequest parseRequest(Map<String, Object> params, Path projectRoot) {
        ShellExecRequest.Builder builder = ShellExecRequest.builder();

        // mode
        String modeStr = McpFileToolSupport.getString(params, "mode", "argv");
        ShellExecRequest.Mode mode = "shell".equalsIgnoreCase(modeStr)
                ? ShellExecRequest.Mode.SHELL
                : ShellExecRequest.Mode.ARGV;
        builder.mode(mode);

        // command
        Object commandObj = params.get("command");
        if (commandObj instanceof List<?> list) {
            List<String> command = new ArrayList<>();
            for (Object item : list) {
                command.add(item == null ? "" : String.valueOf(item));
            }
            builder.command(command);
        } else if (commandObj instanceof String str) {
            builder.command(List.of(str.split("\\s+")));
        }

        // shellCommand
        builder.shellCommand(McpFileToolSupport.getString(params, "shellCommand", null));

        // workingDirectory
        String workDir = McpFileToolSupport.getString(params, "workingDirectory", ".");
        Path resolvedWorkDir = resolveWorkingDirectory(workDir, projectRoot);
        builder.workingDirectory(resolvedWorkDir);

        // timeoutMs
        long timeoutMs = McpFileToolSupport.getLong(params, "timeoutMs", DEFAULT_TIMEOUT_MS);
        timeoutMs = Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, timeoutMs));
        builder.timeoutMs(timeoutMs);

        // maxOutputBytes
        int maxOutputBytes = McpFileToolSupport.getInt(params, "maxOutputBytes", DEFAULT_MAX_OUTPUT_BYTES);
        maxOutputBytes = Math.max(MIN_MAX_OUTPUT_BYTES, Math.min(MAX_MAX_OUTPUT_BYTES, maxOutputBytes));
        builder.maxOutputBytes(maxOutputBytes);

        // captureStderr
        builder.captureStderr(McpFileToolSupport.getBoolean(params, "captureStderr", true));

        // environmentOverrides
        Object envObj = params.get("environmentOverrides");
        if (envObj instanceof Map<?, ?> rawMap) {
            Map<String, String> envOverrides = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                envOverrides.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            builder.environmentOverrides(envOverrides);
        }

        // outputEncoding
        String encodingName = McpFileToolSupport.getString(params, "outputEncoding", "UTF-8");
        try {
            builder.outputEncoding(Charset.forName(encodingName));
        } catch (UnsupportedCharsetException e) {
            throw new IllegalArgumentException(
                    "Unsupported outputEncoding: '" + encodingName + "'. "
                            + "Use a valid Java charset name like UTF-8, GBK, or CP1252.");
        }

        return builder.build();
    }

    private Path resolveWorkingDirectory(String workDir, Path projectRoot) {
        if (workDir == null || workDir.isBlank() || ".".equals(workDir)) {
            return projectRoot;
        }
        Path candidate = Path.of(workDir);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        return projectRoot.resolve(candidate).normalize();
    }

    private void ensurePolicyLoaded(Path projectRoot) {
        if (!policyLoaded) {
            policy.loadFromProjectRoot(projectRoot);
            policyLoaded = true;
        }
    }

    // ---- 可观测性辅助 ----

    private void logExecution(String event, ShellExecRequest request, ShellRiskLevel riskLevel, long durationMs) {
        try {
            String fingerprint = request.getDisplayCommand();
            String hash = sha256(fingerprint);
            LOG.info(String.format("[%s] command=%s hash=%s risk=%s workDir=%s duration=%dms",
                    event,
                    sanitizeForLog(fingerprint),
                    hash,
                    riskLevel != null ? riskLevel.getCode() : "UNKNOWN",
                    request.getWorkingDirectory(),
                    durationMs));
        } catch (Exception e) {
            LOG.debug("Failed to log shell execution event: " + e.getMessage());
        }
    }

    private String sanitizeForLog(String command) {
        if (command == null) return "";
        if (command.length() > 200) {
            return command.substring(0, 200) + "...";
        }
        return command;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return "sha256:" + hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "sha256:unavailable";
        }
    }
}
