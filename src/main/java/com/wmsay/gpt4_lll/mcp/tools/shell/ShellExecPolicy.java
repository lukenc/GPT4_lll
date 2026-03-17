package com.wmsay.gpt4_lll.mcp.tools.shell;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shell 执行策略。
 * 管理 allowlist / denylist / 风险等级映射 / shell 模式开关 / 环境变量白名单。
 * <p>
 * 策略从外部 JSON 配置文件加载，配置文件缺失或解析失败时退回到内置保守默认值。
 * Phase 2 将增加 WatchService 热加载。
 */
public class ShellExecPolicy {

    private static final Logger LOG = Logger.getInstance(ShellExecPolicy.class);

    private static final String CONFIG_FILE_NAME = "shell-policy.json";
    private static final String CONFIG_DIR = ".gpt4lll";

    // ---- 内置保守默认值（配置加载失败时的 fallback） ----

    private static final Set<String> DEFAULT_ALLOWED_EXECUTABLES = Set.of(
            "pwd", "ls", "echo", "cat", "head", "tail", "wc",
            "git", "java", "javac", "gradle", "./gradlew", "gradlew",
            "uname", "whoami", "date", "which", "env", "printenv",
            "node", "npm", "npx", "python", "python3", "pip", "pip3",
            "mvn", "./mvnw", "mvnw",
            "docker", "make", "cmake",
            "find", "grep", "sort", "uniq", "diff", "tr", "cut", "awk", "sed",
            "du", "df", "file", "stat", "realpath", "dirname", "basename"
    );

    private static final Set<String> DEFAULT_ALLOWED_ENV_KEYS = Set.of(
            "JAVA_HOME", "GRADLE_USER_HOME", "MAVEN_HOME", "M2_HOME",
            "NODE_ENV", "PYTHONPATH", "GOPATH", "GOROOT",
            "PATH", "LANG", "LC_ALL", "TZ"
    );

