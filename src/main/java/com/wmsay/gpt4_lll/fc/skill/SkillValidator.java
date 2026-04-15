package com.wmsay.gpt4_lll.fc.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Skill 合规验证器。
 * 对 SkillDefinition 执行三类安全检查：
 * <ol>
 *   <li>敏感词检测（systemPrompt + promptTemplate）</li>
 *   <li>越权指令检测（prompt injection 模式）</li>
 *   <li>工具白名单检测（tools 区段）</li>
 * </ol>
 * 复用 SecurityValidator 的路径遍历和命令注入检测模式（正则表达式），
 * 但不直接依赖 SecurityValidator 类（不同包、不同关注点）。
 *
 * @see SkillDefinition
 */
public class SkillValidator {

    private static final Logger LOG = Logger.getLogger(SkillValidator.class.getName());

    // ── 复用 SecurityValidator 的检测模式 ──────────────────────────────

    /** 路径遍历模式：../ 或 ..\ （与 SecurityValidator.PATH_TRAVERSAL_PATTERN 一致） */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "\\.\\.[\\\\/]"
    );

    /** 命令注入危险字符模式（与 SecurityValidator.COMMAND_INJECTION_PATTERN 一致） */
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            "[;|&`]|\\$\\(|\\|\\||&&"
    );

    // ── 敏感词（危险系统命令）模式 ──────────────────────────────────────

    /** 内置敏感词模式列表 */
    private static final List<Pattern> DEFAULT_DANGEROUS_PATTERNS = Arrays.asList(
            Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE),
            Pattern.compile("format\\s+c:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mkfs\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dd\\s+if=", Pattern.CASE_INSENSITIVE),
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:\\s*&\\s*\\}", Pattern.CASE_INSENSITIVE),  // fork bomb :(){ :|:& }
            Pattern.compile("shutdown\\s+-", Pattern.CASE_INSENSITIVE),
            Pattern.compile("del\\s+/[sfq]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exec\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Runtime\\.getRuntime\\(\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ProcessBuilder", Pattern.CASE_INSENSITIVE),
            Pattern.compile("os\\.system\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("subprocess\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE)
    );

    // ── 越权指令（prompt injection）模式 ─────────────────────────────

    /** 内置 prompt injection 检测模式列表 */
    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("ignore\\s+previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+all\\s+previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("忽略之前的指令"),
            Pattern.compile("忽略以上指令"),
            Pattern.compile("忽略所有指令"),
            Pattern.compile("无视之前的"),
            Pattern.compile("forget\\s+your\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override\\s+system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+a", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+restrictions", Pattern.CASE_INSENSITIVE)
    );

    // ── 可配置的额外敏感词 ──────────────────────────────────────────

    private final List<Pattern> blockedPatterns;

    /**
     * 使用默认配置创建验证器。
     */
    public SkillValidator() {
        this(Collections.emptyList());
    }

    /**
     * 使用自定义 blockedPatterns 创建验证器。
     *
     * @param blockedPatterns 额外的敏感词列表（纯文本，内部转为 Pattern）
     */
    public SkillValidator(List<String> blockedPatterns) {
        if (blockedPatterns == null || blockedPatterns.isEmpty()) {
            this.blockedPatterns = Collections.emptyList();
        } else {
            List<Pattern> compiled = new ArrayList<>();
            for (String raw : blockedPatterns) {
                if (raw != null && !raw.isEmpty()) {
                    compiled.add(Pattern.compile(Pattern.quote(raw), Pattern.CASE_INSENSITIVE));
                }
            }
            this.blockedPatterns = Collections.unmodifiableList(compiled);
        }
    }

    // ── ValidationResult 内部类 ─────────────────────────────────────

    /**
     * 验证结果 — 不可变对象。
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> violations;

        private ValidationResult(boolean valid, List<String> violations) {
            this.valid = valid;
            this.violations = violations == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(violations));
        }

        /** 创建通过验证的结果 */
        public static ValidationResult valid() {
            return new ValidationResult(true, Collections.emptyList());
        }

        /** 创建未通过验证的结果 */
        public static ValidationResult invalid(List<String> violations) {
            return new ValidationResult(false, violations);
        }

        public boolean isValid() { return valid; }

        public List<String> getViolations() { return violations; }
    }

    // ── 核心验证方法 ────────────────────────────────────────────────

    /**
     * 对 SkillDefinition 执行安全合规检查。
     *
     * @param skill            待验证的 Skill 定义
     * @param allowedToolNames 允许使用的工具名称白名单
     * @return 验证结果
     */
    public ValidationResult validate(SkillDefinition skill, List<String> allowedToolNames) {
        if (skill == null) {
            return ValidationResult.invalid(Collections.singletonList("SkillDefinition is null"));
        }

        List<String> violations = new ArrayList<>();

        // 1. 敏感词检测（systemPrompt + promptTemplate）
        checkDangerousPatterns(skill.getSystemPrompt(), "systemPrompt", violations);
        checkDangerousPatterns(skill.getPromptTemplate(), "promptTemplate", violations);

        // 2. 越权指令检测（systemPrompt + promptTemplate）
        checkPromptInjection(skill.getSystemPrompt(), "systemPrompt", violations);
        checkPromptInjection(skill.getPromptTemplate(), "promptTemplate", violations);

        // 3. 路径遍历检测（复用 SecurityValidator 模式）
        checkPathTraversal(skill.getSystemPrompt(), "systemPrompt", violations);
        checkPathTraversal(skill.getPromptTemplate(), "promptTemplate", violations);

        // 4. 命令注入检测（复用 SecurityValidator 模式）
        checkCommandInjection(skill.getSystemPrompt(), "systemPrompt", violations);
        checkCommandInjection(skill.getPromptTemplate(), "promptTemplate", violations);

        // 5. 自定义 blockedPatterns 检测
        checkBlockedPatterns(skill.getSystemPrompt(), "systemPrompt", violations);
        checkBlockedPatterns(skill.getPromptTemplate(), "promptTemplate", violations);

        // 6. 工具白名单检测
        checkToolWhitelist(skill.getTools(), allowedToolNames, violations);

        if (!violations.isEmpty()) {
            LOG.warning("[Skill] Validation failed for skill '" + skill.getName()
                    + "': " + violations.size() + " violation(s) found");
            for (String v : violations) {
                LOG.warning("[Skill]   - " + v);
            }
            return ValidationResult.invalid(violations);
        }

        LOG.fine("[Skill] Validation passed for skill '" + skill.getName() + "'");
        return ValidationResult.valid();
    }

    // ── 私有检测方法 ────────────────────────────────────────────────

    private void checkDangerousPatterns(String text, String fieldName, List<String> violations) {
        if (text == null || text.isEmpty()) return;
        for (Pattern p : DEFAULT_DANGEROUS_PATTERNS) {
            if (p.matcher(text).find()) {
                violations.add("Dangerous pattern detected in " + fieldName
                        + ": matches '" + p.pattern() + "'");
            }
        }
    }

    private void checkPromptInjection(String text, String fieldName, List<String> violations) {
        if (text == null || text.isEmpty()) return;
        for (Pattern p : PROMPT_INJECTION_PATTERNS) {
            if (p.matcher(text).find()) {
                violations.add("Prompt injection pattern detected in " + fieldName
                        + ": matches '" + p.pattern() + "'");
            }
        }
    }

    private void checkPathTraversal(String text, String fieldName, List<String> violations) {
        if (text == null || text.isEmpty()) return;
        if (PATH_TRAVERSAL_PATTERN.matcher(text).find()) {
            violations.add("Path traversal pattern detected in " + fieldName
                    + ": contains '../' or '..\\'");
        }
    }

    private void checkCommandInjection(String text, String fieldName, List<String> violations) {
        if (text == null || text.isEmpty()) return;
        if (COMMAND_INJECTION_PATTERN.matcher(text).find()) {
            violations.add("Command injection pattern detected in " + fieldName
                    + ": contains dangerous characters (;, |, &, `, $())");
        }
    }

    private void checkBlockedPatterns(String text, String fieldName, List<String> violations) {
        if (text == null || text.isEmpty()) return;
        for (Pattern p : blockedPatterns) {
            if (p.matcher(text).find()) {
                violations.add("Blocked pattern detected in " + fieldName
                        + ": matches configured pattern '" + p.pattern() + "'");
            }
        }
    }

    private void checkToolWhitelist(List<String> tools, List<String> allowedToolNames,
                                     List<String> violations) {
        if (tools == null || tools.isEmpty()) return;
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            // No whitelist provided — all tools are disallowed
            for (String tool : tools) {
                violations.add("Tool '" + tool + "' is not in the allowed tool whitelist");
            }
            return;
        }
        for (String tool : tools) {
            if (!allowedToolNames.contains(tool)) {
                violations.add("Tool '" + tool + "' is not in the allowed tool whitelist");
            }
        }
    }
}