    private static final Set<String> DANGEROUS_ENV_KEYS = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH",
            "DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH", "DYLD_FRAMEWORK_PATH",
            "BASH_ENV", "ENV", "CDPATH",
            "PROMPT_COMMAND"
    );

    private static final List<String> DEFAULT_DENY_PATTERNS = List.of(
            "rm\\s+-rf", "rm\\s+-r\\s", "rm\\s+--recursive",
            "sudo\\b", "su\\b",
            "shutdown", "reboot", "halt", "poweroff",
            "mkfs", "diskutil\\s+eraseDisk", "dd\\s+.*of=/dev",
            "chmod\\s+-R", "chown\\s+-R",
            "curl.*\\|.*sh", "curl.*\\|.*bash",
            "wget.*\\|.*sh", "wget.*\\|.*bash",
            "\\bkill\\s+-9\\s+-1\\b",
            ":(){ :|:& };:"  // fork bomb
    );

    // git 子命令风险映射
    private static final Set<String> GIT_READONLY_SUBCOMMANDS = Set.of(
            "status", "log", "diff", "show", "branch", "tag",
            "remote", "describe", "rev-parse", "ls-files", "ls-tree",
            "shortlog", "blame", "stash", "list"
    );

    private static final Set<String> GIT_MUTATING_SUBCOMMANDS = Set.of(
            "add", "commit", "merge", "rebase", "cherry-pick",
            "checkout", "switch", "restore", "stash",
            "pull", "fetch", "clone"
    );

    private static final Set<String> GIT_DANGEROUS_SUBCOMMANDS = Set.of(
            "push", "reset", "clean", "gc", "filter-branch",
            "rebase", "force-push"
    );

    // 网络命令
    private static final Set<String> NETWORK_COMMANDS = Set.of(
            "curl", "wget", "ssh", "scp", "sftp", "rsync",
            "nc", "ncat", "netcat", "telnet", "ftp"
    );

    // 系统破坏性命令
    private static final Set<String> SYSTEM_MUTATING_COMMANDS = Set.of(
            "sudo", "su", "shutdown", "reboot", "halt", "poweroff",
            "mkfs", "mount", "umount", "fdisk", "parted",
            "systemctl", "launchctl", "service",
            "useradd", "userdel", "usermod", "groupadd",
            "iptables", "ufw"
    );

    // ---- 可配置字段 ----

    private Set<String> allowedExecutables;
    private Set<String> allowedEnvKeys;
    private List<Pattern> denyPatterns;
    private boolean shellModeEnabled;
    private boolean defaultApprovalRequired;
    @SuppressWarnings("unused")
    private long lastLoadedTimestamp;

    public ShellExecPolicy() {
        applyDefaults();
    }

    /**
     * 从项目根目录加载策略配置文件。
     * 加载失败时保留当前策略不变（fail-safe）。
     */
    public void loadFromProjectRoot(Path projectRoot) {
        if (projectRoot == null) {
            LOG.warn("Project root is null, using default shell policy");
            return;
        }

        Path configFile = projectRoot.resolve(CONFIG_DIR).resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configFile)) {
            LOG.info("Shell policy config not found at " + configFile + ", using defaults");
            return;
        }

        try {
            String content = Files.readString(configFile);
            JSONObject config = JSON.parseObject(content);
            applyConfig(config);
            lastLoadedTimestamp = System.currentTimeMillis();
            LOG.info("Shell policy loaded from " + configFile);
        } catch (IOException | com.alibaba.fastjson.JSONException e) {
            LOG.warn("Failed to load shell policy config, keeping current policy: " + e.getMessage());
        }
    }

    private void applyDefaults() {
        this.allowedExecutables = new HashSet<>(DEFAULT_ALLOWED_EXECUTABLES);
        this.allowedEnvKeys = new HashSet<>(DEFAULT_ALLOWED_ENV_KEYS);
        this.denyPatterns = compileDenyPatterns(DEFAULT_DENY_PATTERNS);
        this.shellModeEnabled = false;
        this.defaultApprovalRequired = true;
    }

    private void applyConfig(JSONObject config) {
        JSONObject allowlist = config.getJSONObject("allowlist");
        if (allowlist != null) {
            JSONArray executables = allowlist.getJSONArray("executables");
            if (executables != null) {
                Set<String> execs = new HashSet<>();
                for (int i = 0; i < executables.size(); i++) {
                    execs.add(executables.getString(i));
                }
                this.allowedExecutables = execs;
            }
        }

        JSONObject denylist = config.getJSONObject("denylist");
        if (denylist != null) {
            JSONArray patterns = denylist.getJSONArray("patterns");
            if (patterns != null) {
                List<String> patternStrings = new ArrayList<>();
                for (int i = 0; i < patterns.size(); i++) {
                    patternStrings.add(patterns.getString(i));
                }
                this.denyPatterns = compileDenyPatterns(patternStrings);
            }
        }

        JSONObject envWhitelist = config.getJSONObject("environmentWhitelist");
        if (envWhitelist != null) {
            JSONArray keys = envWhitelist.getJSONArray("keys");
            if (keys != null) {
                Set<String> envKeys = new HashSet<>();
                for (int i = 0; i < keys.size(); i++) {
                    envKeys.add(keys.getString(i));
                }
                this.allowedEnvKeys = envKeys;
            }
        }

        Boolean shellMode = config.getBoolean("shellModeEnabled");
        if (shellMode != null) {
            this.shellModeEnabled = shellMode;
        }

        Boolean approvalRequired = config.getBoolean("defaultApprovalRequired");
        if (approvalRequired != null) {
            this.defaultApprovalRequired = approvalRequired;
        }
    }

    private List<Pattern> compileDenyPatterns(List<String> patternStrings) {
        List<Pattern> patterns = new ArrayList<>();
        for (String p : patternStrings) {
            try {
                patterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                LOG.warn("Invalid deny pattern skipped: " + p + " - " + e.getMessage());
            }
        }
        return patterns;
    }

    // ---- 策略查询方法 ----

    /**
     * 检查可执行文件是否在 allowlist 中。
     */
    public boolean isExecutableAllowed(String executable) {
        if (executable == null || executable.isBlank()) {
            return false;
        }
        String normalized = normalizeExecutable(executable);
        return allowedExecutables.contains(normalized);
    }

    /**
     * 检查命令字符串是否匹配 denylist 模式。
     */
    public PolicyViolation checkDenyList(String commandString) {
        if (commandString == null || commandString.isBlank()) {
            return null;
        }
        for (Pattern pattern : denyPatterns) {
            if (pattern.matcher(commandString).find()) {
                return new PolicyViolation(
                        "command_blocked",
                        "Command matches deny pattern: " + pattern.pattern(),
                        Map.of("matchedPattern", pattern.pattern()),
                        "Break the task into safer steps or use dedicated tools."
                );
            }
        }
        return null;
    }

    /**
     * 检查环境变量键是否允许。
     */
    public PolicyViolation checkEnvironmentKey(String key) {
        if (DANGEROUS_ENV_KEYS.contains(key)) {
            return new PolicyViolation(
                    "environment_not_allowed",
                    "Environment variable '" + key + "' is blocked for security reasons",
                    Map.of("key", key, "reason", "dangerous_env_injection"),
                    "Remove the dangerous environment variable."
            );
        }
        if (!allowedEnvKeys.contains(key)) {
            return new PolicyViolation(
                    "environment_not_allowed",
                    "Environment variable '" + key + "' is not in the allowed list",
                    Map.of("key", key),
                    "Only whitelisted environment variables can be set: " + allowedEnvKeys
            );
        }
        return null;
    }

    /**
     * 评估命令的风险等级。
     */
    public ShellRiskLevel assessRiskLevel(List<String> command) {
        if (command == null || command.isEmpty()) {
            return ShellRiskLevel.READ_ONLY;
        }

        String executable = normalizeExecutable(command.get(0));

        if (SYSTEM_MUTATING_COMMANDS.contains(executable)) {
            return ShellRiskLevel.SYSTEM_MUTATING;
        }

        if (NETWORK_COMMANDS.contains(executable)) {
            return ShellRiskLevel.NETWORKED;
        }

        if ("git".equals(executable) && command.size() > 1) {
            String subcommand = command.get(1);
            if (GIT_DANGEROUS_SUBCOMMANDS.contains(subcommand)) {
                return ShellRiskLevel.WORKSPACE_MUTATING;
            }
            if (GIT_MUTATING_SUBCOMMANDS.contains(subcommand)) {
                return ShellRiskLevel.WORKSPACE_MUTATING;
            }
            if (GIT_READONLY_SUBCOMMANDS.contains(subcommand)) {
                return ShellRiskLevel.READ_ONLY;
            }
            return ShellRiskLevel.WORKSPACE_MUTATING;
        }

        if (isBuildCommand(executable, command)) {
            return ShellRiskLevel.WORKSPACE_MUTATING;
        }

        if (isReadOnlyCommand(executable)) {
            return ShellRiskLevel.READ_ONLY;
        }

        return ShellRiskLevel.WORKSPACE_MUTATING;
    }

    public boolean isShellModeEnabled() {
        return shellModeEnabled;
    }

    public boolean isDefaultApprovalRequired() {
        return defaultApprovalRequired;
    }

    /**
     * 判断指定风险等级是否需要审批。
     */
    public boolean requiresApproval(ShellRiskLevel riskLevel) {
        return switch (riskLevel) {
            case READ_ONLY -> false;
            case WORKSPACE_MUTATING -> defaultApprovalRequired;
            case NETWORKED, SYSTEM_MUTATING -> true;
        };
    }

    // ---- 内部辅助方法 ----

    private String normalizeExecutable(String executable) {
        if (executable.contains("/")) {
            int lastSlash = executable.lastIndexOf('/');
            String name = executable.substring(lastSlash + 1);
            if (executable.startsWith("./")) {
                return executable;
            }
            return name;
        }
        return executable;
    }

    private boolean isBuildCommand(String executable, List<String> command) {
        return Set.of("gradle", "./gradlew", "gradlew", "mvn", "./mvnw", "mvnw",
                "make", "cmake", "npm", "npx").contains(executable);
    }

    private boolean isReadOnlyCommand(String executable) {
        return Set.of("pwd", "ls", "echo", "cat", "head", "tail", "wc",
                "uname", "whoami", "date", "which", "env", "printenv",
                "find", "grep", "sort", "uniq", "diff", "tr", "cut",
                "du", "df", "file", "stat", "realpath", "dirname", "basename",
                "java", "javac", "python", "python3", "node"
        ).contains(executable);
    }

    /**
     * 策略违规描述。
     */
    public record PolicyViolation(
            String code,
            String message,
            Map<String, Object> details,
            String suggestion
    ) {
        public Map<String, Object> toErrorMap() {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", code);
            err.put("message", message);
            if (details != null) {
                err.put("details", details);
            }
            if (suggestion != null) {
                err.put("suggestion", suggestion);
            }
            return err;
        }
    }
}
